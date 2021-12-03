package tensorlib

import chisel3._
import chisel3.util._
import scala.math._
//import chisel3.Driver
import chisel3.iotesters.{PeekPokeTester, Driver}
import java.io.PrintWriter
import breeze.linalg._


class Test_Runner_WS(c: PEArray,k_len: Int, c_len: Int, x_len: Int, latency: Int) extends PeekPokeTester(c){

  val mat1 = DenseMatrix(
    (1, 2, 3, 4),
    (5, 6, 7, 8),
    (9, 10, 11, 12),
    (13, 14, 15, 16)
  )
  val mat1_input = DenseMatrix(
    (1, 5, 9, 13),
    (2, 6, 10, 14),
    (3, 7, 11, 15),
    (4, 8, 12, 16),
  )
  val mat2 = DenseMatrix(
    (101,102, 103, 104, 105, 106,107,108,109,110,111,112),
    (113,114, 115, 116, 117, 118,119,120,121,122,123,124),
    (125,126, 127, 128, 129, 130,131,132,133,134,135,136),
    (137,138, 139, 140, 141, 142,143,144,145,146,147,148)
  )
  val mat_ref = mat1 * mat2
  var mat_out = DenseMatrix.tabulate[Int](k_len, x_len){
    case(i, j) => 0
  }
  println("mat 1:\n"+mat1.toString)
  println("mat 2:\n"+mat2.t.toString)
  println("reference result:\n"+mat_ref.toString)
  for(i <- 0 until 12){
    for(j <- 0 until 4){
      
      if(i < 4){
        poke(c.io.data(1).in.get(j).valid, true.B)
        poke(c.io.data(1).in.get(j).bits.valid, true.B)
        poke(c.io.data(1).in.get(j).bits.bits, mat1(j, i))
      }else{
        poke(c.io.data(1).in.get(j).valid, false.B)
        poke(c.io.data(1).in.get(j).bits.valid, false.B)
        poke(c.io.data(1).in.get(j).bits.bits, 0)
      }  
    }
    for(j <- 0 until 4){
      poke(c.io.data(2).in.get(j).valid, true.B)
      poke(c.io.data(2).in.get(j).bits.valid, true.B)
      poke(c.io.data(2).in.get(j).bits.bits, mat2(j,i))
    }
    poke(c.io.exec_valid, false.B)
    poke(c.io.out_valid, false.B)
    println()
    step(1)
  }
  for(i <- 0 until 32){
    for(j <- 0 until 4){
      poke(c.io.data(1).in.get(j).valid, false.B)
      poke(c.io.data(1).in.get(j).bits.valid, false.B)
      poke(c.io.data(1).in.get(j).bits.bits, 0)
    }
    for(j <- 0 until 4){
      poke(c.io.data(2).in.get(j).valid, false.B)
      poke(c.io.data(2).in.get(j).bits.valid, false.B)
      poke(c.io.data(2).in.get(j).bits.bits, 0)
    }
    poke(c.io.exec_valid, true.B)
    poke(c.io.out_valid, false.B)
    println()
    step(1)
  }

  for(i <- 0 until 30){
    for(j <- 0 until 4){
      poke(c.io.data(1).in.get(j).valid, false.B)
      poke(c.io.data(1).in.get(j).bits.valid, false.B)
      poke(c.io.data(1).in.get(j).bits.bits, 0)
    }
    for(j <- 0 until 4){
      poke(c.io.data(2).in.get(j).valid, false.B)
      poke(c.io.data(2).in.get(j).bits.valid, false.B)
      poke(c.io.data(2).in.get(j).bits.bits, 0)
      print(peek(c.io.data(0).out.get(j).bits)+" ")
    }
    poke(c.io.exec_valid, false.B)
    poke(c.io.out_valid, false.B)
    
    println()
    step(1)
  }
  // var out_col = 0
  // while(out_col < k_len * x_len){
    
  //   for(j <- 0 until k_len){
  //     poke(c.io.data(1).in.get(j).valid, false.B)
  //     poke(c.io.data(1).in.get(j).bits.valid, false.B)
  //     poke(c.io.data(1).in.get(j).bits.bits, 0)
  //   }
  //   for(j <- 0 until x_len){
  //     poke(c.io.data(2).in.get(j).valid, false.B)
  //     poke(c.io.data(2).in.get(j).bits.valid, false.B)
  //     poke(c.io.data(2).in.get(j).bits.bits, 0)
  //   }
  //   for(j <- 0 until x_len){
  //     if(peek(c.io.data(0).out.get(j).valid).toInt==1){
  //       mat_out(j, out_col/x_len) = peek(c.io.data(0).out.get(j).bits).toInt
  //       out_col = out_col + 1
  //     }
  //     print(peek(c.io.data(0).out.get(j).bits)+" ")
  //   }
  //   println()
  //   poke(c.io.exec_valid, false.B)
  //   poke(c.io.out_valid, true.B)
  //   step(1)
  // }
  
  // println("hardware output:\n"+mat_out.toString)
  // if(mat_ref == mat_out){
  //   println("result match")
  // }else{
  //   println("failed")
  // }
}


object Test_GemmWS extends App{

   val k_len = 4
   val c_len = 4
   val x_len = 12

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
    (0,  1,  0),
    (1,  1,  1),
   )
   val config = Gen_dataflow(opSpec, stt)
   //val top = Module(new PEArray(config)).io
  //  val str = chisel3.Driver.emitVerilog(new PEArray(config))
  //  new PrintWriter(s"PEArray_ws_test.v") { write(str); close }
   //chisel3.Driver.execute(args, () => new PEArray(config))
   Driver(() => new PEArray(config))(c => new Test_Runner_WS(c, k_len, c_len, x_len,  1))
 }