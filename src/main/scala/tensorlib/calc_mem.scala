package tensorlib

import chisel3._
import chisel3.util._

import chisel3.iotesters
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import org.scalatest._

import breeze.linalg._
import breeze.numerics._
import chisel3.stage.ChiselStage

object MemCalculator_Conv extends App{
  val opSpec = new OperatorSpec {
    val k :: c :: y :: x :: r :: s :: Nil = genIterators(6)
    val o :: w :: i :: Nil                = genTensor(3)

    setExpr(o(k)(y)(x) += w(k)(c)(r)(s) * i(c)(y + r)(x + s))
    k.setRange(16)
    c.setRange(16)
    y.setRange(8)
    x.setRange(8)
    r.setRange(3)
    s.setRange(3)
    setLatency(1)
    o.setWidth(16)
    w.setWidth(16)
    i.setWidth(16)
  }
  val idx = List(0,1,2,3,4,5).permutations
  // 16 * 10 * 10 = 1600
  // 16 * 8 * 8 = 1024
  // 16 * 16 * 3 * 3 = 2304
  // val x=List(0,1,2,3,4,5)
  // var stt = DenseMatrix.zeros[Int](6,6)
  // x.zipWithIndex.foreach{case(i, j)=>{
  //   stt(i,j)=1
  // }}
  
  // println("MEM_SIZE:"+Calc_Mem(opSpec, stt))


  idx.foreach(x=>{
    var stt = DenseMatrix.zeros[Int](6,6)
    x.zipWithIndex.foreach{case(i, j)=>{
      stt(i,j)=1
    }}
    //println(stt)
    Calc_Mem(opSpec, stt)
    
    //println("MEM_SIZE:"+.reduce(_+_))
  })
}
object MemCalculator_Test extends App{
  val opSpec = new OperatorSpec {
    val k :: c :: y :: x :: r :: s :: Nil = genIterators(6)
    val o :: w :: i :: Nil                = genTensor(3)

    setExpr(o(k)(y)(x) += w(k)(c)(r)(s) * i(c)(y + r)(x + s))
    k.setRange(16)
    c.setRange(16)
    y.setRange(8)
    x.setRange(8)
    r.setRange(3)
    s.setRange(3)
    setLatency(1)
    o.setWidth(16)
    w.setWidth(16)
    i.setWidth(16)
  }

  // val stt1 = DenseMatrix(
  //   (1,0,0,0,0,0), 
  //   (0,0,0,1,0,0), 
  //   (1,1,0,1,0,0), 
  //   (0,0,0,0,0,1), 
  //   (0,0,0,0,1,0), 
  //   (0,0,1,0,0,0)
  // )
  // Calc_Mem(opSpec, stt1)

  // val stt2 = DenseMatrix(
  //   (1,0,0,0,0,0), 
  //   (0,1,0,0,0,0), 
  //   (1,1,0,1,0,0), 
  //   (0,0,0,0,0,1), 
  //   (0,0,0,0,1,0), 
  //   (0,0,1,0,0,0)
  // )
  // Calc_Mem(opSpec, stt2)


  // val stt3 = DenseMatrix(
  //   (1,0,0,0,0,0), 
  //   (0,0,0,1,0,0), 
  //   (1,0,0,1,0,1), 
  //   (0,0,0,0,1,0), 
  //   (0,1,0,0,0,0), 
  //   (0,0,1,0,0,0)
  // )
  // Calc_Mem(opSpec, stt3)


  val stt4 = DenseMatrix(
    (1,  0,  0,  0,  0,  0),
    (0,  0,  1,  0,  0,  0),
    (0,  0,  0,  0,  1,  0),
    (0,  1,  0,  0,  0,  0),
    (0,  0,  0,  1,  0,  0),
    (0,  0,  0,  0,  0,  1)
  )
  Calc_Mem(opSpec, stt4)
  val idx = List(0,1,2,3,4,5).permutations

  // val x=List(0,1,2,3,4,5)
  // var stt = DenseMatrix.zeros[Int](6,6)
  // x.zipWithIndex.foreach{case(i, j)=>{
  //   stt(i,j)=1
  // }}
  
  // println("MEM_SIZE:"+Calc_Mem(opSpec, stt))


