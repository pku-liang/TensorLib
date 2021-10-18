package tensorlib

import breeze.linalg._
import breeze.numerics._

import math.abs
import math.round

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

object CalcDelta {
  def apply(matA: DenseMatrix[Int], matT: DenseMatrix[Int]) = {
    val matB = matA.mapValues(_.toDouble) * inv(matT.mapValues(_.toDouble))
    //println("at-1:\n"+matB)
    // val matZ = DenseMatrix.eye[Double](matB.cols) - pinv(matB) * matB
    val d = scala.collection.mutable.ArrayBuffer.empty[DenseVector[Double]]

    def gen(n: Int, x: Array[Double]): List[DenseVector[Double]] = {
      if (n == matB.cols)
        new DenseVector[Double](x.reverse) :: Nil
      else
       gen(n + 1, x :+ 0.0) ++ gen(n + 1, x :+ 1.0) ++ gen(n + 1, x :+ -1.0)
    }

    val testers = gen(0, Array.empty[Double])
    // .filter(x=>{
    //   val y = x.mapValues(_.toInt).toArray
    //   (y(0)|y(1))==0 || (Array(0)++y.drop(3)).reduce(_|_)==0
    // })
    for (v <- testers) {
      //if (!d.exists((u) => math.abs(u dot v) > 1e-5)) {
      val y  = matB * v
      val ny = norm(y)
      val nv = norm(v)
      val dv = d :+ v
      val dv_mat = DenseMatrix.tabulate[Double](dv.length, v.length){
        case(i, j) => dv(i)(j)
      }
      if(ny < 1e-5 && nv > 1e-5 && rank(dv_mat)==dv_mat.rows)
          d += v
    }
    //println(d.map(x=>x.mapValues(_.toInt)))
    // if (matT.rows - rank(matB) != d.length)
    //   throw new Exception("Can not extract valid dataflows")
    d.map(x=>x.mapValues(_.toInt))
  }
}

