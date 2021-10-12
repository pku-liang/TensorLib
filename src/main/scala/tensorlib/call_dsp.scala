package tensorlib

import chisel3._
import chisel3.util._
//import chisel3.Driver
import chisel3.iotesters.{PeekPokeTester, Driver}
class Mulpilter(width: Int, t: Int) extends BlackBox(
  Map("width" -> width, // Verilog parameters
      "type" -> t
    )
){
  val io = IO(new Bundle{
    val clock = Input(Clock())
    val reset = Input(Reset())
    val in_a = Input(UInt(width.W))
    val in_b = Input(UInt(width.W))
    val out_c = Input(UInt((width*2).W))
  })
}