package systolic

import breeze.linalg._
import breeze.numerics._

import math.abs
import math.round

object CalcDelta {
  def apply(matA: DenseMatrix[Double], matT: DenseMatrix[Double]) = {
    val matB = matA * inv(matT)
    // val matZ = DenseMatrix.eye[Double](matB.cols) - pinv(matB) * matB
    val d = scala.collection.mutable.ArrayBuffer.empty[DenseVector[Double]]

    def gen(n: Int, x: Array[Double]): List[DenseVector[Double]] = {
      if (n == matB.cols)
        new DenseVector[Double](x) :: Nil
      else
        gen(n + 1, x :+ 1.0) ++ gen(n + 1, x :+ 0.0) ++ gen(n + 1, x :+ -1.0)
    }

    val testers = gen(0, Array.empty[Double])

    for (v <- testers) {
      if (!d.exists((u) => math.abs(u dot v) > 1e-5)) {
        val y  = matB * v
        val ny = norm(y)
        val nv = norm(v)
        if (ny < 1e-5 && nv > 1e-5)
          d += v
      }
    }
    if (matT.rows - rank(matB) != d.length)
      throw new Exception("Can not extract valid dataflows")
    d
  }
}
