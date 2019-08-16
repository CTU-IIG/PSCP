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

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author annaminaeva
 */
public class Solution {
    private int[][] startTimes;
    private double objValue;
    private List<Integer> numbersOfActivities;
    private boolean networkIsZJ;
    private List<Integer> numbersOfNZJMessages;
    private List<Activity> acts;
    private List<App> dags;
    private int[][] schedule;
    private double[] controlPerfValues;
    private int[] latValues;
    private ProblemInstance prInst;
    private int nRes;
    private boolean provenInfeasible;
    private boolean failedToFind;
    
    public Solution(boolean provenInfeasible_){
        provenInfeasible = provenInfeasible_;
        failedToFind = true;
    }
    
    public Solution(int[][] startTimes_, ProblemInstance prInst, int Cmax_, List<Integer> numbersOfActivities_) {
        objValue = Cmax_;
        acts = prInst.getActs();
        dags = prInst.getApps();
        numbersOfActivities = numbersOfActivities_;
        startTimes = startTimes_;
    }
    
    public Solution(int[][] startTimes_, ProblemInstance prInst_, double ObjValue_,
                    double[] controlPerfValues_, int[] latValues_){
        prInst = prInst_;
        startTimes = startTimes_;
        objValue = ObjValue_;
        acts = prInst.getActs();
        dags = prInst.getApps();
        controlPerfValues = controlPerfValues_;
        latValues = latValues_;
        nRes = acts.get(0).getnResources();

        if(Main.CHECK_SOLUTION_CORRECTNESS) {
            checkCollisions();
            checkPrecedenceConstraints();
            checkLatencyConstraints();

            System.out.println("Solution is correct!");
        }

    }

    public Solution(int[][] startTimes_, ProblemInstance prInst_, double objValue_) {
        prInst = prInst_;
        startTimes = startTimes_;
        acts = prInst.getActs();
        dags = prInst.getApps();
        objValue = objValue_;

        nRes = acts.get(0).getnResources();

        if(Main.CHECK_SOLUTION_CORRECTNESS) {
            checkCollisions();
            checkPrecedenceConstraints();

            System.out.println("Solution is correct!");
        }
    }

    private void checkWhetherNetworkIsZJ(){
        networkIsZJ = true;
        numbersOfNZJMessages = new ArrayList<Integer>();
        
        System.out.println("");
        for (int i = 0; i < acts.size(); i++) {
            if(!acts.get(i).isTask()){
                for (int j = 0; j < acts.get(i).getNJobs(); j++) {
                    if(startTimes[i][j] != startTimes[i][0] + j * acts.get(i).getPeriod()){
                        networkIsZJ = false;
                        numbersOfNZJMessages.add(i + 1);
                        System.out.println("Message number "+ (i + 1) + " is scheduled NZJ");
                        break;
                    }
                }
            }
        }
        System.out.println("");
        
    }

    public double getObjValue() {
        return objValue;
    }
    
    public int[][] checkCollisions(){
        schedule = new int[nRes][acts.get(0).getHP()];

        for (int i = 0; i < nRes; i++) {
            for (int j = 0; j < startTimes.length; j++) {
                int numOfActivity = j;
                int numOccurrences;
                if (numbersOfActivities != null) {
                    numOfActivity = numbersOfActivities.get(j);
                    numOccurrences = 1;
                } else {
                    numOccurrences = acts.get(numOfActivity).getNJobs();
                }

                if (acts.get(numOfActivity).getAssToRes() - 1 == i) {
                    for (int k = 0; k < numOccurrences; k++) {
                        for (int l = 0; l < acts.get(numOfActivity).getProcTime(); l++) {
                            if (schedule[i][(startTimes[j][k] + l) % acts.get(0).getHP()] != 0) {
                                int act1 = numOfActivity;
                                int act2 = schedule[i][(startTimes[j][k] + l) % acts.get(0).getHP()] - 1;
                                System.out.println("TRAGEEEDIJA! " + act1 + " + " + act2 +
                                        " are in conflict at time " + ((startTimes[j][k] + l) % acts.get(0).getHP()) +
                                        " on resource " + acts.get(numOfActivity).getAssToRes());
                                acts.get(act1).printActivity();
                                System.out.print("assigned start times are ");

                                for (int m = 0; m < startTimes[act1].length; m++) {
                                    System.out.print(startTimes[act1][m] + " ");
                                }
                                System.out.println();

                                acts.get(act2).printActivity();
                                System.out.println("assigned start times are ");

                                for (int m = 0; m < startTimes[act2].length; m++) {
                                    System.out.print(startTimes[act2][m] + " ");
                                }
                                System.out.println();

                                System.out.println("Hyper-period is " + prInst.getHP());

                                prInst.getApps().get(acts.get(act1).getAssToApp() - 1).printApp();
                                prInst.getApps().get(acts.get(act2).getAssToApp() - 1).printApp();
                                System.exit(1);
                            }
                            schedule[i][(startTimes[j][k] + l) % acts.get(0).getHP()] = numOfActivity + 1;
                        }
                    }

                }
            }
        }

        return schedule;
    }

