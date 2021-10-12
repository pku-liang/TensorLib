package tensorlib

import scala.collection.mutable.Map
import scala.util.Random

import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint

import breeze.linalg._
import breeze.numerics._
import org.antlr.v4.runtime.atn.SemanticContext.Operator

class IteratorExpr(numIter: Int) {
  var coef   = DenseVector.zeros[Int](numIter)
  var id     = -1
  var c      = 0
  var lbound = 0
  var ubound = 0


  def setRange(range: Int){
    this.ubound = range
  }
  def getNumIters() = numIter
  
  def +(other: IteratorExpr): IteratorExpr = {
    assert(numIter == other.getNumIters())
    val result = new IteratorExpr(numIter)
    result.coef = coef + other.coef
    result.c = c + other.c
    result
  }
  def +(other: Int): IteratorExpr = {
    val result = new IteratorExpr(numIter)
    result.coef = coef
    result.c = other + c
    result
  }
  def +:(other: Int): IteratorExpr = this + c
  def -(other: IteratorExpr): IteratorExpr = {
    assert(numIter == other.getNumIters())
    val result = new IteratorExpr(numIter)
    result.coef = coef - other.coef
    result.c = c - other.c
    result
  }
  def *(k: Int): IteratorExpr = {
    val result = new IteratorExpr(numIter)
    result.coef = k * coef
    result.c = k * c
    result
  }
  def *:(k: Int): IteratorExpr = this * k
}

object IteratorExpr {
  def apply(numIter: Int, spec: OperatorSpec) = new IteratorExpr(numIter)
  def apply(id: Int, numIter: Int) = {
    val x = new IteratorExpr(numIter)
    x.id = id
    x.coef(id) = 1
    x
  }
}

abstract class TensorExpr {
  def generate(): UInt
  def generate_d(): FixedPoint
  def calc(): Int
  def calc_d(): Double
  def findIndexes(spec: OperatorSpec): List[TensorIndex]
  def findOutput(spec: OperatorSpec): Option[TensorIndex]
  def +(other: TensorExpr) = new TensorAddExpr(this, other)
  def -(other: TensorExpr) = new TensorSubExpr(this, other)
  def *(other: TensorExpr) = new TensorMulExpr(this, other)
}

class NilTensorExpr extends TensorExpr {
  def generate()                      = throw new Exception("Can't generate empty expression")
  def generate_d()                    = throw new Exception("Can't generate empty expression")
  def calc()                          = throw new Exception("Can't calculate empty expression")
  def calc_d()                        = throw new Exception("Can't calculate empty expression")
  def findIndexes(spec: OperatorSpec) = Nil
  def findOutput(spec: OperatorSpec)  = None
}

abstract class BinaryTensorExpr(a: TensorExpr, b: TensorExpr) extends TensorExpr {
  def findIndexes(spec: OperatorSpec) = a.findIndexes(spec) ++ b.findIndexes(spec)
  def findOutput(spec: OperatorSpec)  = None
}

class TensorAddExpr(a: TensorExpr, b: TensorExpr) extends BinaryTensorExpr(a, b) {
  def generate(): UInt = {
    val left  = a.generate()
    val right = b.generate()
    left + right
  }
  def generate_d(): FixedPoint = {
    val left  = a.generate_d()
    val right = b.generate_d()
    left + right
  }
  def calc(): Int      = a.calc() + b.calc()
  def calc_d(): Double = a.calc_d() + b.calc_d()
}
class TensorSubExpr(a: TensorExpr, b: TensorExpr) extends BinaryTensorExpr(a, b) {
  def generate(): UInt = {
    val left  = a.generate()
    val right = b.generate()
    left - right
  }
  def generate_d(): FixedPoint = {
    val left  = a.generate_d()
    val right = b.generate_d()
    left - right
  }
  def calc(): Int      = a.calc() - b.calc()
  def calc_d(): Double = a.calc_d() - b.calc_d()
}
class TensorMulExpr(a: TensorExpr, b: TensorExpr) extends BinaryTensorExpr(a, b) {
  def generate(): UInt = {
    val left  = a.generate()
    val right = b.generate()
    left * right
  }
  def generate_d(): FixedPoint = {
    val left  = a.generate_d()
    val right = b.generate_d()
    left * right
  }
  def calc(): Int      = a.calc() * b.calc()
  def calc_d(): Double = a.calc_d() * b.calc_d()
}

