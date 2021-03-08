package systolic

import chisel3._
import chisel3.util._
//import chisel3.Driver
import chisel3.iotesters.{PeekPokeTester, Driver}
import java.io.PrintWriter

sealed trait TensorDataflow
case object DirectDataflow extends TensorDataflow
case object SystolicDataflow extends TensorDataflow
case object StationaryDataflow extends TensorDataflow
class PETensorIO(w: Int, stat: Boolean) extends Bundle {
  val in = Input(Valid(UInt(w.W)))
  val out = Output(Valid(UInt(w.W)))
  val sig_in2trans = if(stat)Some(Input(Bool()))else None
  val sig_stat2trans = if(stat)Some(Input(Bool()))else None
}
class PE(vec: Array[Int], width: Int, dataflow: Array[TensorDataflow], io_type: Array[Boolean], num_op : Int, float: Boolean) extends Module{
  val io = IO(new Bundle {
    val data = new HeterogeneousBag(
      for(i <- 0 until num_op) yield{
        new PETensorIO(width*vec(i), dataflow(i)==StationaryDataflow)
    })
  })
  def im_factory(dataflow: TensorDataflow, io_type: Boolean, width: Int):InternalModule={
    dataflow match {
      case DirectDataflow => if(io_type) new DirectInput(width) else new DirectOutput(width)
      case SystolicDataflow => if(io_type) new SystolicInput(width) else new SystolicOutput(width)
      case StationaryDataflow => if(io_type) new StationaryInput(width) else new StationaryOutput(width)
    }
  }
  //val exec_cycle = RegInit(0.U(10.W))

  val pe = 
    if(float)
      Module(new ComputeCellF(vec, width, num_op)).io
    else
      Module(new ComputeCell(vec, width, num_op)).io
  val ims = for(i <- 0 until num_op) yield{
    Module(im_factory(dataflow(i), io_type(i), vec(i) * width)).io
  }
  //val exec = VecInit(ims.map(it=>it.to_pe.valid)).reduce(_ && _)
  //exec_cycle := Mux(exec_cycle + exec === io.stage_cycle, 0.U, exec_cycle + exec)
  for(i <- 0 until num_op){
    ims(i).in := io.data(i).in
    io.data(i).out := ims(i).out
    pe.data(i).in := ims(i).to_pe.bits
    ims(i).from_pe.bits := pe.data(i).out
    if(io_type(i))
      ims(i).from_pe.valid := ims(i).to_pe.valid
    else
    //VecInit(x.map(it=>pes(it._1)(it._2).data(i).out.valid)).reduce(_ && _)
      ims(i).from_pe.valid := VecInit(ims.map(it=>it.to_pe.valid)).reduce(_ && _)
    if(dataflow(i)==StationaryDataflow){
      ims(i).sig_in2trans.get := io.data(i).sig_in2trans.get
      ims(i).sig_stat2trans.get := io.data(i).sig_stat2trans.get
    }
  }
}

