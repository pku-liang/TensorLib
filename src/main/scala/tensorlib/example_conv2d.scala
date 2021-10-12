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
object Example_GenConv2D extends App {
  //  Conv2D calculation algorithm
  // definition
  val opSpec = new OperatorSpec {
    val k :: c :: y :: x :: r :: s :: Nil = genIterators(6)
    val o :: w :: i :: Nil                = genTensor(3)

    setExpr(o(k)(y)(x) += w(k)(c)(r)(s) * i(c)(y + r)(x + s))
    k.setRange(16)
    c.setRange(16)
    y.setRange(6)
    x.setRange(20)
    r.setRange(3)
    s.setRange(3)
    setLatency(12)
    o.setWidth(16)
    w.setWidth(16)
    i.setWidth(16)
  }
  val stt = DenseMatrix(
    (1,0,0,0,0,0), 
    (0,1,0,0,0,0), 
    (1,1,0,1,0,0), 
    (0,0,0,0,0,1), 
    (0,0,0,0,1,0), 
    (0,0,1,0,0,0)
  )
  val config = Gen_dataflow(opSpec, stt)
  chisel3.Driver.execute(args, () => new PEArray(config))
}

object Gen_GEMM_Dataflows extends App {
  //  Conv2D calculation algorithm
  // definition
  val opSpec = new OperatorSpec {
    val k :: c :: x :: Nil = genIterators(3)
    val o :: w :: i :: Nil = genTensor(3)

    setExpr(o(k)(x) += w(k)(c) * i(c)(x))
    k.setRange(4)
    c.setRange(4)
    x.setRange(20)
    setLatency(1)
    o.setWidth(16)
    w.setWidth(16)
    i.setWidth(16)
  }
  var mat = DenseMatrix((0,1,0),(1,0,0),(1,1,1))
  var num_df = 0
  for(m <- 0 until 512){
    var g = m
    for(i <- 0 until 3){
      for(j <- 0 until 3){
        mat(i,j)=g%2
        g =g/2
      }
    }
    if(TestMat(mat)){
      val config = Gen_dataflow(opSpec, mat)
      val str = chisel3.Driver.emitVerilog(new PEArray(config))
      new PrintWriter(s"PEArray_$num_df.v") { write(str); close }
      num_df = num_df + 1
    }
  }
}
