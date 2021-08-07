package tensorlib

import chisel3._
import chisel3.util._
import scala.math._
//import chisel3.Driver
import chisel3.iotesters.{PeekPokeTester, Driver}
import java.io.PrintWriter
import breeze.linalg._

class TestTop extends Module{
  val io =IO(new Bundle{

  })
  val counter = RegInit(0.U(10.W))
  counter := counter + 1.U
  val opSpec = new OperatorSpec {
    val k :: c :: y :: x :: r :: s :: Nil = genIterators(6)
    val o :: w :: i :: Nil                = genTensor(3)

    setExpr(o(k)(y)(x) += w(k)(c)(r)(s) * i(c)(y + r)(x + s))
    k.setRange(4)
    c.setRange(4)
    y.setRange(6)
    x.setRange(12)
    r.setRange(3)
    s.setRange(3)
    setLatency(1)
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
  val top = Module(gen_dataflow(opSpec, stt)).io

  for(i <- 0 until 4){
    top.data(1).in.get(i).valid := true.B
    top.data(1).in.get(i).bits.valid := true.B
    top.data(1).in.get(i).bits.bits := true.B
  }

  for(i <- 0 until 4){
    top.data(2).in.get(i).valid := true.B
    top.data(2).in.get(i).bits.valid := true.B
    top.data(2).in.get(i).bits.bits := true.B
  }
  top.exec_valid := (counter >= 20.U)
  top.out_valid := false.B
}
class Test_Tester(c: TestTop) extends PeekPokeTester(c){
  for(i <- 0 until 20){
    step(1)
  }
}
object Test_App extends App{
  Driver(() => new TestTop())(c => new Test_Tester(c))
}