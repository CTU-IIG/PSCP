/*
	This file is part of the Periodic_Scheduling_with_Control_Performance program.
	Periodic_Scheduling_with_Control_Performance is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.
	Periodic_Scheduling_with_Control_Performance is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
	GNU General Public License for more details.
	You should have received a copy of the GNU General Public License
	along with Periodic_Scheduling_with_Control_Performance. If not, see <http://www.gnu.org/licenses/>.
 */
package PSCP;

import ilog.concert.IloException;
import gurobi .*;
import java.io.FileWriter;
import java.io.IOException;

/*
    This is the main class of this application, other classes has following objectives:
        1)  Activity - Class for one distinct activity.
        2)  ActivityJob - Class for an element in Flexi Heuristic.
        3)  App - Class for the application.
        4)  CPLocalNeighborhood - CP model for the optimization stage of Flexi heuristic.
        5)  FlexiHeuristic - Flexi heuristic class, described in Section VI.
        6)  Helpers - Class with auxiliary functions and variables, not related directly to any of the
            existing classes.
        7)  ILPLocalNeighborhood - ILP model for the optimization stage of Flexi heuristic.
        8)  ILPOneApp - ILP to schedule a single application to get better bound on heads and tails described in Section V.
        9)  Main - main file with all experiments implemented.
        10) MappingToTTEthernet - class used for nstance generation to map messages to network links with platform as in Figure 1a.
        11) OptimalModel - Parent class, containing necessary methods for optimal approaches.
        12) OptimalModelCP - implementation of CP model in Section V.
        13) OptimalModelILP - ILP model implementation.
        14) ProblemInstance - Class for problem instance. All of the proposed
            approaches work with instances of this class.
        15) ReasonOfDelay - Class for one delay element
        16) ScheduledActivities - Class that contains already scheduled activities. Used by both Flexi and 3-LS heuristic.
        17) Solution - Class that contains the scheduling problem solution.
        18) solutionOneAct - Class that contains scheduling problem solution for one activity,
            used by 3-LS heuristic.
        19) SubModel - Class for the sub-model used in 3-LS heuristic.
        20) ThreeLSheuristic - 3-LS heuristic.
*/

public class Main {
    // the most vital parameters
    static boolean OPTIMIZE_WITH_CRITERION = true;  // if false, just decision problem in both exact and heur
                                                    // ATTENTION: false does not work with SOLVE_ILP_WITH_LAZY = true
    static final boolean CRITERION_SUM = false;     // if false max
    static final boolean SOLVE_BY_HEUR = true;
    static boolean SOLVE_OPT_BY_ILP = false;
    static boolean SOLVE_OPT_BY_CP = true;
    static boolean RUN_3LS_HEURISTIC = false;       // if false Flexi heuristic
    static final boolean RUN_EMS_USE_CASE = false;
    static final boolean RUN_GENERAL_USE_CASE = false;
    static final boolean EXPORT_OPTIMAL_MODELS_TO_FILE = false;
    static final boolean CHECK_SOLUTION_CORRECTNESS = true;

    // mode setting parameters
    static final boolean IS_INSTANCE_GENERATION_MODE = false; // instances are generated and solved to keep only solvable instances
    static final boolean RUN_FEASIBILITY_EXPERIMENT = false;
    static final boolean ONLY_EXPORT_ILP_MODEL = false;
    static final boolean SOLVE_ILP_MODEL_ONLY_GUROBI = false;
    static final boolean IS_DEBUG_MODE = false;      // satisfaction of all constraints is checked and high verbosity
    static final boolean SHOW_INSTANCE = false;      // works only for general automotive case study
    static final int nDiscreeteLatValues = 20;       // how many values are in control performance piece-wise linear                                                       // function, given by input data (do not change)
    static final int MAX_COMM_TIME_CONTROL = 50;     // maximum communication time for control traffic. Used to set                                                        // whether it is a video message

