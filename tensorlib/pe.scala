package tensorlib

import chisel3._
import chisel3.util._
//import chisel3.Driver
import chisel3.iotesters.{PeekPokeTester, Driver}
import java.io.PrintWriter
import scala.collection.mutable.ArrayBuffer
import breeze.linalg._
import breeze.numerics._

sealed trait TensorDataflow
case object DirectDataflow extends TensorDataflow
case object SystolicDataflow extends TensorDataflow
case object StationaryDataflow extends TensorDataflow
// input deq, output enq
class PETensorIO(w: Int, stat: Boolean, back: Boolean) extends Bundle {
  val in = Input(Valid(UInt(w.W)))
  val out = Output(Valid(UInt(w.W)))
  val sig_stat2trans = if(stat)Some(Input(Bool()))else None
  val in_ready = if(back)Some(Output(Bool()))else None
  val out_ready = if(back)Some(Input(Bool()))else None
  override def cloneType = (new PETensorIO(w, stat, back)).asInstanceOf[this.type]
}
class PE(vec: Array[Int], width: Array[Int], dataflow: Array[TensorDataflow], io_type: Array[Boolean], num_op : Int, latency: Int, op_type: Int, back: Boolean) extends Module{
  val io = IO(new Bundle {
    val data = new HeterogeneousBag(
      for(i <- 0 until num_op) yield{
        new PETensorIO(width(i)*vec(i), dataflow(i)==StationaryDataflow, dataflow(i)==StationaryDataflow && back)
    })
  })
  def im_factory(dataflow: TensorDataflow, io_type: Boolean, width: Int, latency: Int):InternalModule={
    dataflow match {
      case DirectDataflow => if(io_type) new DirectInput(width) else new DirectOutput(width)
      case SystolicDataflow => if(io_type) new SystolicInput(width) else new SystolicOutput(width)
      case StationaryDataflow => if(io_type) new StationaryInput_Pipeline(width, latency) else if(back) new StationaryOutput_InCell(width, latency) else new StationaryOutput_OutCell(width, latency)
    }
  }
  //val exec_cycle = RegInit(0.U(20.W))

  val pe = if(op_type==0)
    Module(new ComputeCell_Dummy(vec, width, num_op)).io
  else
    Module(new ComputeCellF(vec, width(0), num_op)).io
  val ims = for(i <- 0 until num_op) yield{
    Module(im_factory(dataflow(i), io_type(i), vec(i) * width(i), latency)).io
  }
  //val exec = VecInit(ims.map(it=>it.to_pe.valid)).reduce(_ && _)
  //exec_cycle := Mux(exec_cycle + exec === io.stage_cycle, 0.U, exec_cycle + exec)
  for(i <- 0 until num_op){
    ims(i).port <> io.data(i)
    pe.data(i).in <> ims(i).to_cell
    if(!io_type(i)){
      ims(i).from_cell.get := pe.data(i).out
      //ims(i).from_cell.get.valid := VecInit(ims.map(it=>it.to_pe.valid)).reduce(_ && _)
      printf("pe out:%d %d\n", ims(i).from_cell.get.bits, ims(i).from_cell.get.valid)
    }
    //VecInit(x.map(it=>pes(it._1)(it._2).data(i).out.valid)).reduce(_ && _)
      
    if(dataflow(i)==StationaryDataflow){
      ims(i).port.sig_stat2trans.get := io.data(i).sig_stat2trans.get
    }
  }
}
class PENetwork(rvec: Array[Int], io_type: Boolean, pe_num: Int, width: Int, addr_width: Int, latency: Int, back: Boolean)extends Module{
  val stat = (rvec(0)&rvec(1))==0 && rvec(2)!=0
  val io = IO(new Bundle{
    val to_pes = Vec(pe_num, Flipped(new PETensorIO(width, stat, back)))
    val to_mem = if(io_type) Input(Valid(UInt(width.W))) else Output(Valid(UInt(width.W)))
    val sig_stat2trans = if(stat)Some(Input(Bool()))else None
  })
  // start: cycle, 
  var (dirx, diry) = (rvec(0), rvec(1))

