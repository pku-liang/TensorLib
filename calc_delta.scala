package tensorlib

import breeze.linalg._
import breeze.numerics._

import math.abs
import math.round

object CalcDelta {
  def apply(matA: DenseMatrix[Int], matT: DenseMatrix[Int]) = {
    println(matA)
    println()
    val matB = matA.mapValues(_.toDouble) * inv(matT.mapValues(_.toDouble))
    println(matB)
    // val matZ = DenseMatrix.eye[Double](matB.cols) - pinv(matB) * matB
    val d = scala.collection.mutable.ArrayBuffer.empty[DenseVector[Double]]

    def gen(n: Int, x: Array[Double]): List[DenseVector[Double]] = {
      if (n == matB.cols)
        new DenseVector[Double](x.reverse) :: Nil
      else
       gen(n + 1, x :+ 0.0) ++ gen(n + 1, x :+ 1.0) ++ gen(n + 1, x :+ -1.0)
    }

    val testers = gen(0, Array.empty[Double]).filter(x=>{
      val y = x.mapValues(_.toInt).toArray
      (y(0)|y(1))==0 || (Array(0)++y.drop(3)).reduce(_|_)==0
    })
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
    println(d.map(x=>x.mapValues(_.toInt)))
    if (matT.rows - rank(matB) != d.length)
      throw new Exception("Can not extract valid dataflows")
    d.map(x=>x.mapValues(_.toInt))
  }
}

object gen_dataflow{
  def apply(opSpec: OperatorSpec, stt: DenseMatrix[Int]) = {
    val sttrange = opSpec.getSTTRange(stt).toArray
    val pe_h = sttrange(0)
    val pe_w = sttrange(1)
    val pes = (0 until pe_h*pe_w).map(x=>(x/pe_w,x%pe_w))
    println(pes)

    val config = opSpec.tensorList.map(t=>{
      var time_init = sttrange.drop(2)
      val rvecs = CalcDelta(opSpec.getAccessMat(t).mapValues(_.toInt),stt)
      // println(rvecs)
      // // PE reuse vector:
      val pe_rvec = rvecs.filter(x=>{x(3 to -1).reduce(_ | _)==0}).map(_.toArray)
      .map(x=>{ 
        var updated = x
        if (x(0)==0&&x(1)==0){
          updated(1) = 1
          time_init(0) = pe_w
        }
          
        updated
      })
      // mem reuse vector
      val mem_rvec = rvecs.filter(x=>{x(0 to 1).reduce(_ | _)==0 && x(3 to -1).reduce(_|_)!=0})
      println("mem_rvec:"+mem_rvec)
      var top_pes = pes
      pe_rvec.foreach(x=>{
        val dir = (x(0), x(1))
        top_pes=top_pes.filter(idx=>{idx._1 - dir._1 < 0 || idx._1 - dir._1 >= pe_h || idx._2 - dir._2 < 0 || idx._2 - dir._2 >= pe_w})
      })
      println(top_pes)
      // var iter_range = opSpec.iterList.map(_.ubound)
      //TraverseTensor(sttrange.drop(2)).foreach(x=>println(x))
      val valid_time = TraverseTensor(time_init).filter(x=>{
        val stt_inst = Array(0, 0) ++ x//.asInstanceOf[Array[Int]]
        //println("stt_inst"+stt_inst.mkString(" "))
        val iter_inst = inv(stt).mapValues(_.toInt) * DenseVector[Int](stt_inst)
        //println(iter_inst)
        iter_inst.toArray.zipWithIndex.forall{case (a, b)=>{
          a>=0 && a < opSpec.iterList(b).ubound
        }}
      })
      var time_range = Array.fill(sttrange.length-2)(0)
      valid_time.foreach(x=>{
        x.zipWithIndex.foreach{case (a, b)=>{
          time_range(b) = time_range(b).max(a+1)
        }}
      })
      println("time_range"+time_range.mkString(" "))
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
      (time_range, mem_dims, mem_range.reduce(_*_))
    })
    println("config",config)
    val vec = opSpec.tensorList.map(x=>1)
    val width = opSpec.tensorList.map(_.width)
    val access = opSpec.tensorList.map(x=>opSpec.getAccessMat(x).mapValues(_.toInt))
    val io_type = Array(false, true, true)
    val op_type = 0
    val latency = opSpec.latency
    val time_range = config.map(x=>{
      Array(latency) ++ x._1
    })
    val mem_dims = config.map(x=>{
      Array(1) ++ x._2.map(t=>t*latency)
    })
    val mem_size = config.map(x=>x._3)
    val str = chisel3.Driver.emitVerilog(new PEArray2D(
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
      mem_dims,
      mem_size,
      false
    ))

  }
}