    // instance generation parameters
    static int NUM_ECUs;
    static int NUM_ECUS_IN_DOMAIN = 5;
    static double PROBABILITY_INTERDOMAIN_COMMUNICATION = 0.1;
    static double PERCENT_VIDEO_APPS;
    static double DESIRABLE_UTILIZATION_OF_VIDEO_MESSAGE;
    static int COEFF_TO_SCALE_ON_CORES;
    static double MIN_UTILIZATION;
    static final double MAX_UTILIZATION_IN_MIN_PERIOD = 0.5;

    // graph heuristic parameters
    static boolean HEUR_MINIMIZE_USING_ILP = true; //if true, using both ILP and CP, if false using CP only
    static final int HEUR_WAY_OF_CHOOSING_NEIBORHOOD = 2; // 1 - random, 2 - the most improvable ones,
                                                    // 3 - first somehow and others as the ones sharing the
                                                    // most resources
    static int HEUR_NUM_APPS_IN_NEIGBORHOOD = 5;
    static int HEUR_MAX_NUM_SOL_IN_NEIGBORHOOD = 2;
    static int HEUR_NUM_APPS_TO_CHOSE_GRAPH_HEUR = HEUR_NUM_APPS_IN_NEIGBORHOOD + 5;
    static int time_limit_opt_heur_one_step = 300;
    static final boolean PRINT_LOCAL_NEIGH_RUN_BY_OPT_APPROACH = true;
    static int max_num_iterations_heur_feasible = 3000;
    static int WAY_TO_SET_DELAY_ELEMENT_INFEASIBLE = 0; // 0 - exactly on place where runs after predecessor,
                                                // 1 - the first interval, 2 - AMOUNT_INTERVALS_TO_SET_DELAY_ELEMENT
    static int NUMBER_INTERVAL_TO_SET_DELAY_ELEMENT = 1;
    static int AMOUNT_INTERVALS_TO_SET_DELAY_ELEMENT = 1;
    static final int NUM_WAYS_TO_SET_DELAY_ELEMENT = 3;
    
    // optimal approaches parameters
    static final boolean SOLVE_OPT_WITH_PW_LIN_EXISTING_ILP = true;
    static long TIME_LIMIT_OPT_SOL = 3000;
    static final double TIME_LIMIT_ILP_ONE_CLUSTER = 1;
    static final boolean PRINT_OPT_OUTPUT_TO_TERMINAL_FILE = true;
    static final boolean SOLVE_CP_INTERVAL_VARS = true;
    static boolean SOLVE_ILP_WITH_LAZY = true;
    static int NUM_DISJ_VARS_FOR_LAZY = 300;

    static int nInstance;
    static SubModel subModelRO;

    public static class RunningCharacteristics{
        int numSetToBeginExperiments;
        int numSetToEndExperiments;
        int numInstanceToStart;
        int numInstanceToEnd;
        String fileNameResults;
        String fileNameTerminal;
        String fullFileNameResultsOut;
        String fullFileNameTerminalOut;
        String importFileNameBosch;
        String exportFileNameBosch;
        int nSet;

        final String instancePathDCE = "instances/spec/spec-";
        String importFileName;

        boolean isGenerating;

        public RunningCharacteristics(){
            if(IS_INSTANCE_GENERATION_MODE) {
                OPTIMIZE_WITH_CRITERION = false;
            }
        };

