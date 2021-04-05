package systolic

import chisel3._
import chisel3.util._
//import chisel3.Driver
import chisel3.iotesters.{PeekPokeTester, Driver}
import java.io.PrintWriter

sealed trait TensorDataflow
case object DirectDataflow extends TensorDataflow
case object SystolicDataflow extends TensorDataflow
case object StationaryDataflow extends TensorDataflow
// input deq, output enq
class PETensorIO(w: Int, stat: Boolean) extends Bundle {
  val in = DeqIO(UInt(w.W))
  val out = EnqIO(UInt(w.W))
  val sig_stat2trans = if(stat)Some(Input(Bool()))else None
}
class PE(vec: Array[Int], width: Array[Int], dataflow: Array[TensorDataflow], io_type: Array[Boolean], num_op : Int, latency: Int, op_type: Int) extends Module{
  val io = IO(new Bundle {
    val data = new HeterogeneousBag(
      for(i <- 0 until num_op) yield{
        new PETensorIO(width(i)*vec(i), dataflow(i)==StationaryDataflow)
    })
  })
  def im_factory(dataflow: TensorDataflow, io_type: Boolean, width: Int, latency: Int):InternalModule={
    dataflow match {
      case DirectDataflow => if(io_type) new DirectInput(width) else new DirectOutput(width)
      case SystolicDataflow => if(io_type) new SystolicInput(width) else new SystolicOutput(width)
      case StationaryDataflow => if(io_type) new StationaryInput_Pipeline(width, latency) else new StationaryOutput_Pipeline(width, latency)
    }
  }
  //val exec_cycle = RegInit(0.U(20.W))

  val pe = 
    if(op_type==0)
      Module(new ComputeCellF(vec, width(0), num_op)).io
    else if(op_type==1)
      Module(new ComputeCell_Latency(vec, width(0), latency)).io
    else
      Module(new ComputeCell_Int(vec, width)).io
  val ims = for(i <- 0 until num_op) yield{
    Module(im_factory(dataflow(i), io_type(i), vec(i) * width(i), latency)).io
  }
  //val exec = VecInit(ims.map(it=>it.to_pe.valid)).reduce(_ && _)
  //exec_cycle := Mux(exec_cycle + exec === io.stage_cycle, 0.U, exec_cycle + exec)
  for(i <- 0 until num_op){
    ims(i).in <> io.data(i).in
    io.data(i).out <> ims(i).out
    pe.data(i).in <> ims(i).to_pe.bits
    
    ims(i).to_pe.ready := true.B

    if(!io_type(i)){
      ims(i).from_pe.get.bits := pe.data(i).out
      ims(i).from_pe.get.valid := VecInit(ims.map(it=>it.to_pe.valid)).reduce(_ && _)
    }
    //VecInit(x.map(it=>pes(it._1)(it._2).data(i).out.valid)).reduce(_ && _)
      
    if(dataflow(i)==StationaryDataflow){
      ims(i).sig_stat2trans.get := io.data(i).sig_stat2trans.get
    }
  }
}