class PEArray2D(pe_h: Int, pe_w: Int, vec: Array[Int], width: Int, stt: Array[Array[Int]], io_type: Array[Boolean], num_op : Int, float: Boolean = false) extends Module{
  val dataflows = for(i <- 0 until num_op) yield{
    val xy_diff = stt(i)(0)!=0||stt(i)(1)!=0
    val t_diff = stt(i)(2)!=0
    //var res = TensorDataflow
    if(!xy_diff && t_diff){
      StationaryDataflow
    }else if(xy_diff && t_diff){
      SystolicDataflow
    }else{
      DirectDataflow
    }
  }
  // pe definition
  val pes = for(i <- 0 until pe_h) yield{
    for(j <- 0 until pe_w) yield{
      Module(new PE(vec, width, dataflows.toArray, io_type, num_op, float)).io
    }
  }
  var num_io_banks = Array.fill(num_op)(0)
  // pe connection, calculate bank number
  for(i <- 0 until num_op){
    val dirx = if(dataflows(i)==StationaryDataflow) 1 else stt(i)(0)
    val diry = if(dataflows(i)==StationaryDataflow) 0 else stt(i)(1)
    // connection ports
    if(io_type(i) ||(dataflows(i)!=DirectDataflow)){   // direct output use reduction tree
      for(j <- 0 until pe_h){
        for(k <- 0 until pe_w){
          if(j+diry < pe_h && j+diry >=0 && k+dirx < pe_w && k+dirx >= 0){
            pes(j)(k).data(i).out <> pes(j+diry)(k+dirx).data(i).in
          }
        }
      }
    }
    
    // input ports
    if(io_type(i)){
      for(j <- 0 until pe_h){
        for(k <- 0 until pe_w){
          if((j-diry >= pe_h || j-diry < 0 || k-dirx >= pe_w || k-dirx < 0)||(dirx == 0 && diry == 0)){
            num_io_banks(i) = num_io_banks(i) + 1
          }
        }
      }
    }else{
      for(j <- 0 until pe_h){
        for(k <- 0 until pe_w){
          if((j+diry >= pe_h || j+diry < 0 || k+dirx >= pe_w || k+dirx < 0)||(dirx == 0 && diry == 0)){
            num_io_banks(i) = num_io_banks(i) + 1
          }
        }
      }
    }
  }
  val io = IO(new Bundle {
    val data = new HeterogeneousBag(
      for(i <- 0 until num_op) yield{
        if(io_type(i)) 
          Input(Vec(num_io_banks(i), Valid(UInt((vec(i) * width).W))))
        else
          Output(Vec(num_io_banks(i), Valid(UInt((vec(i) * width).W))))
    })
    val work = Input(Bool())
    val stage_cycle = Input(UInt(10.W))
  })
  val cur_cycle = RegInit(VecInit(Seq.fill(pe_h * pe_w)(0.U(10.W))))
  when(io.work){
    for(i <- 0 until pe_h * pe_w)
      cur_cycle(i) := Mux(cur_cycle(i) + 1.U === io.stage_cycle, 0.U, cur_cycle(i) + 1.U)
  }
  for(i <- 0 until num_op){
    val dirx = if(dataflows(i)==StationaryDataflow) 1 else stt(i)(0)
    val diry = if(dataflows(i)==StationaryDataflow) 0 else stt(i)(1)
    var bank_id = 0
    // input ports
    if(io_type(i)){
      for(j <- 0 until pe_h){
        for(k <- 0 until pe_w){
          if((j-diry >= pe_h || j-diry < 0 || k-dirx >= pe_w || k-dirx < 0)||(dirx == 0 && diry == 0)){
            pes(j)(k).data(i).in := io.data(i)(bank_id)
            bank_id = bank_id + 1
          }
        }
      }
    }else if(dataflows(i)!=DirectDataflow){
      for(j <- 0 until pe_h){
        for(k <- 0 until pe_w){
          if((j+diry >= pe_h || j+diry < 0 || k+dirx >= pe_w || k+dirx < 0)||(dirx == 0 && diry == 0)){
            io.data(i)(bank_id) := pes(j)(k).data(i).out
            bank_id = bank_id + 1
          }
          if((j-diry >= pe_h || j-diry < 0 || k-dirx >= pe_w || k-dirx < 0)||(dirx == 0 && diry == 0)){
            pes(j)(k).data(i).in.bits := 0.U
            pes(j)(k).data(i).in.valid := true.B
          }
        }
      }
    }else{
      // reduction tree
      import scala.collection.mutable.Set
      import scala.collection.mutable.ListBuffer
      val all_trees = Set[List[(Int, Int)]]();
      for(j <- 0 until pe_h){
        for(k <- 0 until pe_w){
          // no input
          pes(j)(k).data(i).in.valid := false.B
          pes(j)(k).data(i).in.bits := 0.U
          var in_set = false
          all_trees.foreach(x=>{
            if (x contains((j, k))){
              in_set = true
            }
          })
          if(!in_set){
            var lb = new ListBuffer[(Int, Int)]()
            var (dj ,dk) = (j, k)
            while(dj < pe_h && dj >= 0 && dk < pe_w && dk >= 0){
              lb += ((dj, dk))
              dj = dj + diry
              dk = dk + dirx
              //(dj, dk) = (j+diry,  k+dirx)
            }
            all_trees += lb.toList
          }
        }
      }
      var out_id = 0
      all_trees.foreach(x =>{
        val tree = Module(new AddTree(x.length, vec(2) * width)).io
        for(q <- 0 until x.length){
          tree.in.bits(q) := pes(x(q)._1)(x(q)._2).data(i).out.bits
        }
        tree.in.valid := VecInit(x.map(it=>pes(it._1)(it._2).data(i).out.valid)).reduce(_ && _)
        io.data(i)(out_id) := tree.out
        out_id = out_id + 1
      })
    }
  }
  for(i <- 0 until num_op){
    for(j <- 0 until pe_h){
      for(k <- 0 until pe_w){
        if(dataflows(i)==StationaryDataflow){
          pes(j)(k).data(i).sig_in2trans.get := (cur_cycle(j*pe_w+k) >0.asUInt && cur_cycle(j*pe_w+k) <= k.asUInt)
          pes(j)(k).data(i).sig_stat2trans.get := (cur_cycle(j*pe_w+k) === 0.asUInt)
        }
      }
    }
  }
}
object TestDepth extends App {
  chisel3.Driver.execute(args, () => new PEArray2D(16, 16, Array(1,1,1), 16, Array(Array(1, 0, 1), Array(0, 1, 1), Array(0, 0, 1)),Array(true, true, false), 3))
  //val str = chisel3.Driver.emitVerilog(new PEArray2D(16, 16, Array(1,1,1), 16, Array(Array(0, 0, 1), Array(0, 1, 0), Array(1, 0, 0)),Array(true, true, false), 3))
  //new PrintWriter("PEArray_"+dfid+".v") { write(str); close }
  var dfid = 1
  var mat = Array(Array(0,1,0),Array(1,0,0),Array(1,1,1))
  var ss=Set[Int]()
  for(m <- 0 until 512){
    var g = m
    for(i <- 0 until 3){
      for(j <- 0 until 3){
        mat(i)(j)=g%2
        g =g/2
      }
    }
    import scala.collection.mutable.Set
    
    if(TestMat(mat)){
      val idx = mat(1)(0) * 64 + mat(1)(1) * 32 + mat(1)(2) * 16 + mat(2)(0) * 8 + mat(2)(1) * 4 + mat(2)(2) * 2
      if(!(ss contains(idx))){
        Console.printf("%d\n",idx)
        ss = ss + idx
        println(m,dfid,"A:No-Reuse","B:",mat(1).mkString(","),"C:",mat(2).mkString(","))
        val str = chisel3.Driver.emitVerilog(new PEArray2D(8,8,Array(1,1,1), 16, Array(Array(0, 0, 0),mat(1),mat(2)),Array(true, true, false),3))
        new PrintWriter("PEArray_"+dfid+".v") { write(str); close }
        dfid=dfid+1
      }
      
    }
  }
}
object Test_Mttkrp extends App {
  //chisel3.Driver.execute(args, () => new PE(Array(1,1,1), 16, Array(StationaryDataflow, DirectDataflow, DirectDataflow),Array(true, true, false), 3))
  //chisel3.Driver.execute(args, () => new PEArray2D(16, 16, Array(1,1,1), 16, Array(Array(1, 0, 1), Array(0, 1, 1), Array(0, 0, 1)),Array(true, true, false), 3))
  //val str = chisel3.Driver.emitVerilog(new PEArray2D(16, 16, Array(1,1,1), 16, Array(Array(0, 0, 1), Array(0, 1, 0), Array(1, 0, 0)),Array(true, true, false), 3))
  //new PrintWriter("PEArray_"+dfid+".v") { write(str); close }
  var dfid = 1
  var mat = Array(Array(0,1,0),Array(1,0,0),Array(0,0,1))
  for(m <- 0 until 512){
    var g = m
    for(i <- 0 until 3){
      for(j <- 0 until 3){
        mat(i)(j)=g%2
        g =g/2
      }
    }
    if(TestMat(mat)){
      println(m,dfid,"A:",mat(0).mkString(","),"B:",mat(1).mkString(","),"C:",mat(2).mkString(","))
      //val str = chisel3.Driver.emitVerilog(new PEArray2D(16,16,Array(1,2,1), 16, Array(mat(0),mat(1),mat(2)),Array(true, true, false),3))
      //new PrintWriter("PEArrayMttkrp_"+dfid+".v") { write(str); close }
      dfid=dfid+1
    }
  }
}
object Test_Gemm extends App {
  //chisel3.Driver.execute(args, () => new PE(Array(1,1,1), 16, Array(StationaryDataflow, DirectDataflow, DirectDataflow),Array(true, true, false), 3))
  chisel3.Driver.execute(args, () => new PEArray2D(10, 16, Array(1,8,8), 32, Array(Array(0, 0, 1), Array(0, 1, 1), Array(1,0, 1)),Array(true, true, false), 3,true))
  //val str = chisel3.Driver.emitVerilog(new PEArray2D(10,16,Array(1,8, 8), 32, Array(Array(0, 0, 1), Array(0, 1, 1), Array(1,0, 1)),Array(true, true, false),3))
  //new PrintWriter("PEArrayMttkrp_"+dfid+".v") { write(str); close }
  //val str = chisel3.Driver.emitVerilog(new PEArray2D(16, 16, Array(1,1,1), 16, Array(Array(0, 0, 1), Array(0, 1, 0), Array(1, 0, 0)),Array(true, true, false), 3))
  //new PrintWriter("PEArray_"+dfid+".v") { write(str); close }
  // var dfid = 1
  // var mat = Array(Array(0,1,0),Array(1,0,0),Array(1,1,1))
  // for(m <- 0 until 512){
  //   var g = m
  //   for(i <- 0 until 3){
  //     for(j <- 0 until 3){
  //       mat(i)(j)=g%2
  //       g =g/2
  //     }
  //   }
  //   if(TestMat(mat)){
  //     println(m,dfid,"A:",mat(0).mkString(","),"B:",mat(1).mkString(","),"C:",mat(2).mkString(","))
  //     //val str = chisel3.Driver.emitVerilog(new PEArray2D(10,16,Array(1,8,8), 32, Array(mat(0),mat(1),mat(2)),Array(true, true, false),3))
  //     //new PrintWriter("PEArrayMem_"+dfid+".v") { write(str); close }
  //     dfid=dfid+1
  //   }
  // }
}
class PEArray1D(pe_w: Int, vec: Array[Int], width: Int, stt: Array[Array[Int]], io_type: Array[Boolean], num_op : Int, float: Boolean = false) extends Module{
  val dataflows = for(i <- 0 until num_op) yield{
    val x_diff = stt(i)(0)!=0
    val t_diff = stt(i)(1)!=0
    //var res = TensorDataflow
    if(!x_diff && t_diff){
      StationaryDataflow
    }else if(x_diff && t_diff){
      SystolicDataflow
    }else{
      DirectDataflow
    }
  }
  // pe definition
  val pes = for(j <- 0 until pe_w) yield{
    Module(new PE(vec, width, dataflows.toArray, io_type, num_op, float)).io
  }
  