object Gen_dataflow{
  def get_valid_range(opSpec: OperatorSpec, stt: DenseMatrix[Int], time_init: Array[Int]) : Array[Int] = {

    val sttrange = opSpec.getSTTRange(stt).toArray
    val valid_time = TraverseTensor(time_init).filter(x=>{
      val stt_inst = Array(0, 0) ++ x//.asInstanceOf[Array[Int]]
      val iter_inst = inv(stt).mapValues(_.toInt) * DenseVector[Int](stt_inst)
      iter_inst.toArray.zipWithIndex.forall{case (a, b)=>{
        a>=0 && a < opSpec.iterList(b).ubound
      }}
    })
    val time_range = (0 until sttrange.length-2).map(i=>{
      valid_time.map(_(i)+1).reduce(_ max _)
    })
    //println("time_range:" + time_range.mkString(" "))
    time_range.toArray
  }
  def apply(opSpec: OperatorSpec, stt: DenseMatrix[Int]) : SAConfig = {
    println("[Tensorlib] Generating Dataflow.")
    val config = new SAConfig(stt)

    val sttrange = opSpec.getSTTRange(stt).toArray
    config.num_op = opSpec.numTensor
    config.pe_size = (sttrange(0), sttrange(1))
    
    config.latency = opSpec.latency
    config.simd = Array.fill(opSpec.numTensor)(1)
    config.width = opSpec.tensorList.map(_.width).toArray
    config.io_type = (0 until opSpec.numTensor).map(opSpec.outputID!=_).toArray//Array(false, true, true)
    config.stt = stt
    val pe_h = sttrange(0)
    val pe_w = sttrange(1)
    val pes = (0 until pe_h*pe_w).map(x=>(x/pe_w,x%pe_w))
    //println(pes)
    val time_init_ns = sttrange.drop(2)
    val time_init_s = time_init_ns.updated(0, pe_w)
    val time_range_ns = this.get_valid_range(opSpec, stt,time_init_ns)
    val time_range_s = this.get_valid_range(opSpec, stt,time_init_s)
    val sttlen = sttrange.length
    config.tensors = new Calc_Mem()(opSpec, stt).toArray
    config.exec_time = time_range_ns
    config

  }
}
class Min_time{
  def check(peid: (Int, Int), t1: Int, invstt: DenseMatrix[Int]) : Boolean = {
    val stt_len = invstt.rows
    val st_iter = Array(peid._1, peid._2, t1) ++ Array.fill(stt_len - 3)(0)
    val iter =  invstt * DenseVector(st_iter)
    //println(t1, st_iter, iter)
    iter.toArray.forall(_>=0)
  }
  def apply(peid: (Int, Int), stt: DenseMatrix[Int]): Int = {
    val invstt = inv(stt.mapValues(_.toDouble)).mapValues(_.toInt)
    var t1 = -1
    while(!check(peid, t1, invstt)){
      t1 = t1 + 1
    }
    t1
  }
}
class Calc_Mem{
  def apply(opSpec: OperatorSpec, stt: DenseMatrix[Int]) : List[TensorConfig] = {
    val sttrange = opSpec.getSTTRange(stt).toArray
    val sttlen = sttrange.length
    //println("sttrange:"+sttrange.mkString(" "))
    val pe_h = sttrange(0)
    val pe_w = sttrange(1)
    val pes = (0 until pe_h*pe_w).map(x=>(x/pe_w,x%pe_w))
    val time_init_ns = sttrange.drop(2)
    val time_init_s = time_init_ns.updated(0, pe_w)
    //println("time_init:"+time_init_ns.mkString(" "))
    val time_range_ns = Gen_dataflow.get_valid_range(opSpec, stt,time_init_ns)
    val time_range_s = Gen_dataflow.get_valid_range(opSpec, stt,time_init_s)
    //println(time_range_ns.mkString(" "),"|", time_range_s.mkString(" "))
    val config = opSpec.tensorList.map(ts=>{

      val rvecs = CalcDelta(opSpec.getAccessMat(ts).mapValues(_.toInt),stt)
      val ainvt = (opSpec.getAccessMat(ts).mapValues(_.toDouble) * inv(stt.mapValues(_.toDouble))).mapValues(_.toInt)
      //println(rvecs)
      val stat_vec = rvecs.filter(x=>{(x.length <= 3 || x(3 to -1).reduce(_ | _)==0) && x(0)==0 && x(1)==0})
      val pe_stat = stat_vec.length!=0
      //println("pe_stat:"+pe_stat)

      //println("time_range:"+time_range_s.mkString(" ")+","+time_range_ns.mkString(" "))
      val pe_rvec = rvecs.filter(x=>{x.length <= 3 || x(3 to -1).reduce(_ | _)==0}).map(_.toArray)
      .map(x=>{
        var upd = x
        if (x(0)==0&&x(1)==0){
          upd(1) = 1
        }
        upd
      })

      // mem reuse vector
      val mem_rvec = rvecs.filter(x=>{x.length > 3 && x(0 to 1).reduce(_ | _)==0 && x(3 to -1).reduce(_|_)!=0})
      //val inter_rvec =
      var top_pes = pes
      //println("top_pes:"+top_pes.length)
      pe_rvec.foreach(x=>{
        
        val dir = (x(0), x(1))
        top_pes=top_pes.filter(idx=>{idx._1 - dir._1 < 0 || idx._1 - dir._1 >= pe_h || idx._2 - dir._2 < 0 || idx._2 - dir._2 >= pe_w})
      })
      //println("top_pes:"+top_pes)
      val peid = top_pes(0)
      val tensor_range = opSpec.getAddrRange(ts).toArray
      //println("tensor range:"+tensor_range.mkString(" "))
      //给定TOP PEID，寻找这个PEID对应的t1的最大和最小值（在这个时间可以获取有效的数据）
      val max_t = 200
      val min_t = -200
      var st_t = 0
      var ed_t = 0
      for(i <- min_t until max_t){
        val st_vec = new DenseVector(Array(peid._1, peid._2, i) ++ Array.fill(sttlen-3)(0))
        val tensor_idx = (ainvt * st_vec).toArray
        val v=(tensor_idx zip tensor_range).forall{case(idx, r)=>{
          idx >= 0 && idx < r
        }}
        val st_vec_next = new DenseVector(Array(peid._1, peid._2, i+1) ++ Array.fill(sttlen-3)(0))

        val tensor_idx_next = (ainvt * st_vec_next).toArray
        val v_next=(tensor_idx_next, tensor_range).zipped.forall{case(idx, r)=>{
          idx >= 0 && idx < r
        }}
        if(!v && v_next)
          st_t = i
        if(v &&(!v_next))
          ed_t = i
      }
      //print("INNER LOOP:",ed_t, st_t)
      val mem_inner = ed_t - st_t
      val time_range = if(pe_stat) time_range_s else time_range_ns.updated(0, mem_inner)
      var mem_range = time_range.clone()
      var access_mat = DenseMatrix.eye[Int](mem_range.length)
      mem_rvec.foreach(x=>{
        val a = x.toArray.drop(2)
        val last_id = a.zipWithIndex.reverse.find{case(x, id)=>{
          x!=0
        }}.get
        val a_m1 = a.map(x=>{
          -(x/last_id._1)
        })
        a.zipWithIndex.foreach{case(x, id)=>{
          mem_range(id) = mem_range(id) + (mem_range(last_id._2)-1) * a_m1(id)
          access_mat(id,last_id._2) += a_m1(id)
        }}
        mem_range(last_id._2) = 1
      })
      //println("time range: "+time_range.mkString(" "))
      //println("mem range_st: "+mem_range.mkString(" "))
      val rm_dims = mem_rvec.map(x=>{
        val a = x.toArray.drop(2)
        val last_id = a.zipWithIndex.reverse.find{case(x, id)=>{
          x!=0
        }}.get
        last_id._2
      }).sortWith(_>_)
      rm_dims.foreach(x=>{

        access_mat.delete(x, Axis._0)
      })
      val is_reuse_dim = (0 until mem_range.length).map(mem_range(_)==1)
      //println("reuse dim:"+is_reuse_dim.mkString(" "))
      mem_range = mem_range.filter(_!=1)
      // if(!pe_stat)
      //   mem_range = mem_range.updated(0, mem_inner)
      //println("mem range: "+mem_range.mkString(" "))
      //println("RM: "+rm_dims.mkString(" "))
      val mem_range_scale = (0 until time_range.length).map(x=>{
        (Array(1) ++ mem_range.slice(0, x)).reduce(_*_)
      })
      var mem_dims = (access_mat.t * DenseVector(mem_range_scale.toArray)).toArray
      var dataflow = new TensorConfig()
      // mem-reuse condition= 
      dataflow.time_range = time_range.toArray
      dataflow.is_reuse_dim = is_reuse_dim.toArray
      dataflow.mem_range = mem_range
      dataflow.rvecs = rvecs.toArray
      dataflow.top_pes = top_pes.toArray
      dataflow.mem_dims = mem_dims
      dataflow.ainvt = ainvt
      dataflow
    })
    //val bank_mult = config.map(y=>y.reduce(_*_))
    //val res = bank_mult.map(x=>x.reduce(_+_))
    //println("size: "+config.map(x=>x.mem_range.reduce(_*_)*x.top_pes.length).reduce(_+_))
    //println("num: "+config.map(x=>x.top_pes.length).reduce(_+_))
    val t_size = config.map(x=>x.mem_range.reduce(_*_)*x.top_pes.length).reduce(_+_)
    val t_band = config.map(x=>x.top_pes.length).reduce(_+_)
    //println(t_size+","+t_band)
    config
  }
}