class TensorAssignExpr(a: TensorIndex, b: TensorExpr) extends TensorExpr {
  def generate()                      = b.generate()
  def generate_d()                    = b.generate_d()
  def calc(): Int                     = a.calc() + b.calc()
  def calc_d(): Double                = a.calc_d() + b.calc_d()
  def findIndexes(spec: OperatorSpec) = a.findIndexes(spec) ++ b.findIndexes(spec)
  def findOutput(spec: OperatorSpec)  = Some(a)
}

class TensorIndex(indexes: List[IteratorExpr], tensor: Tensor) extends TensorExpr {
  def getIndexes()                          = indexes
  def getTensor()                           = tensor
  def apply(idx: IteratorExpr): TensorIndex = new TensorIndex(indexes :+ idx, tensor)
  def +=(other: TensorExpr)                 = new TensorAssignExpr(this, other)
  def generate()                            = tensor.generate()
  def generate_d()                          = tensor.generate_d()
  def calc(): Int                           = tensor.calc()
  def calc_d(): Double                      = tensor.calc_d()
  def findIndexes(spec: OperatorSpec)       = this :: Nil
  def findOutput(spec: OperatorSpec)        = None
}
class Tensor(id: Int) extends TensorExpr {
  def getID() = id
  def apply(idx: IteratorExpr): TensorIndex = {
    new TensorIndex(idx :: Nil, this)
  }
  def setWidth(w: Int) : Unit = {
    width = w
  }
  def generate()                      = signal
  def generate_d()                    = signal_d
  def calc()                          = value
  def calc_d()                        = value_d
  def findIndexes(spec: OperatorSpec) = Nil
  def findOutput(spec: OperatorSpec)  = None

  var signal: UInt         = 0.U
  var signal_d: FixedPoint = FixedPoint.fromBigInt(0)
  var value: Int           = 0
  var value_d: Double      = 0.0
  var range                = scala.Vector[Int]()
  var width                = 0
}
object TraverseTensor{
  def apply(dims: Seq[Int]) : Seq[Seq[Int]] = {
    val xs = (0 until dims.length).map(x=>(0 until dims(x)))
    combine(xs)
  }
  def combine[A](xs: Traversable[Traversable[A]]): Seq[Seq[A]] =
    xs.foldLeft(Seq(Seq.empty[A])){
      (x, y) => for (a <- x.view; b <- y) yield a :+ b 
    }
}

class OperatorSpec {
  var numIter          = 0
  var iterList         = List[IteratorExpr]()
  var upperbounds      = Array[Int]()
  var numTensor        = 0
  var tensorList       = List[Tensor]()
  var signals          = Array[UInt]()
  var expr: TensorExpr = new NilTensorExpr()
  var latency          = 0
  // var accessMats  = List[DenseMatrix[Int]]().toVector
  // var accessConst = List[DenseVector[Int]]().toVector

  var accessMats = collection.mutable.Map[Int, DenseMatrix[Double]]()
  var constVecs  = collection.mutable.Map[Int, DenseVector[Double]]()

