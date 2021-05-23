package systolic

import chisel3._
import chisel3.util._
import scala.math._
//import chisel3.Driver
import chisel3.iotesters.{PeekPokeTester, Driver}
import java.io.PrintWriter
import breeze.linalg._
class Test_Mem extends Module{
  val io =IO(new Bundle{})
  
  val data_width = Array(32, 32, 32)
  val addr_width = 20
  val pe_size = (10, 16)
  val mat_len = 6
  val latency = 4
  val rnd = new scala.util.Random
  val num_buffer = 2
  // access: latency * dim * double
  val num_operand = 3
  val rd_init = Array(0,0,0)
  val wr_init = Array(0,0,1)
  val mem_dim = Array(1, latency, latency*mat_len)
  val mem_out_dim = Array(1, latency, latency*pe_size._2)
  val mem_time = Array(latency, mat_len, num_buffer)
  val mem_out_time = Array(latency, pe_size._2, num_buffer)
  val mem_out_size = latency*pe_size._2*num_buffer
  val mem_size = latency*mat_len*num_buffer
  println("mem_size:",mem_size)
  val mem = Module(new MemController(
  // to pe: latency -- len -- group
  // to mem: len -- latency -- group
  // low -- high
    mem_size,
    data_width(0),
    addr_width,
    mem_dim,
    mem_dim,
    mem_time,
    mem_time,
    wr_init,
    rd_init
  )).io
  mem.rd_valid := true.B
  mem.wr_valid := true.B
  mem.wr_data := 1.U
  printf("%d %d\n",mem.rd_data.valid, mem.rd_data.bits)
}
class Test_Mem_Tester(c: Test_Mem) extends PeekPokeTester(c){
  for(i <- 0 until 10000){
    step(1)
  }
}
object Test_Mem_App extends App{
  Driver(() => new Test_Mem())(c => new Test_Mem_Tester(c))
}