  var num_io_banks = Array.fill(num_op)(0)
  // pe connection, calculate bank number
  for(i <- 0 until num_op){
    val dirx = if(dataflows(i)==StationaryDataflow) 1 else stt(i)(0)
    // connection ports
    if(io_type(i) ||(dataflows(i)!=DirectDataflow)){   // direct output use reduction tree
      for(k <- 0 until pe_w){
        if(k+dirx < pe_w && k+dirx >= 0){
          pes(k).data(i).out <> pes(k+dirx).data(i).in
        }
      }
      
    }
    
    // input ports
    if(io_type(i)){
        for(k <- 0 until pe_w){
          if((k-dirx >= pe_w || k-dirx < 0)||(dirx == 0)){
            num_io_banks(i) = num_io_banks(i) + 1
          }
        }
    }else{
        for(k <- 0 until pe_w){
          if((k+dirx >= pe_w || k+dirx < 0)||(dirx == 0)){
            num_io_banks(i) = num_io_banks(i) + 1
          }
        }
    }
  }
  val io = IO(new Bundle {
    val data = new HeterogeneousBag(
      for(i <- 0 until num_op) yield{
        if(io_type(i)) 
          Input(Vec(num_io_banks(i), Valid(UInt((vec(i) * width).W))))
        else
          Output(Vec(num_io_banks(i), Valid(UInt((vec(i) * width).W))))
    })
    val work = Input(Bool())
    val stage_cycle = Input(UInt(10.W))
  })
  val cur_cycle = RegInit(VecInit(Seq.fill(pe_w)(0.U(10.W))))
  when(io.work){
    for(i <- 0 until pe_w)
      cur_cycle(i) := Mux(cur_cycle(i) + 1.U === io.stage_cycle, 0.U, cur_cycle(i) + 1.U)
  }
  for(i <- 0 until num_op){
    val dirx = if(dataflows(i)==StationaryDataflow) 1 else stt(i)(0)
    var bank_id = 0
    // input ports
    if(io_type(i)){
      for(k <- 0 until pe_w){
        if((k-dirx >= pe_w || k-dirx < 0)||(dirx == 0)){
          pes(k).data(i).in := io.data(i)(bank_id)
          bank_id = bank_id + 1
        }
      }
    }else if(dataflows(i)!=DirectDataflow){
        for(k <- 0 until pe_w){
          if(( k+dirx >= pe_w || k+dirx < 0)||(dirx == 0)){
            io.data(i)(bank_id) := pes(k).data(i).out
            bank_id = bank_id + 1
          }
          if((k-dirx >= pe_w || k-dirx < 0)||(dirx == 0)){
            pes(k).data(i).in.bits := 0.U
            pes(k).data(i).in.valid := true.B
          }
        }
    }else{
      // reduction tree
      import scala.collection.mutable.Set
      import scala.collection.mutable.ListBuffer
      val all_trees = Set[List[(Int, Int)]]();
      val tree = Module(new AddTree(pe_w, vec(2) * width)).io
      for(k <- 0 until pe_w){
        pes(k).data(i).in.valid := false.B
        pes(k).data(i).in.bits := 0.U
        tree.in.bits(k) := pes(k).data(i).out.bits
        
      }
      tree.in.valid := VecInit((0 until pe_w).map(it=>pes(it).data(i).out.valid)).reduce(_ && _)
      io.data(i)(0) := tree.out
    }
  }
  for(i <- 0 until num_op){
    for(k <- 0 until pe_w){
      if(dataflows(i)==StationaryDataflow){
        pes(k).data(i).sig_in2trans.get := (cur_cycle(k) >0.asUInt && cur_cycle(k) <= k.asUInt)
        pes(k).data(i).sig_stat2trans.get := (cur_cycle(k) === 0.asUInt)
      }
    }
  }
  
}
// 1 1, 1 0, 0 1
// 1 1, 0 1, 1 0
// 0 1, 1 0, 1 1

object Test_1D extends App {
  //chisel3.Driver.execute(args, () => new PEArray1D(16, Array(1,8,8), 32, Array(Array(0, 1), Array(1, 0), Array(1, 1)),Array(true, true, false), 3))
  val str1 = chisel3.Driver.emitVerilog(new PEArray1D(32, Array(1,8,8), 32, Array(Array(1, 1), Array(1, 0), Array(0, 1)),Array(true, true, false), 3))
  new PrintWriter("PEArray1D_1.v") { write(str1); close }
  val str2 = chisel3.Driver.emitVerilog(new PEArray1D(32, Array(1,8,8), 32, Array(Array(1, 1), Array(0, 1), Array(1, 0)),Array(true, true, false), 3))
  new PrintWriter("PEArray1D_2.v") { write(str2); close }

  val str3 = chisel3.Driver.emitVerilog(new PEArray1D(32, Array(1,8,8), 32, Array(Array(0, 1), Array(1, 0), Array(1, 1)),Array(true, true, false), 3))
  new PrintWriter("PEArray1D_3.v") { write(str3); close }
}