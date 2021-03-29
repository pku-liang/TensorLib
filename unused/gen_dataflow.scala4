package systolic
import breeze.linalg._
object TestMat{
  import scala.collection.mutable.Set
  
  def apply(mat: Array[Array[Int]]):Boolean={
    var ret = true
    if(mat(2)(0)==1&&mat(2)(1)==1&&mat(2)(2)==0){
      ret = false
    }
    val s = Set[(Int,Int,Int)]()
    for (i <- 0 until 3){
      for (j <- 0 until 3){
        for (k <- 0 until 3){
          val x = i * mat(0)(0)+j*mat(0)(1)+k*mat(0)(2)
          val y = i * mat(1)(0)+j*mat(1)(1)+k*mat(1)(2)
          val z = i * mat(2)(0)+j*mat(2)(1)+k*mat(2)(2)
          if(s.contains((x,y,z))){
            ret=false
          }
          s.add((x,y,z))
        }
      }
    }
    ret
  }

  def apply(mat: DenseMatrix[Int]):Boolean={
    var ret = true
    if(mat(2,0)==1&&mat(2,1)==1&&mat(2,2)==0){
      ret = false
    }
    val s = Set[(Int,Int,Int)]()
    for (i <- 0 until 3){
      for (j <- 0 until 3){
        for (k <- 0 until 3){
          val x = i * mat(0,0)+j*mat(0,1)+k*mat(0,2)
          val y = i * mat(1,0)+j*mat(1,1)+k*mat(1,2)
          val z = i * mat(2,0)+j*mat(2,1)+k*mat(2,2)
          if(s.contains((x,y,z))){
            ret=false
          }
          s.add((x,y,z))
        }
      }
    }
    ret
  }
}