        public void setRunningCharacteristics(int nSet_) throws IOException{
            nSet = nSet_;
            fullFileNameTerminalOut = fileNameTerminal + String.valueOf(nSet) + ".txt";
            fullFileNameResultsOut = fileNameResults + String.valueOf(nSet) + ".txt";
            Helpers.printToFile(fullFileNameResultsOut, "\n" + nSet +  " set!\n");
            switch(nSet){
                case 1:
                    HEUR_NUM_APPS_IN_NEIGBORHOOD = 2;
                    HEUR_MAX_NUM_SOL_IN_NEIGBORHOOD = 3;
                    time_limit_opt_heur_one_step = 10;
                    max_num_iterations_heur_feasible = 100;
                    SOLVE_OPT_BY_ILP = true;

                    MIN_UTILIZATION = 0.5;
                    COEFF_TO_SCALE_ON_CORES = 8;
                    PERCENT_VIDEO_APPS = 0.3;
                    NUM_DISJ_VARS_FOR_LAZY = 200;
                    DESIRABLE_UTILIZATION_OF_VIDEO_MESSAGE = 0.15;
                    NUM_ECUs = 2;
                    break;
                case 2:
                    HEUR_NUM_APPS_IN_NEIGBORHOOD = 2;
                    HEUR_MAX_NUM_SOL_IN_NEIGBORHOOD = 3;
                    SOLVE_OPT_BY_ILP = true;
                    time_limit_opt_heur_one_step = 20;
                    max_num_iterations_heur_feasible = 300;

                    MIN_UTILIZATION = 0.6;
                    COEFF_TO_SCALE_ON_CORES = 8;
                    PERCENT_VIDEO_APPS = 0.25;
                    NUM_DISJ_VARS_FOR_LAZY = 600;
                    DESIRABLE_UTILIZATION_OF_VIDEO_MESSAGE = 0.1;
                    NUM_ECUs = 2;

                    break;
                case 3:
                    HEUR_NUM_APPS_IN_NEIGBORHOOD = 2;
                    HEUR_MAX_NUM_SOL_IN_NEIGBORHOOD = 3;
                    time_limit_opt_heur_one_step = 30;
                    max_num_iterations_heur_feasible = 500;
                    SOLVE_OPT_BY_ILP = false;

                    MIN_UTILIZATION = 0.65;
                    COEFF_TO_SCALE_ON_CORES = 8;
                    PERCENT_VIDEO_APPS = 0.2;
                    NUM_DISJ_VARS_FOR_LAZY = 600;
                    DESIRABLE_UTILIZATION_OF_VIDEO_MESSAGE = 0.1;
                    NUM_ECUs = 3;
                    break;
                case 4:
                    HEUR_NUM_APPS_IN_NEIGBORHOOD = 3;
                    HEUR_MAX_NUM_SOL_IN_NEIGBORHOOD = 2;
                    SOLVE_OPT_BY_ILP = false;
                    HEUR_MINIMIZE_USING_ILP = false;
                    time_limit_opt_heur_one_step = 300;
                    max_num_iterations_heur_feasible = 1000;

                    MIN_UTILIZATION = 0.7;
                    COEFF_TO_SCALE_ON_CORES = 4;
                    PERCENT_VIDEO_APPS = 0.1;
                    DESIRABLE_UTILIZATION_OF_VIDEO_MESSAGE = 0.01;
                    NUM_ECUs = 12;
                    NUM_ECUS_IN_DOMAIN = 6;
                    NUM_DISJ_VARS_FOR_LAZY = 1000;
                    break;
                case 5:
                    HEUR_NUM_APPS_IN_NEIGBORHOOD = 0;
                    HEUR_MAX_NUM_SOL_IN_NEIGBORHOOD = 0;
                    HEUR_MINIMIZE_USING_ILP = false;
                    SOLVE_OPT_BY_CP = true;
                    SOLVE_OPT_BY_ILP = false;
                    time_limit_opt_heur_one_step = 300;
                    max_num_iterations_heur_feasible = 3000;

                    break;
                case 6:
                    // EMS case study
                    HEUR_NUM_APPS_IN_NEIGBORHOOD = 0;
                    HEUR_MAX_NUM_SOL_IN_NEIGBORHOOD = 0;
                    SOLVE_OPT_BY_ILP = false;
                    HEUR_MINIMIZE_USING_ILP = false;
                    time_limit_opt_heur_one_step = 3000;
                    max_num_iterations_heur_feasible = 3000;

                    MIN_UTILIZATION = 0.7;
                    COEFF_TO_SCALE_ON_CORES = 4;
                    PERCENT_VIDEO_APPS = 0.0001;
                    DESIRABLE_UTILIZATION_OF_VIDEO_MESSAGE = 0.0001;
                    NUM_ECUs = 20;
                    NUM_ECUS_IN_DOMAIN = 8;
                    NUM_DISJ_VARS_FOR_LAZY = 1000;
                    PROBABILITY_INTERDOMAIN_COMMUNICATION = 0.001;
                    break;

                default:
                    System.out.println("Incorrect number of set!");
                    System.exit(0);
                    break;
            }

            isGenerating = IS_INSTANCE_GENERATION_MODE;
        }