  if((rvec(0)&rvec(1))!=0 && rvec(2)==0 && !io_type){
    // adder tree
    val tree = Module(new AddTree(pe_num, width)).io
    for(q <- 0 until pe_num){
      tree.in.bits(q) := io.to_pes(q-1).out.bits
    }
    tree.in.valid := VecInit(io.to_pes.map(x=>x.out.valid)).reduce(_ && _)
    io.to_mem := tree.out
    io.to_pes.foreach(x=>{
      x.in.valid := true.B
      x.in.bits := 0.U
    })
  }else if((rvec(0)&rvec(1))==0 && rvec(2)!=0 && (!io_type) &&(!back)){
    // stationary output

    // pe_num * latency buffer
    val stat_data = Seq.fill(pe_num)(SyncReadMem(latency, UInt(width.W)))//RegInit(VecInit(Seq.fill(pe_num)(VecInit(Seq.fill(latency)(0.U(width.W))))))
    val in_pos = Array.fill(pe_num)(RegInit(0.U(5.W)))
    val data_valid = RegInit(false.B)
    val out_pos = RegInit(0.U(10.W))
    val out_pe = RegInit(0.U(10.W))
    // input
    //printf("pe to network: (%d, %d) (%d, %d) (%d, %d) (%d, %d)\n", io.to_pes(0).out.valid,io.to_pes(0).out.bits, io.to_pes(1).out.valid,io.to_pes(1).out.bits,io.to_pes(2).out.valid,io.to_pes(2).out.bits,io.to_pes(3).out.valid,io.to_pes(3).out.bits)
    for(i <- 0 until pe_num){
      io.to_pes(i).in.bits := 0.U
      io.to_pes(i).in.valid := false.B
      when(io.to_pes(i).out.valid){
        stat_data(i).write(in_pos(i), io.to_pes(i).out.bits)
        in_pos(i) := Mux(in_pos(i)===(latency-1).asUInt, 0.U, in_pos(i) + 1.U)
      }
    }
    when(in_pos(pe_num-1)===(latency-1).asUInt && io.to_pes(pe_num-1).out.valid){
      data_valid := true.B
    }
    when(data_valid){
      out_pos := Mux(out_pos===(latency-1).asUInt, 0.U, out_pos + 1.U)
      out_pe := Mux(out_pos===(latency-1).asUInt, Mux(out_pe===(pe_num-1).asUInt, 0.U, out_pe+1.U), out_pe)
      when(out_pos===(latency-1).asUInt && out_pe===(pe_num-1).asUInt){
        data_valid := false.B
      }
    }
    val stat_data_out = VecInit(stat_data.map(_.read(out_pos)))
    io.to_mem.valid := data_valid
    io.to_mem.bits := stat_data_out(out_pe)//stat_data(out_pe).read(out_pos)
  }else{
    // systolic connection
    for(i <- 1 until pe_num){
      io.to_pes(i).in := io.to_pes(i-1).out
    }
    if(io_type){  //systolic input
      io.to_pes(0).in := io.to_mem
    }else{        //systolic output
      io.to_pes(0).in.valid := false.B
      io.to_pes(0).in.bits := 0.U
      io.to_mem := io.to_pes(pe_num-1).out
    }
  }
  // sig_stat2trans signal transfer
  val sig_stat2trans_tope = ShiftRegister(io.sig_stat2trans.get, latency+3)
  if(stat){
    for(i <- 0 until pe_num){
      if(io_type){
        // stationary input
        io.to_pes(i).sig_stat2trans.get := ShiftRegister(io.sig_stat2trans.get,i*latency)
      }else{
        io.to_pes(i).sig_stat2trans.get := sig_stat2trans_tope
      }
      if(back && i > 0){
        // reverse
        io.to_pes(i-1).out_ready.get := io.to_pes(i).in_ready.get
      }
    }
    if(back)
      io.to_pes(pe_num-1).out_ready.get := true.B
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
class PEArray2D(pe_size: (Int, Int), vec: Seq[Int], width: Seq[Int], addr_width: Int = 16,stt: DenseMatrix[Int], access: Seq[DenseMatrix[Int]], io_type: Array[Boolean], latency: Int,  op_type: Int = 0, time_range: Seq[Array[Int]], mem_range: Seq[Array[Int]], mem_dims: Seq[Array[Int]], mem_size: Seq[Int], back: Boolean) extends Module{
  // val dims = 1 +: (0 until time_range.length-1).map(x=>time_range.slice(0, x+1).reduce(_*_)).toArray
  //val mem_time = 
  val exec_time = Module(new MultiDimTime(addr_width, time_range(1), Array.fill(time_range(1).length)(0))).io
  val mem_outer_time = for(i <- 0 until num_op) yield{
    Module(new MultiDimTime(addr_width, mem_range(i), Array.fill(mem_range(i).length)(0))).io
  }
  printf("exec_time: %d %d %d\n",exec_time.index(0), exec_time.index(1), exec_time.index(2))
  val (pe_h, pe_w) = pe_size
  val num_op = access.length
  val ainvt = access.map(x=>x*(inv(stt).mapValues(_.toInt)))
  // find reuse vector
  val rvec = access.map(x=>CalcDelta(x, stt)(0).toArray)
  val dataflows = for(i <- 0 until num_op) yield{
    val xy_diff = rvec(i)(0)!=0||rvec(i)(1)!=0
    val t_diff = rvec(i)(2)!=0
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
      Module(new PE(vec.toArray, width.toArray, dataflows.toArray, io_type, num_op, latency, op_type, back)).io
    }
  }
  var num_io_banks = Array.fill(num_op)(0)
  // pe connection, calculate bank number

  // 3-dimension vector: num_op, num_bank, num_pe
  val bank_peid = Array.fill(num_op)(ArrayBuffer[ArrayBuffer[(Int, Int)]]())


  // calc bank num
  for(i <- 0 until num_op){
    val dirx = if(dataflows(i)==StationaryDataflow) 1 else rvec(i)(0)
    val diry = if(dataflows(i)==StationaryDataflow) 0 else rvec(i)(1)
    println(i, dirx, diry)
    for(j <- 0 until pe_h){
      for(k <- 0 until pe_w){
        // find first peid
        if(j - diry >= pe_h || j - diry < 0 || k - dirx >= pe_w || k - dirx < 0){
          bank_peid(i) += ArrayBuffer[(Int, Int)]()
          bank_peid(i)(num_io_banks(i)) += ((j, k))
          if((dirx|diry)!=0){
            var (js, ks) = (j+diry, k+dirx)
            while(js < pe_h && js >= 0 && ks < pe_w && ks >= 0){
              bank_peid(i)(num_io_banks(i)) += ((js, ks))
              js += diry
              ks += dirx
            }
          }
          num_io_banks(i) = num_io_banks(i) + 1
        }
      }
    }
    // println("BANK_PEID"+i+":"+bank_peid(i).length)
    // for(t <- 0 until bank_peid(i).length){
    //   println(bank_peid(i)(t).mkString(" "))
    // }
  }

  // create pe network
  val pe_net = for(i <- 0 until num_op) yield{
    for(j <- 0 until bank_peid(i).length) yield{
      val use_back = dataflows(i)==StationaryDataflow && io_type(i)==false && back
      Module(new PENetwork(rvec(i), io_type(i), bank_peid(i)(j).length, vec(i) * width(i), 16, latency, use_back)).io
    }
  }

  // create IO
  println("bank num:",num_io_banks(0),num_io_banks(1),num_io_banks(2))
  val io = IO(new Bundle {
    val data = new HeterogeneousBag(
      for(i <- 0 until num_op) yield{
        new PEArrayDataBundle(io_type(i), num_io_banks(i), vec(i) * width(i))
    })
    val exec_valid = Input(Bool())
    val out_valid = Input(Bool())
  })

  exec_time.in := io.exec_valid
  // link PE with network
  for(i <- 0 until num_op){
    for(j <- 0 until bank_peid(i).length){
      for(k <- 0 until bank_peid(i)(j).length){
        val (idy,idx) = bank_peid(i)(j)(k)
        pe_net(i)(j).to_pes(k).out := pes(idy)(idx).data(i).out
        pes(idy)(idx).data(i).in := pe_net(i)(j).to_pes(k).in
        //pe_net(i)(j).to_pes(k) <> pes(idy)(idx).data(i)
        if(dataflows(i)==StationaryDataflow){
          pes(idy)(idx).data(i).sig_stat2trans.get := pe_net(i)(j).to_pes(k).sig_stat2trans.get
          if(!(io_type(i))&&back){
            pe_net(i)(j).to_pes(k).in_ready.get := pes(idy)(idx).data(i).in_ready.get
            pes(idy)(idx).data(i).out_ready.get := pe_net(i)(j).to_pes(k).out_ready.get
          }
        }
      }
    }
  }

  for(i <- 0 until num_op){
    for(j <- 0 until bank_peid(i).length){
      if((rvec(i)(0)&rvec(i)(1))==0 && rvec(i)(2)!=0){
        if(io_type(i))
          pe_net(i)(j).sig_stat2trans.get := (exec_time.index(0)===0.U && exec_time.index(1)===0.U)
        else
          pe_net(i)(j).sig_stat2trans.get := exec_time.index(1)===0.U
      }
    }
  }
  // create memory
  val mem = for(i <- 0 until num_op) yield{
    for(j <- 0 until bank_peid(i).length) yield{
      //println("mem:"+i+","+j)
      // cycle 0 read address?
      // val init_st = DenseVector[Int](Array(bank_peid(i)(j)(0)._1, bank_peid(i)(j)(0)._2, 0))
      // val init_rd_idx = (inv(stt).mapValues(_.toInt) * init_st)
      val init_rd_idx = Array.fill(time_range(0).length)(0)
      var init_wr_idx = Array.fill(time_range(0).length)(0)
      //println(init_rd_idx.mkString(" "), init_wr_idx.mkString(" "))
      // var stat_dims = dims.clone
      // var stat_time = time_range.clone
      // for(k <- 2 until stat_dims.length){
      //   stat_dims(k) = stat_dims(k) / time_range(1) * bank_peid(i)(j).length
      // }
      //stat_time(1) = stat_time(1) / time_range(1) * bank_peid(i)(j).length
      if(dataflows(i)==StationaryDataflow)
        Module(new MemController(mem_size(i), width(i) * vec(i), addr_width, mem_dims(i), mem_dims(i), time_range(i), mem_range(i), init_rd_idx, init_wr_idx)).io
      else
        Module(new MemController(mem_size(i), width(i) * vec(i), addr_width, mem_dims(i), mem_dims(i), time_range(i), mem_range(i), init_rd_idx, init_wr_idx)).io
    }
  }

  for(i <- 0 until num_op){
    for(j <- 0 until bank_peid(i).length){
      if(io_type(i)){
        mem(i)(j).rd_valid := io.exec_valid
        mem(i)(j).wr_valid := io.data(i).in.get(j).valid
        pe_net(i)(j).to_mem := mem(i)(j).rd_data
        mem(i)(j).wr_data := io.data(i).in.get(j).bits
      }else{
        mem(i)(j).wr_valid := pe_net(i)(j).to_mem.valid
        mem(i)(j).rd_valid := io.out_valid
        mem(i)(j).wr_data := pe_net(i)(j).to_mem
        io.data(i).out.get(j) := mem(i)(j).rd_data
      }
    }
  }
}
