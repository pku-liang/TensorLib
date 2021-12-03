package tensorlib

import chisel3._
import chisel3.util._
//import chisel3.Driver
import chisel3.iotesters.{PeekPokeTester, Driver}
import java.io.PrintWriter
import scala.collection.mutable.ArrayBuffer
import breeze.linalg._
import breeze.numerics._

class DummyPE(rvec: Array[Int], width: Int) extends Module{
  val io = IO(new Bundle{
    val data = new PETensorIO(width, false)
  })
  if(rvec(2)!=0){
    val reg = RegNext(io.data.in, 0.U.asTypeOf(chiselTypeOf(io.data.in)))
    io.data.out := reg
  }else{
    io.data.out := io.data.in
  }
}
  
class PENetwork(op_id: Int, rvec: Array[Int], io_type: Boolean, pe_num: Int, width: Int)extends Module{
  val io = IO(new Bundle{
    val to_pes = Vec(pe_num, Flipped(new PETensorIO(width, false)))
    val to_mem = new PETensorIO(width, false)//if(io_type) Input(Valid(UInt(width.W))) else Output(Valid(UInt(width.W)))
  })
  //printf("op %d: to_mem = %d\n", op_id.asUInt, io.to_mem.bits)
  // start: cycle, 

  if((rvec(0)&rvec(1))!=0 && rvec(2)==0 && !io_type){
    // adder tree
    val tree = Module(new AddTree(pe_num, width)).io
    for(q <- 0 until pe_num){
      tree.in.bits(q) := io.to_pes(q).out.bits
    }
    tree.in.valid := VecInit(io.to_pes.map(x=>x.out.valid)).reduce(_ && _)
    io.to_mem.out := tree.out
    io.to_pes.foreach(x=>{
      x.in.valid := true.B
      x.in.bits := 0.U
    })
    //println("tree.")
  }else{
    //systolic connection
    for(i <- 1 until pe_num){
      io.to_pes(i).in := io.to_pes(i-1).out
    }
    if(io_type){  //systolic input
      io.to_pes(0).in := io.to_mem.in
      io.to_mem.out := io.to_pes(pe_num-1).out
    }else{        //systolic output
      io.to_pes(0).in.valid := false.B
      io.to_pes(0).in.bits := 0.U
      io.to_mem.out := io.to_pes(pe_num-1).out
    }
  }
}
object MShiftReg{
  def apply[T <: Data](len: Int, data: T) : T = {
    val shiftreg = RegInit(VecInit(Seq.fill(len)(0.U.asTypeOf(data))))
    shiftreg(0) := data
    for(i <- 1 until len)
      shiftreg(i) := shiftreg(i-1)
    shiftreg(len-1)
  }
}

class PEArray(params: SAConfig) extends Module{
  val latency = params.latency
  val exec_time = Array(latency) ++ params.exec_time
  println("exec_time:"+exec_time.mkString(" "))
  val exec_ctrl = Module(new MultiDimTime(params.addr_width, exec_time, Array.fill(exec_time.length)(0))).io
  printf(p"exec_ctrl: ${exec_ctrl.index}\n")
  val num_op = params.num_op
  val op_type = params.op_type
  val invstt = inv(params.stt.mapValues(_.toDouble)).mapValues(_.toInt)

