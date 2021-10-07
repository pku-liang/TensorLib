package tensorlib

import chisel3._
import chisel3.util._
import scala.math._
//import chisel3.Driver
import chisel3.iotesters.{PeekPokeTester, Driver}
import java.io.PrintWriter
import breeze.linalg._


class Test_Runner_Gemm(c: PEArray,k_len: Int, c_len: Int, x_len: Int, latency: Int) extends PeekPokeTester(c){
  val r = new scala.util.Random
  val mat1 = DenseMatrix.tabulate[Int](k_len, c_len){
    case(i, j)=> r.nextInt(10)+1
  }
  val mat2 = DenseMatrix.tabulate[Int](x_len, c_len){
    case(i, j)=> r.nextInt(10)+1
  }
  
  
  val mat_ref = mat1 * mat2.t
  var mat_out = DenseMatrix.tabulate[Int](k_len, x_len){
    case(i, j) => 0
  }
  println("mat 1:")
  println(mat1.toString)
  println("mat 2:")
  println(mat2.toString)
  println("mat 3:")
  println(mat_ref.toString)
  for(i <- 0 until c_len){
    for(j <- 0 until k_len){
      poke(c.io.data(1).in.get(j).valid, true.B)
      poke(c.io.data(1).in.get(j).bits.valid, true.B)
      poke(c.io.data(1).in.get(j).bits.bits, mat1(j,i))
    }
    for(j <- 0 until x_len){
      poke(c.io.data(2).in.get(j).valid, true.B)
      poke(c.io.data(2).in.get(j).bits.valid, true.B)
      poke(c.io.data(2).in.get(j).bits.bits, mat2(j,i))
    }
    poke(c.io.exec_valid, false.B)
    poke(c.io.out_valid, false.B)
    step(1)
  }
  for(i <- 0 until c_len+k_len+x_len){
    for(j <- 0 until k_len){
      poke(c.io.data(1).in.get(j).valid, false.B)
      poke(c.io.data(1).in.get(j).bits.valid, false.B)
      poke(c.io.data(1).in.get(j).bits.bits, 0)
    }
    for(j <- 0 until x_len){
      poke(c.io.data(2).in.get(j).valid, false.B)
      poke(c.io.data(2).in.get(j).bits.valid, false.B)
      poke(c.io.data(2).in.get(j).bits.bits, 0)
    }
    poke(c.io.exec_valid, true.B)
    poke(c.io.out_valid, false.B)
    step(1)
  }

  for(i <- 0 until k_len*3){
    for(j <- 0 until k_len){
      poke(c.io.data(1).in.get(j).valid, false.B)
      poke(c.io.data(1).in.get(j).bits.valid, false.B)
      poke(c.io.data(1).in.get(j).bits.bits, 0)
    }
    for(j <- 0 until x_len){
      poke(c.io.data(2).in.get(j).valid, false.B)
      poke(c.io.data(2).in.get(j).bits.valid, false.B)
      poke(c.io.data(2).in.get(j).bits.bits, 0)
    }
    println()
    poke(c.io.exec_valid, false.B)
    poke(c.io.out_valid, false.B)
    step(1)
  }
  var out_col = 0
  while(out_col < k_len * x_len){
    
    for(j <- 0 until k_len){
      poke(c.io.data(1).in.get(j).valid, false.B)
      poke(c.io.data(1).in.get(j).bits.valid, false.B)
      poke(c.io.data(1).in.get(j).bits.bits, 0)
    }
    for(j <- 0 until x_len){
      poke(c.io.data(2).in.get(j).valid, false.B)
      poke(c.io.data(2).in.get(j).bits.valid, false.B)
      poke(c.io.data(2).in.get(j).bits.bits, 0)
    }
    for(j <- 0 until x_len){
      if(peek(c.io.data(0).out.get(j).valid).toInt==1){
        mat_out(j, out_col/x_len) = peek(c.io.data(0).out.get(j).bits).toInt
        out_col = out_col + 1
      }
      print(peek(c.io.data(0).out.get(j).bits)+" ")
    }
    println()
    poke(c.io.exec_valid, false.B)
    poke(c.io.out_valid, true.B)
    step(1)
  }
  println(mat_ref.toString)
  println(mat_out.toString)
  if(mat_ref == mat_out){
    println("result match")
  }else{
    println("failed")
  }
}
object Test_Gemm extends App{
  
  val k_len = 4
  val c_len = 10
  val x_len = 4
  
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
  //val top = Module(new PEArray(config)).io
  chisel3.Driver.execute(args, () => new PEArray(config))
  Driver(() => new PEArray(config))(c => new Test_Runner_Gemm(c, k_len, c_len, x_len, 1))
}