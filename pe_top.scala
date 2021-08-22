package tensorlib

import chisel3._
import chisel3.util._
//import chisel3.Driver
import chisel3.iotesters.{PeekPokeTester, Driver}
import java.io.PrintWriter
import scala.collection.mutable.ArrayBuffer
import breeze.linalg._
import breeze.numerics._


class PETop(params: SAConfig) extends Module{
  val io = IO(new Bundle{

  })
  val latency = params.latency
  val exec_time = Array(latency) ++ params.exec_time
  val exec_ctrl = Module(new MultiDimTime(params.addr_width, exec_time, Array.fill(exec_time.length)(0))).io
  exec_ctrl.in := true.B
  val num_op = params.num_op
  val op_type = 0

  val dataflows = for(i <- 0 until num_op) yield{
    val rvec = params.tensors(i).rvecs(0)
    val xy_diff = rvec(0)!=0||rvec(1)!=0
    val t_diff = rvec(2)!=0
    //var res = TensorDataflow
    if(!xy_diff && t_diff){
      StationaryDataflow
    }else if(xy_diff && t_diff){
      SystolicDataflow
    }else{
      DirectDataflow
    }
  }
  val pe_h = params.pe_size._1
  val pe_w = params.pe_size._2
  val pes = for(i <- 0 until pe_h) yield{
    for(j <- 0 until pe_w) yield{
      Module(new PE(params.simd, params.width, dataflows.toArray, params.io_type, num_op, latency, op_type)).io
    }
  }
  val st_time = for(i <- 0 until pe_h)yield{
    for(j <- 0 until pe_w)yield{
      Min_time((pe_h, pe_w), params.stt)
    }
  }
  for(i <- 0 until pe_h){
    for(j <- 0 until pe_w){
      for(k <- 0 until num_op){
        pes(i)(j).data(0).in.bits := 1.U
        pes(i)(j).data(1).in.bits := 1.U
        pes(i)(j).data(2).in.bits := 2.U
        pes(i)(j).data(0).in.valid := true.B
        pes(i)(j).data(1).in.valid := true.B
        pes(i)(j).data(2).in.valid := true.B
        pes(i)(j).sig_stat2trans := (exec_ctrl.index(1)===st_time(i)(j).asUInt)
      }
    }
  }
}

class PE_Tester(c: PETop) extends PeekPokeTester(c){
  for(i <- 0 until 20){
    step(1)
  }
}
object TestPE_App extends App{
  //chisel3.Driver.execute(args, () => new TestTop())
  val opSpec = new OperatorSpec {
    val k :: c :: x :: Nil = genIterators(3)
    val o :: w :: i :: Nil = genTensor(3)

    setExpr(o(k)(x) += w(k)(c) * i(c)(x))
    k.setRange(16)
    c.setRange(16)
    x.setRange(20)
    setLatency(1)
    o.setWidth(16)
    w.setWidth(16)
    i.setWidth(16)
  }
  val stt = DenseMatrix(
   (1,  0,  0),  
   (0,  1,  0),  
   (1,  1,  1),  
  )
  val config = Gen_dataflow(opSpec, stt)
  Driver(() => new PETop(config))(c => new PE_Tester(c))
}