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
class MultiDimTime(dim: Array[Int], init: Array[Int]) extends Module{
  val len = dim.length
  val width = for(i <- 0 until len)yield{
    ceil(log(dim(i)+1)).toInt
  }
  val io = IO(new Bundle{
    val in = Input(Bool())
    val out = new HeterogeneousBag(
      for(i <- 0 until len) yield{
        Output(UInt(width(i).W))
    })
  })
  val regs = for(i <- 0 until len)yield{
    RegInit(init(i).asUInt(width(i).W))
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
        }.otherwise{
          regs(i) := regs(i)+io.in
        }
      }
    }else{
      when(back(i)){
        regs(i) := 0.U
      }.otherwise{
        regs(i) := regs(i)+io.in
      }
    }
  }
  for(i <- 0 until len){
    io.out(i) := regs(i)
  }
}
class MultiDimMem(dim: Array[Int], size: Int, dw: Int) extends Module{
  val len = dim.length
  val width = for(i <- 0 until len)yield{
    ceil(log(dim(i)+1)).toInt
  }
  val io = IO(new Bundle{
    val rd_addr = Input(Valid(new HeterogeneousBag(
      (0 until len).map(i=>UInt(width(i).W))
    )))
    val rd_data = Output(Valid(UInt(dw.W)))
    val wr_addr = Input(Valid(new HeterogeneousBag(
      (0 until len).map(i=>UInt(width(i).W))
    )))
    val wr_data = Input(UInt(dw.W))
  })
  val mem = SyncReadMem(size, UInt(dw.W))
  val rd_part_addr = (0 until len).map(i=>{
    dim(i).asUInt * io.rd_addr.bits(i)
  })
  val rd_addr_reg = RegInit(0.U.asTypeOf(chiselTypeOf(io.rd_addr)))
  rd_addr_reg.valid := io.rd_addr.valid
  for(i <- 0 until len){
    rd_addr_reg.bits(i) := rd_part_addr(i)
  }
  io.rd_data.valid := RegNext(rd_addr_reg.valid, false.B)
  io.rd_data.bits := mem.read(rd_addr_reg.bits.reduce(_+_),rd_addr_reg.valid)
  val wr_part_addr = (0 until len).map(i=>{
    dim(i).asUInt * io.wr_addr.bits(i)
  })
  val wr_addr_reg = RegInit(0.U.asTypeOf(chiselTypeOf(io.wr_addr)))
  wr_addr_reg.valid := io.wr_addr.valid
  val wr_data_reg = RegNext(io.wr_data, 0.U.asTypeOf(chiselTypeOf(io.wr_data)))
  when(wr_addr_reg.valid){
    mem.write(wr_addr_reg.bits.reduce(_+_), wr_data_reg)
  }
}