package systolic

import chisel3._
import chisel3.util._
//import chisel3.Driver
import chisel3.iotesters.{PeekPokeTester, Driver}

class My_fmac extends BlackBox{
  val io = IO(new Bundle{
    val aclk = Input(Clock())
    val s_axis_a_tvalid = Input(Bool())
    val s_axis_a_tdata = Input(UInt(32.W))
    val s_axis_b_tvalid = Input(Bool())
    val s_axis_b_tdata = Input(UInt(32.W))
    val s_axis_c_tvalid = Input(Bool())
    val s_axis_c_tdata = Input(UInt(32.W))
    val m_axis_result_tvalid = Output(Bool())
    val m_axis_result_tdata = Output(UInt(32.W))
  })
}
class My_fadd extends BlackBox{
  val io = IO(new Bundle{
    val aclk = Input(Clock())
    val s_axis_a_tvalid = Input(Bool())
    val s_axis_a_tdata = Input(UInt(32.W))
    val s_axis_b_tvalid = Input(Bool())
    val s_axis_b_tdata = Input(UInt(32.W))
    val m_axis_result_tvalid = Output(Bool())
    val m_axis_result_tdata = Output(UInt(32.W))
  })
}
class My_fmul extends BlackBox{
  val io = IO(new Bundle{
    val aclk = Input(Clock())
    val s_axis_a_tvalid = Input(Bool())
    val s_axis_a_tdata = Input(UInt(32.W))
    val s_axis_b_tvalid = Input(Bool())
    val s_axis_b_tdata = Input(UInt(32.W))
    val m_axis_result_tvalid = Output(Bool())
    val m_axis_result_tdata = Output(UInt(32.W))
  })
}

class ComputeCellF(vec: Array[Int], width: Int, num_op : Int) extends Module {
    val io = IO(new Bundle {
      val data = new HeterogeneousBag(
      for(i <- 0 until num_op) yield{
        new Bundle{
          val in = Input(UInt((vec(i)*width).W))
          val out = Output(UInt((vec(i)*width).W))
        }
      }) 
    })
    //printf("%d %d %d %d %d\n",io.data.in_a.bits, io.data.in_b.bits, io.data.in_c.bits, io.data.out_c.bits, io.data.in_c.valid)
    val vec_a = Wire(Vec(vec(0), UInt(width.W)))
    val vec_b = Wire(Vec(vec(1), UInt(width.W)))
    val vec_c_in = Wire(Vec(vec(2), UInt(width.W)))
    val vec_c_out = Wire(Vec(vec(2), UInt(width.W)))
    for(i <- 0 until vec(0)){
      vec_a(i):=io.data(0).in(i*width+width-1, i*width)
    }
    for(i <- 0 until vec(1)){
      vec_b(i):=io.data(1).in(i*width+width-1, i*width)
    }
    for(i <- 0 until vec(2)){
      vec_c_in(i):=io.data(2).in(i*width+width-1, i*width)
    }
    val fmac = for(i <- 0 until vec(0) * vec(1)) yield Module(new My_fmac).io
    // val fadd = for(i <- 0 until m*n) yield Module(new My_fadd).io
    // val fmul = for(i <- 0 until m*n) yield Module(new My_fmul).io
    for(i <- 0 until vec(0)){
      for(j <- 0 until vec(1)){
        // fmul.clk = clock
        // fmul.s_axis_a_tvalid:=true.B
        // fmul.s_axis_b_tvalid:=true.B
        // fmul.s_axis_a_tdata:=vec_a(i)
        // fmul.s_axis_b_tdata:=vec_b(j)
        // fadd.s_axis_a_tdata:=fmul.m_axis_result_tdata
        // fadd.s_axis_a_tvalid:=fmul.m_axis_result_tvalid
        // fadd.s_axis_b_tdata:=vec()
        fmac(i*vec(1)+j).aclk := clock
        fmac(i*vec(1)+j).s_axis_a_tvalid:=true.B
        fmac(i*vec(1)+j).s_axis_b_tvalid:=true.B
        fmac(i*vec(1)+j).s_axis_a_tdata:=vec_a(i)
        fmac(i*vec(1)+j).s_axis_b_tdata:=vec_b(j)
        fmac(i*vec(1)+j).s_axis_c_tvalid:=true.B
        fmac(i*vec(1)+j).s_axis_c_tdata:=vec_c_in(i*vec(1)+j)
        vec_c_out(i*vec(1)+j):=fmac(i*vec(1)+j).m_axis_result_tdata
      }
    }
    io.data(2).out := vec_c_out.asUInt
    io.data(1).out := io.data(1).in
    io.data(0).out := io.data(0).in
    //printf("%x %x %x %x %x %x\n", io.in_b, vec_b(0), vec_b(1), vec_c_out(0), vec_c_out(1), io.out_c)
}

