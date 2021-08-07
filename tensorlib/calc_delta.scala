package tensorlib

import breeze.linalg._
import breeze.numerics._

import math.abs
import math.round

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
class TensorDataflow{
  var time_range = Array[Int]()
  var mem_range = Array[Int]()
  var rvecs = Array[DenseVector[Int]]()
  var top_pes = Array[(Int, Int)]()
}
class SAConfig{
  var pe_size = (0, 0)
  var exec_time = Array[Int]()
  var latency = 0
  var num_op = 0
  var width = Array[Int]()
  var simd = Array[Int]()
  var io_type = Array[Int]()
  var op_type = 0
  var ops_dataflow = Array[TensorDataflow]()
}
object gen_dataflow{
  def get_valid_range(opSpec: OperatorSpec, stt: DenseMatrix[Int], time_init: Array[Int]) : Array[Int] = {
    
    val sttrange = opSpec.getSTTRange(stt).toArray
    val valid_time = TraverseTensor(time_init).filter(x=>{
      val stt_inst = Array(0, 0) ++ x//.asInstanceOf[Array[Int]]
      val iter_inst = inv(stt).mapValues(_.toInt) * DenseVector[Int](stt_inst)
      iter_inst.toArray.zipWithIndex.forall{case (a, b)=>{
        a>=0 && a < opSpec.iterList(b).ubound
      }}
    })
    //var time_ranger = Array.fill(sttrange.length-2)(0)
    
    // valid_time.foreach(x=>{
    //   x.zipWithIndex.foreach{case (a, b)=>{
    //     time_ranger(b) = time_ranger(b).max(a+1)
    //   }}
    // })
    //println("time_range"+time_ranger.mkString(" "))
    val time_range = (0 until sttrange.length-2).map(i=>{
      valid_time.map(_(i)+1).reduce(_ max _)
    })
    //println("get_valid_range:"+time_init.mkString(" "))
    //println("time_range:"+time_range.toArray.mkString(" "))
    //time_range.toArray
    time_init
  }
  def apply(opSpec: OperatorSpec, stt: DenseMatrix[Int]) : PEArray2D = {
    val config = new SAConfig()

    val sttrange = opSpec.getSTTRange(stt).toArray
    config.num_op = opSpec.numTensor
    config.pe_size = (sttrange(0), sttrange(1))
    config.exec_time = sttrange.drop(2)
    config.latency = opSpec.latency
    config.simd = Array.fill(opSpec.numTensor)(0)
    config.width = opSpec.tensorList.map(_.width).toArray
    config.io_type = Array(false, true, true)

    val pe_h = sttrange(0)
    val pe_w = sttrange(1)
    val pes = (0 until pe_h*pe_w).map(x=>(x/pe_w,x%pe_w))
    //println(pes)
    val time_init_ns = sttrange.drop(2)
    val time_init_s = time_init_ns.updated(0, pe_w)
    val time_range_ns = this.get_valid_range(opSpec, stt,time_init_ns)
    val time_range_s = this.get_valid_range(opSpec, stt,time_init_s)
    config.ops_dataflow = opSpec.tensorList.map(ts=>{
      val rvecs = CalcDelta(opSpec.getAccessMat(ts).mapValues(_.toInt),stt)
      val ainvt = (opSpec.getAccessMat(ts).mapValues(_.toDouble) * inv(stt.mapValues(_.toDouble))).mapValues(_.toInt)
      //println(rvecs)
      val stat_vec = rvecs.filter(x=>{(x.length <= 3 || x(3 to -1).reduce(_ | _)==0) && x(0)==0 && x(1)==0})
      val pe_stat = stat_vec.length!=0
      //println("pe_stat:"+pe_stat)
      val time_range = if(pe_stat) time_range_s else time_range_ns
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

      var mem_range = time_range.clone()
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
        }}
        mem_range(last_id._2) = 1
      })
      val rm_dims = mem_rvec.map(x=>{
        val a = x.toArray.drop(2)
        val last_id = a.zipWithIndex.reverse.find{case(x, id)=>{
          x!=0
        }}.get
        last_id._2
      })
      
      // var mem_dims = (0 until time_range.length).map(x=>{
      //   if (rm_dims.contains(x))
      //     (Array(1) ++ mem_range.slice(0, x-1)).reduce(_*_)
      //   else
      //     (Array(1) ++ mem_range.slice(0, x)).reduce(_*_)
      // })
      val bank_size = if(pe_stat)
          mem_range
        else
          mem_range.updated(0, mem_inner)
      println("mem_range"+bank_size.mkString(" ")+","+ top_pes.length)
      //bank_size.foreach(x=>println(x.mkString(" ")))
      //bank_size.reduce(_*_) * top_pes.length
      //(time_range, mem_dims, mem_range.reduce(_*_)*top_pes.length)
      var dataflow = new TensorDataflow()
      dataflow.time_range = time_range
      dataflow.mem_range = mem_range
      dataflow.rvecs = rvecs
      dataflow.top_pes = top_pes
      dataflow
    }).toArray


    val op_dataflow = opSpec.tensorList.map(t=>{
      var time_range = time_range_ns
      val rvecs = CalcDelta(opSpec.getAccessMat(t).mapValues(_.toInt),stt)
      // println(rvecs)
      // // PE reuse vector:
      val pe_rvec = rvecs.filter(x=>{x(3 to -1).reduce(_ | _)==0}).map(_.toArray)
      .map(x=>{ 
        var upd = x
        if (x(0)==0&&x(1)==0){
          time_range = time_range_s
          upd(1) = 1
        }
        upd
      })
      // mem reuse vector
      val mem_rvec = rvecs.filter(x=>{x(0 to 1).reduce(_ | _)==0 && x(3 to -1).reduce(_|_)!=0})
      //println("mem_rvec:"+mem_rvec)
      var top_pes = pes
      pe_rvec.foreach(x=>{
        val dir = (x(0), x(1))
        top_pes=top_pes.filter(idx=>{idx._1 - dir._1 < 0 || idx._1 - dir._1 >= pe_h || idx._2 - dir._2 < 0 || idx._2 - dir._2 >= pe_w})
      })
      //println(top_pes)
      var mem_range = time_range
      
      mem_rvec.foreach(x=>{
        val a = x.toArray.drop(2)
        val last_id = a.zipWithIndex.reverse.find{case(x, id)=>{
          x!=0
        }}.get
        val a_m1 = a.map(x=>{
          -(x/last_id._1)
        })
        a.zipWithIndex.foreach{case(x, id)=>{
          mem_range(id) = mem_range(id) + mem_range(last_id._2) * a_m1(id)
        }}
        mem_range(last_id._2) = 1
      })
      val rm_dims = mem_rvec.map(x=>{
        val a = x.toArray.drop(2)
        val last_id = a.zipWithIndex.reverse.find{case(x, id)=>{
          x!=0
        }}.get
        last_id._2
      })
      println("mem_range"+mem_range.mkString(" "))
      var mem_dims = (0 until time_range.length).map(x=>{
        if (rm_dims.contains(x))
          (Array(1) ++ mem_range.slice(0, x-1)).reduce(_*_)
        else
          (Array(1) ++ mem_range.slice(0, x)).reduce(_*_)
      })
      println("mem_dims"+mem_dims)
      (time_range, mem_dims, mem_range)
    })
    println("config",op)
    val vec = opSpec.tensorList.map(x=>1)
    val width = opSpec.tensorList.map(_.width)
    val access = opSpec.tensorList.map(x=>opSpec.getAccessMat(x).mapValues(_.toInt))
    val io_type = Array(false, true, true)
    val op_type = 0
    val latency = opSpec.latency
    val time_range = config.map(x=>{
      Array(latency) ++ x._1
    })
    val mem_range = config.map(x=>{
      Array(latency) ++ x._3
    })
    val mem_dims = config.map(x=>{
      Array(1) ++ x._2.map(t=>t*latency)
    })
    println("time_range:"+time_range.map(_.mkString(" ")))
    println("mem_dims:"+mem_dims.map(_.mkString(" ")))
    val mem_size = config.map(x=>x._3.reduce(_*_))
    new PEArray2D(
      (pe_h,pe_w), 
      vec, 
      width, 
      16,
      stt, 
      access, 
      io_type, 
      latency,  
      op_type, 
      time_range,
      mem_range,
      mem_dims,
      mem_size,
      false
    )

  }
}
object Calc_Mem{
  def apply(opSpec: OperatorSpec, stt: DenseMatrix[Int]) = {
    val sttrange = opSpec.getSTTRange(stt).toArray
    val sttlen = sttrange.length
    //println("sttrange:"+sttrange.mkString(" "))
    val pe_h = sttrange(0)
    val pe_w = sttrange(1)
    val pes = (0 until pe_h*pe_w).map(x=>(x/pe_w,x%pe_w))
    val time_init_ns = sttrange.drop(2)
    val time_init_s = time_init_ns.updated(0, pe_w)
    //println("time_init_s:"+time_init_s.mkString(" "))
    val time_range_ns = gen_dataflow.get_valid_range(opSpec, stt,time_init_ns)
    val time_range_s = gen_dataflow.get_valid_range(opSpec, stt,time_init_s)
    //println(time_range_ns.mkString(" "),"|", time_range_s.mkString(" "))
    val config = opSpec.tensorList.map(ts=>{
      
      val rvecs = CalcDelta(opSpec.getAccessMat(ts).mapValues(_.toInt),stt)
      val ainvt = (opSpec.getAccessMat(ts).mapValues(_.toDouble) * inv(stt.mapValues(_.toDouble))).mapValues(_.toInt)
      //println(rvecs)
      val stat_vec = rvecs.filter(x=>{(x.length <= 3 || x(3 to -1).reduce(_ | _)==0) && x(0)==0 && x(1)==0})
      val pe_stat = stat_vec.length!=0
      //println("pe_stat:"+pe_stat)
      val time_range = if(pe_stat) time_range_s else time_range_ns
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

      var mem_range = time_range.clone()
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
        }}
        mem_range(last_id._2) = 1
      })
      val rm_dims = mem_rvec.map(x=>{
        val a = x.toArray.drop(2)
        val last_id = a.zipWithIndex.reverse.find{case(x, id)=>{
          x!=0
        }}.get
        last_id._2
      })
      
      // var mem_dims = (0 until time_range.length).map(x=>{
      //   if (rm_dims.contains(x))
      //     (Array(1) ++ mem_range.slice(0, x-1)).reduce(_*_)
      //   else
      //     (Array(1) ++ mem_range.slice(0, x)).reduce(_*_)
      // })
      val bank_size = if(pe_stat)
          mem_range
        else
          mem_range.updated(0, mem_inner)
      println("mem_range"+bank_size.mkString(" ")+","+ top_pes.length)
      //bank_size.foreach(x=>println(x.mkString(" ")))
      //bank_size.reduce(_*_) * top_pes.length
      //(time_range, mem_dims, mem_range.reduce(_*_)*top_pes.length)
      (time_range, mem_range, top_pes)
    })
    //val bank_mult = config.map(y=>y.reduce(_*_))
    //val res = bank_mult.map(x=>x.reduce(_+_))
    //println("size: "+config)
    config
  }
}