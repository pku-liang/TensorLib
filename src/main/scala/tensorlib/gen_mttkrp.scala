package tensorlib

import chisel3._
import chisel3.util._

import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import org.scalatest._
import chisel3._

import breeze.linalg._
import breeze.numerics._
import chisel3.stage.ChiselStage
import java.io.PrintWriter

object Gen_Mttkrp extends App{
  val opSpec = new OperatorSpec {
    val i :: j :: k :: l :: o :: Nil = genIterators(5)
    val y :: a :: b :: c :: Nil                = genTensor(4)

    setExpr(y(o)(i)(j) += a(o)(i)(k)(l) * b(k)(j) * c(l)(j))
    i.setRange(12)
    j.setRange(12)
    k.setRange(12)
    l.setRange(12)
    o.setRange(4)
    setLatency(12)
    y.setWidth(256)
    a.setWidth(256)
    b.setWidth(32)
    c.setWidth(32)
    useMTTKRP(2)
  }
  val stt = DenseMatrix(
   (1,  0,  0, 0, 0),  
   (0,  1,  0, 0, 0),  
   (1,  1,  1, 0, 0),
   (0,  0,  0, 1, 0),
   (0,  0,  0, 0, 1)  
  )
  val config = Gen_dataflow(opSpec, stt, false)
  val str = chisel3.Driver.emitVerilog(new PEArray(config))
  new PrintWriter(s"PEArray_mttkrp.v") { write(str); close }
}