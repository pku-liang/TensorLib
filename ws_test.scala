package systolic

import chisel3._
import chisel3.util._
import scala.math._
//import chisel3.Driver
import chisel3.iotesters.{PeekPokeTester, Driver}
import java.io.PrintWriter
import breeze.linalg._

class Test_WS_Tester(c: Test_WS_Top) extends PeekPokeTester(c){
  for(i <- 0 until 100){
    step(1)
  }
}

class Test_WS_Top extends Module{
  val io = IO(new Bundle{})
  val stt = DenseMatrix((0,0,1),(1,0,0),(1,1,1)).mapValues(_.toDouble)
  val aa     = DenseMatrix((1.0, 0.0, 0.0), (0.0, 0.0, 1.0))
  val ab     = DenseMatrix((0.0, 1.0, 0.0), (0.0, 0.0, 1.0))
  val ac     = DenseMatrix((1.0, 0.0, 0.0), (0.0, 1.0, 0.0))
  val df = Array(aa, ab, ac).map(x=>CalcDelta(x, stt)(0).toArray.map(_.toInt))
  println("dataflow:")
  df.foreach(x=>{
    x.foreach(y=>print(y," "))
    println()
  })
  val pe_size = (3, 3)
  val mat_len = 8
  val latency = 4
  val rnd = new scala.util.Random
  
//   val mat1 = DenseMatrix.tabulate(pe_size._1, pe_size._2){case(i,j)=>rnd.nextInt(10)}
//   val mat2 = DenseMatrix.tabulate(pe_size._2, mat_len){case(i,j)=>rnd.nextInt(10)}
  val mat1 = for(i <- 0 until latency) yield{
    DenseMatrix.tabulate(pe_size._1, pe_size._2){case(i,j)=>1}
  }
  val mat2 = for(i <- 0 until latency) yield{
    DenseMatrix.tabulate(pe_size._2, mat_len){case(i,j)=>1}
  }
  // val mat1 = for(i <- 0 until latency) yield{
  //   DenseMatrix.tabulate(pe_size._1, pe_size._2){case(i,j)=>rnd.nextInt(10)}
  // }
  // val mat2 = for(i <- 0 until latency) yield{
  //   DenseMatrix.tabulate(pe_size._2, mat_len){case(i,j)=>rnd.nextInt(10)}
  // }
  val matout = (0 until latency).map(i=>mat1(i)*mat2(i))
  (0 until latency).foreach(x=>{
    println("matout:",x)
    println(matout(x))
  })
  val mat1_reg = for(i <- 0 until pe_size._1) yield{
    RegInit(VecInit((0 until pe_size._2*latency).map(x=>{mat1(x%latency)(i, floor(x/latency).toInt).asUInt(20.W)}).toArray))
  }
  val mat2_reg = for(i <- 0 until pe_size._2) yield{
    RegInit(VecInit((0 until mat_len*latency).map(x=>{mat2(x%latency)(i, floor(x/latency).toInt).asUInt(20.W)}).toArray))
  }
  val matout_reg = for(i <- 0 until pe_size._1) yield{
    RegInit(VecInit(Seq.fill(pe_size._2*latency)(0.U(20.W))))
  }
  val cycle = RegInit(1.U(20.W))
  val stage_cycle = mat_len * latency + pe_size._1 + pe_size._2
  cycle := Mux(cycle ===stage_cycle.U, 0.U, cycle + 1.U)
  val m = Module(new PEArray2D(pe_size._1,pe_size._2,Array(1,1,1), Array(32,32,32), df,Array(true, true, false),3, latency, 1)).io
  val load_input = RegInit(false.B)
  when(cycle===0.U){
    load_input := true.B
  }

  m.work := true.B
  m.stage_cycle := stage_cycle.U
  val output_time = Seq.fill(3)(Module(new MultiDimTime(12,Array(latency, pe_size._2), Array(0,0))).io)
  for(j <- 0 until pe_size._1){
    val input_weight_cond = cycle>=1.asUInt && cycle<(pe_size._2 * latency+1).U
    m.data(0)(j).valid := input_weight_cond
    m.data(0)(j).bits := Mux(load_input, 2.U, 1.U)//Mux(input_weight_cond, mat1_reg(j)(cycle-1.asUInt), 0.U)
    m.data(2)(j).ready := true.B
    
    output_time(j).in := m.data(2)(j).valid
    when(m.data(2)(j).valid){
      matout_reg(j)(output_time(j).out(0) + output_time(j).out(1) * latency.asUInt) := m.data(2)(j).bits
    }
  }
  for(j <- 0 until pe_size._2){
    m.data(1)(j).valid := true.B
    m.data(1)(j).bits := 1.U//Mux(load_input && cycle>=j.asUInt && cycle<(mat_len * latency).U+j.asUInt, mat2_reg(j)(cycle-j.asUInt), 0.U)
  }
  printf("cycle=%d, input: ", cycle)
  for(j <- 0 until pe_size._1){
    printf("%d ", m.data(0)(j).bits)
  }
  printf("\n")
  printf("time: %d %d, data:(%d, %d)\n",output_time(0).out(0), output_time(0).out(1), m.data(2)(0).valid, m.data(2)(0).bits)
}

object Test_WS_App extends App{
  Driver(() => new Test_WS_Top())(c => new Test_WS_Tester(c))
}