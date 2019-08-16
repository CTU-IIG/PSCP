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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ThreeLSheuristic {
    private ProblemInstance probInstSched;
    private ScheduledActivities scheduledActs;
    private List<Integer> actsToSchedule;
    private List<Integer> scheduledFromScratch;
    private SubModel subModel;
    public static int nIter;
    
    private int numActivities;

    public ThreeLSheuristic(ProblemInstance problemInstanceScheduling, SubModel subModelRO) throws IloException {
        this.subModel = subModelRO;
        this.probInstSched = problemInstanceScheduling;
        numActivities = problemInstanceScheduling.getActs().size();
        actsToSchedule = new ArrayList<>();
        scheduledFromScratch =  new ArrayList();

        if(subModelRO != null){
            if(subModelRO.isScheduledByOpt()){
                subModelRO.end();
            }
        }

        scheduledActs = new ScheduledActivities(problemInstanceScheduling);
    }

    
    private SolutionOneAct[] SecondLevelOfScheduling(int numActivityToUnschedule,
            Activity activity) throws IloException{
        scheduledActs.UnscheduleActivity(numActivityToUnschedule);
        SolutionOneAct[] problemSolutionForTwoActivities = new SolutionOneAct[1];

        subModel = new SubModel(activity, scheduledActs);
        if(subModel.isFeasible()){
            subModel.scheduleWithTwoActivities(probInstSched.getActs().get(numActivityToUnschedule));
            problemSolutionForTwoActivities = subModel.solveWithTwoActivities();
        }
        
        return problemSolutionForTwoActivities;
    }
    
    public ScheduledActivities Solve() throws IloException{
        SolutionOneAct problemSolutionForOneActivity = null;
        boolean someActIsCurUnscheduled = false;
        List<Integer> numRootProblem =  new ArrayList();
        
        SortActivitiesAccordingToThePriorityRule();
        
        nIter = 0;
        while(scheduledActs.getNumsOfSchedActs().size() < probInstSched.getActs().size()){
            nIter++;
            if(Main.IS_DEBUG_MODE) System.out.println(nIter);

            int i = Collections.min(actsToSchedule);
            actsToSchedule.remove(actsToSchedule.indexOf(i));
            Activity activity = probInstSched.getActs().get(i);
            if(Main.IS_DEBUG_MODE) System.out.println("Number of scheduled activity is " + activity.getID());

            //schedule activity by activity 
            subModel = new SubModel(activity, scheduledActs);
            if(subModel.isFeasible()){
                problemSolutionForOneActivity = subModel.solve();
            }
            
             // if the solution is found and there are no infeasible variables
            if(problemSolutionForOneActivity != null && subModel.isFeasible()){
                scheduledActs.AddScheduledActivity(problemSolutionForOneActivity.getStartTimes(), activity.getID());
                someActIsCurUnscheduled = false;
                
                //add currently released activity to activity to schedule array
                actsToSchedule = new ArrayList<>();
                addActsToActToScheduleArray();
            }
            else{
                //choose activity to unschedule
                int nActUnsch = scheduledActs.getScheduledActivityToUnschedule(
                        activity.getAssToRes(), scheduledFromScratch, activity.getAllPredecessors(), numRootProblem);
                
                if(nActUnsch == -1){
                    return null;
                }
                
                if(numRootProblem.contains(nActUnsch)){
                    //return null;
                    SolutionOneAct[] solutionTwoActs =
                            SecondLevelOfScheduling(nActUnsch, activity);
                    
                    if(solutionTwoActs != null && subModel.isFeasible()){
                        AssignFoundSolutionTwoActs(nActUnsch, solutionTwoActs, activity.getID());
                        //solutionTwoActs[0].CheckJitter();
                        //solutionTwoActs[1].CheckJitter();
                    }
                    else{
                        if(!ThirdLevelScheduling(activity.getID(), nActUnsch)){
                            return null;
                        }
                    }
                    
                    // add unscheduled activities to the top of the array
                    actsToSchedule = new ArrayList<>();
                    addActsToActToScheduleArray();
                    
                    if(!someActIsCurUnscheduled){
                        if(!numRootProblem.contains(i)){
                            numRootProblem.add(i);
                        }
                    }
                    someActIsCurUnscheduled = false;
                }
                else{
                    scheduledActs.UnscheduleActivity(nActUnsch);
                    actsToSchedule.add(0,i);
                    if(!someActIsCurUnscheduled){
                        if(!numRootProblem.contains(i)){
                            numRootProblem.add(i);
                        }
                    }
                    someActIsCurUnscheduled = true;
                    if(Main.IS_DEBUG_MODE) System.out.println("Unschedule!");
                }
            }
            
            if(subModel.isScheduledByOpt()){
                subModel.end();
            }
        }
        
        //CheckResultingScheduleOnDuplicates();
        
        return scheduledActs;
    }
    
    private void SortActivitiesAccordingToThePriorityRule(){
        int[] idInArray = new int[numActivities];
        Collections.sort(probInstSched.getActs());
        for(int i = 0; i < numActivities; i++) {
            idInArray[probInstSched.getActs().get(i).getID()] = i;
            probInstSched.getActs().get(i).setID(i);
        }
        
        for(int i = 0; i < numActivities; i++) {
            probInstSched.getActs().get(i).changePredecessorsAndSuccessorsAfterSorting(idInArray);
        }
        
        for(int i = 0; i < numActivities; i++) {
            if(probInstSched.getActs().get(i).getDirectPreds().isEmpty()){
                actsToSchedule.add(i);
            }
        }
    }

    //This method adds all activities that have their precedence constraints satisfied in scheduledActivities array
    private void addActsToActToScheduleArray(){
        for(int j = 0; j < probInstSched.getActs().size(); j++) {
            if(probInstSched.getActs().get(j).ArePredecessorsScheduled(scheduledActs.getNumsOfSchedActs())
            && !scheduledActs.getNumsOfSchedActs().contains(j)){
                actsToSchedule.add(j);
            }
        }
    }
    
    private boolean ThirdLevelScheduling(int numActivityCurScheduled, int numActivityToUnschedule) throws IloException{
        
        // if this pair was already scheduled 'from sratch', heuristic returns fail
        if(scheduledFromScratch.contains(numActivityCurScheduled) && scheduledFromScratch.contains(numActivityToUnschedule)){
            return false;
        }

        scheduledActs.UnscheduleAllActivitiesButPreviouslyScheduledAndPreceeding(scheduledFromScratch, 
                probInstSched.getActs().get(numActivityToUnschedule), 
                probInstSched.getActs().get(numActivityCurScheduled));
        
        subModel = new SubModel(probInstSched.getActs().get(numActivityCurScheduled),
                scheduledActs);
        
        if(!subModel.isFeasible()){
            return false;
        }
        
        subModel.scheduleWithTwoActivities(probInstSched.getActs().get(numActivityToUnschedule));
        
        SolutionOneAct[] problemSolutionForTwoActivities = subModel.solveWithTwoActivities();
        //scheduledActivities.PrintTheFinalSchedule(problemInstanceScheduling.getnResources(), problemInstanceScheduling.getHP());
                
        if(problemSolutionForTwoActivities == null){
            return false;
        }
        
        AssignFoundSolutionTwoActs(numActivityToUnschedule,
                problemSolutionForTwoActivities, numActivityCurScheduled);
        
        scheduledFromScratch.add(numActivityCurScheduled);
        scheduledFromScratch.add(numActivityToUnschedule);
        for(int i = 0; i < probInstSched.getActs().get(numActivityCurScheduled).getAllPredecessors().size(); i++) {
            scheduledFromScratch.add(probInstSched.getActs().get(numActivityCurScheduled).getAllPredecessors().get(i));
        }
        for(int i = 0; i < probInstSched.getActs().get(numActivityToUnschedule).getAllPredecessors().size(); i++) {
            scheduledFromScratch.add(probInstSched.getActs().get(numActivityToUnschedule).getAllPredecessors().get(i));
        }
        
        return true;
    }
   
    private void AssignFoundSolutionTwoActs(int numActivityToUnschedule,
                                            SolutionOneAct[] problemSolutionForTwoActivities, int numActivityCurrentlyScheduled) throws IloException{
        
        scheduledActs.AddScheduledActivity(problemSolutionForTwoActivities[0].getStartTimes(), numActivityCurrentlyScheduled);
        scheduledActs.AddScheduledActivity(problemSolutionForTwoActivities[1].getStartTimes(), numActivityToUnschedule);
    }
     
}
