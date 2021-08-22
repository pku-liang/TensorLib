package tensorlib

import breeze.linalg._
import breeze.numerics._

import math.abs
import math.round
sealed trait TensorDataflow
case object DirectDataflow extends TensorDataflow
case object SystolicDataflow extends TensorDataflow
case object StationaryDataflow extends TensorDataflow

class TensorConfig{
  var time_range = Array[Int]()
  var mem_range = Array[Int]()
  var rvecs = Array[DenseVector[Int]]()
  var top_pes = Array[(Int, Int)]()
  var mem_dims = Array[Int]()
}
class SAConfig(s: DenseMatrix[Int]){
  var pe_size = (0, 0)
  var exec_time = Array[Int]()
  var latency = 0
  var num_op = 0
  var width = Array[Int]()
  var simd = Array[Int]()
  var io_type = Array[Boolean]()
  var op_type = 0
  var tensors = Array[TensorConfig]()
  var stt = s
  var addr_width = 16
}
