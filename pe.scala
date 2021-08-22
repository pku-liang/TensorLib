package tensorlib

import chisel3._
import chisel3.util._
//import chisel3.Driver
import chisel3.iotesters.{PeekPokeTester, Driver}
import java.io.PrintWriter
import scala.collection.mutable.ArrayBuffer
import breeze.linalg._
import breeze.numerics._


// input deq, output enq
class PETensorIO(w: Int, stat: Boolean) extends Bundle {
  val in = Input(Valid(UInt(w.W)))
  val out = Output(Valid(UInt(w.W)))
  //val sig_stat2trans = Input(Bool())
  override def cloneType = (new PETensorIO(w, stat)).asInstanceOf[this.type]
}
class PE(vec: Array[Int], width: Array[Int], dataflow: Array[TensorDataflow], io_type: Array[Boolean], num_op : Int, latency: Int, op_type: Int) extends Module{
  val io = IO(new Bundle {
    val data = new HeterogeneousBag(
      for(i <- 0 until num_op) yield{
        new PETensorIO(width(i)*vec(i), false)
    })
    val sig_stat2trans = Input(Bool())
  })
  printf(p"data0=${io.data(0).in.bits},data1=${io.data(1).in.bits},data2=${io.data(2).in.bits},sig=${io.sig_stat2trans}\n")
  printf(p"${io}\n")
  def im_factory(dataflow: TensorDataflow, io_type: Boolean, width: Int, latency: Int):InternalModule={
    dataflow match {
      case DirectDataflow => if(io_type) new DirectInput(width) else new DirectOutput(width)
      case SystolicDataflow => if(io_type) new SystolicInput(width, latency) else new SystolicOutput(width)
      case StationaryDataflow => if(io_type) new StationaryInput_Pipeline(width, latency) else new StationaryOutput(width, latency)
    }
  }
  //val exec_cycle = RegInit(0.U(20.W))

  val pe = Module(new ComputeCell_Latency(vec, width(0), latency)).io
  val ims = for(i <- 0 until num_op) yield{
    Module(im_factory(dataflow(i), io_type(i), vec(i) * width(i), latency)).io
  }
  //val exec = VecInit(ims.map(it=>it.to_pe.valid)).reduce(_ && _)
  //exec_cycle := Mux(exec_cycle + exec === io.stage_cycle, 0.U, exec_cycle + exec)
  for(i <- 0 until num_op){
    ims(i).port <> io.data(i)
    pe.data(i).in := ims(i).to_cell.bits
    if(!io_type(i)){
      ims(i).from_cell.get.bits := pe.data(i).out
      ims(i).from_cell.get.valid := ShiftRegister(ims(i).to_cell.valid, latency)
      //ims(i).from_cell.get.valid := VecInit(ims.map(it=>it.to_pe.valid)).reduce(_ && _)
      //printf("pe out:%d %d\n", ims(i).from_cell.get.bits, ims(i).from_cell.get.valid)
    }else{
      //printf("pe in:%d %d\n", io.data(i).in.bits, io.data(i).in.valid)
    }
    //VecInit(x.map(it=>pes(it._1)(it._2).data(i).out.valid)).reduce(_ && _)
      
    if(dataflow(i)==StationaryDataflow){
      ims(i).sig_stat2trans.get := io.sig_stat2trans
    }
  }
}

class PEArrayDataBundle(io_type: Boolean, vec: Int, width: Int) extends Bundle{
  val in = if(io_type) 
      Some(Vec(vec, Input(Valid(Valid(UInt(width.W))))))
    else
      None
  val out = if(!io_type) 
      Some(Vec(vec, Output(Valid(UInt(width.W)))))
    else
      None
}

