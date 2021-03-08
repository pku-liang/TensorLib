package systolic

import chisel3._
import chisel3.util._
import scala.math.log10
import chisel3.stage.ChiselStage
//import chisel3.Driver
import chisel3.iotesters.{PeekPokeTester, Driver}
import java.io.PrintWriter
class AddTree(len: Int, width: Int) extends Module{
  def treedep(k: Int): Int = (log10(k-1)/log10(2)).toInt+1
  val io = IO(new Bundle{
    val in = Input(Valid(Vec(len, UInt(width.W))))
    val out = Output(Valid(UInt(width.W)))
  })
  val dep=treedep(len)
  var newlen=len
  val valids = RegInit(VecInit(Seq.fill(dep)(false.B)))
  valids(0) := io.in.valid
  for(i <- 1 until dep){
    valids(i) := valids(i-1)
  }
  val regs=for(i <- 0 until dep) yield{
    newlen=(newlen-1)/2+1
    Reg(Vec(newlen, UInt(width.W)))
  }
  newlen=(len-1)/2+1
  for(j <- 0 until newlen){
    if(j*2+1<len){
      regs(0)(j):=io.in.bits(j*2)+io.in.bits(j*2+1)
    }else{
      regs(0)(j):=io.in.bits(j*2)
    }
  }
  for(i <- 1 until dep){
    for(j <- 0 until (newlen-1)/2+1){
      if(j*2+1<newlen){
        regs(i)(j):=regs(i-1)(j*2)+regs(i-1)(j*2+1)
      }else{
        regs(i)(j):=regs(i-1)(j*2)
      }
    }
    newlen=(newlen-1)/2+1
  }
  io.out.bits:=regs(dep-1)(0)
  io.out.valid := valids(dep-1)
}
class InternalModule(width: Int, stat: Boolean) extends Module{
  val io = IO(new Bundle{
    val in = Input(Valid(UInt(width.W)))
    val out = Output(Valid(UInt(width.W)))
    val from_pe = Input(Valid(UInt(width.W)))
    val to_pe = Output(Valid(UInt(width.W)))
    val sig_in2trans = if(stat)Some(Input(Bool()))else None
    val sig_stat2trans = if(stat)Some(Input(Bool()))else None
  })
}
object InternalModule{
  def apply(dataflow: TensorDataflow, io_type: Boolean, width: Int){
    dataflow match {
      case DirectDataflow => if(io_type) new DirectInput(width) else new DirectOutput(width)
      case SystolicDataflow => if(io_type) new SystolicInput(width) else new SystolicOutput(width)
      case StationaryDataflow => if(io_type) new StationaryInput(width) else new StationaryOutput(width)
    }
  }
}
class SystolicInput(width: Int) extends InternalModule(width, false){
  val reg = RegInit(0.U.asTypeOf(Valid(UInt(width.W))))
  reg := io.in
  io.out := reg
  io.to_pe := reg
}
class DirectInput(width: Int) extends InternalModule(width, false){
  val reg = RegInit(0.U.asTypeOf(Valid(UInt(width.W))))
  reg := io.in
  io.to_pe := reg
  io.out := io.in
}
class DirectOutput(width: Int) extends InternalModule(width, false){
  io.out := io.from_pe
  io.to_pe.bits := 0.U
  io.to_pe.valid := true.B
}
class SystolicOutput(width: Int) extends InternalModule(width, false){

  val reg = RegInit(0.U.asTypeOf(Valid(UInt(width.W))))
  reg := io.in
  io.to_pe := reg
  io.out := io.from_pe
}

/*
stationary input:
从右向左传。
stage_cycle: 完成一轮tile计算所需的cycle数。
最左边的PE只接收一个输入。最右边的PE接受所有输入，自己留最后一个，其余的向左传。
stat表示用于PE计算的寄存器，trans表示用来传输的寄存器。当每个PE的一轮计算完成后，令stat=trans，从而无缝开始进行下一轮计算。


*/
class StationaryInput(width: Int) extends InternalModule(width, true){
  val trans = RegInit(0.U.asTypeOf(Valid(UInt(width.W))))
  val stat = RegInit(0.U.asTypeOf(Valid(UInt(width.W))))
  val reg_in2trans = RegInit(false.B)
  val reg_stat2trans = RegInit(false.B)
  reg_in2trans := io.sig_in2trans.get
  reg_stat2trans := io.sig_stat2trans.get
  io.out := trans
  io.to_pe.bits := stat.bits
  when(reg_in2trans){
    trans := io.in
  }
  when(reg_stat2trans){
    stat := trans
  }
  io.to_pe.valid := stat.valid
}
class StationaryOutput(width: Int) extends InternalModule(width, true){

  val trans = RegInit(0.U.asTypeOf(Valid(UInt(width.W))))
  //val stat_C = Module(new RegIO(m*n,width))
  val stat = RegInit(0.U.asTypeOf(Valid(UInt(width.W))))
  val reg_in2trans = RegInit(false.B)
  val reg_stat2trans = RegInit(false.B)
  reg_in2trans := io.sig_in2trans.get
  reg_stat2trans := io.sig_stat2trans.get
  //printf("%d %d\n",stat_C, trans_C)
  io.out:=trans
  stat := io.from_pe
  when(reg_stat2trans){
    trans := stat
  }.elsewhen(reg_in2trans){
    trans := io.in
  }
  io.to_pe.valid := true.B
  io.to_pe.bits := Mux(reg_stat2trans, 0.U, stat.bits)
}