        public void setInstancePaths(int nSet, int nInstance) {
            importFileNameBosch = "instances/fromSC/Set " + String.valueOf(nSet) + "/problem_instance" + String.valueOf(nInstance) + ".dat";
            exportFileNameBosch = "instances/fromSC/Set " + String.valueOf(nSet) + "/problem_instance_TT"+ "-" + String.valueOf(nInstance) + ".dat";

            if(IS_INSTANCE_GENERATION_MODE){
                importFileName = importFileNameBosch;
            }
            else{
                importFileName = exportFileNameBosch;
            }
        }
    }

    public static class StatisticsOfApproaches {
        long[] runTimeFeasibleHeur = new long[NUM_WAYS_TO_SET_DELAY_ELEMENT];
        long[] runTimeOptPhaseCPHeur = new long[NUM_WAYS_TO_SET_DELAY_ELEMENT];
        long[] runTimeOptPhaseILPHeur = new long[NUM_WAYS_TO_SET_DELAY_ELEMENT];
        long[] runTimeObjectGenHeur = new long[NUM_WAYS_TO_SET_DELAY_ELEMENT];
        double[] critFeasHeur = new double[NUM_WAYS_TO_SET_DELAY_ELEMENT];
        double[] critOptCPHeur = new double[NUM_WAYS_TO_SET_DELAY_ELEMENT];
        double[] critOptILPHeur = new double[NUM_WAYS_TO_SET_DELAY_ELEMENT];

        long runTimeILPTotal;
        long runTimeCPTotal;
        long runTimeILPGen;
        long runTimeCPGen;
        double critILP;
        double critCP;

        public StatisticsOfApproaches() {}

        public void printStatisticsToFile(String fileName) throws IOException{
            FileWriter writer = new FileWriter(fileName,true);
            for (int i = 0; i < NUM_WAYS_TO_SET_DELAY_ELEMENT; i++) {
                long totalRunTimeILP = runTimeObjectGenHeur[i] + runTimeFeasibleHeur[i] + runTimeOptPhaseILPHeur[i];
                long totalRunTimeCP = runTimeObjectGenHeur[i] + runTimeFeasibleHeur[i] + runTimeOptPhaseCPHeur[i];
                writer.write(runTimeFeasibleHeur[i] + " " + critFeasHeur[i] + " " + totalRunTimeILP + " "
                        + critOptILPHeur[i] + " "  + totalRunTimeCP + " " + critOptCPHeur[i] +"\n");
            }

            writer.write(runTimeILPGen + " " + runTimeILPTotal + " " + critILP + " " + runTimeCPGen + " " +
                    runTimeCPTotal + " " + critCP + "\n\n");
            writer.close();
        }
    }

    private static void printRunningStatisticsExactModel(long runTimeGen, long runTimeTotal, double crit, Solution sol) {
        System.out.println("Generation time is " + runTimeGen);
        System.out.println("Total runtime is " + runTimeTotal);
        System.out.println("Criterion is " + crit);


        if (sol.provenInfeasible()) {
            System.out.println("The model proved the instance to be infeasible.");
        }

        if(!sol.provenInfeasible() && sol.failedToFind()) {
            System.out.println("The model failed to find a solution due to time limit.");
        }
    }

