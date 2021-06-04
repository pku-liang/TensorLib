package systolic

import chisel3._
import chisel3.util._
import scala.math._
//import chisel3.Driver
import chisel3.iotesters.{PeekPokeTester, Driver}
import java.io.PrintWriter
import breeze.linalg._

object Gen_OS_GEMM extends App{
	val pe_size = (15, 11)
	val vec = Array(1, 8, 8)
	val width = Array(32,32,32)
	val stt = DenseMatrix((1,0,0),(0,1,0),(1,1,1))
  val latency = 12
  val time_range = Array(latency, 16, 16)
	val access = Array(
		DenseMatrix((1, 0, 0), (0, 0, 1)),
		DenseMatrix((0, 1, 0), (0, 0, 1)),
		DenseMatrix((1, 0, 0), (0, 1, 0))
		)
	val io_type = Array(true,true,false)
	
	val op_type = 1
  val back = false
  val str = chisel3.Driver.emitVerilog(new PEArray2D(
    pe_size, 
    vec, 
    width, 
    16,
    stt, 
    access, 
    io_type, 
    latency,  
    op_type, 
    time_range,
    back))
  new PrintWriter("out.v") { write(str); close }
}

object Gen_OS_GEMM_INT extends App{
	val pe_size = (30, 22)
	val vec = Array(1, 8, 8)
	val width = Array(16,16,16)
	val stt = DenseMatrix((1,0,0),(0,1,0),(1,1,1))
  val latency = 12
  val time_range = Array(latency, 16, 16)
	val access = Array(
		DenseMatrix((1, 0, 0), (0, 0, 1)),
		DenseMatrix((0, 1, 0), (0, 0, 1)),
		DenseMatrix((1, 0, 0), (0, 1, 0))
		)
	val io_type = Array(true,true,false)
	
	val op_type = 1
  val back = false
  val str = chisel3.Driver.emitVerilog(new PEArray2D(
    pe_size, 
    vec, 
    width, 
    16,
    stt, 
    access, 
    io_type, 
    latency,  
    op_type, 
    time_range,
    back))
  new PrintWriter("OSGEMM_INT_30x22.v") { write(str); close }
}
class TestTop extends Module{
  val io =IO(new Bundle{

  })
  val pe_size = (4, 4)
	val vec = Array(1, 1, 1)
	val width = Array(16,16,16)
	val stt = DenseMatrix(
    (1,0,0),
    (0,1,0),
    (1,1,1)
  )  // OS
  // val stt = DenseMatrix(
  //   (0,0,1),
  //   (0,1,0),
  //   (1,1,1)
  // )  // WS
  val latency = 12
  val time_range = Array(latency, 16, 16)
	val access = Array(
		DenseMatrix((1, 0, 0), (0, 0, 1)),
		DenseMatrix((0, 1, 0), (0, 0, 1)),
		DenseMatrix((1, 0, 0), (0, 1, 0))
		)
	val io_type = Array(true,true,false)
	val back = false
	val op_type = 0
  val top = Module(new PEArray2D(
    pe_size, 
    vec, 
    width, 
    16,
    stt, 
    access, 
    io_type, 
    latency,  
    op_type, 
    time_range,
    back)).io
  val cycle = RegInit(0.U(10.W))
  cycle := cycle + 1.U
  printf("cycle: %d\n",cycle)
  top.out_valid := false.B
  when(cycle < 20.U){
    top.exec_valid := false.B
    for(i <- 0 until 2){
      for(j <- 0 until 4){
        top.data(i).in.get(j).valid := true.B
        top.data(i).in.get(j).bits.valid := true.B
        top.data(i).in.get(j).bits.bits := 1.U
      }
    }
  }.otherwise{
    top.exec_valid := true.B
    for(i <- 0 until 2){
      for(j <- 0 until 4){
        top.data(i).in.get(j).valid := true.B
        top.data(i).in.get(j).bits.valid := true.B
        top.data(i).in.get(j).bits.bits := 1.U
      }
    }
  }
}

class Test_Tester(c: TestTop) extends PeekPokeTester(c){
  for(i <- 0 until 800){
    step(1)
  }
}
object Test_App extends App{
  Driver(() => new TestTop())(c => new Test_Tester(c))
}