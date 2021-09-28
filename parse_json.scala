package tensorlib
import scala.language.dynamics._
import dijon._
import breeze.linalg._

object CodeGen extends App{
  def run(file_name: String){
    val lines = scala.io.Source.fromFile(file_name).mkString
    val param = parse(lines)
    val op_type = param.benchmark
    val bitwidth = param.bitwidth.asInt.get
    val length = param.length
    val stt_o = param.STT
    val stt = (DenseMatrix.tabulate[Int](3,3){case(i, j)=>(stt_o(i)(j).asInt.get)})
    val opSpec_Gemm = new OperatorSpec {
      val k :: c :: x :: Nil = genIterators(3)
      val o :: w :: i :: Nil = genTensor(3)

      setExpr(o(k)(x) += w(k)(c) * i(c)(x))
      k.setRange(length(0).asInt.get)
      c.setRange(length(1).asInt.get)
      x.setRange(length(2).asInt.get)
      setLatency(1)
      o.setWidth(bitwidth)
      w.setWidth(bitwidth)
      i.setWidth(bitwidth)
    }
    val config = Gen_dataflow(opSpec_Gemm, stt)
    chisel3.Driver.execute(args, () => new PEArray(config))
    val opSpec_Conv = new OperatorSpec {
    
      val k :: c :: y :: x :: r :: s :: Nil = genIterators(6)
      val o :: w :: i :: Nil                = genTensor(3)

      setExpr(o(k)(y)(x) += w(k)(c)(r)(s) * i(c)(y + r)(x + s))
      k.setRange(4)
      c.setRange(4)
      y.setRange(6)
      x.setRange(4)
      r.setRange(3)
      s.setRange(3)
      setLatency(1)
      o.setWidth(16)
      w.setWidth(16)
      i.setWidth(16)
    }
  }
    
    println("Scala Command Line Argument Example");
      
    // You pass any thing at runtime
    // that will be print on the console
    for(arg<-args)
    {
        println(arg);
    }
    run(args(0))
  
}