    private static void solveProblemWithGurobiILP(String[] args) throws IOException, IloException, GRBException{
        String inFileBeginning = "lp_instances/";
        String fileStat = "gurobi_set_";
        RunningCharacteristics chars = setGlobalRunningCharacteristics(args);
        for (int nSet = 3; nSet < 4; nSet++) {
            String inFileMiddle = "Set_" + String.valueOf(nSet) + "/lp_";
            fileStat = fileStat + String.valueOf(nSet) + ".txt";
            for (nInstance = 1; nInstance <= 100; nInstance++) {
                chars.setRunningCharacteristics(nSet);
                chars.setInstancePaths(nSet, nInstance);
                System.out.println("Instance " + nInstance + " is processing");

                String fileName = inFileBeginning + inFileMiddle + String.valueOf(nInstance) + ".lp";
                System.out.println("\n\nSolve by ILP with Gurobi:\n");

                ProblemInstance prInst = new ProblemInstance(chars.importFileName, chars, 0);

                SOLVE_ILP_WITH_LAZY = false;
                long startTimeILP = System.currentTimeMillis();
                OptimalModelILP model = new OptimalModelILP(prInst,false,
                        Helpers.createBoolArrayValues(prInst.getActs().size(), true), chars, null);
                long generation_time = System.currentTimeMillis() - startTimeILP;

                model.exportModel(fileName);

                GRBEnv env = new GRBEnv();
                GRBModel grbModel = new GRBModel(env, fileName);
                double timeLeftToRun = TIME_LIMIT_OPT_SOL - generation_time/1000;
                grbModel.getEnv().set(GRB.DoubleParam.TimeLimit, timeLeftToRun);
                long startTimeILPSolving = System.currentTimeMillis();
                grbModel.optimize();
                long solving_time = System.currentTimeMillis() - startTimeILPSolving;
                long runTimeILPTotal = generation_time + solving_time;

                FileWriter writer = new FileWriter(fileStat,true);
                writer.write(nInstance + "-th instance\n");
                writer.write(generation_time + " " + runTimeILPTotal + " " + grbModel.get(GRB.DoubleAttr.ObjVal) + "\n\n");
                writer.close();
            }
        }
    }

    // Export ILP models to solve by Gurobi
    private static void exportILPModels(String[] args)  throws IOException, IloException{
        String outFileStart = "lp_instances/";
        RunningCharacteristics chars = setGlobalRunningCharacteristics(args);
        for (int nSet = 3; nSet < 4; nSet++) {
            String outFileMiddle = "Set_" + String.valueOf(nSet) + "/lp_";
            for (nInstance = 56; nInstance <= 100; nInstance++) {
                chars.setRunningCharacteristics(nSet);
                chars.setInstancePaths(nSet, nInstance);
                System.out.println("Instance " + nInstance + " is processing");

                ProblemInstance prInst = new ProblemInstance(chars.importFileName, chars, 0);

                SOLVE_ILP_WITH_LAZY = false;
                OptimalModelILP model = new OptimalModelILP(prInst,false,
                        Helpers.createBoolArrayValues(prInst.getActs().size(), true), chars, null);
                String fileName = outFileStart + outFileMiddle + String.valueOf(nInstance) + ".lp";
                model.exportModel(fileName);
            }
        }
    }

