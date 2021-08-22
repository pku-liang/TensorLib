package tensorlib

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
    vec: Int,
    addr_width: Int,
    wr_dim: Array[Int],
    rd_dim: Array[Int],
    wr_pattern: Array[Int],
    rd_pattern: Array[Int],
    wr_init: Array[Int],
    rd_init: Array[Int]
  ) extends Module{
  val all_width = data_width * vec
  println("wr_dim:",DenseVector[Int](wr_dim), "rd_dim:", DenseVector[Int](rd_dim))

  val mem = Module(new MultiDimMem(addr_width,wr_dim, rd_dim, wr_init, rd_init, mem_size, data_width, vec)).io
  val wr_time = Module(new MultiDimTime(addr_width,wr_pattern,wr_init)).io
  val rd_time = Module(new MultiDimTime(addr_width,rd_pattern,rd_init)).io
  val io = IO(new Bundle{
    val rd_valid = Input(Bool())
    val wr_valid = Input(Bool())
    val wr_update = Input(Bool())
    val rd_data = Output(Valid(UInt(all_width.W)))
    val wr_data = Input(Valid(UInt(all_width.W)))
  })
  //printf("controller rd_v:%d, wr_v:%d\n",io.rd_valid, io.wr_valid)
  wr_time.in := io.wr_valid
  mem.wr_addr.bits := wr_time.out
  mem.wr_addr.valid := io.wr_valid //&& mem.final_wr_addr < mem_size.asUInt
  mem.wr_update := io.wr_update
  rd_time.in := io.rd_valid
  mem.rd_addr.bits := rd_time.out
  mem.rd_addr.valid := io.rd_valid //&& mem.final_rd_addr < mem_size.asUInt


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
  //val scale_dim = (0 until len).map(i=>{dim.slice(0, i+1).reduce(_*_)})
  val regs = for(i <- 0 until len)yield{
    RegInit(init(i).asUInt(addr_width.W))
  }
  val back = (0 until len).map(i=>regs(i)+io.in===dim(i).asUInt)
  // val back = (0 until len).map(i=>{
  //   next.slice(0,i+1).reduce(_ && _)
  // })
  //regs(0):= Mux(next(0)===dim(0), 0.U, next(0))
  //printf("reg: %d %d %d, back:%d %d %d, out: %d %d %d\n",regs(0), regs(1), regs(2), back(0),back(1), back(2), io.out(0), io.out(1), io.out(2))
  for(i <- 1 until len){
    io.index(i) := regs(i)
      //io.out(i):= (!back(i))    // 1: +1, 0: back to 0
    //io.out(i) := ((!back(i-1)) << 1) | (!back(i))  // 1: keep
    val back_pre = back.slice(0, i).reduce(_ && _)
    io.out(i) := ((!back_pre) << 1) | (!back(i))
    when(back_pre){
      when(back(i)){
        regs(i) := 0.U
      }.otherwise{
        regs(i) := regs(i)+io.in
      }
    }
      // when(back(i-1)){    //
        // when(back(i)){
        //   regs(i) := 0.U
        // }.otherwise{
        //   regs(i) := regs(i)+io.in
        // }
      // }
  }
  io.index(0) := regs(0)
  when(back(0)){
    regs(0) := 0.U
    io.out(0) := 0.U
  }.otherwise{
    regs(0) := regs(0)+io.in
    io.out(0) := io.in
  }
  // io.out(1):=out_reg(1)
  // io.out(2):=out_reg(2)
  // for(i <- 0 until len){
  //   io.out(i) := out_reg(i)
  // }
}
class MultiDimMem(addr_width: Int, wr_dim: Array[Int], rd_dim: Array[Int], wr_init: Array[Int], rd_init: Array[Int], size: Int, dw: Int, vec: Int) extends Module{
  val len_wr = wr_dim.length
  val len_rd = rd_dim.length
  val all_width = dw * vec
  val io = IO(new Bundle{
    val rd_addr = Input(Valid(Vec(len_rd, UInt(2.W))))
    val rd_data = Output(Valid(UInt(all_width.W)))
    val wr_addr = Input(Valid(Vec(len_wr, UInt(2.W))))
    val wr_data = Input(Valid(UInt(all_width.W)))
    val wr_update = Input(Bool())
  })
  println("wr_dim:"+wr_dim.mkString(" "))
  println("wr_init:"+wr_init.mkString(" "))
  
