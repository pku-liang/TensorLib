package tensorlib

import chisel3._
import chisel3.util._
import scala.math._
//import chisel3.Driver
import chisel3.iotesters.{PeekPokeTester, Driver}
import java.io.PrintWriter
import breeze.linalg._

class TestTop_GemmWS extends Module{
  val io =IO(new Bundle{

  })
  val counter = RegInit(0.U(10.W))
  counter := counter + 1.U
  printf("cycle: %d\n", counter)
  val opSpec = new OperatorSpec {
    val k :: c :: x :: Nil = genIterators(3)
    val o :: w :: i :: Nil = genTensor(3)

    setExpr(o(k)(x) += w(k)(c) * i(c)(x))
    k.setRange(4)
    c.setRange(4)
    x.setRange(8)
    setLatency(4)
    o.setWidth(16)
    w.setWidth(16)
    i.setWidth(16)
  }
  val stt = DenseMatrix(
   (1,  0,  0),  
   (0,  1,  0),  
   (1,  1,  1),  
  )
  val config = Gen_dataflow(opSpec, stt)
  val top = Module(new PEArray(config)).io
  //val top = Module(gen_dataflow(opSpec, stt)).io
  val mat1 = DenseMatrix(
    (1,1,4,2),
    (2,4,1,2),
    (4,4,3,3),
    (2,2,2,2)
  ).t
  val mat2 = DenseMatrix(
    (1,1,4,2),
    (2,4,1,2),
    (4,4,3,3),
    (2,2,2,2),
    (1,2,4,4),
    (2,1,3,3),
    (1,1,3,4),
    (3,2,1,4)
  )
  val mat3 =  mat1.t * mat2.t
  val mat1_reg = VecInit.tabulate(16)(i=>{
    val ix = i % 4
    val iy = i / 4
    mat1(iy,ix).asUInt
  })
  val mat2_reg = VecInit.tabulate(32)(i=>{
    val ix = i % 4
    val iy = i / 4
    mat2(iy,ix).asUInt
  })
  println(mat3)
  for(i <- 0 until 4){
    top.data(1).in.get(i).valid := (counter < 16.U)
    top.data(1).in.get(i).bits.valid := (counter < 16.U)
    top.data(1).in.get(i).bits.bits := mat1_reg((counter/4.U)*4.U+i.asUInt)
  }

  for(i <- 0 until 4){
    top.data(2).in.get(i).valid := (counter < 32.U)
    top.data(2).in.get(i).bits.valid := (counter < 32.U)
    top.data(2).in.get(i).bits.bits := mat2_reg((counter/4.U)*4.U+i.asUInt)
  }
  
  top.exec_valid := (counter >= 10.U)
  top.out_valid := false.B
}
class Test_Runner_GemmWS(c: TestTop_GemmWS) extends PeekPokeTester(c){
  for(i <- 0 until 100){
    step(1)
  }
}
object Test_GemmWS extends App{
  //chisel3.Driver.execute(args, () => new TestTop())
  Driver(() => new TestTop_GemmWS())(c => new Test_Runner_GemmWS(c))
}