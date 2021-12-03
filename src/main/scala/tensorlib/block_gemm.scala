package tensorlib

import chisel3._
import chisel3.util._
import scala.math._
//import chisel3.Driver
import chisel3.iotesters.{PeekPokeTester, Driver}
import java.io.PrintWriter
import breeze.linalg._

object Block_GEMM extends App{
  
  val k_len = 15
  val c_len = 128
  val x_len = 11
  
  val opSpec = new OperatorSpec {
    val k :: m :: n :: om :: on :: Nil = genIterators(5)
    val o :: w :: i :: Nil = genTensor(3)
    // 
    setExpr(o(om)(on)(m)(n) += w(om)(m)(k) * i(on)(k)(n))
    k.setRange(64)
    m.setRange(8)
    n.setRange(8)
    om.setRange(8)
    on.setRange(8)
    setLatency(1)
    o.setWidth(16)
    w.setWidth(16)
    i.setWidth(16)
  }
  val stt = DenseMatrix(
   
   (0,  1,  0,  0,  0),  
   (0,  0,  1,  0,  0),  
   (1,  1,  1,  0,  0),  
   (0,  0,  0,  1,  0),
   (0,  0,  0,  0,  1)
  )
  val config = Gen_dataflow(opSpec, stt)
  chisel3.Driver.execute(args, () => new PEArray(config))
}