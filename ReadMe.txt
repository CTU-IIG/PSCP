The program PSCP is distributed under the terms of the GNU General Public License.

Author: Anna Minaeva (minaevaana@gmail.com)

This is the implementation of the ILP, CP, Flexi and 3-LS heuristic approaches on the periodic scheduling problem with Control Performance Optimization. 

To run it, IBM ILOG CPLEX Optimization Studio library must be installed and added to the project (“cplex.jar, ILOG.CP.jar”). Moreover, Project Properties->Run->VM options should contain “-Djava.library.path="/path_to_ibm/IBM/ILOG/CPLEX_Studio126/cplex/bin/x86-64_osx":"/path_to_ibm/IBM/ILOG/CPLEX_Studio126/cpoptimizer/bin/x86-64_osx"” or similar.

“Main.java” contains main function and the implementation of all experimental environment.

—————————————————————————————————————————————————————————————————————

The problem instances are in the folder “instances/”, where there are folders named Set N, N = 1,2,3,4,5 and the folder Case study. They contain instances for Sets 1 to 5 and the case study.

——————————————————————————————————————————————————————————————————————

Remark:

If you find this software useful for your research or you create an algorithm
based on this software, please cite our original paper in your publication list.


Anna Minaeva, Debayan Roy, Benny Akesson, Zdenek Hanzalek, Samarjit Chakraborty: Efficient Heuristic and Exact Approaches for Control Performance Optimization in Time-Triggered Periodic Scheduling.