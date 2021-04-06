package systolic

import chisel3._
import chisel3.util._
import scala.math._
//import chisel3.Driver
import chisel3.iotesters.{PeekPokeTester, Driver}
import java.io.PrintWriter
import breeze.linalg._
class MemController(
  // to pe: latency -- len -- group
  // to mem: len -- latency -- group
  // low -- high
  mem_size: Int,
  data_width: Int,
  addr_width: Int,
  wr_dim: Array[Int],
  rd_dim: Array[Int],
  wr_pattern: Array[Int],
  rd_pattern: Array[Int],
  wr_init: Array[Int],
  rd_init: Array[Int]
  ) extends Module{
  val mem = Module(new MultiDimMem(addr_width,wr_dim, rd_dim, mem_size, data_width)).io
  val wr_time = Module(new MultiDimTime(addr_width,wr_pattern,wr_init)).io
  val rd_time = Module(new MultiDimTime(addr_width,rd_pattern,rd_init)).io
  val io = IO(new Bundle{
    val rd_valid = Input(Bool())
    val wr_valid = Input(Bool())
    val rd_data = Output(Valid(UInt(data_width.W)))
    val wr_data = Input(UInt(data_width.W))
  })
  wr_time.in := io.wr_valid
  mem.wr_addr.bits := wr_time.out
  mem.wr_addr.valid := io.wr_valid

  rd_time.in := io.rd_valid
  mem.rd_addr.bits := rd_time.out
  mem.rd_addr.valid := io.rd_valid

  io.rd_data := mem.rd_data
  mem.wr_data := io.wr_data
}
class PEArray_Mem extends Module{
  val stt = DenseMatrix((0,1,0),(1,0,0),(1,1,1)).mapValues(_.toDouble)
  val aa     = DenseMatrix((1.0, 0.0, 0.0), (0.0, 0.0, 1.0))
  val ab     = DenseMatrix((0.0, 1.0, 0.0), (0.0, 0.0, 1.0))
  val ac     = DenseMatrix((1.0, 0.0, 0.0), (0.0, 1.0, 0.0))
  val df = Array(aa, ab, ac).map(x=>CalcDelta(x, stt)(0).toArray.map(_.toInt))
  for(i <- 0 until 3){
    println("df",i,df(i).foreach(x=>print(x)))
  }
  val pe_size = (10, 16)
  val mat_len = 256
  val latency = 12
  val rnd = new scala.util.Random
  val num_buffer = 2
  // access: latency * dim * double
  val num_operand = 3
  val mem_dim = Array(1, latency, latency*mat_len)
  val mem_out_dim = Array(1, latency, latency*pe_size._2)
  val mem_time = Array(latency, mat_len, num_buffer)
  val mem_out_time = Array(latency, pe_size._2, num_buffer)
  val mem_out_size = latency*pe_size._2*num_buffer
  val mem_size = latency*mat_len*num_buffer
  val data_width = Array(32, 32, 32)
  val addr_width = 20
  val simd = Array(1,8,8)
  val simd_width = (0 until 3).map(i=>data_width(i) * simd(i))
  val rd_init = Array(0,0,0)
  val wr_init = Array(0,0,1)
  val io = IO(new Bundle{
    val work = Input(Bool())
    val mat1_wr = Input(Vec(pe_size._1, Valid(UInt(simd_width(0).W))))
    val mat2_wr = Input(Vec(pe_size._2, Valid(UInt(simd_width(1).W))))
    val matout_rd = Output(Vec(pe_size._1, Valid(UInt(simd_width(2).W))))
    val rd_output = Input(Bool())
    val stage_cycle = Input(UInt(20.W))
  })
  
  val mat1_mem = Seq.fill(pe_size._1)(Module(new MemController(mem_size, simd_width(0), addr_width,mem_dim, mem_dim, mem_time, mem_time,rd_init, wr_init )).io)
  val mat2_mem = Seq.fill(pe_size._2)(Module(new MemController(mem_size, simd_width(1), addr_width,mem_dim, mem_dim, mem_time, mem_time,rd_init, wr_init )).io)
  val matout_mem = Seq.fill(pe_size._1)(Module(new MemController(mem_out_size, simd_width(2), addr_width,mem_out_dim, mem_out_dim, mem_out_time, mem_out_time,wr_init, rd_init)).io)

  val mat1_rd_valid = Seq.fill(pe_size._1)(RegInit(false.B))
  val mat2_rd_valid = Seq.fill(pe_size._2)(RegInit(false.B))

  val op_type = 0
  val io_type = Array(true,true,false)
  val pes = Module(new PEArray2D(pe_size._1,pe_size._2,simd, data_width, df,io_type,num_operand, latency, op_type)).io
  pes.work := io.work
  pes.stage_cycle := io.stage_cycle//(mat_len*latency).asUInt
  mat1_rd_valid(0) := io.work
  for(i <- 1 until pe_size._1){
    mat1_rd_valid(i) := mat1_rd_valid(i-1)
  }
  mat2_rd_valid(0) := io.work
  for(i <- 1 until pe_size._2){
    mat2_rd_valid(i) := mat2_rd_valid(i-1)
  }

