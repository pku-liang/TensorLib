package tensorlib
import java.io._
import scala.language.dynamics._
import dijon._
import breeze.linalg._

object ParseJson extends App{
  def run(file_name: String, output_file:String){
    val lines = scala.io.Source.fromFile(file_name).mkString
    val param = parse(lines)
    val op_type = param.benchmark.asString.get
    val bitwidth = param.bitwidth.asInt.get
    val length = param.length
    val stt_o = param.STT
    val stt = op_type match {
      case "GEMM" => (DenseMatrix.tabulate[Int](3,3){case(i, j)=>(stt_o(i)(j).asInt.get)})
      case "CONV2D" => (DenseMatrix.tabulate[Int](6,6){case(i, j)=>(stt_o(i)(j).asInt.get)})
    }
    val opSpec = op_type match {
       case "GEMM" => new OperatorSpec {
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
      case "CONV2D" => new OperatorSpec {
        val k :: c :: y :: x :: r :: s :: Nil = genIterators(6)
        val o :: w :: i :: Nil                = genTensor(3)

        setExpr(o(k)(y)(x) += w(k)(c)(r)(s) * i(c)(y + r)(x + s))
        k.setRange(length(0).asInt.get)
        c.setRange(length(1).asInt.get)
        y.setRange(length(2).asInt.get)
        x.setRange(length(3).asInt.get)
        r.setRange(length(4).asInt.get)
        s.setRange(length(5).asInt.get)
        setLatency(1)
        o.setWidth(bitwidth)
        w.setWidth(bitwidth)
        i.setWidth(bitwidth)
      }
    }
    val config = Gen_dataflow(opSpec, stt)
    chisel3.Driver.execute(args, () => new PEArray(config))

    var src = new File("./PEArray.v")
    val dst = new File(output_file)
    new FileOutputStream(dst) getChannel() transferFrom(
      new FileInputStream(src) getChannel, 0, Long.MaxValue )
  }
    println("Scala Command Line Argument Example");
      
    // You pass any thing at runtime
    // that will be print on the console
    for(arg<-args)
    {
        println(arg);
    }
    run(args(0), args(1))
  
}
