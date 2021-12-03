package tensorlib

import breeze.linalg._
import breeze.numerics._

import math.abs
import math.round
import scala.math.{max, min}
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
    println("at-1:\n"+matB)
    // val matZ = DenseMatrix.eye[Double](matB.cols) - pinv(matB) * matB
    val d = scala.collection.mutable.ArrayBuffer.empty[DenseVector[Double]]

    def gen(n: Int, x: Array[Double]): List[DenseVector[Double]] = {
      if (n == matB.cols)
        new DenseVector[Double](x.reverse) :: Nil
      else
       gen(n + 1, x :+ 0.0) ++ gen(n + 1, x :+ 1.0) ++ gen(n + 1, x :+ -1.0)// ++ gen(n + 1, x :+ 2.0)
    }

    val testers = gen(0, Array.empty[Double]).filter(x=>{
      val y = x.mapValues(_.toInt)
      y(2) != -1 && (y(0)==0||y(1)==0)    // no diagonal connection
    })
    // .filter(x=>{
    //   val y = x.mapValues(_.toInt).toArray
    //   (y(0)|y(1))==0 || (Array(0)++y.drop(3)).reduce(_|_)==0
    // })
    val df_tester = testers.filter(x=>{
      val y = x.mapValues(_.toInt).toArray
      (Array(0) ++ y.drop(3)).reduce(_|_)==0
    }).sortWith(_(2)>_(2))
    val mem_tester = testers.filter(x=>{
      val y = x.mapValues(_.toInt).toArray
      (y(0)|y(1))==0
    })
    for (t <- Array(df_tester, mem_tester, testers)){
      for (v <- t) {
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
    }
    
    
    //println(d.map(x=>x.mapValues(_.toInt)))
    // if (matT.rows - rank(matB) != d.length)
    //   throw new Exception("Can not extract valid dataflows")
    // d.foreach(println(_))
    // println()
    val r = d.map(x=>x.mapValues(_.toInt))
    println("reuse vector:"+r)
    r 
  }
}

object Gen_dataflow{

  def apply(opSpec: OperatorSpec, stt: DenseMatrix[Int],rotate: Boolean = false) : SAConfig = {
    println("[Tensorlib] Generating Dataflow.")
    val config = new SAConfig(stt)

    val sttrange = opSpec.getSTTRange(stt).toArray
    config.num_op = opSpec.numTensor
    config.pe_size = (sttrange(0), sttrange(1))
    config.iter_range = opSpec.getIterRange()
    config.latency = opSpec.latency
    //config.simd = Array(8,1,8)//Array.fill(opSpec.numTensor)(1)
    config.width = opSpec.tensorList.map(_.width).toArray
    config.io_type = (0 until opSpec.numTensor).map(opSpec.outputID!=_).toArray//Array(false, true, true)
    config.stt = stt
    config.op_type = opSpec.op_type
    config.custom_mem = opSpec.custom_mem
    println("op type: "+config.op_type)
    val pe_h = sttrange(0)
    val pe_w = sttrange(1)
    val pes = (0 until pe_h*pe_w).map(x=>(x/pe_w,x%pe_w))
    val sttlen = sttrange.length
    config.tensors = Calc_Mem(opSpec, stt, rotate).toArray
    config.exec_time = config.tensors.map(_.exec_time).reduce{(x: Array[Int], y: Array[Int])=>
      (x zip y).map{case(t, v)=>max(t, v)}
    }.toArray
    config.start_t0 = config.tensors.map(_.start_t0).reduce(_ min _)
    println(s"start t0 = ${config.start_t0}")
    config

  }
}