  for(i <- 0 until pe_size._1){
    mat1_mem(i).rd_valid := mat1_rd_valid(i)
    mat1_mem(i).wr_valid := io.mat1_wr(i).valid
    mat1_mem(i).wr_data := io.mat1_wr(i).bits
    pes.data(0)(i).valid := mat1_mem(i).rd_data.valid
    pes.data(0)(i).bits := mat1_mem(i).rd_data.bits

    matout_mem(i).rd_valid := io.rd_output
    matout_mem(i).wr_valid := pes.data(2)(i).valid
    matout_mem(i).wr_data := pes.data(2)(i).bits
    io.matout_rd(i).valid := matout_mem(i).rd_data.valid
    io.matout_rd(i).bits := matout_mem(i).rd_data.bits
    pes.data(2)(i).ready := true.B
  }
  for(i <- 0 until pe_size._2){
    mat2_mem(i).rd_valid := mat2_rd_valid(i)
    mat2_mem(i).wr_valid := io.mat2_wr(i).valid
    mat2_mem(i).wr_data := io.mat2_wr(i).bits
    pes.data(1)(i).valid := mat2_mem(i).rd_data.valid
    pes.data(1)(i).bits := mat2_mem(i).rd_data.bits

  }
}

class PEArray_Mem_WS extends Module{
  val stt = DenseMatrix((0,0,1),(1,0,0),(1,1,1)).mapValues(_.toDouble)
  val aa     = DenseMatrix((1.0, 0.0, 0.0), (0.0, 0.0, 1.0))
  val ab     = DenseMatrix((0.0, 1.0, 0.0), (0.0, 0.0, 1.0))
  val ac     = DenseMatrix((1.0, 0.0, 0.0), (0.0, 1.0, 0.0))
  val df = Array(aa, ab, ac).map(x=>CalcDelta(x, stt)(0).toArray.map(_.toInt))
  for(i <- 0 until 3){
    println("df",i,df(i).foreach(x=>print(x)))
  }
  val pe_size = (10, 16)
  val mat_len = 256
  val latency = 12
  val rnd = new scala.util.Random
  val num_buffer = 2
  // access: latency * dim * double
  val num_operand = 3
  val mem_dim = Array(1, latency, latency*mat_len)
  val mem_st_dim = Array(1, latency, latency*pe_size._2)
  val mem_time = Array(latency, mat_len, num_buffer)
  val mem_st_time = Array(latency, pe_size._2, num_buffer)
  val mem_st_size = latency*pe_size._2*num_buffer
  val mem_size = latency*mat_len*num_buffer
  val data_width = Array(32, 32, 32)
  val addr_width = 20
  val simd = Array(1,8,8)
  val simd_width = (0 until 3).map(i=>data_width(i) * simd(i))
  val rd_init = Array(0,0,0)
  val wr_init = Array(0,0,1)
  val io = IO(new Bundle{
    val work = Input(Bool())
    val matst_wr = Input(Vec(pe_size._1, Valid(UInt(simd_width(0).W))))
    val mat1_wr = Input(Vec(pe_size._2, Valid(UInt(simd_width(1).W))))
    val mat2_rd = Output(Vec(pe_size._1, Valid(UInt(simd_width(2).W))))
    val rd_output = Input(Bool())
    val stage_cycle = Input(UInt(20.W))
  })


  val matst_mem = Seq.fill(pe_size._1)(Module(new MemController(mem_st_size, simd_width(0), addr_width,mem_st_dim, mem_st_dim, mem_st_time, mem_st_time,wr_init, rd_init)).io)
  val mat1_mem = Seq.fill(pe_size._2)(Module(new MemController(mem_size, simd_width(1), addr_width,mem_dim, mem_dim, mem_time, mem_time,rd_init, wr_init )).io)
  val mat2_mem = Seq.fill(pe_size._1)(Module(new MemController(mem_size, simd_width(2), addr_width,mem_dim, mem_dim, mem_time, mem_time,rd_init, wr_init )).io)
  
  val matst_rd_valid = Seq.fill(pe_size._1)(RegInit(false.B))
  val mat1_rd_valid = Seq.fill(pe_size._2)(RegInit(false.B))

  val op_type = 0
  val io_type = Array(true,true,false)
  val pes = Module(new PEArray2D(pe_size._1,pe_size._2,simd, data_width, df,io_type,num_operand, latency, op_type)).io
  val cur_cycle = RegInit(0.U(20.W))
  pes.work := io.work
  pes.stage_cycle := io.stage_cycle//(mat_len*latency).asUInt
  matst_rd_valid(0) := cur_cycle < (pe_size._2 * latency).asUInt
  for(i <- 1 until pe_size._1){
    matst_rd_valid(i) := matst_rd_valid(i-1)
  }
  mat1_rd_valid(0) := io.work
  for(i <- 1 until pe_size._2){
    mat1_rd_valid(i) := mat1_rd_valid(i-1)
  }

  for(i <- 0 until pe_size._1){
    matst_mem(i).rd_valid := matst_rd_valid(i)
    matst_mem(i).wr_valid := io.matst_wr(i).valid
    matst_mem(i).wr_data := io.matst_wr(i).bits
    pes.data(0)(i).valid := matst_mem(i).rd_data.valid
    pes.data(0)(i).bits := matst_mem(i).rd_data.bits

    mat2_mem(i).rd_valid := io.rd_output
    mat2_mem(i).wr_valid := pes.data(2)(i).valid
    mat2_mem(i).wr_data := pes.data(2)(i).bits
    io.mat2_rd(i).valid := mat2_mem(i).rd_data.valid
    io.mat2_rd(i).bits := mat2_mem(i).rd_data.bits
    pes.data(2)(i).ready := true.B
  }
  for(i <- 0 until pe_size._2){
    mat1_mem(i).rd_valid := mat1_rd_valid(i)
    mat1_mem(i).wr_valid := io.mat1_wr(i).valid
    mat1_mem(i).wr_data := io.mat1_wr(i).bits
    pes.data(1)(i).valid := mat1_mem(i).rd_data.valid
    pes.data(1)(i).bits := mat1_mem(i).rd_data.bits

  }
}

object Gen_PEArray_MEM_WS extends App{
  val str = chisel3.Driver.emitVerilog(new PEArray_Mem_WS())
  new PrintWriter("PEArray_mem.v") { write(str); close }
}