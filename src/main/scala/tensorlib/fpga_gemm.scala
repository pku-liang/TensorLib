package tensorlib

import chisel3._
import chisel3.util._
import scala.math._
//import chisel3.Driver
import chisel3.iotesters.{PeekPokeTester, Driver}
import java.io.PrintWriter
import breeze.linalg._

class PEArrayTop(config: SAConfig) extends Module{
  val io = IO(new Bundle{
    val in_a = Input(Valid(Valid(UInt(256.W))))
    val in_b = Input(Valid(Valid(UInt(32.W))))
    val out_c = Output(Valid(UInt(256.W)))
    val exec_valid = Input(Bool())
    val out_valid = Input(Bool())
  })
  val pearray = Module(new PEArray(config)).io
  pearray.exec_valid := io.exec_valid
  pearray.out_valid := io.out_valid
  for(i <- 0 until 11){
    pearray.data(2).in.get(i).valid := io.in_a.valid
    pearray.data(2).in.get(i).bits.valid := io.in_a.bits.valid
    pearray.data(2).in.get(i).bits.bits := io.in_a.bits.bits + i.asUInt
    
  }
  for(i <- 0 until 15){
    pearray.data(1).in.get(i).valid := io.in_a.valid
    pearray.data(1).in.get(i).bits.valid := io.in_a.bits.valid
    pearray.data(1).in.get(i).bits.bits := io.in_a.bits.bits + i.asUInt
  }
  val pe_out_data = VecInit(pearray.data(0).out.get.toArray).map(_.bits).reduce((x, y)=>{
    x+y
  })
  val pe_out_valid = VecInit(pearray.data(0).out.get.toArray).map(_.valid).reduce((x, y)=>{
    x|y
  })
  io.out_c.bits := pe_out_data
  io.out_c.valid := pe_out_valid
}
object FPGA_GEMM extends App{
  
  val k_len = 15
  val c_len = 128
  val x_len = 11
  
  val opSpec = new OperatorSpec {
    val k :: c :: x :: Nil = genIterators(3)
    val o :: w :: i :: Nil = genTensor(3)

    setExpr(o(k)(x) += w(k)(c) * i(c)(x))
    k.setRange(k_len)
    c.setRange(c_len)
    x.setRange(x_len)
    setLatency(12)
    o.setWidth(256)
    w.setWidth(32)
    i.setWidth(256)
    useCustomKernel(true)
  }
  val stt = DenseMatrix(
   (1,  0,  0),  
   (0,  0,  1),  
   (1,  1,  1),  
  )
  val config = Gen_dataflow(opSpec, stt)
  chisel3.Driver.execute(args, () => new PEArray(config))

}