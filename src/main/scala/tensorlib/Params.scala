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
  var tensor_range = Array[Int]()
  var start_t0 = 0
  var exec_time = Array[Int]()
  var time_range = Seq[Array[Int]]()
  var mem_range = Seq[Array[Int]]()
  var pe_rvecs = Array[DenseVector[Int]]()
  var rvecs = Array[DenseVector[Int]]()
  var top_pes = Array[(Int, Int)]()
  var mem_dims = Seq[Array[Int]]()
  var ainvt = new DenseMatrix(0, 0, Array.empty[Int])
  var is_reuse_dim = Array[Boolean]()
  var rotate = false
}
class SAConfig(s: DenseMatrix[Int]){
  var start_t0 = 0
  var iter_range = Array[Int]()
  var pe_size = (0, 0)
  var exec_time = Array[Int]()
  var latency = 0
  var num_op = 0
  var width = Array[Int]()
  var io_type = Array[Boolean]()
  var op_type = 0
  var tensors = Array[TensorConfig]()
  var stt = s
  var addr_width = 16
  var custom_mem = 0
}
