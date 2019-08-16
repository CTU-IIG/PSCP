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

import ilog.concert.*;
import ilog.cplex.IloCplex;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ILPOneApp {
    
    private IloCplex solver;
    private IloNumVar[][] startTime;
    private IloObjective objective;
    private List<IloConstraint> resConstr;
    private IloNumVar latency;
    
    private int nJobs;
    private int[][] startT;
    private int period;
    private int numActivities;
    private App app;
    private boolean isScheduleEmpty;
    private List<List<List<List<Integer>>>> actIntervals;
    private List<List<Integer>> actsInConstrs;
    private int nConstr;

    private void SetVariableBounds() throws IloException{
        for (int i = 0; i < numActivities; i++) {
            for (int j = 0; j < nJobs; j++) {
                startTime[i][j].setLB(j * period);
                startTime[i][j].setUB((j + 1) * period + app.getE2eLatBound());
            }
        }
    }
    
    private void SetCriterion() throws IloException{
        objective = solver.addMinimize(latency);
    }
    
    private void SetLatency() throws IloException{
        latency = solver.numVar(0, app.getE2eLatBound(), IloNumVarType.Int);
        
        for (int k = 0; k < app.getLeafActs().size(); k++) {   
            Activity activityLeaf = app.getLeafActs().get(k);
            for (int i = 0; i < app.getRootActs().size(); i++) {
                //latency is maximum over all leaf-to-root pairs
                solver.addGe(
                        solver.diff(
                                latency,
                                solver.diff(
                                        startTime[app.getLeafActs().get(k).getIdInAppActArray()][0],
                                        startTime[app.getRootActs().get(i).getIdInAppActArray()][0]
                                )
                        ),
                        activityLeaf.getProcTime()
                );
            }
        }
    }
    
    private void SetResourceConstraintsZJ(int act1, int act2) throws IloException{
        IloNumVar var1 = solver.numVar(0, 1, IloNumVarType.Int);
        
        actsInConstrs.add(new ArrayList<Integer>());
        actsInConstrs.get(nConstr).add(act1);
        actsInConstrs.get(nConstr).add(act2);
        nConstr++;
        resConstr.add(
                solver.addGe(
                    solver.sum(
                            solver.prod(1.0, startTime[act1][0]),
                            solver.prod(-1.0, startTime[act2][0]),
                            solver.prod(period, var1)
                    ),
                app.getActs().get(act2).getProcTime()
                )
        );

        actsInConstrs.add(new ArrayList<Integer>());
        actsInConstrs.get(nConstr).add(act1);
        actsInConstrs.get(nConstr).add(act2);
        nConstr++;
        resConstr.add(
                solver.addGe(
                    solver.sum(
                            solver.prod(1.0, startTime[act2][0]),
                            solver.prod(-1.0, startTime[act1][0]),
                            solver.prod(-period, var1)
                    ),
                    app.getActs().get(act1).getProcTime() - period
                )
        );
    }
    
    private void SetResourceConstraintsNZJ(int mes1, int mes2) throws IloException{
        int HP = app.getE2eLatBound();
        for(int j = 0; j < app.getActs().get(mes2).getNJobs(); j++) {
            IloNumVar startTimeSecond = startTime[mes2][j];
            for(int l = 0; l < app.getActs().get(mes1).getNJobs(); l++) {
                IloNumVar startTimeFirst = startTime[mes1][l];
                if(!(startTimeSecond.getUB() + app.getActs().get(mes2).getProcTime() < startTimeFirst.getLB() ||
                        startTimeFirst.getUB() + app.getActs().get(mes1).getProcTime() < startTimeSecond.getLB() )){
                    IloNumVar var1 = solver.numVar(0, 1, IloNumVarType.Int);
                    
                    actsInConstrs.add(new ArrayList<Integer>());
                    actsInConstrs.get(nConstr).add(mes1);
                    actsInConstrs.get(nConstr).add(mes2);
                    nConstr++;
                    solver.addLe(
                            solver.sum(
                                    solver.prod(1.0, startTimeSecond),
                                    solver.prod(-1.0, startTimeFirst),
                                    solver.prod(-HP, var1)
                            ),
                            -1.0 * app.getActs().get(mes2).getProcTime());

                    actsInConstrs.add(new ArrayList<Integer>());
                    actsInConstrs.get(nConstr).add(mes1);
                    actsInConstrs.get(nConstr).add(mes2);
                    
                    nConstr++;
                    solver.addLe(
                            solver.sum(
                                    solver.prod(1.0, startTimeFirst),
                                    solver.prod(-1.0, startTimeSecond),
                                    solver.prod(HP, var1)
                            ),
                            -1.0 * app.getActs().get(mes1).getProcTime() + HP);
                }
            }
        }
    }
    
    private void SetResourceConstraints() throws IloException{
        resConstr = new ArrayList<>();
        for(int i = 0; i < numActivities; i++){
            for(int j = i + 1; j < numActivities; j++){
                if(app.getActs().get(i).getAssToRes() == app.getActs().get(j).getAssToRes()){
                    if(isScheduleEmpty || app.getActs().get(i).isTask()){
                        SetResourceConstraintsZJ(i, j);
                    }
                    else{
                        // otherwise, messages on the network should be scheduled NZJ
                        SetResourceConstraintsNZJ(i, j);
                    }
                }
            }
        }
    }
    
    private void SetPrecedenceConstraints() throws IloException{
        for(int i = 0; i < numActivities; i++){
            for(int j = 0; j < app.getActs().get(i).getDirectPreds().size(); j++) {
                int noOfTaskPreceeding = app.getActivitiesNumbers().indexOf(app.getActs().get(i).getDirectPreds().get(j));
                int numOfTaskFollowing = i;
                
                if(isScheduleEmpty || app.getActs().get(i).isTask()
                        && app.getActs().get(j).isTask()){
                    
                    solver.addGe(
                            solver.diff(
                                    startTime[numOfTaskFollowing][0],
                                    startTime[noOfTaskPreceeding][0]
                            ),
                            app.getActs().get(noOfTaskPreceeding).getProcTime()
                    );
                }
                else{
                    // if it scheduled NZJ and either of activities is 
                    // a message, add precedence constraints job-to-job
                    if(app.getActs().get(i).isTask()){
                        // the i-th activity is a task, j is a message
                        for (int k = 0; k < nJobs; k++) {
                            solver.addGe(
                                    solver.diff(
                                            startTime[numOfTaskFollowing][0],
                                            startTime[noOfTaskPreceeding][k]
                                    ),
                                    app.getActs().get(noOfTaskPreceeding).getProcTime() - period * k
                            );
                        }
                    }
                    else{
                        // the i-th activity is message and the j-th activity is task
                        // since two messages cannot be in precedence relations
                        for (int k = 0; k < nJobs; k++) {
                            solver.addGe(
                                    solver.diff(
                                            startTime[numOfTaskFollowing][k],
                                            startTime[noOfTaskPreceeding][0]
                                    ),
                                    app.getActs().get(noOfTaskPreceeding).getProcTime() + period * k
                            );
                        }
                    }
                }
                
            } 
        }
    }
    
    private void SetStartTimesOnECU() throws IloException{
        for (int i = 0; i < numActivities; i++) {
            if(isScheduleEmpty || app.getActs().get(i).isTask()){
                for (int j = 1; j < nJobs; j++) {
                    actsInConstrs.add(new ArrayList<Integer>());
                    actsInConstrs.get(nConstr).add(i);
                    nConstr++;
                    solver.addEq(solver.diff(startTime[i][j], startTime[i][0]), period * j);
                }
            }
        }
    }
    
    private void SetExistingScheduleConstraints() throws IloException{
        int HP = period;
        for (int i = 0; i < numActivities; i++) {
            for (int nJob = 0; nJob < actIntervals.get(i).size(); nJob++) {
                int nIntervals = actIntervals.get(i).get(nJob).size();
                int startIndex = 0;
                int endIndex = nIntervals - 1;
                
                // if the start of the first interval is at the beginning of the schedule,
                // set lower bound on the end of the first interval 
                if(!actIntervals.get(i).get(nJob).isEmpty()){
                    if(actIntervals.get(i).get(nJob).get(0).get(0) <= nJob * period - 1){
                        startTime[i][nJob].setLB(actIntervals.get(i).get(nJob).get(0).get(1));
                        startIndex = 1;
                    }

                    // if the end of the last interval is at the end of the period, 
                    // set upper bound on the end of this interval plus period, since 
                    // we schedule over two periods
                    if(actIntervals.get(i).get(nJob).get(nIntervals - 1).get(1) >= (nJob + 1) * period + 1){
                        startTime[i][nJob].setUB(actIntervals.get(i).get(nJob).get(nIntervals - 1).get(0));
                        endIndex = nIntervals - 2;
                    }

                    for (int j = startIndex; j <= endIndex; j++) {
                        // we need to add resource interval constraints to the first
                        // and to the second period
                        IloNumVar var1 = solver.numVar(0, 1, IloNumVarType.Int);
                        
                        actsInConstrs.add(new ArrayList<Integer>());
                        actsInConstrs.get(nConstr).add(i);
                        
                        nConstr++;
                        solver.addGe(
                                solver.sum(
                                        startTime[i][nJob], 
                                        solver.prod(HP, var1)
                                ), 
                                actIntervals.get(i).get(nJob).get(j).get(1)
                        );

                        actsInConstrs.add(new ArrayList<Integer>());
                        actsInConstrs.get(nConstr).add(i);
                       
                        nConstr++;
                        solver.addLe(
                                solver.sum(
                                        startTime[i][nJob], 
                                        solver.prod(HP, var1)
                                ), 
                                actIntervals.get(i).get(nJob).get(j).get(0) + HP
                        );
                    }
                }
                
            }
        }
    }
    
    public ILPOneApp(App app_, boolean isScheduleEmpty, List<List<List<List<Integer>>>> actIntervals)
            throws IloException, FileNotFoundException{
        this.isScheduleEmpty = isScheduleEmpty;
        this.app = app_;
        period = app_.getPeriod();
        nJobs = app_.getActs().get(0).getHP()/period;
        numActivities = app_.getNumActs();
        solver = new IloCplex();
        this.actIntervals = actIntervals;
        actsInConstrs = new ArrayList<>();
        nConstr = 0;
        
        startTime = new IloNumVar[numActivities][nJobs];
        for (int i = 0; i < numActivities; i++) {
            startTime[i] = solver.numVarArray(nJobs, 0, app.getE2eLatBound() - 1, IloNumVarType.Int);
        }
        
        SetVariableBounds();
        SetLatency();
        SetCriterion();
        SetResourceConstraints();
        SetPrecedenceConstraints();
        SetStartTimesOnECU();

        solver.setOut(null);

        if(!isScheduleEmpty){
            OutputStream osstr;
            osstr = new DataOutputStream(new FileOutputStream(Helpers.outputFileCPLEXlog));
            solver.setOut(osstr);
            SetExistingScheduleConstraints();
        }
        else{
            solver.addEq(startTime[app_.getRootActs().get(0).getIdInAppActArray()][0], 0);
        }
    }
    
    public Solution Solve(List<Integer> problematicResources) throws IloException, IOException{
        solver.setParam(IloCplex.IntParam.TimeLimit, Main.TIME_LIMIT_ILP_ONE_CLUSTER);
        if(solver.solve()){
            startT = new int[numActivities][nJobs];
            for (int i = 0; i < numActivities; i++) {
                for (int j = 0; j < nJobs; j++) {
                    if(isScheduleEmpty ||
                            app.getActs().get(i).isTask()){
                        startT[i][j] = (int) Math.round(solver.getValues(startTime[i])[0]) % period + j * period;
                    }
                    else{
                        startT[i][j] = (int) Math.round(solver.getValues(startTime[i])[j]);
                    }
                }
            }
            
            return new Solution(startT, app.getPrInst(), (int) Math.round(solver.getObjValue()), app.getActivitiesNumbers());
        }
        else{
            if(problematicResources == null){
                return null;
            }
            Scanner in = new Scanner(new File(Helpers.outputFileCPLEXlog));
            problematicResources.add(app.getNApp());
            
            int size = problematicResources.size();

            int nConfConstr = -1;
            while(in.hasNext()){
                String s = in.nextLine();
                if(s.contains("infeasible")){
                    String[] t = s.split("Row 'c");
                    String[] r = t[1].split("'");
                    nConfConstr = (int) Math.floor(Integer.parseInt(r[0]) / 2);
                    System.out.println("Problematic resource is " +
                            (app.getActs().get(actsInConstrs.get(nConfConstr).get(0)).getAssToRes() - 1));
                    problematicResources.add(app.getNApp());
                    break;
                }
            }
            
            // there is nothing to parse
            for (int i = 0; i < app.getNumActs(); i++) {
                if(startTime[i][0].getLB() > startTime[i][0].getUB()){
                    System.out.println("Problematic resource is " + (app.getActs().get(i).getAssToRes() - 1));
                }
            }
            
            return null;
        }
    }
}
