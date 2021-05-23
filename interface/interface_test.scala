package tensorlib

import chisel3._
import chisel3.util._

import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import breeze.linalg._
import breeze.numerics._

object InterfaceMain extends App {
  // val opSpec = new OperatorSpec {
  //   val c :: k :: x :: y :: r :: s :: Nil = genIterators(6)
  //   val o :: w :: i :: Nil                = genTensor(3)

  //   setExpr(o(k)(y)(x) += w(k)(r)(s) * i(c)(y + r)(x + s))
  // }
  // val opSpec = new OperatorSpec {
  //   val i :: j :: k :: Nil = genIterators(3)
  //   val c :: a :: b :: Nil = genTensor(3)

  //   setExpr(c(i)(k) += a(i)(j) * b(j)(k))
  // }
  // val opSpec = new OperatorSpec {
  //   val i :: j :: k :: l :: Nil = genIterators(4)
  //   val d :: a :: b :: c :: Nil = genTensor(4)

  //   setExpr(d(i)(j) += a(i)(j)(l) * b(k)(j) * c(l)(j))
  // }
  // val opSpec = new OperatorSpec {
  //   val i :: j :: k :: l :: m :: Nil = genIterators(5)
  //   val d :: a :: b :: c :: Nil      = genTensor(4)

  //   setExpr(d(i)(j)(k) += a(i)(l)(m) * b(l)(j) * c(m)(k))
  // }

  val opSpec = new OperatorSpec {
    val i :: j :: k :: Nil = genIterators(3)
    val a :: b :: c :: Nil = genTensor(3)

    setExpr(c(i)(j) += a(i)(k) * b(k)(j))
    
  }

  // val ds = new DesignSpace(opSpec)

  // val s   = scala.collection.mutable.Set[List[String]]()
  // val stt = ds.genSTT()
  // // for (i <- 0 until stt.length) {
  // //   println(stt(i)._3)
  // //   println(stt(i)._1)
  // //   println(stt(i)._2)
  // //   println()
  // // }
  // println(stt.length)
  // for (i <- 0 until stt.length) {
  //   if (!s.contains(stt(i)._3)) {
  //     println(stt(i)._3)
  //     for (j <- 0 until stt(i)._1.rows) {
  //       for (k <- 0 until stt(i)._1.cols) {
  //         print(math.round(stt(i)._1(j, k)))
  //         print(' ')
  //       }
  //       println()
  //     }
  //     // println(stt(i)._1)
  //     println(stt(i)._2)
  //   }
  //   s += stt(i)._3
  // }
  // println(s.size)
  // println(s)
}
