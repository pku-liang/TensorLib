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



class ComputeCell_Mttkrp (vec: Array[Int], width: Int, num_op : Int) extends Module {
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
    val n = vec(1)/2
    val m = vec(0)
    val vec_a = Wire(Vec(vec(0), UInt(width.W)))
    val vec_b = Wire(Vec(vec(1), UInt(width.W)))
    val vec_c_in = Wire(Vec(vec(2), UInt(width.W)))
    val vec_c_out = Wire(Vec(vec(2), UInt(width.W)))
    val atimeb = RegInit(VecInit(Seq.fill(vec(2))(0.U(width.W))))
    for(i <- 0 until vec(0)){
      vec_a(i):=io.data(0).in(i*width+width-1, i*width)
    }
    for(i <- 0 until vec(1)){
      vec_b(i):=io.data(1).in(i*width+width-1, i*width)
    }
    for(i <- 0 until vec(2)){
      vec_c_in(i):=io.data(2).in(i*width+width-1, i*width)
    }
    
    for(i <- 0 until m){
      for(j <- 0 until n){
        atimeb(i*n+j) := vec_a(i) * vec_b(j)
        vec_c_out(i*n+j):=atimeb(i*n+j)*vec_b(j+n)+vec_c_in(i*n+j)
        //vec_c_out(i*vec(1)+j):=vec_a(i)*vec_b(j)+vec_c_in(i*vec(1)+j)//Mux(io.data.in_a.valid && io.data.in_b.valid && io.data.in_c.valid,vec_a(i)*vec_b(j)+vec_c_in(i*n+j), 0.U)
      }
    }
    io.data(2).out := vec_c_out.asUInt
    io.data(1).out := io.data(1).in
    io.data(0).out := io.data(0).in
    //printf("%x %x %x %x %x %x\n", io.data.in_b, vec_b(0), vec_b(1), vec_c_out(0), vec_c_out(1), io.data.out_c)
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

class ComputeCell_Mttkrp_4 (vec: Array[Int], width: Int, num_op : Int) extends Module {
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
    val n = vec(1)
    val m = vec(0)
    val vec_a = Wire(Vec(vec(0), UInt(width.W)))
    val vec_b = Wire(Vec(vec(1), UInt(width.W)))
    val vec_c = Wire(Vec(vec(2), UInt(width.W)))
    val vec_d_in = Wire(Vec(vec(2), UInt(width.W)))
    val vec_d_out = Wire(Vec(vec(2), UInt(width.W)))
    val atimeb = RegInit(VecInit(Seq.fill(vec(2))(0.U(width.W))))
    for(i <- 0 until vec(0)){
      vec_a(i):=io.data(0).in(i*width+width-1, i*width)
    }
    for(i <- 0 until vec(1)){
      vec_b(i):=io.data(1).in(i*width+width-1, i*width)
    }
    for(i <- 0 until vec(1)){
      vec_c(i):=io.data(2).in(i*width+width-1, i*width)
    }
    for(i <- 0 until vec(2)){
      vec_d_in(i):=io.data(3).in(i*width+width-1, i*width)
    }
    
    for(i <- 0 until m){
      for(j <- 0 until n){
        atimeb(i*n+j) := vec_a(i) * vec_b(j)
        vec_d_out(i*n+j):=atimeb(i*n+j)*vec_c(j)+vec_d_in(i*n+j)
        //vec_c_out(i*vec(1)+j):=vec_a(i)*vec_b(j)+vec_c_in(i*vec(1)+j)//Mux(io.data.in_a.valid && io.data.in_b.valid && io.data.in_c.valid,vec_a(i)*vec_b(j)+vec_c_in(i*n+j), 0.U)
      }
    }
    io.data(3).out := vec_d_out.asUInt
    io.data(2).out := io.data(2).in
    io.data(1).out := io.data(1).in
    io.data(0).out := io.data(0).in
    //printf("%x %x %x %x %x %x\n", io.data.in_b, vec_b(0), vec_b(1), vec_c_out(0), vec_c_out(1), io.data.out_c)
}