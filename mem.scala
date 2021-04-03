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
// time: input bool, output time stamp, 0 cycle delay
class MultiDimTime(addr_width: Int, dim: Array[Int], init: Array[Int]) extends Module{
  val len = dim.length
  val io = IO(new Bundle{
    val in = Input(Bool())
    

    // out=0, back to 0
    // out=1, add 1
    // out=2, keep
    val out = new HeterogeneousBag(
      for(i <- 0 until len) yield{
        Output(UInt(addr_width.W))
    })
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
  // for(i <- 0 until len){
  //   io.out(i) := regs(i)
  // }
}
class MultiDimMem(addr_width: Int, wr_dim: Array[Int], rd_dim: Array[Int], size: Int, dw: Int) extends Module{
  val len = wr_dim.length
  val io = IO(new Bundle{
    val rd_addr = Input(Valid(new HeterogeneousBag(
      (0 until len).map(i=>UInt(addr_width.W))
    )))
    val rd_data = Output(Valid(UInt(dw.W)))
    val wr_addr = Input(Valid(new HeterogeneousBag(
      (0 until len).map(i=>UInt(addr_width.W))
    )))
    val wr_data = Input(UInt(dw.W))
  })
  val mem = SyncReadMem(size, UInt(dw.W))
  val rd_addr_reg = RegInit(0.U.asTypeOf(chiselTypeOf(io.rd_addr)))
  val rd_part_addr = (0 until len).map(i=>{
    MuxLookup(io.rd_addr.bits(i), rd_addr_reg.bits(i), Array(0.U -> 0.U, 1.U -> (rd_addr_reg.bits(i) + rd_dim(i).asUInt)))
    //Mux(io.rd_addr.bits(i)===0.U, 0.U, rd_addr_reg.bits(i) + rd_dim(i).asUInt)
  })
  
  val mem_rd_addr = rd_addr_reg.bits.reduce(_+_)
  rd_addr_reg.valid := io.rd_addr.valid
  for(i <- 0 until len){
    rd_addr_reg.bits(i) := rd_part_addr(i)
  }
  io.rd_data.valid := RegNext(rd_addr_reg.valid, false.B)
  io.rd_data.bits := mem.read(rd_addr_reg.bits.reduce(_+_),rd_addr_reg.valid)
  val wr_addr_reg = RegInit(0.U.asTypeOf(chiselTypeOf(io.wr_addr)))
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
    mem.write(mem_wr_addr, wr_data_reg)
  }
  printf("wr_addr:%d, rd_addr:%d\n",mem_wr_addr, mem_rd_addr)
}