object Mem_bound{
  def apply(peid: (Int, Int), ainvt: DenseMatrix[Int], tensor_range: Array[Int]): (Int, Int) = {
    val sttlen = ainvt.cols
    val base_tvec = new DenseVector(Array(peid._1, peid._2) ++ Array.fill(sttlen-2)(0))
    val base_ivec = (ainvt * base_tvec).toArray
    val time_tvec = new DenseVector(Array(0, 0, 1) ++ Array.fill(sttlen-3)(0))
    val time_ivec = (ainvt * time_tvec).toArray
    var up_bound = tensor_range.reduce(_+_)
    var low_bound = -up_bound
    // println("base ivec:"+base_ivec.mkString(" "))
    // println("time ivec:"+time_ivec.mkString(" "))
    // println("iter_range:"+iter_range)
    for(i <- 0 until tensor_range.length){
      if(time_ivec(i)!=0){
        up_bound = min(up_bound, (tensor_range(i) - base_ivec(i))/time_ivec(i))
        low_bound = max(low_bound, -base_ivec(i) / time_ivec(i))
      }
    }
    //println(s"$peid, $up_bound, $low_bound")
    (low_bound, up_bound)
  }
}
object Calc_Mem{
  def apply(opSpec: OperatorSpec, stt: DenseMatrix[Int], rotate: Boolean = false) : List[TensorConfig] = {
    val sttrange = opSpec.getSTTRange(stt).toArray
    val sttlen = sttrange.length
    //println("sttrange:"+sttrange.mkString(" "))
    val pe_h = sttrange(0)
    val pe_w = sttrange(1)
    val pes = (0 until pe_h*pe_w).map(x=>(x/pe_w,x%pe_w))
    val time_init_ns = sttrange.drop(2)
    val time_init_s = time_init_ns.updated(0, pe_w)
    //val time_range_ns = Gen_dataflow.get_valid_range(opSpec, stt,time_init_ns)
    //val time_range_s = Gen_dataflow.get_valid_range(opSpec, stt,time_init_s)
    val invstt = inv(stt.mapValues(_.toDouble)).mapValues(_.toInt)
    //println(time_range_ns.mkString(" "),"|", time_range_s.mkString(" "))
    val config = opSpec.tensorList.map(ts=>{

      val rvecs = CalcDelta(opSpec.getAccessMat(ts).mapValues(_.toInt),stt)
      val ainvt = opSpec.getAccessMat(ts).mapValues(_.toInt) * invstt
      val stat_vec = rvecs.filter(x=>{(x.length <= 3 || x(3 to -1).reduce(_ | _)==0) && x(0)==0 && x(1)==0})
      val pe_stat = stat_vec.length!=0
      val pe_rvecs_pre = rvecs.filter(x=>{x.length <= 3 || x(3 to -1).reduce(_ | _)==0})
      val can_rotate = rotate && pe_rvecs_pre.length==2
      val pe_rvecs = if(can_rotate) Array(rvecs(0)) else rvecs.filter(x=>{x.length <= 3 || x(3 to -1).reduce(_ | _)==0}).toArray
      println("rvecs"+pe_rvecs_pre)
      // .map(x=>{
      //   var upd = x
      //   if (x(0)==0&&x(1)==0){
      //     upd(1) = 1
      //   }
      //   upd
      // })
      val mem_rvec_pre = rvecs.filter(x=>{x.length > 3 && x(0 to 1).reduce(_ | _)==0 && x(3 to -1).reduce(_|_)!=0}).toArray
      val mem_rvec = if(can_rotate) Array(rvecs(1)) ++ mem_rvec_pre else mem_rvec_pre

      // 确定不需要的dimension并删除
      val time_range = if(pe_stat) time_init_s else time_init_ns
      println("time range: "+time_range.mkString(" "))
      var mem_range = time_range.clone()
      var access_mat = DenseMatrix.eye[Int](mem_range.length)
      mem_rvec.foreach(x=>{
        println(s"x:$x")
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
        mem_range(last_id._2) = 0
      })
      val rm_dims = mem_rvec.map(x=>{
        val a = x.toArray.drop(2)
        val last_id = a.zipWithIndex.reverse.find{case(x, id)=>{
          x!=0
        }}.get
        last_id._2
      }).sortWith(_>_)
      rm_dims.foreach(x=>{
        access_mat = access_mat.delete(x, Axis._0)
      })
      //println(s"rm_dims:${rm_dims},access mat:$access_mat")
      val is_reuse_dim = (0 until mem_range.length).map(mem_range(_)==0)
      mem_range = mem_range.filter(_!=0)
      var top_pes = pes
      //println("top_pes:"+top_pes.length)
      pe_rvecs.foreach(x=>{
        
        val dir = if(x(0)==0&&x(1)==0) (0, 1) else (x(0), x(1))
        top_pes=top_pes.filter(idx=>{idx._1 - dir._1 < 0 || idx._1 - dir._1 >= pe_h || idx._2 - dir._2 < 0 || idx._2 - dir._2 >= pe_w})
      })
      //println("top_pes:"+top_pes)
      val tensor_range = opSpec.getAddrRange(ts).toArray
      //println("tensor range:"+tensor_range.mkString(" "))
      //给定TOP PEID，寻找这个PEID对应的t1的最大和最小值（在这个时间可以获取有效的数据）
      // 对每个top peid，寻找t1的最小值和最大值，作为memory size
      val bounds = for(peid <- top_pes) yield{
        Mem_bound(peid, ainvt, tensor_range)
      }
      var time_range_max = if(pe_stat) 0 else bounds.map{x=>x._2-x._1}.reduce(_ max _)
      val start_t0 = if(pe_stat) 0 else bounds.map(x=>x._1).reduce(_ min _)
      // println("mem range_st: "+mem_range.mkString(" "))
      
      
      // if(!pe_stat)
      //   mem_range = mem_range.updated(0, mem_inner)
      println("mem range: "+mem_range.mkString(" ")+" "+top_pes.length)
      val mem_dims = for((peid, bound) <- (top_pes zip bounds))yield{
        val pe_time_range = if(pe_stat) time_init_ns.updated(0, pe_w) else time_init_ns.updated(0, bound._2-bound._1)
        //val mem_inner_range = if(pe_stat) time_init_ns.updated(0, pe_w) else time_range.updated(0, bound._2-bound._1)
        val pe_mem_range = (access_mat * DenseVector(pe_time_range)).toArray
        val mem_range_scale = (0 until time_range.length).map(x=>{
          (Array(1) ++ pe_mem_range.slice(0, x)).reduce(_*_)
        }).toArray
        //val mem_dims = (access_mat.t * DenseVector(mem_range_scale.toArray)).toArray
        //println(s"pe_time_range: ${pe_time_range.mkString(" ")},mem_range: ${pe_mem_range.mkString(" ")}")
        (pe_time_range, pe_mem_range, mem_range_scale)
        
      }
      
      
      var dataflow = new TensorConfig()
      // mem-reuse condition= 
      // time range是取memory的range，mem_range是memory本身的range
      dataflow.start_t0 = start_t0
      
      dataflow.exec_time = time_init_ns.updated(0, time_range_max)
      println("dataflow exec time:"+dataflow.exec_time.mkString(" "))
      dataflow.is_reuse_dim = is_reuse_dim.toArray
      dataflow.pe_rvecs = pe_rvecs
      dataflow.rvecs = rvecs.toArray
      dataflow.ainvt = ainvt
      dataflow.top_pes = top_pes.toArray
      dataflow.time_range = mem_dims.map(_._1)
      dataflow.mem_range = mem_dims.map(_._2)
      dataflow.mem_dims = mem_dims.map(_._3)
      dataflow.tensor_range = tensor_range
      dataflow.rotate = can_rotate
      dataflow
    })
    config.foreach(c=>{
      print(c.mem_range.map(r=>r.reduce(_*_)).reduce(_+_)+" ")
    })
    println()
    //val bank_mult = config.map(y=>y.reduce(_*_))
    //val res = bank_mult.map(x=>x.reduce(_+_))
    //println("size: "+config.map(x=>x.mem_range.reduce(_*_)*x.top_pes.length).reduce(_+_))
    //println("num: "+config.map(x=>x.top_pes.length).reduce(_+_))
    //val t_size = config.map(x=>x.mem_range.reduce(_*_)*x.top_pes.length).toArray
    //val t_band = config.map(x=>x.top_pes.length).reduce(_+_)
    //println("size:"+t_size.mkString(" "))
    config
  }
}
object Name_Dataflow{
  def apply(rvec: Array[Int]) : TensorDataflow = {
    val xy_diff = rvec(0)!=0||rvec(1)!=0
    val t_diff = rvec(2)!=0
    //var res = TensorDataflow
    val df = if(!xy_diff && t_diff){
      StationaryDataflow
    }else if(xy_diff && t_diff){
      SystolicDataflow
    }else{
      DirectDataflow
    }
    df
  }
}