  // dataflow: the last dataflow in rvec
  val dataflows = for(i <- 0 until num_op) yield{
    val rvec_id = params.tensors(i).pe_rvecs.length - 1
    val rvec = params.tensors(i).pe_rvecs(rvec_id).toArray
    
    //println(s"\ttensor id = ${i}, dataflow = ${df.getClass}")
    Name_Dataflow(rvec)
  }
  var related_col_id = 0
  for(k <- 0 until num_op){
    if(dataflows(k)==StationaryDataflow)
      for(i <- 2 until params.tensors(k).ainvt.cols){
        //println(params.tensors(k).ainvt(::, i).toArray.mkString(" "))
        val c = params.tensors(k).ainvt(::, i).toArray.forall(_==0)
        // if c, not related
        if((!c) && related_col_id==0){
          related_col_id = i
        }
      }
  }
  //println("related col id:"+related_col_id)
  val pe_h = params.pe_size._1
  val pe_w = params.pe_size._2
  val pes = for(i <- 0 until pe_h) yield{
    for(j <- 0 until pe_w) yield{
      Module(new PE(params.width, dataflows.toArray, params.io_type, num_op, latency, op_type)).io
    }
  }
  println(s"\tPE size = $pe_h x $pe_w")
  val bank_peid = for(i <- 0 until num_op)yield{
    for(j <- 0 until params.tensors(i).top_pes.length)yield{
      ArrayBuffer[(Int, Int)]()
    }
  }
  // create PE network
  // outer level
  val pe_net = for(i <- 0 until num_op)yield{
    println("pe_rvec:" + params.tensors(i).pe_rvecs.mkString(" "))
    // rvec2: inner; rvec1: outer
    val rvec2 = params.tensors(i).pe_rvecs(0).toArray
    val rvec2_name = Name_Dataflow(rvec2)
    val diry2 = if(rvec2_name==StationaryDataflow) 0 else rvec2(0)
    val dirx2 = if(rvec2_name==StationaryDataflow) 1 else rvec2(1)
    val rvec1 = if(params.tensors(i).pe_rvecs.length==2) params.tensors(i).pe_rvecs(1).toArray else Array(0, 0, 0)
    val rvec1_name = Name_Dataflow(rvec1)
    val (dirx1, diry1) = if(rvec1_name == StationaryDataflow){
      if(dirx2!=0) (0, 1) else (1, 0)
    }else{
      (rvec1(1), rvec1(0))
    }
    println(s"dir:$diry1,$dirx1   $diry2,$dirx2")
    // println("top_pes:"+params.tensors(i).top_pes.mkString(" "))
    for(t <- 0 until params.tensors(i).top_pes.length)yield{
      val (j, k) = params.tensors(i).top_pes(t)
      //bank_peid(i)(t) += ((j, k))
      val inner_nets = ArrayBuffer[Bundle{val to_pes: chisel3.Vec[tensorlib.PETensorIO]; val to_mem: tensorlib.PETensorIO}]()
      var (js, ks) = (j, k)
      while(js < pe_h && js >= 0 && ks < pe_w && ks >= 0){
        bank_peid(i)(t) += ((js, ks))
        val inner_net_pes = ArrayBuffer[(Int, Int)]()
        
        var (jss, kss) = (js, ks)
        while(jss < pe_h && jss >= 0 && kss < pe_w && kss >= 0){
          inner_net_pes += ((jss, kss))
          jss += diry2
          kss += dirx2
          if((dirx2|diry2)==0){
            jss = -100
            kss = -100
          }
        }
        val inner = Module(new PENetwork(i, rvec2.toArray, params.io_type(i),inner_net_pes.length, params.width(i))).io
        for(u <- 0 until inner_net_pes.length){
          val (pey, pex) = inner_net_pes(u)
          inner.to_pes(u).out := pes(pey)(pex).data(i).out
          pes(pey)(pex).data(i).in := inner.to_pes(u).in
        }
        inner_nets += inner
        js += diry1
        ks += dirx1
        if((dirx1|diry1)==0){
          js = -100
          ks = -100
        }
      }
      val outer_net = Module(new PENetwork(i, rvec1, params.io_type(i), bank_peid(i)(t).length, params.width(i))).io
      // connect outer net and inner net 
      for(u <- 0 until inner_nets.length){
        // input tensor
        if(params.io_type(i)){
          inner_nets(u).to_mem.in := outer_net.to_pes(u).in
          val dummy_pes = Module(new DummyPE(rvec1, params.width(i))).io
          dummy_pes.data.in := outer_net.to_pes(u).in
          outer_net.to_pes(u).out := dummy_pes.data.out
        }else{  // output tensor
          // doesn't support reduction in output, only take one output 
          outer_net.to_pes(u).out := inner_nets(u).to_mem.out
          inner_nets(u).to_mem.in := 0.U.asTypeOf(chiselTypeOf(outer_net.to_pes(u).out))
        }
      }
      outer_net
    }
  }
  // pe_net_inner

