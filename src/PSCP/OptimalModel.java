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

import java.io.IOException;
import java.util.*;

public class OptimalModel {
    protected int nActs;
    protected int nApps;
    protected int numMessages;
    protected int numTasks;
    protected int HP;
    protected int numHPToCreateMesVars;
    protected int bigM;
    protected ArrayList<Integer> taskNumbers;
    protected ArrayList<Integer> messageNumbers;
    protected ArrayList<Integer> notScheduledNumbers;
    protected List<Activity> acts;
    protected boolean[] isActInNeigh;
    protected boolean isLocalNeiborhood;
    protected ScheduledActivities schedActs;
    protected String terminalFileName;
    
    protected ProblemInstance prInst;

    public OptimalModel(ProblemInstance probInst_, boolean[] isActInNeigborhood_) {
        prInst = probInst_;
        nActs = prInst.getActs().size();
        nApps = prInst.getApps().size();
        numMessages = 0;
        numTasks = 0;
        HP = prInst.getHP();
        acts = prInst.getActs();
        isActInNeigh = isActInNeigborhood_;

        int maxStartTime = 0;
        for (int i = 0; i < prInst.getApps().size(); i++) {
            if(maxStartTime < prInst.getApps().get(i).getPeriod() - 1 + prInst.getApps().get(i).getE2eLatBound()) {
                maxStartTime = prInst.getApps().get(i).getPeriod() - 1 + prInst.getApps().get(i).getE2eLatBound();
            }
        }

        numHPToCreateMesVars = (int) Math.ceil(maxStartTime / HP);
        bigM = maxStartTime;
        
        ComputeTasksAndMessagesNumbers();
    }

    private void ComputeTasksAndMessagesNumbers(){
        taskNumbers = new ArrayList<>();
        messageNumbers = new ArrayList<>();
        notScheduledNumbers = new ArrayList<>();
        for (int i = 0; i < nActs; i++) {
            if(acts.get(i).isTask()){
                taskNumbers.add(i);
                acts.get(i).setNumInTaskOrMessageArrayForILP(taskNumbers.size() - 1);
                numTasks++;
            }
            else{
                messageNumbers.add(i);
                acts.get(i).setNumInTaskOrMessageArrayForILP(messageNumbers.size() - 1);
                numMessages++;
            }
        }
    }
    
    protected void SetDeducedPrecConstraintsTasks()  throws IloException{};
    
    protected void SetPrecedenceConstr() throws IloException{};
    
    protected void createTaskVars() throws IloException{};
    protected void createMesVars(int nHPtoRepeat) throws IloException{};
    protected void createLatVars() throws IloException{};
    
    private void setResourceConstraintsECU() throws IloException{};
    
    private void setResourceConstraintsNetwork() throws IloException{};
    
    protected void setSTOfUnscheduledActivities(int[][] startTime){
        for (int i = 0; i < notScheduledNumbers.size(); i++) {
            Activity act = acts.get(notScheduledNumbers.get(i));
            for (int j = 0; j < act.getNJobs(); j++) {
                startTime[act.getID()][j] = j * act.getPeriod();
            }
        }
    }

    protected void setParamsToSolver(long timeLimit, Main.RunningCharacteristics chars) throws IloException, IOException {}

    protected int[][] storeTasksAndMessages() throws IloException {
        return null;
    }

    private int[] getLatencies(int[][] startTimes) throws IloException{
        int[] latencies = new int[prInst.getNumOrdCritApps()];
        int OCCounter = 0;
        for (int i = 0; i < prInst.getApps().size(); i++) {
            if(prInst.getApps().get(i).isOrderCritical()){
                latencies[OCCounter] = getLatency(OCCounter);
                if(Main.IS_DEBUG_MODE){
                    System.out.println("");
                    System.out.println("Period of application "+ i + " is "+ prInst.getApps().get(i).getPeriod());
                    System.out.println("Application " + i + " has latency "
                            + latencies[OCCounter]);



                    System.out.println("Activities start at: ");
                    for (int j = 0; j < prInst.getApps().get(i).getActs().size(); j++) {
                        System.out.println(j + "th activity starts at " +
                                startTimes[prInst.getApps().get(i).getActs().get(j).getID()][0]
                                + " and lasts " + prInst.getApps().get(i).getActs().get(j).getProcTime());
                    }
                }
                OCCounter++;
            }
        }
        return latencies;
    }

    public Solution solve(long timeLimit, Main.RunningCharacteristics chars) throws IloException, IOException {
        terminalFileName = chars.fullFileNameTerminalOut;
        setParamsToSolver(timeLimit, chars);

        if(solveModel()){
            int[][] startTimes = storeTasksAndMessages();
            int[] latencies = getLatencies(startTimes);

            double objV = 0;
            double[] controlPerfValues = null;
            if(Main.OPTIMIZE_WITH_CRITERION && !Main.IS_INSTANCE_GENERATION_MODE) {
                //controlPerfValues = getControlPerformanceValues();
                objV = getObjValue();
            }

            Helpers.printToFile(chars.fullFileNameTerminalOut, "\nOptimal objective value is " + objV);
            if(isLocalNeiborhood){
                Helpers.printToFile(chars.fullFileNameTerminalOut,
                        ", time is " + (System.currentTimeMillis() - FlexiHeuristic.globalSTHeurOpt));
            }

            Solution sol = new Solution(startTimes, prInst, objV, controlPerfValues, latencies);
            endSolver();

            return sol;
        }
        else {
            boolean provenInfeasible = false;
            if(solverProveInfeasibility()){
                provenInfeasible = true;
            }

            Solution sol = new Solution(provenInfeasible);
            System.out.println("\nNo solution found");
            Helpers.printToFile(chars.fullFileNameTerminalOut, "\nNo solution found");
            endSolver();

            return sol;
        }
    }

    protected void endSolver() {
        // crash. Need to be overrided in the child
    }

    protected boolean solveModel() throws IloException {
        return false;
    }

    protected int getLatency(int i) throws IloException{
        return 0;
    }

    protected double[] getControlPerformanceValues() throws IloException {
        return null;
    }

    protected boolean solverProveInfeasibility() throws IloException {
        return true;
    }

    protected double getObjValue() throws IloException {
        return 0;
    }

    protected void exportModel() throws IloException {}
}
