package systolic

import chisel3._
import chisel3.util._
import scala.math._
//import chisel3.Driver
import chisel3.iotesters.{PeekPokeTester, Driver}
import java.io.PrintWriter
import breeze.linalg._

object Gen_OS_GEMM extends App{
	val pe_size = (4, 4)
	val vec = Array(1, 1, 1)
	val width = Array(16,16,16)
	val stt = DenseMatrix((1,0,0),(0,1,0),(1,1,1))
  val time_range = Array(16, 16, 16)
	val access = Array(
		DenseMatrix((1, 0, 0), (0, 0, 1)),
		DenseMatrix((0, 1, 0), (0, 0, 1)),
		DenseMatrix((1, 0, 0), (0, 1, 0))
		)
	val io_type = Array(true,true,false)
	val latency = 1
	val op_type = 0
	val m = new PEArray2D(
    pe_size, 
    vec, 
    width, 
    16,
    stt, 
    access, 
    io_type, 
    latency,  
    op_type, 
    time_range
  )
  val str = chisel3.Driver.emitVerilog(m)
  new PrintWriter("out.v") { write(str); close }
}
