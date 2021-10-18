package tensorlib

import chisel3._
import chisel3.util._
import scala.math._
//import chisel3.Driver
import chisel3.iotesters.{PeekPokeTester, Driver}
import java.io.PrintWriter
import breeze.linalg._

object Example_GenGEMM extends App{
  
  val k_len = 8
  val c_len = 20
  val x_len = 8
  
  val opSpec = new OperatorSpec {
    val k :: c :: x :: Nil = genIterators(3)
    val o :: w :: i :: Nil = genTensor(3)

    setExpr(o(k)(x) += w(k)(c) * i(c)(x))
    k.setRange(k_len)
    c.setRange(c_len)
    x.setRange(x_len)
    setLatency(1)
    o.setWidth(16)
    w.setWidth(16)
    i.setWidth(16)
  }
  val stt = DenseMatrix(
   (1,  0,  0),  
   (0,  0,  1),  
   (1,  1,  1),  
  )
  val config = Gen_dataflow(opSpec, stt)
  chisel3.Driver.execute(args, () => new PEArray(config))
}