  val io = IO(new Bundle {
    val data = new HeterogeneousBag(
      for(i <- 0 until num_op) yield{
        new PEArrayDataBundle(params.io_type(i), params.tensors(i).top_pes.length, params.width(i))
    })
    val exec_valid = Input(Bool())
    val out_valid = Input(Bool())
  })
  exec_ctrl.in := io.exec_valid
  //printf(p"exec_valid: ${io.exec_valid}\n")
  val st_time = for(i <- 0 until pe_h)yield{
    for(j <- 0 until pe_w)yield{
      val lbound = Mem_bound((i, j), invstt, params.iter_range)._1
      //println(s"i=$i, j=$j, st_time=${lbound- params.start_t0}")
      lbound - params.start_t0 + 1
    }
  }
  for(i <- 0 until pe_h){
    for(j <- 0 until pe_w){
      //printf(p"(${pes(i)(j).data(0).out.bits}, ${pes(i)(j).data(1).out.bits}, ${pes(i)(j).data(2).out.bits}, ${pes(i)(j).data(1).sig_stat2trans.get}) ")
      for(k <- 0 until num_op){
        if(dataflows(k)==StationaryDataflow){
          
          //println(s"k=${k}, type=${params.io_type(k)}")
          if((!params.io_type(k))){
            // output stationary
            pes(i)(j).data(k).sig_stat2trans.get := MShiftReg(6,  exec_ctrl.index(1)===(st_time(i)(j)+1).asUInt && (if (exec_ctrl.index.length<=2 || related_col_id <= 3) true.B else VecInit(exec_ctrl.index.drop(2).slice(0, related_col_id-3)).forall(_===0.U)))
          }
          else{
            val st1 = exec_ctrl.index(1)===st_time(i)(j).asUInt && exec_ctrl.index(0) === 0.U
            val st2 = if (exec_ctrl.index.length<=2 || related_col_id <= 3) true.B else VecInit(exec_ctrl.index.drop(2).slice(0, related_col_id-3)).forall((x: UInt)=>x===0.U)
            pes(i)(j).data(k).sig_stat2trans.get := MShiftReg(6, st1 && st2)
          }
            // input stationary
            
        }
      }
    }
    
    printf("\n")
  }
  //println("finish control signals")
  val mem = for(i <- 0 until num_op) yield{
    val io_type = params.io_type(i)
    val is_reuse_dim = Array(false)++params.tensors(i).is_reuse_dim
    for(j <- 0 until bank_peid(i).length) yield{
      val inner_range = Array(latency)++params.tensors(i).time_range(j)
      val outer_range = Array(latency)++params.tensors(i).mem_range(j)
      val outer_dim = (0 until outer_range.length).map(x=>{
        (Array(1) ++ outer_range.slice(0, x)).reduce(_*_)
      }).toArray
      val inner_dim = Array(1) ++ params.tensors(i).mem_dims(j).map(x=>x*latency)
      val init_rd_idx = if(io_type) Array.fill(inner_range.length)(0) else Array.fill(outer_range.length)(0)
      var init_wr_idx = if(!io_type) Array.fill(inner_range.length)(0) else Array.fill(outer_range.length)(0)
      val mem_size = outer_range.reduce(_*_)
      println(s"\tMEM ${i}: size = $mem_size")
      if(io_type) // input
        Module(new MemController(mem_size, params.width(i), params.addr_width, outer_dim, inner_dim, Array.fill(inner_range.length)(false), outer_range, inner_range, init_wr_idx, init_rd_idx, params.custom_mem, io_type)).io
      else  // output
        Module(new MemController(mem_size, params.width(i), params.addr_width, inner_dim, outer_dim, is_reuse_dim, inner_range, outer_range, init_wr_idx, init_rd_idx, params.custom_mem, io_type)).io
    }
  }


  val lower_network = for(i <- 0 until num_op) yield {
    val mode = if(params.tensors(i).rotate) 1 else 0
    Module(new LowerNetwork(bank_peid(i).length, params.width(i), mode)).io
  }
  for(i <- 0 until num_op){
    val max_st = params.tensors(i).top_pes.map(x=>{
      st_time(x._1)(x._2)
    }).reduce(_ max _)
    val max_delay = max_st *latency
    println("max_st = " + max_st)
    println("exec_time = " + params.exec_time(0))
    //assert(params.exec_time(0) > max_st + 4)
    val shiftreg = RegInit(VecInit(Seq.fill(max_delay.max(1))(false.B))) 
    for(i <- 1 until max_delay)
      shiftreg(i) := shiftreg(i-1)
    if(max_delay>0)
      shiftreg(0) := io.exec_valid
    
    for(j <- 0 until bank_peid(i).length){
      // when 
      val (pey, pex) = params.tensors(i).top_pes(j)
      if(params.io_type(i)){      // input
        mem(i)(j).wr_update := false.B 
        if(dataflows(i)!=StationaryDataflow){   // systolic input
          val lbound = Mem_bound((pey, pex), params.tensors(i).ainvt, params.tensors(i).tensor_range)._1 - params.start_t0
          //println(s"pey=$pey, pex=$pex, lbound=$lbound")
          val delay_time = lbound*latency
          if(delay_time>0){
            mem(i)(j).rd_valid := shiftreg(delay_time-1)
          }else{
            mem(i)(j).rd_valid := io.exec_valid
          }
        }
        // STATIONARY INPUT 
        else{
          mem(i)(j).rd_valid :=  MShiftReg(3,io.exec_valid && exec_ctrl.index(1) >= (st_time(pey)(pex)).asUInt && exec_ctrl.index(1) < (pe_w+st_time(pey)(pex)).asUInt)
        }
          
        mem(i)(j).wr_valid := io.data(i).in.get(j).valid
        mem(i)(j).wr_data := io.data(i).in.get(j).bits
        lower_network(i).in(j) := mem(i)(j).rd_data

        pe_net(i)(j).to_mem.in := lower_network(i).out(j)
      }else{
        pe_net(i)(j).to_mem.in := 0.U.asTypeOf(chiselTypeOf(pe_net(i)(j).to_mem.in))
        mem(i)(j).wr_update := true.B 
        lower_network(i).in(j) := pe_net(i)(j).to_mem.out
        mem(i)(j).wr_valid := lower_network(i).out(j).valid
        mem(i)(j).wr_data := lower_network(i).out(j)
        mem(i)(j).rd_valid := io.out_valid
        io.data(i).out.get(j) := mem(i)(j).rd_data
      }
    }
    if(params.tensors(i).rotate){
      lower_network(i).sig_rot.get := exec_ctrl.index(0)
    }
  }
  
}
