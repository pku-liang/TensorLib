package systolic

import chisel3._
import chisel3.util._
import scala.math._
//import chisel3.Driver
import chisel3.iotesters.{PeekPokeTester, Driver}
import java.io.PrintWriter
import breeze.linalg._

class Test_Systolic_Tester(c: Test_OS_Top) extends PeekPokeTester(c){
  for(i <- 0 until 100){
    step(1)
  }
}

class Test_OS_Top extends Module{
  val io = IO(new Bundle{})
  val stt = DenseMatrix((1,0,0),(0,1,0),(1,1,1)).mapValues(_.toDouble)
  val aa     = DenseMatrix((1.0, 0.0, 0.0), (0.0, 0.0, 1.0))
  val ab     = DenseMatrix((0.0, 1.0, 0.0), (0.0, 0.0, 1.0))
  val ac     = DenseMatrix((1.0, 0.0, 0.0), (0.0, 1.0, 0.0))
  val df = Array(aa, ab, ac).map(x=>CalcDelta(x, stt)(0).toArray.map(_.toInt))
  println("dataflow:",df)
  val pe_size = (3, 3)
  val mat_len = 12
  val latency = 4
  val rnd = new scala.util.Random
  // val mat1 = DenseMatrix.tabulate(pe_size._1, mat_len){case(i,j)=>rnd.nextInt(10)}
  // val mat2 = DenseMatrix.tabulate(pe_size._2, mat_len){case(i,j)=>rnd.nextInt(10)}
  val mat1 = for(i <- 0 until latency) yield{
    DenseMatrix.tabulate(pe_size._1, mat_len){case(i,j)=>rnd.nextInt(10)}
  }
  val mat2 = for(i <- 0 until latency) yield{
    DenseMatrix.tabulate(pe_size._2, mat_len){case(i,j)=>rnd.nextInt(10)}
  }
  val matout = (0 until latency).map(i=>mat1(i)*mat2(i).t)
  (0 until latency).foreach(x=>{
    println("matout:",x)
    println(matout(x))
  })
  val mat1_reg = for(i <- 0 until pe_size._1) yield{
    RegInit(VecInit((0 until mat_len*latency).map(x=>{mat1(x%latency)(i, floor(x/latency).toInt).asUInt(10.W)}).toArray))
  }
  val mat2_reg = for(i <- 0 until pe_size._2) yield{
    RegInit(VecInit((0 until mat_len*latency).map(x=>{mat2(x%latency)(i, floor(x/latency).toInt).asUInt(10.W)}).toArray))
  }
  val matout_reg = for(i <- 0 until pe_size._1) yield{
    RegInit(VecInit(Seq.fill(pe_size._2*latency)(0.U(10.W))))
  }
  val cycle = RegInit(0.U(10.W))
  val stage_cycle = mat_len * latency + pe_size._1 + pe_size._2
  cycle := Mux(cycle ===stage_cycle.U, 0.U, cycle + 1.U)
  val m = Module(new PEArray2D(pe_size._1,pe_size._2,Array(1,1,1), Array(32,32,32), df,Array(true, true, false),3, latency, 1)).io
  
  m.work := true.B
  m.stage_cycle := stage_cycle.U
  val output_time = Seq.fill(3)(Module(new MultiDimTime(Array(latency, pe_size._2), Array(0,0))).io)
  for(j <- 0 until pe_size._1){
    m.data(0)(j).valid := true.B
    m.data(0)(j).bits := Mux(cycle>=j.asUInt && cycle<(mat_len * latency).U+j.asUInt, mat1_reg(j)(cycle-j.asUInt), 0.U)
    m.data(2)(j).ready := true.B
    
    output_time(j).in := m.data(2)(j).valid
    when(m.data(2)(j).valid){
      matout_reg(j)(output_time(j).out(0) + output_time(j).out(1) * latency.asUInt) := m.data(2)(j).bits
    }
  }
  for(j <- 0 until pe_size._2){
    m.data(1)(j).valid := true.B
    m.data(1)(j).bits := Mux(cycle>=j.asUInt && cycle<(mat_len * latency).U+j.asUInt, mat2_reg(j)(cycle-j.asUInt), 0.U)
  }
  printf("cycle=%d, input: ", cycle)
  for(j <- 0 until pe_size._1){
    printf("%d ", m.data(0)(j).bits)
  }
  printf("\n")
  printf("time: %d %d, data:(%d, %d)\n",output_time(0).out(0), output_time(0).out(1), m.data(2)(0).valid, m.data(2)(0).bits)
  
}

object Gen_PEArray_MEM extends App{
  val str = chisel3.Driver.emitVerilog(new PEArray_Mem())
  new PrintWriter("PEArray_mem.v") { write(str); close }
}
object Test_Systolic_App extends App{
  Driver(() => new Test_OS_Top())(c => new Test_Systolic_Tester(c))
}
object Gen_OS_GEMM extends App{
  val stt = DenseMatrix((1,0,0),(0,1,0),(1,1,1)).mapValues(_.toDouble)
  val aa     = DenseMatrix((1.0, 0.0, 0.0), (0.0, 0.0, 1.0))
  val ab     = DenseMatrix((0.0, 1.0, 0.0), (0.0, 0.0, 1.0))
  val ac     = DenseMatrix((1.0, 0.0, 0.0), (0.0, 1.0, 0.0))
  val df = Array(aa, ab, ac).map(x=>CalcDelta(x, stt)(0).toArray.map(_.toInt))
  val pe_size = (20, 32)
  val latency = 4
  val data_width = Array(16,16,32)
  val num_operand = 3
  val simd = Array(1,8,8)
  val op_type = Array(true,true,false)
  val float_type = 2
  val str = chisel3.Driver.emitVerilog(new PEArray2D(pe_size._1,pe_size._2,simd, data_width, df,op_type,num_operand, latency, float_type))
  new PrintWriter("GemmOS_Int16_"+latency+pe_size._1+"x"+pe_size._2+".v") { write(str); close }
}
