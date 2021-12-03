package tensorlib

import chisel3._
import chisel3.util._
//import chisel3.Driver
import chisel3.iotesters.{PeekPokeTester, Driver}
import java.io.PrintWriter
import scala.collection.mutable.ArrayBuffer
import breeze.linalg._
import breeze.numerics._

class LowerNetwork(num_banks: Int, width: Int, mode: Int, max_rot : Int = 5)extends Module{
  val vwidth = width + 1
  val io = IO(new Bundle{
    val in = Input(Vec(num_banks, Valid(UInt(width.W))))
    val out = Output(Vec(num_banks, Valid(UInt(width.W))))
    // val sig_cross = if(mode==2) Some(Input(Vec(num_banks, UInt((log(num_banks+1)).W)))) else None
    val sig_rot = if(mode==1) Some(Input(UInt((log(num_banks+1)).W))) else None
  })
  val reg_buf = RegNext(io.in)
  
  // if(mode==2){ // crossbar
  //   val reg_sig_cross = RegNext(io.sig_cross.get)
  //   for(i <- 0 until num_banks){
  //     val map_array = (0 until p).map(x=>(x.asUInt->reg_buf((i+x)%num_banks))).toArray
  //     io.out(i) := MuxLookup(reg_sig_cross(i), reg_buf(0), map_array)
  //   }
  // }
  
  if(mode==1){  // rotate
    val reg_sig_rot = RegNext(io.sig_rot.get)
    val rot_reg = RegInit(VecInit(Seq.fill(max_rot)(0.U.asTypeOf(chiselTypeOf(io.in)))))
    val d = reg_buf.asUInt
    for(i <- 0 until max_rot){
      val rot_len = i * vwidth
      val a = Wire(UInt((num_banks*vwidth).W))
      a := d << rot_len
      val out = a | (d >> (num_banks*vwidth - rot_len))
      rot_reg(i.asUInt) := out.asTypeOf(chiselTypeOf(io.in))
    }
    io.out := rot_reg(RegNext(reg_sig_rot))

    // val reg_sig_rot = RegNext(io.sig_rot.get * vwidth.asUInt)
    // val d = reg_buf.asUInt
    // val a = Wire(UInt((num_banks*vwidth).W))
    // a := d << reg_sig_rot
    // val out = a | (d >> ((num_banks*vwidth).asUInt - reg_sig_rot))
    // io.out := RegNext(out.asTypeOf(chiselTypeOf(io.out)))
  }
  if(mode==0){
    io.out := RegNext(reg_buf)
  }
}
object Test_Connect extends App{
  val str = chisel3.Driver.emitVerilog(new LowerNetwork(4, 16, 1))
  new PrintWriter(s"LowerNetwork.v") { write(str); close }
}