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
    val in = DeqIO(Vec(len, UInt(width.W)))
    val out = EnqIO(UInt(width.W))
  })
  val dep=treedep(len)
  var newlen=len
  val valids = RegInit(VecInit(Seq.fill(dep)(false.B)))
  io.in.ready := true.B
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
class DecBundle(width: Int) extends Bundle{
  val bits = UInt(width.W)
  val valid = Bool()
  val ready = Bool()
  override def cloneType = (new DecBundle(width)).asInstanceOf[this.type]
}
object DecoupledReg{
  def apply(width: Int)={
    val x=RegInit({
      val b = Wire(new DecBundle(width))
      b.bits := 0.U
      b.valid := false.B
      b.ready := true.B
      b
    })
    x
  }
}
class InternalModule(width: Int, stat: Boolean, output: Boolean) extends Module{
  val io = IO(new Bundle{
    val in = DeqIO(UInt(width.W))
    val out = EnqIO(UInt(width.W))
    val from_pe = if(output) Some(DeqIO(UInt(width.W))) else None
    val to_pe = EnqIO(UInt(width.W))
    val sig_stat2trans = if(stat)Some(Input(Bool()))else None
  })
}
object InternalModule{
  def apply(dataflow: TensorDataflow, io_type: Boolean, width: Int, latency: Int){
    dataflow match {
      case DirectDataflow => if(io_type) new DirectInput(width) else new DirectOutput(width)
      case SystolicDataflow => if(io_type) new SystolicInput(width) else new SystolicOutput(width)
      case StationaryDataflow => if(io_type) new StationaryInput_Pipeline(width, latency) else new StationaryOutput_Pipeline(width, latency)
    }
  }
}
class SystolicInput(width: Int) extends InternalModule(width, false, false){
  val reg = DecoupledReg(width)
  val to_pe_delay1 = DecoupledReg(width)
  val to_pe_delay2 = DecoupledReg(width)
  to_pe_delay1 := reg
  to_pe_delay2 := to_pe_delay1
  reg <> io.in
  io.out <> reg
  io.to_pe <> to_pe_delay2
}
class DirectInput(width: Int) extends InternalModule(width, false, false){
  val reg = DecoupledReg(width)
  reg <> io.in
  io.to_pe := reg
  io.out := io.in
}
class DirectOutput(width: Int) extends InternalModule(width, false, true){
  io.out <> io.from_pe.get
  io.to_pe.bits <> 0.U
  io.to_pe.valid <> true.B
}
class SystolicOutput(width: Int) extends InternalModule(width, false, true){

  val reg = DecoupledReg(width)
  reg <> io.in
  io.to_pe <> reg
  io.out <> io.from_pe.get
  //printf("to pe:%d %d,from pe:%d %d\n",io.to_pe.valid, io.to_pe.bits, io.from_pe.get.valid, io.from_pe.get.bits)
}

/*
stationary input:
从右向左传。
stage_cycle: 完成一轮tile计算所需的cycle数。
最左边的PE只接收一个输入。最右边的PE接受所有输入，自己留最后一个，其余的向左传。
stat表示用于PE计算的寄存器，trans表示用来传输的寄存器。当每个PE的一轮计算完成后，令stat=trans，从而无缝开始进行下一轮计算。


*/


class StationaryInput_Pipeline(width: Int, latency: Int) extends InternalModule(width, true, false){
  val trans = DecoupledReg(width)
  

  val update = RegInit(0.U.asTypeOf(Valid(Vec(latency, UInt(width.W)))))
  //val stat_C = Module(new RegIO(m*n,width))
  val stat = RegInit(0.U.asTypeOf(Valid(Vec(latency, UInt(width.W)))))
  val reg_stat2trans = RegInit(false.B)
  val write_trans_pos = RegInit(0.U(4.W))
  val read_stat_pos = RegInit(0.U(4.W))
  io.out.valid := update.valid  //  update写满了，写入下一个buffer
  io.out.bits := trans.bits
  // update没更新完，且trans发送数据给update
  write_trans_pos := Mux(!update.valid, Mux(write_trans_pos+trans.valid.asUInt===latency.asUInt, 0.U, write_trans_pos+trans.valid.asUInt), write_trans_pos)
  when((!update.valid) && trans.valid){
    update.bits(write_trans_pos) := trans.bits
  }
  printf("trans:%d %d, write_pos:%d, update: %d, stat: %d, read_stat_pos: %d, to_PE:%d, %d\n",trans.valid, trans.bits, write_trans_pos, update.valid, stat.valid,read_stat_pos, io.to_pe.valid, io.to_pe.bits)
  // 运算时，每次读取不同的stat
  read_stat_pos := Mux(stat.valid, Mux(read_stat_pos+1.U===latency.asUInt, 0.U, read_stat_pos+1.U),read_stat_pos)
  when(write_trans_pos===(latency-1).asUInt && trans.valid){
    update.valid := true.B
  }
  reg_stat2trans := io.sig_stat2trans.get
  //printf("%d %d, %d %d\n",io.in.bits, reg_in2trans, stat.bits, trans.bits
  trans <> io.in
  when(reg_stat2trans){
    stat := update
    update.valid :=false.B
  }
  io.to_pe.valid := stat.valid
  io.to_pe.bits := stat.bits(read_stat_pos)
}

class StationaryOutput_Pipeline(width: Int, latency: Int) extends InternalModule(width, true, true){

  //val trans = RegInit(0.U.asTypeOf(Decoupled(UInt(width.W))))
  val trans = RegInit(0.U.asTypeOf(Valid(UInt(width.W))))
  val stat = Module(new Queue(UInt(width.W),latency))
  //when(io.out.ready){    // next 
    when(stat.io.count===0.U){
      trans.valid := io.in.valid
      trans.bits := io.in.bits
    }.elsewhen(stat.io.deq.ready){
      trans.valid := stat.io.deq.valid
      trans.bits := stat.io.deq.bits
    }
  //}
  stat.io.deq.ready := io.out.ready || (!trans.valid)
  io.in.ready := (stat.io.count===0.U)
  //val stat_C = Module(new RegIO(m*n,width))
  //val stat = RegInit(VecInit(Seq.fill(latency)(0.U.asTypeOf(Decoupled(UInt(width.W))))))
  val reg_stat2trans = RegInit(false.B)
  reg_stat2trans := io.sig_stat2trans.get
  //printf("%d %d\n",reg_stat2trans,io.from_pe.get.bits)
  //printf("queue: %d, stat enq: %d %d, stat deq: %d %d %d, trans=%d %d, output=%d %d %d\n",stat.io.count, stat.io.enq.valid, stat.io.enq.bits, stat.io.deq.valid, stat.io.deq.bits, stat.io.deq.ready, trans.valid, trans.bits, io.out.valid, io.out.bits, io.out.ready)
  io.out.valid := trans.valid
  io.out.bits := trans.bits
  //stat := io.from_pe
  when(reg_stat2trans){
    stat.io.enq.bits := io.from_pe.get.bits
    stat.io.enq.valid := io.from_pe.get.valid
    //trans := stat
  }.otherwise{
    stat.io.enq.bits := 0.U
    stat.io.enq.valid := false.B
  }
  io.to_pe.valid := true.B
  io.to_pe.bits := Mux(reg_stat2trans, 0.U, io.from_pe.get.bits)
  io.from_pe.get.ready := true.B
}
