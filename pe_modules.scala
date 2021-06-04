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
class DecBundle(width: Int) extends Bundle{
  val bits = UInt(width.W)
  val valid = Bool()
  override def cloneType = (new DecBundle(width)).asInstanceOf[this.type]
}
object DecoupledReg{
  def apply(width: Int)={
    val x=RegInit({
      val b = Wire(new DecBundle(width))
      b.bits := 0.U
      b.valid := false.B
      b
    })
    x
  }
}
class InternalModule(width: Int, stat: Boolean, output: Boolean, back: Boolean) extends Module{
  val io = IO(new Bundle{
    val port = new PETensorIO(width, stat, back)
    val from_cell = if(output) Some(Input(Valid(UInt(width.W)))) else None
    val to_cell = Output(Valid(UInt(width.W)))
  })
}
object InternalModule{
  def apply(dataflow: TensorDataflow, io_type: Boolean, width: Int, latency: Int){
    dataflow match {
      case DirectDataflow => if(io_type) new DirectInput(width) else new DirectOutput(width)
      case SystolicDataflow => if(io_type) new SystolicInput(width) else new SystolicOutput(width)
      case StationaryDataflow => if(io_type) new StationaryInput_Pipeline(width, latency) else new StationaryOutput_OutCell(width, latency)
    }
  }
}
class SystolicInput(width: Int) extends InternalModule(width, false, false, false){
  val reg = DecoupledReg(width)
  val to_cell_delay1 = DecoupledReg(width)
  val to_cell_delay2 = DecoupledReg(width)
  to_cell_delay1 := reg
  to_cell_delay2 := to_cell_delay1
  reg <> io.port.in
  io.port.out <> reg
  io.to_cell <> to_cell_delay2
}
class DirectInput(width: Int) extends InternalModule(width, false, false, false){
  val reg = DecoupledReg(width)
  reg <> io.port.in
  io.to_cell := reg
  io.port.out := io.port.in
}
class DirectOutput(width: Int) extends InternalModule(width, false, true, false){
  io.port.out <> io.from_cell.get
  io.to_cell.bits <> 0.U
  io.to_cell.valid <> true.B
}
class SystolicOutput(width: Int) extends InternalModule(width, false, true, false){

  val reg = DecoupledReg(width)
  reg <> io.port.in
  io.to_cell <> reg
  io.port.out <> io.from_cell.get
  //printf("to pe:%d %d,from pe:%d %d\n",io.to_cell.valid, io.to_cell.bits, io.from_cell.get.valid, io.from_cell.get.bits)
}

/*
stationary input:
从右向左传。
stage_cycle: 完成一轮tile计算所需的cycle数。
最左边的PE只接收一个输入。最右边的PE接受所有输入，自己留最后一个，其余的向左传。
stat表示用于PE计算的寄存器，trans表示用来传输的寄存器。当每个PE的一轮计算完成后，令stat=trans，从而无缝开始进行下一轮计算。


*/


class StationaryInput_Pipeline(width: Int, latency: Int) extends InternalModule(width, true, false, false){
  val trans = DecoupledReg(width)
  

