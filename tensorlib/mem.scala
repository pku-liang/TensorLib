package systolic

import chisel3._
import chisel3.util._
import scala.math._
//import chisel3.Driver
import chisel3.iotesters.{PeekPokeTester, Driver}
import java.io.PrintWriter
import breeze.linalg._
object log{
  def apply(x: Int):Int={
    (log10(x)/log10(2)).toInt+2
  }
}
class MemController(
    // to pe: latency -- len -- group
    // to mem: len -- latency -- group
    // low -- high
    mem_size: Int,
    data_width: Int,
    addr_width: Int,
    wr_dim: Array[Int],
    rd_dim: Array[Int],
    wr_pattern: Array[Int],
    rd_pattern: Array[Int],
    wr_init: Array[Int],
    rd_init: Array[Int]
  ) extends Module{
  println("wr_dim:",DenseVector[Int](wr_dim))
  val mem = Module(new MultiDimMem(addr_width,wr_dim, rd_dim, wr_init, rd_init, mem_size, data_width)).io
  val wr_time = Module(new MultiDimTime(addr_width,wr_pattern,wr_init)).io
  val rd_time = Module(new MultiDimTime(addr_width,rd_pattern,rd_init)).io
  val io = IO(new Bundle{
    val rd_valid = Input(Bool())
    val wr_valid = Input(Bool())
    val rd_data = Output(Valid(UInt(data_width.W)))
    val wr_data = Input(Valid(UInt(data_width.W)))
  })
  wr_time.in := io.wr_valid
  mem.wr_addr.bits := wr_time.out
  mem.wr_addr.valid := io.wr_valid && mem.final_wr_addr < mem_size.asUInt

  rd_time.in := io.rd_valid
  mem.rd_addr.bits := rd_time.out
  mem.rd_addr.valid := io.rd_valid && mem.final_rd_addr < mem_size.asUInt


  io.rd_data := mem.rd_data
  mem.wr_data := io.wr_data
}
// time: input bool, output time stamp, 0 cycle delay
class MultiDimTime(addr_width: Int, dim: Array[Int], init: Array[Int]) extends Module{
  val len = dim.length
  val io = IO(new Bundle{
    val in = Input(Bool())
    

    // out=0, back to 0
    // out=1, add 1
    // out=2, keep
    val out = Output(Vec(len, UInt(2.W)))
    val index = Output(Vec(len, UInt(addr_width.W)))
  })
  val regs = for(i <- 0 until len)yield{
    RegInit(init(i).asUInt(addr_width.W))
  }
  val next = (0 until len).map(i=>regs(i)+io.in===dim(i).asUInt)
  val back = (0 until len).map(i=>{
    next.slice(0,i+1).reduce(_ && _)
  })
  //regs(0):= Mux(next(0)===dim(0), 0.U, next(0))
  //printf("reg: %d %d, back:%d %d\n",reg(0), reg(1), back(0),back(1))
  for(i <- 0 until len){
    io.index(i) := regs(i)
    if(i!=0){
      when(back(i-1)){
        when(back(i)){
          regs(i) := 0.U
          io.out(i) := 0.U
        }.otherwise{
          regs(i) := regs(i)+io.in
          io.out(i) := 1.U
        }
      }.otherwise{
        io.out(i) := 2.U
      }
    }else{
      when(back(i)){
        regs(i) := 0.U
        io.out(i) := 0.U
      }.otherwise{
        regs(i) := regs(i)+io.in
        io.out(i) := 1.U
      }
    }
  }
  // io.out(1):=out_reg(1)
  // io.out(2):=out_reg(2)
  // for(i <- 0 until len){
  //   io.out(i) := out_reg(i)
  // }
}
class MultiDimMem(addr_width: Int, wr_dim: Array[Int], rd_dim: Array[Int], wr_init: Array[Int], rd_init: Array[Int], size: Int, dw: Int) extends Module{
  val len = wr_dim.length
  val io = IO(new Bundle{
    val rd_addr = Input(Valid(Vec(len, UInt(2.W))))
    val rd_data = Output(Valid(UInt(dw.W)))
    val wr_addr = Input(Valid(Vec(len, UInt(2.W))))
    val wr_data = Input(Valid(UInt(dw.W)))
    val final_rd_addr = Output(UInt(addr_width.W))
    val final_wr_addr = Output(UInt(addr_width.W))
  })
  val mem = SyncReadMem(size, UInt((dw+1).W))
  val rd_addr_reg = RegInit({
    val b = Wire(Input(Valid(new HeterogeneousBag(
      (0 until len).map(i=>UInt(addr_width.W))
    ))))
    b.valid := false.B
    for(i <- 0 until len){
      b.bits(i) := (rd_init(i) * rd_dim(i)).asUInt
    }
    b
  })
  val rd_part_addr = (0 until len).map(i=>{
    MuxLookup(io.rd_addr.bits(i), rd_addr_reg.bits(i), Array(0.U -> 0.U, 1.U -> (rd_addr_reg.bits(i) + rd_dim(i).asUInt)))
    //Mux(io.rd_addr.bits(i)===0.U, 0.U, rd_addr_reg.bits(i) + rd_dim(i).asUInt)
  })
  
  val mem_rd_addr = rd_addr_reg.bits.reduce(_+_)
  rd_addr_reg.valid := io.rd_addr.valid
  for(i <- 0 until len){
    rd_addr_reg.bits(i) := rd_part_addr(i)
  }
  
  val mem_output = mem.read(rd_addr_reg.bits.reduce(_+_),rd_addr_reg.valid)
  io.rd_data.valid := mem_output(dw)
  io.rd_data.bits := mem_output(dw-1, 0)
  val wr_addr_reg = RegInit({
    val b = Wire(Input(Valid(new HeterogeneousBag(
      (0 until len).map(i=>UInt(addr_width.W))
    ))))
    b.valid := false.B
    for(i <- 0 until len){
      println(i, wr_init(i), wr_dim(i))
      b.bits(i) := (wr_init(i) * wr_dim(i)).asUInt
    }
    b
  })
  val wr_part_addr = (0 until len).map(i=>{
    MuxLookup(io.wr_addr.bits(i), wr_addr_reg.bits(i), Array(0.U -> 0.U, 1.U -> (wr_addr_reg.bits(i) + wr_dim(i).asUInt)))
  })
  
  wr_addr_reg.valid := RegNext(io.wr_addr.valid, false.B)
  for(i <- 0 until len){
    wr_addr_reg.bits(i) := wr_part_addr(i)
  }
  val wr_data_reg = RegNext(io.wr_data, 0.U.asTypeOf(chiselTypeOf(io.wr_data)))
  val mem_wr_addr = wr_addr_reg.bits.reduce(_+_)
  
  when(wr_addr_reg.valid){
    mem.write(mem_wr_addr, Cat(wr_data_reg.valid, wr_data_reg.bits))
  }
  printf("wr_addr:%d, rd_addr:%d\n",mem_wr_addr, mem_rd_addr)

  io.final_wr_addr := mem_wr_addr
  io.final_rd_addr := mem_rd_addr 
}