    private static Solution solveOptimally(StatisticsOfApproaches stat, ProblemInstance prInst,
                                           RunningCharacteristics chars) throws IOException, IloException{
        Solution sol = null;
        if(SOLVE_OPT_BY_CP) {
            Helpers.printToFile(chars.fullFileNameTerminalOut, "\n\nSolve by CP:\n");
            long startTimeCP = System.currentTimeMillis();
            OptimalModelCP model = new OptimalModelCP(prInst, Helpers.createBoolArrayValues(prInst.getActs().size(), true), null);

            stat.runTimeCPGen = System.currentTimeMillis() - startTimeCP;
            long leftRunTimeCP = TIME_LIMIT_OPT_SOL * 1000 - stat.runTimeCPGen;
            if(leftRunTimeCP > 0){
                sol = model.solve(leftRunTimeCP / 1000, chars);
            }
            else {
                sol = new Solution(false);
            }
            stat.runTimeCPTotal = System.currentTimeMillis() - startTimeCP;

            if (!sol.failedToFind()) {
                stat.critCP = sol.getObjValue();
                if(IS_INSTANCE_GENERATION_MODE) {
                    return sol;
                }
            }

            if(IS_DEBUG_MODE) {
                System.out.print("\n\nCP model:\n");
                printRunningStatisticsExactModel(stat.runTimeCPGen, stat.runTimeCPTotal, stat.critCP, sol);
            }
        }

        if(SOLVE_OPT_BY_ILP){
            Helpers.printToFile(chars.fullFileNameTerminalOut, "\n\nSolve by ILP:\n");
            long startTimeILP = System.currentTimeMillis();
            boolean isRelaxedOnIntegralityOfQuotients = false;
            OptimalModelILP model1 = new OptimalModelILP(prInst,
                    isRelaxedOnIntegralityOfQuotients, Helpers.createBoolArrayValues(prInst.getActs().size(), true),
                    chars, null);
            stat.runTimeILPGen = System.currentTimeMillis() - startTimeILP;
            
            long leftRunTime = TIME_LIMIT_OPT_SOL * 1000 - stat.runTimeILPGen;

            if(leftRunTime > 0){
                sol = model1.solve(leftRunTime / 1000, chars);
            }
            else{
                sol = new Solution(false);
            }

            stat.runTimeILPTotal = System.currentTimeMillis() - startTimeILP;
            
            if(!sol.failedToFind()){
                stat.critILP = sol.getObjValue();
            }

            if(IS_DEBUG_MODE) {
                System.out.print("\n\nILP model:\n");
                printRunningStatisticsExactModel(stat.runTimeILPGen, stat.runTimeILPTotal, stat.critILP, sol);
            }
        }

        return sol;
    }

    private static Solution solveHeuristically(StatisticsOfApproaches stat, ProblemInstance prInst,
                                               RunningCharacteristics chars, int nInstance)
            throws IOException, IloException {
        long startTime = System.currentTimeMillis();
        Solution solHeur;
        if(RUN_3LS_HEURISTIC) {
            ThreeLSheuristic heur3LS = new ThreeLSheuristic(prInst, subModelRO);
            ScheduledActivities scheduledActs = heur3LS.Solve();
            if(scheduledActs == null) {
                solHeur = null;
            }
            else {
                scheduledActs.computeAndSetResultingE2ELatencies();
                scheduledActs.computeObjectiveMax();
                solHeur = new Solution(scheduledActs.getActST(), prInst, scheduledActs.getObjValue());
                stat.runTimeFeasibleHeur[Main.WAY_TO_SET_DELAY_ELEMENT_INFEASIBLE] = System.currentTimeMillis() - startTime;
                stat.critOptCPHeur[Main.WAY_TO_SET_DELAY_ELEMENT_INFEASIBLE] = solHeur.getObjValue();
            }
        }
        else {
            FlexiHeuristic heuristic = new FlexiHeuristic(prInst);
            stat.runTimeObjectGenHeur[WAY_TO_SET_DELAY_ELEMENT_INFEASIBLE] = System.currentTimeMillis() - startTime;
            solHeur = heuristic.Solve(stat, chars);
        }

        return solHeur;
    }

    private static RunningCharacteristics setGlobalRunningCharacteristics(String[] args) {
        RunningCharacteristics chars = new RunningCharacteristics();
        chars.numSetToBeginExperiments = Integer.valueOf(args[0]);
        chars.numSetToEndExperiments = Integer.valueOf(args[1]);
        chars.numInstanceToStart = Integer.valueOf(args[2]);
        chars.numInstanceToEnd = Integer.valueOf(args[3]);
        chars.fileNameResults = String.valueOf(args[4]);
        chars.fileNameTerminal = String.valueOf(args[5]);
        SOLVE_OPT_BY_ILP = Integer.valueOf(args[6]) > 0;
        SOLVE_OPT_BY_CP = Integer.valueOf(args[7]) > 0;
        SOLVE_ILP_WITH_LAZY = Integer.valueOf(args[8]) > 0;

        return chars;
    }