  idx.foreach(x=>{
    var stt = DenseMatrix.zeros[Int](6,6)
    x.zipWithIndex.foreach{case(i, j)=>{
      stt(i,j)=1
    }}
    
    //println(stt)
    //println("MEM_SIZE:"+Calc_Mem(opSpec, stt).reduce(_+_))
  })
  
  // val stt = DenseMatrix(
  //   (1,0,0,0,0,0), 
  //   (0,1,0,0,0,0), 
  //   (1,1,0,1,0,0), 
  //   (0,0,0,0,0,1), 
  //   (0,0,0,0,1,0), 
  //   (0,0,1,0,0,0)
  // )
  // println(stt)
  // val config = Gen_dataflow(opSpec, stt)
  // (new ChiselStage).emitVerilog(new PEArray(config))
  // // r, y, c
  // // o(y) = I(y+r) * W(r, c)
  // // k c y x r s
  // // val stt = DenseMatrix(
  // //   (1,0,0,0,0,0), 
  // //   (0,0,0,1,0,0), 
  // //   (0,0,0,0,0,1), 
  // //   (0,1,0,0,0,0), 
  // //   (0,0,0,0,1,0), 
  // //   (0,0,1,0,0,0)
  // // )
  
}

// 1920 + 3312 + 
object MemCalculator_Depth extends App{
  val opSpec = new OperatorSpec {
    val k :: y :: x :: r :: s :: Nil = genIterators(5)
    val o :: w :: i :: Nil                = genTensor(3)

    setExpr(o(k)(y)(x) += w(k)(r)(s) * i(k)(y + r)(x + s))
    k.setRange(16)
    y.setRange(8)
    x.setRange(8)
    r.setRange(3)
    s.setRange(3)
    setLatency(1)
    o.setWidth(16)
    w.setWidth(16)
    i.setWidth(16)
  }
  val idx = List(0,1,2,3,4).permutations
  idx.foreach(x=>{
    var stt = DenseMatrix.zeros[Int](5,5)
    x.zipWithIndex.foreach{case(i, j)=>{
      stt(i,j)=1
    }}
    Calc_Mem(opSpec, stt)
  })
}

object MemCalculator_Mttkrp extends App{
  val opSpec = new OperatorSpec {
    val i :: j :: k :: l :: Nil = genIterators(4)
    val y :: a :: b :: c :: Nil                = genTensor(4)

    setExpr(y(i)(j) += a(i)(k)(l) * b(k)(l) * c(l)(j))
    i.setRange(32)
    j.setRange(32)
    k.setRange(32)
    l.setRange(32)
    setLatency(1)
    y.setWidth(16)
    a.setWidth(16)
    b.setWidth(16)
    c.setWidth(16)
  }
  val idx = List(0,1,2,3).permutations
  idx.foreach(x=>{
    var stt = DenseMatrix.zeros[Int](4,4)
    x.zipWithIndex.foreach{case(i, j)=>{
      stt(i,j)=1
    }}
    Calc_Mem(opSpec, stt)
    println()
  })
}
object MemCalculator_ttmc extends App{
  val opSpec = new OperatorSpec {
    val i :: j :: k :: l :: m :: Nil = genIterators(5)
    val y :: a :: b :: c :: Nil                = genTensor(4)

    setExpr(y(i)(j)(k) += a(i)(l)(m) * b(l)(j) * c(m)(k))
    i.setRange(32)
    j.setRange(32)
    k.setRange(32)
    l.setRange(32)
    m.setRange(32)
    setLatency(1)
    y.setWidth(16)
    a.setWidth(16)
    b.setWidth(16)
    c.setWidth(16)
  }
  val idx = List(0,1,2,3,4).permutations
  idx.foreach(x=>{
    var stt = DenseMatrix.zeros[Int](5,5)
    x.zipWithIndex.foreach{case(i, j)=>{
      stt(i,j)=1
    }}
    Calc_Mem(opSpec, stt)
  })
}

object MemCalc_GEMM extends App{
  val opSpec = new OperatorSpec {
    val k :: c :: x :: Nil = genIterators(3)
    val o :: w :: i :: Nil = genTensor(3)

    setExpr(o(k)(x) += w(k)(c) * i(c)(x))
    k.setRange(16)
    c.setRange(16)
    x.setRange(20)
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
  val config = Calc_Mem(opSpec, stt)
}