class PEArray2D(pe_h: Int, pe_w: Int, vec: Array[Int], width: Array[Int], stt: Array[Array[Int]], io_type: Array[Boolean], num_op : Int, latency: Int,  op_type: Int = 0) extends Module{
  val dataflows = for(i <- 0 until num_op) yield{
    val xy_diff = stt(i)(0)!=0||stt(i)(1)!=0
    val t_diff = stt(i)(2)!=0
    //var res = TensorDataflow
    if(!xy_diff && t_diff){
      StationaryDataflow
    }else if(xy_diff && t_diff){
      SystolicDataflow
    }else{
      DirectDataflow
    }
  }
  // pe definition
  val pes = for(i <- 0 until pe_h) yield{
    for(j <- 0 until pe_w) yield{
      Module(new PE(vec, width, dataflows.toArray, io_type, num_op, latency, op_type)).io
    }
  }
  var num_io_banks = Array.fill(num_op)(0)
  // pe connection, calculate bank number
  for(i <- 0 until num_op){
    
    val dirx = if(dataflows(i)==StationaryDataflow){
      if(io_type(i))
        1
      else
        -1
    }else stt(i)(0)
    val diry = if(dataflows(i)==StationaryDataflow) 0 else stt(i)(1)
    println(i, dirx, diry)
    // connection ports
    if(io_type(i) ||(dataflows(i)!=DirectDataflow)){   // direct output use reduction tree
      for(j <- 0 until pe_h){
        for(k <- 0 until pe_w){
          if(j+diry < pe_h && j+diry >=0 && k+dirx < pe_w && k+dirx >= 0){
            pes(j)(k).data(i).out <> pes(j+diry)(k+dirx).data(i).in
          }else{
            pes(j)(k).data(i).out.ready := true.B
          }
        }
      }
    }
    
    // input ports
    if(io_type(i)){
      for(j <- 0 until pe_h){
        for(k <- 0 until pe_w){
          if((j-diry >= pe_h || j-diry < 0 || k-dirx >= pe_w || k-dirx < 0)||(dirx == 0 && diry == 0)){
            num_io_banks(i) = num_io_banks(i) + 1
          }
        }
      }
    }else{
      for(j <- 0 until pe_h){
        for(k <- 0 until pe_w){
          if((j+diry >= pe_h || j+diry < 0 || k+dirx >= pe_w || k+dirx < 0)||(dirx == 0 && diry == 0)){
            num_io_banks(i) = num_io_banks(i) + 1
          }
        }
      }
    }
  }
  println("!!!",num_io_banks(0),num_io_banks(1),num_io_banks(2))
  val io = IO(new Bundle {
    val data = new HeterogeneousBag(
      for(i <- 0 until num_op) yield{
        if(io_type(i)) 
          Vec(num_io_banks(i), DeqIO(UInt((vec(i) * width(i)).W)))
        else
          Vec(num_io_banks(i), EnqIO(UInt((vec(i) * width(i)).W)))
    })
    val work = Input(Bool())
    val stage_cycle = Input(UInt(20.W))
  })
  // val cur_cycle = RegInit(VecInit(Seq.fill(pe_h * pe_w)(0.U(20.W))))
  // when(io.work){
  //   for(i <- 0 until pe_h * pe_w)
  //     cur_cycle(i) := Mux(cur_cycle(i) + 1.U === io.stage_cycle, 0.U, cur_cycle(i) + 1.U)
  // }
  val cur_cycle = RegInit(0.U(20.W))
  when(io.work){
    cur_cycle := Mux(cur_cycle + 1.U === io.stage_cycle, 0.U, cur_cycle + 1.U)
  }
  for(i <- 0 until num_op){
    val dirx = if(dataflows(i)==StationaryDataflow){
      if(io_type(i))
        1
      else
        -1
    }else stt(i)(0)
    val diry = if(dataflows(i)==StationaryDataflow) 0 else stt(i)(1)
    var bank_id = 0
    // input ports
    if(io_type(i)){
      for(j <- 0 until pe_h){
        for(k <- 0 until pe_w){
          if((j-diry >= pe_h || j-diry < 0 || k-dirx >= pe_w || k-dirx < 0)||(dirx == 0 && diry == 0)){
            pes(j)(k).data(i).in <> io.data(i)(bank_id)
            bank_id = bank_id + 1
          }
        }
      }
    }else if(dataflows(i)!=DirectDataflow){
      for(j <- 0 until pe_h){
        for(k <- 0 until pe_w){
          if((j+diry >= pe_h || j+diry < 0 || k+dirx >= pe_w || k+dirx < 0)||(dirx == 0 && diry == 0)){
            io.data(i)(bank_id) <> pes(j)(k).data(i).out
            bank_id = bank_id + 1
          }
          if((j-diry >= pe_h || j-diry < 0 || k-dirx >= pe_w || k-dirx < 0)||(dirx == 0 && diry == 0)){
            pes(j)(k).data(i).in.bits := 0.U
            if(dataflows(i)==StationaryDataflow)
              pes(j)(k).data(i).in.valid := false.B
            else
              pes(j)(k).data(i).in.valid := true.B
          }
        }
      }
    }else{
      // reduction tree
      import scala.collection.mutable.Set
      import scala.collection.mutable.ListBuffer
      val all_trees = Set[List[(Int, Int)]]();
      for(j <- 0 until pe_h){
        for(k <- 0 until pe_w){
          // no input
          pes(j)(k).data(i).in.valid := false.B
          pes(j)(k).data(i).in.bits := 0.U
          var in_set = false
          all_trees.foreach(x=>{
            if (x contains((j, k))){
              in_set = true
            }
          })
          if(!in_set){
            var lb = new ListBuffer[(Int, Int)]()
            var (dj ,dk) = (j, k)
            while(dj < pe_h && dj >= 0 && dk < pe_w && dk >= 0){
              lb += ((dj, dk))
              dj = dj + diry
              dk = dk + dirx
              //(dj, dk) = (j+diry,  k+dirx)
            }
            all_trees += lb.toList
          }
        }
      }
      var out_id = 0
      all_trees.foreach(x =>{
        val tree = Module(new AddTree(x.length, vec(2) * width(i))).io
        for(q <- 0 until x.length){
          tree.in.bits(q) := pes(x(q)._1)(x(q)._2).data(i).out.bits
        }
        tree.in.valid := VecInit(x.map(it=>pes(it._1)(it._2).data(i).out.valid)).reduce(_ && _)
        io.data(i)(out_id) := tree.out
        out_id = out_id + 1
      })
    }
  }
  //val pe_sig_stat_trans_r = RegInit(VecInit(Seq.fill(pe_h)(VecInit(Seq.fill(8)(false.B)))))
  
  // for (m <- 0 until pe_h) {
  //      pe_sig_stat_trans_r(m)(0) := (cur_cycle < latency.asUInt)     
  // }

  // for(i <- 0 until pe_h) {
  //   //8 is pipline in cross slr
  //   for(j <- 1 until 8) {
  //      pe_sig_stat_trans_r(i)(j) := pe_sig_stat_trans_r(i)(j-1)
  //   }
  // }
  val pe_sig_stat_trans_r = RegInit(VecInit(Seq.fill(pe_h+pe_w)(false.B)))
  pe_sig_stat_trans_r(0) := (cur_cycle < latency.asUInt)
  for(i <- 1 until pe_h + pe_w){
    pe_sig_stat_trans_r(i) := pe_sig_stat_trans_r(i-1)
  }
  for(i <- 0 until num_op){
    for(j <- 0 until pe_h){
      for(k <- 0 until pe_w){
        if(dataflows(i)==StationaryDataflow){
          if(io_type(i)){
            pes(j)(k).data(i).sig_stat2trans.get := (cur_cycle===(j+k*latency+k).asUInt)
          }else{// output
            pes(j)(k).data(i).sig_stat2trans.get := pe_sig_stat_trans_r(j+k) //(cur_cycle === 0.asUInt)
          }
          
        }
      }
    }
  }
  // printf("cycle:%d\n",cur_cycle)
  // for(i <- 0 until 4){
  //   for(j <- 0 until 4){
  //     printf("(%x %x %x)",  pes(i)(j).data(0).in.bits, pes(i)(j).data(1).in.bits, pes(i)(j).data(2).in.bits)
  //   }
  //   printf("\n")
  // }
  // printf("\n")
}