    private static ProblemInstance exportProblemInstance(ProblemInstance prInst,
                                                 RunningCharacteristics chars) throws IOException, IloException{
        int val = prInst.isUtilizationInBoundsAndInstanceSchedulable();

        // this is used during instance generation to achieve schedulability of the instance
        if(val != 0){
            if(val == -1) {
                DESIRABLE_UTILIZATION_OF_VIDEO_MESSAGE -= 0.001;
            }
            if(val == 1) {
                DESIRABLE_UTILIZATION_OF_VIDEO_MESSAGE += 0.001;
            }

            return null;
        }

        prInst.exportProblemInstanceWithTTEthernet(chars.exportFileNameBosch);
        chars.isGenerating = false;
        prInst = new ProblemInstance(chars.exportFileNameBosch, chars, 0);

        return prInst;
    }

    private static void runFeasibilityExperiment(RunningCharacteristics chars, int nInstance, int nSet) throws IOException, IloException {
        double max_ut_opt = 0;
        long time_opt = 0;
        double max_ut_heur = 0;
        long time_heur = 0;
        long prev_run_time_heur = 0;
        long prev_run_time_opt = 0;

        OPTIMIZE_WITH_CRITERION = false;
        SOLVE_OPT_BY_ILP = false;
        SOLVE_OPT_BY_CP = false;
        WAY_TO_SET_DELAY_ELEMENT_INFEASIBLE = 0;

        ProblemInstance prInst = new ProblemInstance(chars.importFileName, chars, 0.5);

        StatisticsOfApproaches stat = null;
        boolean heurDone = false;
        boolean optDone = false;
        for (double max_ut = 0.5; max_ut <= 1.00; max_ut += 0.01) {
            Helpers.printArrayElementPerRow(prInst.computeTotalUtilizationOnEachResource());
            stat = new StatisticsOfApproaches();

            /*if(!optDone) {
                Solution solOpt = solveOptimally(stat, prInst, chars);
                if(solOpt.failedToFind() || max_ut > 0.989) {
                    max_ut_opt = Helpers.getMax(prInst.getUtilizations()) - 0.01;
                    time_opt = prev_run_time_opt;
                    optDone = true;
                }
            }*/

            if(!heurDone) {
                Solution solHeur = solveHeuristically(stat, prInst, chars, nInstance);
                if(solHeur == null) {
                    max_ut_heur = Helpers.getMax(prInst.getUtilizations()) - 0.01;
                    time_heur = prev_run_time_heur;
                    heurDone = true;
                }
            }

            //if(heurDone && optDone) break;
            if(heurDone) break;

            prev_run_time_heur = stat.runTimeFeasibleHeur[Main.WAY_TO_SET_DELAY_ELEMENT_INFEASIBLE];
            prev_run_time_opt = stat.runTimeCPTotal;

            prInst.scaleResWithMaxUtAndUtMoreThanGivenToRequiredUt(max_ut + 0.01);
        }

        FileWriter writer = new FileWriter(Helpers.outFileFeas + String.valueOf(nSet) + ".dat",true);
        writer.write(nInstance + " instance\n");
        writer.write(time_heur + " " + max_ut_heur + " " + time_opt + " "
                + " " + max_ut_opt + " " + stat.runTimeCPTotal + "\n");
        writer.close();
    }

