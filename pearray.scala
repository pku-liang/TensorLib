package tensorlib

import chisel3._
import chisel3.util._
//import chisel3.Driver
import chisel3.iotesters.{PeekPokeTester, Driver}
import java.io.PrintWriter
import scala.collection.mutable.ArrayBuffer
import breeze.linalg._
import breeze.numerics._


class PENetwork(op_id: Int, rvec: Array[Int], io_type: Boolean, pe_num: Int, width: Int, addr_width: Int, latency: Int)extends Module{
  val stat = (rvec(0)&rvec(1))==0 && rvec(2)!=0
  val io = IO(new Bundle{
    val to_pes = Vec(pe_num, Flipped(new PETensorIO(width, stat)))
    val to_mem = if(io_type) Input(Valid(UInt(width.W))) else Output(Valid(UInt(width.W)))
  })
  //printf("op %d: to_mem = %d\n", op_id.asUInt, io.to_mem.bits)
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
  }else{
    //systolic connection
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
}


class PEArray(params: SAConfig) extends Module{
  val latency = params.latency
  val exec_time = Array(latency) ++ params.exec_time
  val exec_ctrl = Module(new MultiDimTime(params.addr_width, exec_time, Array.fill(exec_time.length)(0))).io
  val num_op = params.num_op
  val op_type = 0
  val mem_outer_time = for(i <- 0 until num_op) yield{
    val mem_range = Array(latency) ++ params.tensors(i).mem_range
    Module(new MultiDimTime(params.addr_width, params.tensors(i).mem_range, Array.fill(mem_range.length)(0))).io
  }
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
  val bank_peid = for(i <- 0 until num_op)yield{
    for(j <- 0 until params.tensors(i).top_pes.length)yield{
      ArrayBuffer[(Int, Int)]()
    }
  }

  for(i <- 0 until num_op){
    val rvec = params.tensors(i).rvecs(0)
    val dirx = if(dataflows(i)==StationaryDataflow) 1 else rvec(1)
    val diry = if(dataflows(i)==StationaryDataflow) 0 else rvec(0)
    println(i, diry, dirx)
    for(t <- 0 until params.tensors(i).top_pes.length){
      val (j, k) = params.tensors(i).top_pes(t)
      bank_peid(i)(t) += ((j, k))
      if((dirx|diry)!=0){
        var (js, ks) = (j+diry, k+dirx)
        while(js < pe_h && js >= 0 && ks < pe_w && ks >= 0){
          bank_peid(i)(t) += ((js, ks))
          js += diry
          ks += dirx
        }
      }
    }
  }

  val pe_net = for(i <- 0 until num_op) yield{
    val num_bank = params.tensors(i).top_pes.length
    for(j <- 0 until num_bank) yield{

      Module(new PENetwork(i, params.tensors(i).rvecs(0).toArray, params.io_type(i), bank_peid(i)(j).length, params.simd(i) * params.width(i), 16, latency)).io
    }
  }
  val io = IO(new Bundle {
    val data = new HeterogeneousBag(
      for(i <- 0 until num_op) yield{
        new PEArrayDataBundle(params.io_type(i), params.tensors(i).top_pes.length, params.simd(i) * params.width(i))
    })
    val exec_valid = Input(Bool())
    val out_valid = Input(Bool())
  })

  exec_ctrl.in := true.B//io.exec_valid
  for(i <- 0 until num_op){
    mem_outer_time(i).in := true.B//io.out_valid
  }
  // link PE with network
  for(i <- 0 until num_op){
    for(j <- 0 until bank_peid(i).length){
      println("bank_peid:"+i+" "+j+" "+bank_peid(i)(j).length)
      for(k <- 0 until bank_peid(i)(j).length){
        val (idy,idx) = bank_peid(i)(j)(k)
        pe_net(i)(j).to_pes(k).out := pes(idy)(idx).data(i).out
        pes(idy)(idx).data(i).in := pe_net(i)(j).to_pes(k).in
      }
    }
  }
  val st_time = for(i <- 0 until pe_h)yield{
    for(j <- 0 until pe_w)yield{
      val mintime=Min_time((pe_h, pe_w), params.stt)
      println("mintime="+mintime)
    }
  }
  for(i <- 0 until pe_h){
    for(j <- 0 until pe_w){
      for(k <- 0 until num_op){
        pes(i)(j).sig_stat2trans := (exec_ctrl.index(1)===st_time(i)(j).asUInt)
      }
    }
  }
  val mem = for(i <- 0 until num_op) yield{
    for(j <- 0 until bank_peid(i).length) yield{
      val io_type = params.io_type(i)
      //println("mem:"+i+","+j)
      // cycle 0 read address?
      // val init_st = DenseVector[Int](Array(bank_peid(i)(j)(0)._1, bank_peid(i)(j)(0)._2, 0))
      // val init_rd_idx = (inv(stt).mapValues(_.toInt) * init_st)
      val inner_range = Array(latency)++params.tensors(i).time_range
      val outer_range = Array(latency)++params.tensors(i).mem_range
      val outer_dim = (0 until outer_range.length).map(x=>{
        (Array(1) ++ outer_range.slice(0, x)).reduce(_*_)
      }).toArray
      val inner_dim = Array(1) ++ params.tensors(i).mem_dims.map(x=>x*latency)
      val init_rd_idx = if(io_type) Array.fill(inner_range.length)(0) else Array.fill(outer_range.length)(0)
      var init_wr_idx = if(!io_type) Array.fill(inner_range.length)(0) else Array.fill(outer_range.length)(0)
      val mem_size = outer_range.reduce(_*_)
      if(io_type) // input
        Module(new MemController(mem_size, params.width(i), params.simd(i), params.addr_width, outer_dim, inner_dim, outer_range, inner_range, init_wr_idx, init_rd_idx)).io
      else  // output
        Module(new MemController(mem_size, params.width(i), params.simd(i), params.addr_width, inner_dim, outer_dim, inner_range, outer_range, init_wr_idx, init_rd_idx)).io
    }
  }




  for(i <- 0 until num_op){
    for(j <- 0 until bank_peid(i).length){
      mem(i)(j).wr_update := false.B 
      if(params.io_type(i)){
        if(dataflows(i)!=StationaryDataflow)
          mem(i)(j).rd_valid := io.exec_valid
        else
          mem(i)(j).rd_valid := io.exec_valid && exec_ctrl.index(1) < (st_time(i)(j) + pe_w).asUInt
        mem(i)(j).wr_valid := io.data(i).in.get(j).valid
        mem(i)(j).wr_data := io.data(i).in.get(j).bits
        pe_net(i)(j).to_mem := mem(i)(j).rd_data
      }else{
        mem(i)(j).wr_valid := pe_net(i)(j).to_mem.valid
        mem(i)(j).rd_valid := io.out_valid
        mem(i)(j).wr_data := pe_net(i)(j).to_mem
        io.data(i).out.get(j) := mem(i)(j).rd_data
      }
    }
  }

  // for(i <- 0 until num_op){
  //   for(j <- 0 until bank_peid(i).length){
  //     if(params.io_type(i)){
  //       pe_net(i)(j).to_mem := io.data(i).in.get(j).bits
  //     }else{
  //       io.data(i).out.get(j) := pe_net(i)(j).to_mem
  //     }
  //   }
  // }
}
