package systolic

import chisel3._
import chisel3.util._
import breeze.linalg._

import java.io.PrintWriter

object Gen_Depth extends App {
  var dfid = 20
  import scala.collection.mutable.Set
  val ss = Set[DenseMatrix[Int]]()
  var mat = DenseMatrix((0,1,0),(1,0,0),(1,1,1))
  for(m <- 0 until 512){
    var g = m
    for(i <- 0 until 3){
      for(j <- 0 until 3){
        mat(i,j)=g%2
        g =g/2
      }
    }
    if(TestMat(mat)){
      
      val aa     = DenseMatrix((1.0, 0.0, 0.0), (0.0, 1.0, 0.0))
      val ab     = DenseMatrix((1.0, 1.0, 0.0), (0.0, 0.0, 1.0))
      val ac     = DenseMatrix((0.0, 1.0, 0.0), (0.0, 0.0, 1.0))
      val matd = mat.mapValues(_.toDouble)
      val res = List(CalcDelta(aa, matd)(0), CalcDelta(ab, matd)(0), CalcDelta(ac, matd)(0))
      val rest = List(CalcDelta(ab, matd)(0), CalcDelta(aa, matd)(0), CalcDelta(ac, matd)(0))
      val restm = DenseMatrix(rest:_*).mapValues(_.toInt)
      val resm = DenseMatrix(res:_*).mapValues(_.toInt)
      for(i <- 0 until 3){
        for(j <- 0 until 3){
          if(resm(i,j)== (-1))
            resm(i,j)=1
          if(restm(i,j)== (-1))
            restm(i,j)=1
        }
      }
      if(!(ss.contains(restm)) && !(ss.contains(resm)) && resm(2,::).t!=DenseVector(1,1,0)){
        println(mat)
        println()
        println(resm)
        val str = chisel3.Driver.emitVerilog(new PEArray2D(16,16,Array(1,1,1), 16, Array(res(0).toArray.map(_.toInt),res(1).toArray.map(_.toInt),res(2).toArray.map(_.toInt)),Array(true, true, false),3, false))
        new PrintWriter("PEArrayDepth_"+dfid+".v") { write(str); close }
        dfid=dfid+1
        ss+=resm
        ss+=restm
      }
    }
  }
}

object Gen_Gemm extends App {
  var dfid = 1
  import scala.collection.mutable.Set
  val ss = Set[DenseMatrix[Int]]()
  var mat1 = DenseMatrix((1,0,0),(0,1,0),(0,1,1))
  var mat2 = DenseMatrix((1,0,0),(0,1,0),(1,0,1))
  var mat3 = DenseMatrix((1,0,0),(0,1,0),(0,0,1))
  var mat4 = DenseMatrix((1,0,0),(0,1,0),(1,1,1))
  var mat5 = DenseMatrix((1,0,0),(0,0,1),(0,1,0))
  val aa     = DenseMatrix((1.0, 0.0, 0.0), (0.0, 1.0, 0.0))
  val ab     = DenseMatrix((0.0, 1.0, 0.0), (0.0, 0.0, 1.0))
  val ac     = DenseMatrix((1.0, 0.0, 0.0), (0.0, 0.0, 1.0))
  List(mat1, mat2, mat3, mat4, mat5).foreach(mat =>{
    val matd = mat.mapValues(_.toDouble)
    val res = List(CalcDelta(aa, matd)(0), CalcDelta(ab, matd)(0), CalcDelta(ac, matd)(0))
    val rest = List(CalcDelta(ab, matd)(0), CalcDelta(aa, matd)(0), CalcDelta(ac, matd)(0))
    val restm = DenseMatrix(rest:_*).mapValues(_.toInt)
    val resm = DenseMatrix(res:_*).mapValues(_.toInt)
    println(mat)
    println()
    println(resm)
    List((4,4),(4,8),(8,8),(8,16),(16,16)).foreach(size =>{
      val str = chisel3.Driver.emitVerilog(new PEArray2D(size._1,size._2,Array(1,1,1), 16, Array(res(0).toArray.map(_.toInt),res(1).toArray.map(_.toInt),res(2).toArray.map(_.toInt)),Array(true, true, false),3, false))
      new PrintWriter("PEArrayGemm_"+dfid+size._1+"x"+size._2+".v") { write(str); close }
    })
    
    dfid=dfid+1
  })
  
  
}


object Gen_Mttkrp extends App {
  var dfid = 1
  import scala.collection.mutable.Set
  val ss = Set[DenseMatrix[Int]]()
  var mat4 = DenseMatrix((1,0,0),(0,1,0),(1,1,1))
  var mat5 = DenseMatrix((1,0,0),(0,1,0),(0,0,1))
  var mat6 = DenseMatrix((1,0,0),(0,0,1),(0,1,0))
  val aa     = DenseMatrix((0.0, 1.0, 0.0), (0.0, 0.0, 1.0))
  val ab     = DenseMatrix((1.0, 0.0, 0.0), (0.0, 0.0, 1.0))
  val ac     = DenseMatrix((1.0, 0.0, 0.0), (0.0, 0.0, 1.0))
  val ad     = DenseMatrix((1.0, 0.0, 0.0), (0.0, 1.0, 0.0))
  List(mat4,mat5,mat6).foreach(mat =>{
    val matd = mat.mapValues(_.toDouble)
    val res = List(CalcDelta(aa, matd)(0), CalcDelta(ab, matd)(0), CalcDelta(ac, matd)(0),CalcDelta(ad, matd)(0))
    val rest = List(CalcDelta(ab, matd)(0), CalcDelta(aa, matd)(0), CalcDelta(ac, matd)(0))
    val restm = DenseMatrix(rest:_*).mapValues(_.toInt)
    val resm = DenseMatrix(res:_*).mapValues(_.toInt)
    println(mat)
    println()
    println(resm)
    List((4,4),(4,8),(8,8),(8,16),(16,16)).foreach(size =>{
      val str = chisel3.Driver.emitVerilog(new PEArray2D(size._1,size._2,Array(1,1,1,1), 16, Array(res(0).toArray.map(_.toInt),res(1).toArray.map(_.toInt),res(2).toArray.map(_.toInt),res(3).toArray.map(_.toInt)),Array(true, true, true, false),4, false))
      new PrintWriter("PEArrayMttkrp_"+dfid+size._1+"x"+size._2+".v") { write(str); close }
    })
    
    dfid=dfid+1
  })
  
  
}