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
object Example_GenConv2DWS extends App {
  //  Conv2D calculation algorithm
  // o(k)(y)(x) += w(k)(c)(r)(s) * i(c)(y + r)(x + s)


  val opSpec_fpga = new OperatorSpec {
    val k :: c :: y :: x :: r :: s :: Nil = genIterators(6)
    val o :: w :: i :: Nil                = genTensor(3)

    setExpr(o(k)(y)(x) += w(k)(c)(r)(s) * i(c)(y + r)(x + s))
    k.setRange(13)
    c.setRange(12)
    y.setRange(8)
    x.setRange(32)
    r.setRange(3)
    s.setRange(3)
    setLatency(12)
    o.setWidth(256)
    w.setWidth(32)
    i.setWidth(256)
    useCustomKernel(true)
    useCustomMem(false)
  }

  val opSpec_asic = new OperatorSpec {
    val k :: c :: y :: x :: r :: s :: Nil = genIterators(6)
    val o :: w :: i :: Nil                = genTensor(3)

    setExpr(o(k)(y)(x) += w(k)(c)(r)(s) * i(c)(y + r)(x + s))
    k.setRange(16)
    c.setRange(16)
    y.setRange(64)
    x.setRange(64)
    r.setRange(3)
    s.setRange(3)
    setLatency(1)
    o.setWidth(32)
    w.setWidth(32)
    i.setWidth(32)
    useCustomMem(true)
  }
  val stt = DenseMatrix(
    (1,0,0,0,0,0), 
    (0,1,0,0,0,0), 
    (1,1,0,1,0,0), 
    (0,0,0,0,0,1), 
    (0,0,0,0,1,0), 
    (0,0,1,0,0,0)
  )

  val config = Gen_dataflow(opSpec_asic, stt, true)
  val str = chisel3.Driver.emitVerilog(new PEArray(config))
  new PrintWriter(s"PEArray_ws_asic.v") { write(str); close }
}

object Example_GenConv2DRotate extends App {
  //  Conv2D calculation algorithm
  // o(k)(y)(x) += w(k)(c)(r)(s) * i(c)(y + r)(x + s)

  val opSpec_fpga = new OperatorSpec {
    val k :: c :: y :: x :: r :: s :: Nil = genIterators(6)
    val o :: w :: i :: Nil                = genTensor(3)

    setExpr(o(k)(y)(x) += w(k)(c)(r)(s) * i(c)(y + r)(x + s))
    k.setRange(13)
    c.setRange(32)
    y.setRange(12)
    x.setRange(8)
    r.setRange(3)
    s.setRange(3)
    setLatency(12)
    o.setWidth(256)
    w.setWidth(32)
    i.setWidth(256)
    useCustomKernel(true)
    useCustomMem(false)
  }

  val opSpec_asic = new OperatorSpec {
    val k :: c :: y :: x :: r :: s :: Nil = genIterators(6)
    val o :: w :: i :: Nil                = genTensor(3)

    setExpr(o(k)(y)(x) += w(k)(c)(r)(s) * i(c)(y + r)(x + s))
    k.setRange(16)
    c.setRange(64)
    y.setRange(16)
    x.setRange(64)
    r.setRange(3)
    s.setRange(3)
    setLatency(1)
    o.setWidth(32)
    w.setWidth(32)
    i.setWidth(32)
    useCustomMem(true)
  }
  val stt2 =  DenseMatrix(
    (1,  0,  0,  0,  0,  0),
    (0,  0,  1,  0,  0,  0),
    (1,  0,  0,  0,  1,  0),
    (0,  1,  0,  0,  0,  0),
    (0,  0,  0,  1,  0,  0),
    (0,  0,  0,  0,  0,  1)
  )
  val config = Gen_dataflow(opSpec_fpga, stt2, true)
  val str = chisel3.Driver.emitVerilog(new PEArray(config))
  new PrintWriter(s"PEArray_rotate_fpga.v") { write(str); close }


  val config_asic = Gen_dataflow(opSpec_asic, stt2, true)
  val str_asic = chisel3.Driver.emitVerilog(new PEArray(config_asic))
  new PrintWriter(s"PEArray_rotate_asic.v") { write(str_asic); close }
}


object Example_GenConv2DStride extends App {
  //  Conv2D calculation algorithm
  // o(k)(y)(x) += w(k)(c)(r)(s) * i(c)(y + r)(x + s)

  val opSpec_fpga = new OperatorSpec {
    val k :: c :: y :: x :: r :: s :: Nil = genIterators(6)
    val o :: w :: i :: Nil                = genTensor(3)

    setExpr(o(k)(y)(x) += w(k)(c)(r)(s) * i(c)(y + y + r)(x + x + s))
    k.setRange(14)
    c.setRange(32)
    y.setRange(11)
    x.setRange(8)
    r.setRange(3)
    s.setRange(3)
    setLatency(12)
    o.setWidth(256)
    w.setWidth(32)
    i.setWidth(256)
    useCustomKernel(true)
    useCustomMem(false)
  }
  val stt2 =  DenseMatrix(
    (1,  0,  0,  0,  0,  0),
    (0,  0,  1,  0,  0,  0),
    (1,  0,  0,  0,  1,  0),
    (0,  1,  0,  0,  0,  0),
    (0,  0,  0,  1,  0,  0),
    (0,  0,  0,  0,  0,  1)
  )
  val config = Gen_dataflow(opSpec_fpga, stt2, false)

}