  println("rd_dim:"+rd_dim.mkString(" "))
  println("rd_init:"+rd_init.mkString(" "))
  val mem = SyncReadMem(size, UInt((all_width+1).W))
  val rd_addr_reg = RegInit({
    val b = Wire(Input(Valid(new HeterogeneousBag(
      (0 until len_rd).map(i=>UInt(addr_width.W))
    ))))
    b.valid := false.B
    for(i <- 0 until len_rd){
      b.bits(i) := (rd_init(i) * rd_dim(i)).asUInt
    }
    b
  })
  val rd_part_addr = (0 until len_rd).map(i=>{
    MuxLookup(io.rd_addr.bits(i), rd_addr_reg.bits(i), Array(0.U -> 0.U, 1.U -> (rd_addr_reg.bits(i) + rd_dim(i).asUInt)))
    //Mux(io.rd_addr.bits(i)===0.U, 0.U, rd_addr_reg.bits(i) + rd_dim(i).asUInt)
  })
  
  val mem_rd_addr = rd_addr_reg.bits.reduce(_+_)
  rd_addr_reg.valid := io.rd_addr.valid
  for(i <- 0 until len_rd){
    rd_addr_reg.bits(i) := rd_part_addr(i)
  }
  
  
  val mem_req_valid = RegNext(rd_addr_reg.valid, false.B)
  
  val wr_addr_reg = RegInit({
    val b = Wire(Input(Valid(new HeterogeneousBag(
      (0 until len_wr).map(i=>UInt(addr_width.W))
    ))))
    b.valid := false.B
    for(i <- 0 until len_wr){
      //println(i, wr_init(i), wr_dim(i))
      b.bits(i) := (wr_init(i) * wr_dim(i)).asUInt
    }
    b
  })
  val wr_part_addr = (0 until len_wr).map(i=>{
    MuxLookup(io.wr_addr.bits(i), wr_addr_reg.bits(i), Array(0.U -> 0.U, 1.U -> (wr_addr_reg.bits(i) + wr_dim(i).asUInt)))
  })
  
  
  val mem_wr_addr = wr_addr_reg.bits.reduce(_+_)
  for(i <- 0 until len_wr){
    wr_addr_reg.bits(i) := wr_part_addr(i)
  }

  // if update write, cycle 0: read valid signal, generate address
  
  
  
  val wr_data_1 = RegNext(io.wr_data, 0.U.asTypeOf(chiselTypeOf(io.wr_data)))
  val wr_update_1 = RegNext(io.wr_update, false.B)
  wr_addr_reg.valid := io.wr_addr.valid
  // cycle 1: read memory
  val final_rd_addr = Mux(wr_update_1, mem_wr_addr, mem_rd_addr)
  val final_rd_valid = Mux(wr_update_1, wr_addr_reg.valid, rd_addr_reg.valid)
  val mem_output = mem.read(final_rd_addr,final_rd_valid)
  val wr_data_2 = RegNext(wr_data_1, 0.U.asTypeOf(chiselTypeOf(io.wr_data)))
  val wr_update_2 = RegNext(wr_update_1, false.B)
  val wr_addr_2 = RegNext(mem_wr_addr, 0.U)
  val wr_addr_valid_2 = RegNext(wr_addr_reg.valid, false.B)


  //cycle 2: add with input
  val reduction_cell = Module(new Reduction_Dummy(vec, dw)).io
  reduction_cell.in_a := mem_output(dw-1, 0)
  reduction_cell.in_b := wr_data_2.bits

  val wr_data_final = RegNext(Mux(wr_update_2, reduction_cell.out, wr_data_2.bits))
  val wr_addr_3 = RegNext(wr_addr_2, 0.U)
  val wr_valid_3 = RegNext(wr_data_2.valid)


  // cycle 3: write to mem
  when(wr_valid_3){
    mem.write(wr_addr_3, (wr_valid_3 << dw.asUInt) | wr_data_final)
    //mem.write(wr_addr_3, wr_data_final)
  }


  io.rd_data.valid := RegNext(mem_req_valid && mem_output(dw))
  io.rd_data.bits := RegNext(Mux(mem_req_valid, mem_output(dw-1, 0), 0.U))
  
  
  
  printf("rd_addr: %d, rd_data: %d %d\n",final_rd_addr, io.rd_data.valid, io.rd_data.bits)
  
  //printf("wr_data2: %d, wr_addr:%d %d, wr_data: %d, rd_addr:%d, rd_data: %d %d\n",wr_data_2.bits, wr_addr_3, wr_valid_3, wr_data_final, final_rd_addr, io.rd_data.bits, io.rd_data.valid)

  // printf("memory:\n")
  // for(i<- 0 until 50){
  //   printf("%d ",mem.read(i.asUInt))
  //   if(i%10==9)
  //     printf("\n")
  // }
}