class ComputeCell(vec: Array[Int], width: Int, num_op : Int) extends Module {
    val io = IO(new Bundle {
      val data = new HeterogeneousBag(
      for(i <- 0 until num_op) yield{
        new Bundle{
          val in = Input(UInt((vec(i)*width).W))
          val out = Output(UInt((vec(i)*width).W))
        }
      }) 
    })
    //printf("%d %d %d %d %d\n",io.data.in_a.bits, io.data.in_b.bits, io.data.in_c.bits, io.data.out_c.bits, io.data.in_c.valid)
    val vec_a = Wire(Vec(vec(0), UInt(width.W)))
    val vec_b = Wire(Vec(vec(1), UInt(width.W)))
    val vec_c_in = Wire(Vec(vec(2), UInt(width.W)))
    val vec_c_out = Wire(Vec(vec(2), UInt(width.W)))
    for(i <- 0 until vec(0)){
      vec_a(i):=io.data(0).in(i*width+width-1, i*width)
    }
    for(i <- 0 until vec(1)){
      vec_b(i):=io.data(1).in(i*width+width-1, i*width)
    }
    for(i <- 0 until vec(2)){
      vec_c_in(i):=io.data(2).in(i*width+width-1, i*width)
    }
    // val fadd = for(i <- 0 until m*n) yield Module(new My_fadd).io
    // val fmul = for(i <- 0 until m*n) yield Module(new My_fmul).io
    for(i <- 0 until vec(0)){
      for(j <- 0 until vec(1)){
        // fmul.clk = clock
        // fmul.s_axis_a_tvalid:=true.B
        // fmul.s_axis_b_tvalid:=true.B
        // fmul.s_axis_a_tdata:=vec_a(i)
        // fmul.s_axis_b_tdata:=vec_b(j)
        // fadd.s_axis_a_tdata:=fmul.m_axis_result_tdata
        // fadd.s_axis_a_tvalid:=fmul.m_axis_result_tvalid
        // fadd.s_axis_b_tdata:=vec()
        vec_c_out(i*vec(1)+j):=vec_c_in(i*vec(1)+j) + vec_a(i) * vec_b(j)
      }
    }
    io.data(2).out := vec_c_out.asUInt
    io.data(1).out := io.data(1).in
    io.data(0).out := io.data(0).in
    //printf("%x %x %x %x %x %x\n", io.in_b, vec_b(0), vec_b(1), vec_c_out(0), vec_c_out(1), io.out_c)
}
class ComputeCell_Latency(vec: Array[Int], width: Int, latency: Int) extends Module{
  val io = IO(new Bundle {
    val data = new HeterogeneousBag(
    for(i <- 0 until 3) yield{
      new Bundle{
        val in = Input(UInt((vec(i)*width).W))
        val out = Output(UInt((vec(i)*width).W))
      }
    }) 
  })
  val vec_a = Wire(Vec(vec(0), UInt(width.W)))
  val vec_b = Wire(Vec(vec(1), UInt(width.W)))
  val vec_c_in = Wire(Vec(vec(2), UInt(width.W)))
  val vec_c_out = Wire(Vec(vec(2), UInt(width.W)))
  val delay_a = ShiftRegister(vec_a, latency, 0.U.asTypeOf(Vec(vec(0), UInt(width.W))), true.B)
  val delay_b = ShiftRegister(vec_b, latency, 0.U.asTypeOf(Vec(vec(1), UInt(width.W))), true.B)
  val delay_c = ShiftRegister(vec_c_in, latency, 0.U.asTypeOf(Vec(vec(2), UInt(width.W))), true.B)

