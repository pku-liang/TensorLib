# TensorLib: A Spatial Accelerator Generation Framework for Tensor Algebra

## Introduction

Tensor algebra finds applications in various domains, and these applications, especially when accelerated on spatial hardware accelerators, can deliver high performance and low power. Spatial hardware accelerator exhibits complex design space. This project introduces Tensorlib, a spatial accelerator generation framework for tensor algebra. Given a tensor algebra and its dataflow in the accelerator, Tensorlib is able to generate the RTL code of the spatial architecture of the acclerator. 


Tensor algebra is represented with a nested loop notation, and the dataflow is represented with Space-Time Transformation. Tensorlib first determines the PE array dataflow of the tensor algorithm by analyzing the reuse behavior of each tensor element in the PE array. According to different space-time relationship, Tensorlib identifies unicast, multicast, systolic array, reduction tree and other dataflow architectures.

Next, Tensorlib generates the 3-level hierarchy of spatial accelerators in a bottom-up manner. It first uses the dataflow type of each tensor to select the internal modules of PE and connect them with the computation IP to form the PE structure. Next, it connects the PEs together with the generated patterns to form the PE array. Finally, it generates the memory modules with access pattern and external memory IPs, and the controller which provides control signals for both PE and memory ports.


## Usage

```Test.scala``` gives an example of how to generate Verilog code or simulate the hardware with Chisel built-in simulator. The accelerator is generated with the following parameters. The parameters are passed into ```PEArray2D``` class for module instantiation.

```scala
    val pe_size = (15, 11)              // the size of PE array
	val vec = Array(1, 8, 8)            // the simd vectorization inside PE
	val width = Array(32,32,32)         // the data width
	val stt = DenseMatrix((1,0,0),(0,1,0),(1,1,1))      // the space-time transformation matrix
    val latency = 12                    // the kernel calculation latency
    val time_range = Array(latency, 16, 16)     // the multi-dimension time of execution
	val access = Array(                 // the tensor access matrix
		DenseMatrix((1, 0, 0), (0, 0, 1)),
		DenseMatrix((0, 1, 0), (0, 0, 1)),
		DenseMatrix((1, 0, 0), (0, 1, 0))
		)
	val io_type = Array(true,true,false)    // whether the tensor is input or output in the statement
	
```