  var outputID = 0
  def getSTTRange(stt: DenseMatrix[Int]) : DenseVector[Int] = {
    val bound = iterList.map(_.ubound)
    // seq of vector
    val iter_ranges = TraverseTensor(Seq.fill(numIter)(2)).map(x=>{
      stt * new DenseVector((x, bound).zipped.map{case(a,b)=>a*b}.toArray)
    })
    // vector
    //val bound_ranges = iter_ranges.map(_(i)).reduce(_ m)
    val bound_ranges = DenseVector.tabulate[Int](iterList.length)(i=>
      iter_ranges.map(_(i)).reduce(_ max _)
    )
    bound_ranges
  }
  def getAddrRange(t: Tensor) : DenseVector[Int] = {
    val access = getAccessMat(t)
    val bound = iterList.map(_.ubound-1)
    // seq of vector
    val iter_ranges = TraverseTensor(Seq.fill(numIter)(2)).map(x=>{
      access.mapValues(_.toInt) * new DenseVector((x, bound).zipped.map{case(a,b)=>a*b}.toArray)+1
    })
    // vector
    val bound_ranges = DenseVector.tabulate[Int](access.rows)(i=>
      iter_ranges.map(_(i)).reduce(_ max _)
    )
    bound_ranges
  }
  def setLatency(l: Int) : Unit = {
    latency = l
  }
  // def getTimeRange(stt: DenseMatrix[Int], id:(Int, Int)) : DenseVector[Int] = {
    
  // }
  def genIterators(n: Int): List[IteratorExpr] = {
    numIter = n
    // upperbounds = Array.ofDim[Int](n)
    iterList = (for (i <- 0 until n) yield IteratorExpr(i, n)).toList
    iterList
  }
  // def setUpperbound(id: Int, bound: Int): Unit = {
  // assert(0 <= id && id < numIter)
  // upperbounds(id) = bound
  // }
  def genTensor(n: Int): List[Tensor] = {
    numTensor = n
    // signals = Array.ofDim[UInt](n)
    tensorList = (for (i <- 0 until n) yield new Tensor(i)).toList
    tensorList
  }
  def getSignal(t: Tensor) = t.signal

  def setExpr(e: TensorExpr) = {
    expr = e
    val idxs = e.findIndexes(this)
    for (idx <- idxs) {
      val rows = idx.getIndexes()
      val id   = idx.getTensor().getID()
      accessMats += (id -> DenseMatrix.tabulate[Double](rows.length, numIter) {
        case (i, j) => rows(i).coef(j)
      })
      constVecs += (id -> DenseVector.tabulate[Double](idx.getIndexes().length)(idx.getIndexes()(_).c))
    }
    e.findOutput(this) match {
      case Some(x) => outputID = x.getTensor().getID()
      case None    => throw new Exception("No output")
    }
  }
  def genExpr(sig: Array[UInt]) = {
    signals = sig
    expr.generate()
  }
  def genExpr() = expr.generate()
  // def genModule(width: Int, input_order: List[Tensor]) = {
  //   new Module {
  //     val input  = Input(Vec(input_order.length, UInt(width.W)))
  //     val output = Output(UInt(width.W))
  //     for ((t, i) <- input_order.zipWithIndex)
  //       t.signal = input(i)
  //     output := genExpr()
  //   }
  // }

  def setSignal(t: Tensor, sig: UInt) = t.signal = sig
  def setRange(t: Tensor, range: scala.Vector[Int]) = {
    if (range.length != accessMats(t.getID).rows)
      throw new Exception("Range incompactable with the dimension of tensor")
    t.range = range
  }

  def genTest(v_min: Int, v_max: Int) = {
    val rand = new Random()
    def buildArray(range: scala.Vector[Int]) = {
      val sz = range.foldLeft(1)(_ * _)
      val a  = Array.ofDim[Int](sz)
      for (i <- 0 until sz) a(i) = rand.nextInt(v_max - v_min + 1) + v_min
      a
    }
    val arrays =
      (
        for (t <- tensorList) yield buildArray(t.range)
      )
    def iter(x: Int) = {}
  }

  def getAccessMat(t: Tensor) = accessMats(t.getID())
  def getConstVecs(t: Tensor) = constVecs(t.getID())

  implicit class IntTImesIteratorExpr(x: Int) {
    def *(y: IteratorExpr) = y * x
  }
  implicit class IntPlusIteratorExpr(x: Int) {
    def +(y: IteratorExpr) = y + x
  }
}

object Helpers {
  implicit class IntTImesIteratorExpr(x: Int) {
    def *(y: IteratorExpr) = y * x
  }
  implicit class IntPlusIteratorExpr(x: Int) {
    def +(y: IteratorExpr) = y + x
  }
}