    private static void runGeneralUseCase() throws IOException, IloException {
        StatisticsOfApproaches stat = new StatisticsOfApproaches();
        RunningCharacteristics chars = new RunningCharacteristics();
        chars.fileNameResults = "outUseCase.txt";
        chars.fullFileNameTerminalOut = "TerminalUseCase.txt";
        FileWriter writer = new FileWriter(chars.fileNameResults, true);

        ProblemInstance prInst = new ProblemInstance("instances/spec/spec-4-1-10.xml", chars, 0);

        System.out.println("Number of jobs of problem instance " + nInstance + " is " + prInst.computeNJobs());
        for (int i = 0; i < NUM_WAYS_TO_SET_DELAY_ELEMENT; i++) {
            WAY_TO_SET_DELAY_ELEMENT_INFEASIBLE = i;
            Solution solHeur = solveHeuristically(stat, prInst, chars, nInstance);
        }

        Solution solOpt = solveOptimally(stat, prInst, chars);

        stat.printStatisticsToFile(chars.fileNameResults);
    }

    public static void main(String[] args) throws IOException, IloException, GRBException {
        System.out.println("Copyright 2019-2020 Anna Minaeva, Debayan Roy, Benny Akesson, Zdenek Hanzalek, Samarjit Chakraborty.");
        System.out.println("The program is distributed under the terms of the GNU General Public License.");

        RunningCharacteristics chars = setGlobalRunningCharacteristics(args);

        if(RUN_GENERAL_USE_CASE) {
            runGeneralUseCase();
            return;
        }

        if(ONLY_EXPORT_ILP_MODEL){
            exportILPModels(args);
            return;
        }

        if(SOLVE_ILP_MODEL_ONLY_GUROBI) {
            solveProblemWithGurobiILP(args);
            return;
        }

        if(RUN_EMS_USE_CASE) {
            chars.numSetToBeginExperiments = 6;
            chars.numSetToEndExperiments = 6;
            SOLVE_OPT_BY_ILP = false;
        }

        for (int nSet = chars.numSetToBeginExperiments; nSet <= chars.numSetToEndExperiments; nSet++) {
            Helpers.printToFile(Helpers.outFileIter,"\n \n" + nSet + " set \n");
            for (nInstance = chars.numInstanceToStart; nInstance <= chars.numInstanceToEnd; nInstance++) {
                chars.setRunningCharacteristics(nSet);
                chars.setInstancePaths(nSet, nInstance);

                Helpers.printInstanceDone((nInstance - 1), chars.fullFileNameTerminalOut);
                Helpers.printToFile(chars.fullFileNameResultsOut, nInstance + " instance\n");

                if(RUN_FEASIBILITY_EXPERIMENT) {
                    runFeasibilityExperiment(chars, nInstance, nSet);
                    continue;
                }

                ProblemInstance prInst = new ProblemInstance(chars.importFileName, chars, 0);
                prInst.exportProblemInstanceWithTTEthernet("/Users/annaminaeva/git/IP_paper/src/IP_source_code/instances/fromSC/Set 5/problem_instance_TT-" + Integer.toString(nInstance) + ".dat");

                if (IS_INSTANCE_GENERATION_MODE) {
                    prInst = exportProblemInstance(prInst, chars);
                    if (prInst == null) {
                        nInstance--;
                        continue;
                    }
                }

                StatisticsOfApproaches stat = new StatisticsOfApproaches();
                if (SOLVE_BY_HEUR) {
                    for (int i = 0; i < 1; i++) {
                        WAY_TO_SET_DELAY_ELEMENT_INFEASIBLE = i;
                        Solution solHeur = solveHeuristically(stat, prInst, chars, nInstance);
                    }

                    if (!Main.IS_INSTANCE_GENERATION_MODE) {
                        if ((!SOLVE_OPT_BY_ILP && !SOLVE_OPT_BY_CP)) {
                            stat.printStatisticsToFile(chars.fullFileNameResultsOut);
                            continue;
                        }
                    }
                }

                Solution solOpt = solveOptimally(stat, prInst, chars);
                if (IS_INSTANCE_GENERATION_MODE && solOpt.failedToFind()) {
                    nInstance--;
                    continue;
                }

                stat.printStatisticsToFile(chars.fullFileNameResultsOut);
            }
        }
    }
}