  for(i <- 0 until vec(0)){
    vec_a(i):=io.data(0).in(i*width+width-1, i*width)
  }
  for(i <- 0 until vec(1)){
    vec_b(i):=io.data(1).in(i*width+width-1, i*width)
  }
  for(i <- 0 until vec(2)){
    vec_c_in(i):=io.data(2).in(i*width+width-1, i*width)
  }
  // val fadd = for(i <- 0 until m*n) yield Module(new My_fadd).io
  // val fmul = for(i <- 0 until m*n) yield Module(new My_fmul).io
  for(i <- 0 until vec(0)){
    for(j <- 0 until vec(1)){
      // fmul.clk = clock
      // fmul.s_axis_a_tvalid:=true.B
      // fmul.s_axis_b_tvalid:=true.B
      // fmul.s_axis_a_tdata:=vec_a(i)
      // fmul.s_axis_b_tdata:=vec_b(j)
      // fadd.s_axis_a_tdata:=fmul.m_axis_result_tdata
      // fadd.s_axis_a_tvalid:=fmul.m_axis_result_tvalid
      // fadd.s_axis_b_tdata:=vec()
      vec_c_out(i*vec(1)+j):=delay_c(i*vec(1)+j) + delay_a(i) * delay_b(j)
    }
  }
  io.data(2).out := vec_c_out.asUInt
  io.data(1).out := io.data(1).in
  io.data(0).out := io.data(0).in
}
class ComputeCell_Int(vec: Array[Int], width: Array[Int]) extends Module{
  val io = IO(new Bundle {
    val data = new HeterogeneousBag(
    for(i <- 0 until 3) yield{
      new Bundle{
        val in = Input(UInt((vec(i)*width(i)).W))
        val out = Output(UInt((vec(i)*width(i)).W))
      }
    }) 
  })
  val vec_a = Wire(Vec(vec(0), UInt(width(0).W)))
  val vec_b = Wire(Vec(vec(1), UInt(width(1).W)))
  val vec_c_in = Wire(Vec(vec(2), UInt(width(2).W)))
  for(i <- 0 until vec(0)){
    vec_a(i):=io.data(0).in(i*width(0)+width(0)-1, i*width(0))
  }
  for(i <- 0 until vec(1)){
    vec_b(i):=io.data(1).in(i*width(1)+width(1)-1, i*width(1))
  }
  for(i <- 0 until vec(2)){
    vec_c_in(i):=io.data(2).in(i*width(2)+width(2)-1, i*width(2))
  }

  // delay 1
  val reg_a1 = RegNext(vec_a, 0.U.asTypeOf(chiselTypeOf(vec_a)))
  val reg_b1 = RegNext(vec_b, 0.U.asTypeOf(chiselTypeOf(vec_b)))
  val reg_c1 = RegNext(vec_c_in, 0.U.asTypeOf(chiselTypeOf(vec_c_in)))


  // delay 2
  val reg_a2 = RegNext(reg_a1, 0.U.asTypeOf(chiselTypeOf(vec_a)))
  val reg_b2 = RegNext(reg_b1, 0.U.asTypeOf(chiselTypeOf(vec_b)))
  val reg_c2 = RegNext(reg_c1, 0.U.asTypeOf(chiselTypeOf(vec_c_in)))

  // mult
  val mul = for(i <- 0 until vec(0))yield{
    for(j <- 0 until vec(1))yield{
      Module(new Mulpilter(width(0), if(width==8)0 else 1)).io
    }
  }
  val reg_c3 = RegNext(reg_c2, 0.U.asTypeOf(chiselTypeOf(vec_c_in)))
  for(i <- 0 until vec(0)){
    for(j <- 0 until vec(1)){
      mul(i)(j).clock:=clock
      mul(i)(j).reset := reset
      mul(i)(j).in_a := reg_a2(i)
      mul(i)(j).in_b := reg_b2(j)
    }
  }

  // add 
  val reg_add = RegInit(0.U.asTypeOf(chiselTypeOf(vec_c_in)))
  for(i <- 0 until vec(0)){
    for(j <- 0 until vec(1)){
      reg_add(i*vec(1)+j):=mul(i)(j).out_c + reg_c3(i*vec(1)+j)
    }
  }
  io.data(2).out := reg_add.asUInt
  io.data(1).out := io.data(1).in
  io.data(0).out := io.data(0).in
}