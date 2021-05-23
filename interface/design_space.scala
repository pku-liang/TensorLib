package tensorlib

import breeze.linalg._
import breeze.numerics._

case class Dataflow(name: String, dmat: DenseMatrix[Int], bDims: Int, tDims: Int)

class DesignSpace(opSpec: OperatorSpec) {
  val _opSpec = opSpec
  val nDims   = opSpec.numIter
  val dataflow = scala.Vector(
    Dataflow("x_systolic", DenseMatrix((0, 1, 0), (-1, 0, 1)), 1, 1),
    Dataflow("y_systolic", DenseMatrix((1, 0, 0), (0, -1, 1)), 1, 1),
    Dataflow("xy_systolic", DenseMatrix((1, -1, 0), (-1, 0, 1)), 1, 1),
    Dataflow("x_multicast", DenseMatrix((0, 1, 0), (0, 0, 1)), 1, 1),
    Dataflow("y_multicast", DenseMatrix((1, 0, 0), (0, 0, 1)), 1, 1),
    Dataflow("xy_multicast", DenseMatrix((1, -1, 0), (0, 0, 1)), 1, 1),
    Dataflow("stationary", DenseMatrix((1, 0, 0), (0, 1, 0)), 2, 0),
    Dataflow("2d_multicast", DenseMatrix((0, 0, 1)), 0, 1),
    Dataflow("x_systolic-y_multicast", DenseMatrix((-1, 0, 1)), 0, 1),
    Dataflow("y_systolic-x_multicast", DenseMatrix((0, -1, 1)), 0, 1),
    Dataflow("x_multicast-stationary", DenseMatrix((0, 1, 0)), 1, 0),
    Dataflow("y_multicast-stationary", DenseMatrix((1, 0, 0)), 1, 0)
  )

  val reuseVecs = scala.Vector(
    DenseVector(1.0, 0.0, 0.0),
    DenseVector(0.0, 1.0, 0.0),
    DenseVector(0.0, 0.0, 1.0),
    DenseVector(1.0, 1.0, 0.0),
    DenseVector(1.0, 0.0, 1.0),
    DenseVector(0.0, 1.0, 1.0),
    DenseVector(1.0, 1.0, 1.0)
  )

  val reuseType = scala.Vector(
    ("x_systolic", scala.Vector(4)),
    ("y_systolic", scala.Vector(5)),
    ("xy_systolic", scala.Vector(6)),
    ("x_multicast", scala.Vector(0)),
    ("y_multicast", scala.Vector(1)),
    ("xy_multicast", scala.Vector(3)),
    ("stationary", scala.Vector(2)),
    ("2d_multicast", scala.Vector(0, 1)),
    ("x_systolic-y_multicast", scala.Vector(1, 4)),
    ("y_systolic-x_multicast", scala.Vector(0, 5)),
    ("x_multicast-stationary", scala.Vector(0, 2)),
    ("y_multicast-stationary", scala.Vector(1, 2))
  )

  val input_tensors = Range(0, opSpec.numTensor).filter(_ != opSpec.outputID)
  val matA          = input_tensors.map(opSpec.accessMats(_)).reduce(DenseMatrix.vertcat(_, _))
  val pinvA         = pinv(matA)
  val candidate = input_tensors.map((x) => {
    val c = dataflow
      .filter((d) => d.bDims + d.tDims <= opSpec.accessMats(x).rows)
      .map((d) => {
        val af = searchAccessFunc(opSpec.accessMats(x).rows, d.bDims, d.tDims)
        af.map((a) => (d.name, a, d.dmat))
      })
      .toList
    reduceIfNonempty(c)
  })
  val restMats =
    if (nDims > 3)
      searchRestMat(input_tensors.map(opSpec.accessMats(_).rows).reduce(_ + _))
        .filter((m) => if (nDims > 4) (rank(m) == nDims - 3) else true)
    else Nil

  def makeOutput(
      x: Int,
      mat: DenseMatrix[Int],
      afs: List[DenseMatrix[Int]],
      dataflows: List[String]
  ): List[(DenseMatrix[Double], List[DenseMatrix[Int]], List[String])] = {
    if (x == opSpec.numTensor - 1)
      (convert(mat, Double), afs, dataflows) :: Nil
    else
      (for ((name, amat, dmat) <- candidate(x)) yield {
        if (x == 0)
          makeOutput(x + 1, amat * dmat, amat :: Nil, name :: Nil)
        else
          makeOutput(x + 1, DenseMatrix.vertcat(mat, amat * dmat), amat +: afs, name +: dataflows)
      }).reduce(_ ++ _)
  }