    public void printSolution() {
        System.out.println();
        System.out.println("Objective value is " + objValue);
        System.out.println();

        int[][] resourceSchedule = checkCollisions();
        for (int i = 0; i < nRes; i++) {
            System.out.println("Resource " + (i + 1) + ": ");
            for (int j = 0; j < resourceSchedule.length; j++) {
                System.out.format("%3d | ", resourceSchedule[i][j]);
            }
            System.out.println();
        }
    }

    public int[][] getStartTimes() {
        return startTimes;
    }

    public double[] getControlPerfValues() {
        return controlPerfValues;
    }

    public List<App> getDags() {
        return dags;
    }

    public boolean provenInfeasible() {
        return provenInfeasible;
    }

    public boolean failedToFind() {
        return failedToFind;
    }
    
    public void checkPrecedenceConstraints(){
        for (int i = 0; i < acts.size(); i++) {
            int succ = i;
            for (int j = 0; j < acts.get(i).getDirectPreds().size(); j++) {
                int pred = acts.get(i).getDirectPreds().get(j);
                for (int k = 0; k < acts.get(succ).getNJobs(); k++) {
                    if(startTimes[succ][k] < startTimes[pred][k] + acts.get(pred).getProcTime()){
                        System.out.println("TRAGEEEEDIJA");
                        System.out.println("Precedence relation is not satisfied of predecessor " + pred + " and successor " + succ);
                        System.out.println("Start time of successor is " + startTimes[succ][k] + ", finish time of predecessor is " + (startTimes[pred][k] + acts.get(pred).getProcTime()));
                        System.exit(1);
                    }
                }
            }
        }
    }
    
    public void checkLatencyConstraints(){
        int counter = 0;
        for (int i = 0; i < prInst.getApps().size(); i++) {
            if(prInst.getApps().get(i).isOrderCritical()) {
                if(latValues[counter] > prInst.getApps().get(i).getE2eLatBound()) {
                    System.out.println("END-TO-END latency constraint for application " + i + " is not satisfied!");
                    System.exit(1);
                }
                counter++;
            }
        }
    }
    
    public void getSchedActsFromSolConverted(ScheduledActivities schedActs) {
        assignSolToCurSTActsInSchedActs(schedActs);
        schedActs.computeAndSetResultingE2ELatencies();
        schedActs.computeObjective();
    }

    public void assignSolToCurSTActsInSchedActs(ScheduledActivities schedActs){
        schedActs.setActST(startTimes);
    }

    public void printControlPerfValues(){
        System.out.println("");
        System.out.println("Control performance values are:");
        for (int i = 0; i < controlPerfValues.length; i++) {
            System.out.println("Application " + i + ": " + controlPerfValues[i]);

        }
    }

    public void printLatValues(){
        System.out.println("");
        System.out.println("Latency values are:");
        for (int i = 0; i < latValues.length; i++) {
            System.out.println("Application " + i + ": " + latValues[i]);
        }
    }

    public int[][] getSchedule() {
        return schedule;
    }

}