  val update = RegInit(0.U.asTypeOf(Valid(Vec(latency, UInt(width.W)))))
  //val stat_C = Module(new RegIO(m*n,width))
  val stat = RegInit(0.U.asTypeOf(Valid(Vec(latency, UInt(width.W)))))
  val reg_stat2trans = RegInit(0.U.asTypeOf(Vec(latency+1, Bool())))
  //false.B)
  val write_trans_pos = RegInit(0.U(4.W))
  val read_stat_pos = RegInit(0.U(4.W))
  io.port.out.valid := update.valid  //  update写满了，写入下一个buffer
  io.port.out.bits := trans.bits
  // update没更新完，且trans发送数据给update
  write_trans_pos := Mux(!update.valid, Mux(write_trans_pos+trans.valid.asUInt===latency.asUInt, 0.U, write_trans_pos+trans.valid.asUInt), write_trans_pos)
  when((!update.valid) && trans.valid){
    update.bits(write_trans_pos) := trans.bits
  }
  printf("stat2trans: %d  trans:%d %d, write_pos:%d, update: %d, stat: %d, read_stat_pos: %d, to_cell:%d, %d\n",io.port.sig_stat2trans.get, trans.valid, trans.bits, write_trans_pos, update.valid, stat.valid,read_stat_pos, io.to_cell.valid, io.to_cell.bits)
  // 运算时，每次读取不同的stat
  read_stat_pos := Mux(stat.valid, Mux(read_stat_pos+1.U===latency.asUInt, 0.U, read_stat_pos+1.U),read_stat_pos)
  when(write_trans_pos===(latency-1).asUInt && trans.valid){
    update.valid := true.B
  }
  reg_stat2trans(0) := io.port.sig_stat2trans.get
  for(i <- 1 until latency+1){
    reg_stat2trans(i) := reg_stat2trans(i-1)
  }
  //printf("%d %d, %d %d\n",io.in.bits, reg_in2trans, stat.bits, trans.bits
  trans <> io.port.in
  when(reg_stat2trans(0)){
    stat := update
    update.valid :=false.B
  }
  io.to_cell.valid := RegNext(stat.valid, false.B)
  io.to_cell.bits := RegNext(stat.bits(read_stat_pos), false.B)
}

class StationaryOutput_OutCell(width: Int, latency: Int) extends InternalModule(width, true, true, false){
  // start from 0, latency cycles
  val reg_stat2trans = RegInit(false.B)
  reg_stat2trans := io.port.sig_stat2trans.get
  //stat := io.from_cell
  //printf("pe out: %d %d\n",io.from_cell.get.bits, io.from_cell.get.valid)
  when(reg_stat2trans){
    io.port.out.bits := io.from_cell.get.bits
    io.port.out.valid := io.from_cell.get.valid
    //trans := stat
  }.otherwise{
    io.port.out.bits := 0.U
    io.port.out.valid := false.B
  }
  io.to_cell.valid := true.B
  io.to_cell.bits := Mux(reg_stat2trans, 0.U, io.from_cell.get.bits)
}

class StationaryOutput_InCell(width: Int, latency: Int) extends InternalModule(width, true, true, true){
  val trans = SyncReadMem(latency, UInt(width.W))
  val move = RegInit(0.U(width.W))
  val trans_in_addr = RegInit(0.U(width.W))
  val trans_out_addr = RegInit(0.U(width.W))
  val trans_move_fin = RegInit(false.B)
  val trans_out_valid = RegInit(false.B)
  val reg_stat2trans = RegInit(false.B)
  reg_stat2trans := io.port.sig_stat2trans.get

  // write to trans buffer
  when(reg_stat2trans&&io.from_cell.get.valid){
    trans.write(trans_in_addr, io.from_cell.get.bits)
    trans_in_addr := Mux(trans_in_addr===(latency-1).asUInt, 0.U, trans_in_addr + 1.U)
    when(trans_in_addr === (latency-1).asUInt){
      trans_out_valid := true.B
    }
    //trans := stat
  }
  

}

// class StationaryOutput(width: Int) extends InternalModule(width, true){

//   val trans = RegInit(0.U.asTypeOf(Valid(UInt(width.W))))
//   //val stat_C = Module(new RegIO(m*n,width))
//   val stat = RegInit(0.U.asTypeOf(Valid(UInt(width.W))))
//   val reg_in2trans = RegInit(false.B)
//   val reg_stat2trans = RegInit(false.B)
//   reg_in2trans := io.sig_in2trans.get
//   reg_stat2trans := io.sig_stat2trans.get
//   //printf("%d %d\n",stat_C, trans_C)
//   io.out:=trans
//   stat := io.from_cell
//   when(reg_stat2trans){
//     trans := stat
//   }.elsewhen(reg_in2trans){
//     trans := io.in
//   }
//   io.to_cell.valid := true.B
//   io.to_cell.bits := Mux(reg_stat2trans, 0.U, stat.bits)
// }