  def genSTT(): List[(DenseMatrix[Double], List[DenseMatrix[Int]], List[String])] = {
    val outputs = makeOutput(0, DenseMatrix(0, 0), Nil, Nil)
      .filter {
        case (mat, afs, dataflow) =>
          rank(mat) == 3
      }
      .map {
        case (mat, afs, dataflow) =>
          (mat, afs.reverse, (checkReuse(opSpec.accessMats(opSpec.outputID) * pinvA * mat) match {
            case Some((s, v)) => s
            case None         => "none"
          }) :: dataflow.reverse)
      }
      .filter { case (mat, afs, dataflow) => dataflow.head != "none" }
    println(outputs.length)
    println(restMats.length)
    var c: List[(DenseMatrix[Double], List[DenseMatrix[Int]], List[String])] = Nil
    if (nDims > 3)
      for ((m, a, d) <- outputs; r <- restMats) {
        val m1 = pinvA * DenseMatrix.horzcat(m, r)
        if (math.abs(det(m1)) > 1e-3) {
          val mat = inv(m1)
          if (validSTT(mat))
            c = (mat, a, d) :: c
        }
      } else
      for ((m, a, d) <- outputs) {
        val m1 = pinvA * m
        if (math.abs(det(m1)) > 1e-3) {
          val mat = inv(m1)
          if (validSTT(mat))
            c = (mat, a, d) :: c
        }
      }
    c
  }

  def validSTT(m: DenseMatrix[Double]): Boolean = {
    def notInt(x: Double)    = math.abs(x.round - x) > 1e-5
    def oneOrzero(x: Double) = math.abs(x) < 1e-5 || math.abs(x - 1.0) < 1e-5
    val idx                  = (for (i <- 0 until m.rows) yield (for (j <- 0 until m.cols) yield (i, j)).toList).reduce(_ ++ _)
    (!idx.exists { case (i, j) => (!oneOrzero(m(i, j)) || m(i, j) < 0) })
  }

  def searchRestMat(totM: Int): List[DenseMatrix[Double]] = {
    def dfs(x: Int, sel: List[Int]): List[DenseMatrix[Double]] = {
      if (x == totM) {
        val v = sel.toVector
        DenseMatrix.tabulate[Double](totM, nDims - 3) {
          case (i, j) => if (v(i) == j) 1.0 else 0.0
        } :: Nil
      } else
        reduceIfNonempty(
          (for (i <- (-1) until (nDims - 3))
            yield dfs(x + 1, i :: sel)).toList
        )
    }
    dfs(0, Nil)
  }

  def searchAccessFunc(mDims: Int, bDims: Int, tDims: Int): List[DenseMatrix[Int]] = {
    def link(s: List[Int], a: Int) = if (s.nonEmpty) s :+ a else a :: Nil
    def makeMat(sel: List[Int], skew: Boolean): DenseMatrix[Int] = {
      val v = sel.toVector
      DenseMatrix.tabulate[Int](mDims, bDims + tDims) {
        case (i, j) => if (v(i) == j) 1 else (if (j == 0 && skew && v(i) >= bDims) -1 else 0)
      }
    }

    def dfs(x: Int, c: Int, sel: List[Int]): List[DenseMatrix[Int]] = {
      if (x == mDims)
        if (c != bDims + tDims)
          Nil
        else if (tDims > 0)
          makeMat(sel, false) :: makeMat(sel, true) :: Nil
        else
          makeMat(sel, false) :: Nil
      else {
        val selb =
          reduceIfNonempty(
            Range(0, bDims).toList.filter(!sel.contains(_)).map((i) => dfs(x + 1, c + 1, i :: sel))
          )
        val selt = reduceIfNonempty(
          Range(0, tDims).toList
            .filter((i) => !sel.contains(i + bDims))
            .map((i) => dfs(x + 1, c + 1, (i + bDims) :: sel))
        )
        selb ++ selt ++ dfs(x + 1, c, -1 :: sel)
      }
    }
    dfs(0, 0, Nil)
  }

  def checkReuse(matT: DenseMatrix[Double]) = {
    val d = scala.collection.mutable.ArrayBuffer.empty[Int]
    for (i <- 0 until reuseVecs.length) {
      val v = reuseVecs(i)
      if (!d.exists((u) => math.abs(reuseVecs(u) dot v) > 1e-5)) {
        val y  = matT * v
        val ny = norm(y)
        val nv = norm(v)
        if (ny < 1e-5 && nv > 1e-5)
          d += i
      }
    }
    reuseType.find { case (s, v) => d.equals(v) }
  }

  def reduceIfNonempty[T](s: List[List[T]]) =
    if (s.isEmpty)
      Nil
    else
      s.reduce(_ ++ _)
}
