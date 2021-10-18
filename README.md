# TensorLib: A Spatial Accelerator Generation Framework for Tensor Algebra

## Introduction

Tensor algebra finds applications in various domains, and these applications, especially when accelerated on spatial hardware accelerators, can deliver high performance and low power. Spatial hardware accelerator exhibits complex design space. This project introduces Tensorlib, a spatial accelerator generation framework for tensor algebra. Given a tensor algebra and its dataflow in the accelerator, Tensorlib is able to generate the RTL code of the spatial architecture of the acclerator. 


Tensor algebra is represented with a nested loop notation, and the dataflow is represented with Space-Time Transformation. Tensorlib first determines the PE array dataflow of the tensor algorithm by analyzing the reuse behavior of each tensor element in the PE array. According to different space-time relationship, Tensorlib identifies unicast, multicast, systolic array, reduction tree and other dataflow architectures.

Next, Tensorlib generates the 3-level hierarchy of spatial accelerators in a bottom-up manner. It first uses the dataflow type of each tensor to select the internal modules of PE and connect them with the computation IP to form the PE structure. Next, it connects the PEs together with the generated patterns to form the PE array. Finally, it generates the memory modules with access pattern and external memory IPs, and the controller which provides control signals for both PE and memory ports.

## Installation

1.  Install Java	
    ```	
    sudo apt-get install default-jdk	
    ```
   	
2.  [Install sbt](http://www.scala-sbt.org/release/docs/Installing-sbt-on-Linux.html),	
    which isn't available by default in the system package manager:	
    ```	
    echo "deb https://dl.bintray.com/sbt/debian /" | sudo tee -a /etc/apt/sources.list.d/sbt.list	
    sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 642AC823	
    sudo apt-get update	
    sudo apt-get install sbt	
    ```
3. Clone the project
```git clone https://github.com/pku-liang/tensorlib```

## Usage

```interface_test.scala``` gives an example of how to generate Verilog code or simulate the hardware with Chisel built-in simulator. The accelerator is generated with the following parameters. The parameters are passed into ```PEArray2D``` class for module instantiation.

To generate the hardware accelerator, the user should provide the operation specification ```opSpec``` and space-time transformation configuration ```stt```.
### OpSpec

OpSpec is a class to describe the tensor algorithm. The tensor algorithm is descirbed with nested loop. Firstly, the loop iterators and tensor notations are defined with ```genIterators``` and ```genTensor```.
```scala
    val k :: c :: y :: x :: r :: s :: Nil = genIterators(6)
    val o :: w :: i :: Nil                = genTensor(3)
```
Next, the operation is defined with ```SetExpr``` function, which gives the statement for tensor algorithm. For example, the following notation defines a 2D convolution algorithm.
```scala
setExpr(o(k)(y)(x) += w(k)(c)(r)(s) * i(c)(y + r)(x + s))
```
To define the workload size and data type, each iterator's range, and each tensor's data width is specified.
```scala
k.setRange(16)
o.setWidth(16)
```
### Space-Time Transformation
After the operation is defined, the space-time transformation (STT) matrix is required to define the dataflow. The rank of STT matrix must be equal to the number of iterators. It defines two PE array dimension (PE_x and PE_y). The rest dimensions are multi-dimensional time. The following matrix shows a weight-stationary systolic array architecture for Conv2D operation.
```scala
val stt = DenseMatrix(
    (1,0,0,0,0,0), 
    (0,1,0,0,0,0), 
    (1,1,0,1,0,0), 
    (0,0,0,0,0,1), 
    (0,0,0,0,1,0), 
    (0,0,1,0,0,0)
  )
```
### Generate Verilog
```scala
  val config = Gen_dataflow(opSpec, stt)
  chisel3.Driver.execute(args, () => new PEArray(config))
```
Call ```Gen_dataflow(opSpec, stt)``` to generate the dataflow configuration, and Chisel driver to generate the verilog code for the accelerator specified by the particular operation and dataflow.

## Example
See ```example_gemm.scala``` for a GEMM example.
See ```example_conv2d.scala``` for an 2D convolution example.

## Paper
Please cite our DAC 2021 paper if our work is used in your research.
```
@inproceedings{jia2021tensorlib,
  title={TensorLib: A Spatial Accelerator Generation Framework for Tensor Algebra},
  author={Liancheng Jia and others},
  booktitle = {DAC},
  year={2021},
}
```