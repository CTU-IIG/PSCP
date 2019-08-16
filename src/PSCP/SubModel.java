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
import ilog.concert.IloNumVar;
import ilog.concert.IloNumVarType;
import ilog.concert.IloObjective;
import ilog.cplex.IloCplex;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SubModel{
    //ILP
    private IloNumVar[] startTimeSecondActivity;
    private IloCplex solverILP;
    private List<List<List<Integer>>> intsOfResConstrsJobs;
    private int[][] boundsOnJobs;
    private List<List<List<Integer>>> secondIntervalsOfResourceConstraintsForJobs;
    
    private int[][] secondBoundsOnJobs;
    private boolean isFeasible;
    private boolean isScheduledByOpt;
    private List<Integer> nInfeasActs;
    
    private Activity activity;
    private ScheduledActivities schedActs;
    private Activity secondActivity;
    private boolean toBeSolvedByILP;
    private int HP;
    private int nConstr;
    private IloObjective ObjectiveILP;
    private IloNumVar[] startTimeILP;

    public boolean isFeasible() {
        return isFeasible;
    }

    public boolean isScheduledByOpt() {
        return isScheduledByOpt;
    }

    private void periodConstraintsSetting(Activity activity, int[][] boundsOnJobs) throws IloException{
        int period = activity.getPeriod();
        for(int j = 0; j < activity.getHP() / activity.getPeriod(); j++) {
            boundsOnJobs[j][0] = j * period + activity.getCriticalLengthBefore();
            boundsOnJobs[j][1] = (j + 2) * period - activity.getCriticalLengthAfter();
        }
    }
    
    private void setPrecedenceConstraints(Activity activity, int[][] boundsOnJobs) throws IloException{
        for(int i = 0; i < activity.getDirectPreds().size(); i++) {
            // if it's the second activity and it is in precedence relations with 
            // the first activity, skip it
            if(secondActivity != null){
                if(secondActivity.getDirectPreds().contains(this.activity.getID()) ||
                        this.activity.getDirectPreds().contains(secondActivity.getID())){
                    continue;
                }
            }
            
            for(int j = 0; j < activity.getHP() / activity.getPeriod(); j++) {
                int nPred = activity.getDirectPreds().get(i);
                if(schedActs.getActST()[nPred] == null){
                    System.out.println("Some of the predecessors of currently scheduled activity are not already scheduled");
                    System.exit(0);
                }
                
                for(int k = 0; k < activity.getAllSucc().size(); k++) {
                   if(Helpers.ArrayListContainsValue(schedActs.getNumsOfSchedActs(), activity.getAllSucc().get(k))) {
                       System.out.println("\n\n\n\n\n\n\n\n Problem! One of the successors of the scheduled activity are already scheduled!\n\n\n\n\n\n\n\n");
                   }
                }
                
                int tPredEnds = 
                        schedActs.getActST()[nPred][j] +
                        schedActs.getPrInst().getActs().get(nPred).getProcTime();
                if(boundsOnJobs[j][0] < tPredEnds){
                    boundsOnJobs[j][0] = tPredEnds;
                }
            }
        }
    }
    
    private void concatenateIntervals(List<List<Integer>> intsOfResConstrs){
        for(int i = 0; i < intsOfResConstrs.size() - 1; i++) {
            if(intsOfResConstrs.get(i).get(0) > intsOfResConstrs.get(i).get(1))
                System.out.println("PROBLEM WITH SORTING WHILE CONCATENATING");
            if(intsOfResConstrs.get(i + 1).get(0) < intsOfResConstrs.get(i).get(1)){
                intsOfResConstrs.get(i).set(1, Math.max(intsOfResConstrs.get(i).get(1), intsOfResConstrs.get(i + 1).get(1)));
                intsOfResConstrs.remove(i + 1);
                i--;
            }
        }
    }
  
    private void obtainResourceIntervals(int[][] boundsOnJobs, Activity act, List<List<List<Integer>>> intsOfResConstrsJobs){
        int nUsedRes = act.getAssToRes() - 1;
        int startPrevInt = 0;
        int nJobs = act.getHP() / act.getPeriod();
        for(int j = 0; j < nJobs; j++) {
            //find interval for this job
            int startInterval = startPrevInt;
            boolean isStartIntPrevSet = false;
            
            for(int k = startInterval; k < schedActs.getIntsOfResConstrs().get(nUsedRes).size(); k++) {
                int endCurInt = schedActs.getIntsOfResConstrs().get(nUsedRes).get(k).get(1);
                if(j != nJobs - 1 
                        && endCurInt > boundsOnJobs[j + 1][0]
                        && !isStartIntPrevSet){
                    startPrevInt = k;
                    isStartIntPrevSet = true;
                }
                
                if(j * act.getPeriod() >= schedActs.getIntsOfResConstrs().get(nUsedRes).get(k).get(1)){
                    // due to precedence constraints this interval should not be considered
                    continue;
                }

                if(schedActs.getIntsOfResConstrs().get(nUsedRes).get(k).get(0) < boundsOnJobs[j][1] + act.getProcTime()){
                    intsOfResConstrsJobs.get(j).add(new ArrayList<Integer>());
                    int last = intsOfResConstrsJobs.get(j).size() - 1;
                    intsOfResConstrsJobs.get(j).get(last).add(
                            schedActs.getIntsOfResConstrs().get(nUsedRes).get(k).get(0) - act.getProcTime());
                    intsOfResConstrsJobs.get(j).get(last).add(
                            schedActs.getIntsOfResConstrs().get(nUsedRes).get(k).get(1));
                }

                if(schedActs.getIntsOfResConstrs().get(nUsedRes).get(k).get(1) >= boundsOnJobs[j][1] + act.getProcTime()){
                    /*startPrevInt = k;
                    if(schedActs.getIntsOfResConstrs().get(nUsedRes).get(k).get(1) == boundsOnJobs[j][1] + activity.getProcTime()){
                        startPrevInt = k + 1;
                    }*/
                    break;
                }
                
            }
            
            if(!intsOfResConstrsJobs.get(j).isEmpty()){
                concatenateIntervals(intsOfResConstrsJobs.get(j));
                if(-intsOfResConstrsJobs.get(j).get(0).get(0) + intsOfResConstrsJobs.get(j).get(0).get(1) >= act.getPeriod()){
                    isFeasible = false;
                }
            }
        } 
        
        // we need to add intervals of the first period to the last job due to the deadline extention
        int nCurJob = nJobs - 1;
        for (int i = 0; i < schedActs.getIntsOfResConstrs().get(nUsedRes).size(); i++) {
            //we add them only till the first period
            if(schedActs.getIntsOfResConstrs().get(nUsedRes).get(i).get(0) > boundsOnJobs[nCurJob][1] % act.getPeriod() + act.getProcTime()){
                break;
            }
            
            intsOfResConstrsJobs.get(nCurJob).add(
                    Arrays.asList(
                            schedActs.getIntsOfResConstrs().get(nUsedRes).get(i).get(0) - act.getProcTime() + act.getHP(),
                            schedActs.getIntsOfResConstrs().get(nUsedRes).get(i).get(1) + act.getHP()
                    )
            ); 
        }
        
        if(!intsOfResConstrsJobs.get(nCurJob).isEmpty()){
            // move start interval to the end if the start of first interval is 
            // negative and it's an activity with 1 job
            if(intsOfResConstrsJobs.get(nCurJob).get(0).get(0) < 0 && nJobs == 1){
                intsOfResConstrsJobs.get(nCurJob).add(
                        Arrays.asList(
                                2 * act.getHP() + intsOfResConstrsJobs.get(nCurJob).get(0).get(0),
                                2 * act.getHP()
                        )
                );
            }
            
            concatenateIntervals(intsOfResConstrsJobs.get(nCurJob));
        }
       
    }

    private void setSchedActsAndPeriodUsingExistingInts(Activity activity, 
            List<List<List<Integer>>> intsOfResConstrsForJobs, int[][] boundsOnJobs) throws IloException{
        int nUsedResource = activity.getAssToRes() - 1;
        
        if(!schedActs.getIntsOfResConstrs().get(nUsedResource).isEmpty()){
            for(int j = 0; j < activity.getNJobs(); j++) {
                if(!intsOfResConstrsForJobs.get(j).isEmpty()){
                    List<List<Integer>> resIntervals = intsOfResConstrsForJobs.get(j);
                    int numJob = j;
                    if(!resIntervals.isEmpty()) {
                        boolean correctIntervalFound = false;
                        while (!correctIntervalFound) {
                            if(resIntervals.size() == 0) {
                                nInfeasActs.add(numJob);
                                isFeasible = false;
                                break;
                            }

                            if(Main.IS_DEBUG_MODE) System.out.println("activity # " + activity.getID() + ", lower bound " + boundsOnJobs[numJob][0] + ", upper bound "+ boundsOnJobs[numJob][1] + ", current interval (" + resIntervals.get(0).get(0) + "," + resIntervals.get(0).get(1) + ")");

                            if (boundsOnJobs[numJob][0] < resIntervals.get(0).get(1) &&
                                    boundsOnJobs[numJob][0] > resIntervals.get(0).get(0)) {
                                boundsOnJobs[numJob][0] = resIntervals.get(0).get(1);
                                break;
                            }

                            if (boundsOnJobs[numJob][0] >= resIntervals.get(0).get(1)) {
                                if(resIntervals.size() == 1 && boundsOnJobs[numJob][1] >= resIntervals.get(0).get(1)) {
                                    break;
                                }
                                resIntervals.remove(0);
                                continue;
                            }

                            if (boundsOnJobs[numJob][0] <= resIntervals.get(0).get(0)) {
                                correctIntervalFound = true;
                            }
                        }
                    }
                }
            }
        }
    }
     
    public void setSTBoundsAndIntsForILPNZJ(Activity activity, IloNumVar[] startTime, int[][] boundsOnJobs,
            List<List<List<Integer>>> intsOfResConstrsJobs) throws IloException{
        for(int i = 0; i < activity.getNJobs(); i++) {
            startTime[i].setLB(boundsOnJobs[i][0]);
            startTime[i].setUB(boundsOnJobs[i][1]);
            
            for(int j = 0; j < intsOfResConstrsJobs.get(i).size(); j++) {
                IloNumVar var1 = solverILP.numVar(0, 1, IloNumVarType.Int);
                solverILP.addGe(
                        solverILP.sum(
                                startTime[i], 
                                solverILP.prod(2 * HP, var1)
                        ), 
                        intsOfResConstrsJobs.get(i).get(j).get(1)
                );
                
                solverILP.addLe(
                        solverILP.sum(
                                startTime[i], 
                                solverILP.prod(2 * HP, var1)
                        ), 
                        intsOfResConstrsJobs.get(i).get(j).get(0) + 2 * HP
                );
                nConstr++;
            }
        }
    }

    private void scheduleAsILP(Activity activity, IloNumVar[] startTime, int[][] boundsOnJobs,
            List<List<List<Integer>>> intsOfResConstrsForJobs) throws IloException{

            for(int k = 1; k < activity.getNJobs(); k++) {
                solverILP.addEq(solverILP.diff(
                        startTime[k],
                        startTime[0]
                        ),
                        k * activity.getPeriod()
                );
            }

            setSTBoundsAndIntsForILPNZJ(activity, startTime, boundsOnJobs, intsOfResConstrsForJobs);
            setPrecedenceConstraintsForJobsOfTheActivityILP(activity, startTime);
    }

    public SubModel(Activity activity_, ScheduledActivities schedActs_) throws IloException{
        isFeasible = true;
        HP = activity_.getHP();
        schedActs = schedActs_;
        activity = activity_;
        nInfeasActs = new ArrayList<>();
        intsOfResConstrsJobs = new ArrayList<>();
        for(int i = 0; i < HP / activity.getPeriod(); i++) {
            intsOfResConstrsJobs.add(new ArrayList<List<Integer>>());
        }
        boundsOnJobs = new int[activity.getHP() / activity.getPeriod()][2];
        
        periodConstraintsSetting(activity, boundsOnJobs);
        setPrecedenceConstraints(activity, boundsOnJobs);
 
        // If the activity has relevant jitter requirement, schedule it with ILP model. Otherwise, schedule as soon as possible.
        obtainResourceIntervals(boundsOnJobs, activity, intsOfResConstrsJobs);
        setSchedActsAndPeriodUsingExistingInts(activity,intsOfResConstrsJobs, boundsOnJobs);
        if(activity.isTask()){
            int nJobsToSchedule = activity.getNJobs();
            for (int i = 0; i < activity.getNJobs(); i++) {
                if(!intsOfResConstrsJobs.get(i).isEmpty() && intsOfResConstrsJobs.get(i).get(0).get(0) < boundsOnJobs[i][0] 
                        && intsOfResConstrsJobs.get(i).get(0).get(1) >= boundsOnJobs[i][1]){
                    isFeasible = false;
                }
            }

            isScheduledByOpt = true;
            solverILP = new IloCplex();

            startTimeILP = solverILP.numVarArray(nJobsToSchedule, 0, activity.getHP() - 1, IloNumVarType.Int);
            scheduleAsILP(activity, startTimeILP, boundsOnJobs, intsOfResConstrsJobs);
            int[] objCoeffs = new int[startTimeILP.length];
            Helpers.InitializeTo(objCoeffs, 1);
            ObjectiveILP = solverILP.addMinimize(solverILP.scalProd(objCoeffs,startTimeILP));
        }

    }

    private int isNumberOutsideIntervals(List<List<Integer>> intsOfResConstrsForJobs, int number){
        for (int i = 0; i < intsOfResConstrsForJobs.size(); i++) {
            if(number > intsOfResConstrsForJobs.get(i).get(0) && number < intsOfResConstrsForJobs.get(i).get(1)){
                return i;
            }
            if(number >= intsOfResConstrsForJobs.get(i).get(1)){
                return -1;
            }
        }
        return -1;
    }
    
    private int setST(int nJob, int endPrevJob) throws IloException {
        int sTime;
        boundsOnJobs[nJob][0] = endPrevJob;
        setSchedActsAndPeriodUsingExistingInts(activity, intsOfResConstrsJobs, boundsOnJobs);
        if(isFeasible) {
            sTime = boundsOnJobs[nJob][0];
        }
        else {
            sTime = boundsOnJobs[nJob][1] + 1;
        }
        
        return sTime;
    }
    
    private double[] solveNZJ() throws IloException {
        double[] startTime = new double [activity.getNJobs()];
        boolean isFirstRound = true;
        for(int j = 0; j < activity.getNJobs(); j++) {
            if(boundsOnJobs[j][0] > boundsOnJobs[j][1]){
                return null;
            }
            
            if(j > 0){
                int curST = (int) Math.max(boundsOnJobs[j][0], startTime[j - 1]);
                int endPrevJob = (int) Math.round(startTime[j - 1]) + activity.getProcTime();
                if(endPrevJob < curST){
                    startTime[j] = curST;
                }
                else{
                    startTime[j] = setST(j, endPrevJob);
                }
            }
            else{
                startTime[j] = boundsOnJobs[j][0];
            }

            if(j == activity.getNJobs() - 1){
                if(startTime[j] + activity.getProcTime() - activity.getHP() > startTime[0]){
                    // the last and the first jobs are colliding
                    // move the first job later and check if it's 
                    // not colliding with the second job
                    return null;
                }
            }

            if(startTime[j] > boundsOnJobs[j][1]){
                return null;
            }
        }
        
        return startTime;
    }
    
    public SolutionOneAct solve() throws IloException{
        // this.ModelToFile(Helpers.outputFileForModels);       
        if(activity.isTask()){
            if(solverILP.solve()){
                SolutionOneAct problemSolutionForOneActivity = new SolutionOneAct(solverILP.getValues(startTimeILP), activity);
                return problemSolutionForOneActivity;
            }
            else{
                return null;
            }
        }
        else{
            double[] sTimes;
            sTimes = solveNZJ();

            if(sTimes != null){
                return new SolutionOneAct(sTimes, activity);
            }
            else{
                return null;
            }
        }
   }
    
    private void setResourceConstraintsForTwoActivitiesNZJILP() throws IloException{
        //set resource constraints 
        for(int j = 0; j < secondActivity.getNJobs(); j++) {
            IloNumVar startTimeSecond = startTimeSecondActivity[j];
            if(activity.getAssToRes() == secondActivity.getAssToRes() 
                    //&& !activity.getAllPredecessors().contains(secondActivity.getIdInArray()) 
                    //   && !activity.getAllSuccessors().contains(secondActivity.getIdInArray())
                    ){
                for(int l = 0; l < activity.getNJobs(); l++) {
                    IloNumVar startTimeFirst = startTimeILP[l];
                    if(!(startTimeSecond.getUB() + secondActivity.getProcTime() < startTimeFirst.getLB() ||
                            startTimeFirst.getUB() + activity.getProcTime() < startTimeSecond.getLB() )){
                        //The domains of two variables are intersecting, the constraint of exclusivness must be created
                        int addedValueToFirst = 0;
                        int bigMCoeff = 1;
                        setResConstrNZJPairJobsILP(activity, secondActivity, startTimeFirst, 
                                startTimeSecond, addedValueToFirst, bigMCoeff);
                    }
                }
                
                int isZJ = 0;
                setResConstrInThe2ndHPILP(activity, secondActivity, startTimeILP, 
                        startTimeSecondActivity, 0, 0, isZJ);
            }
        }
    }

    public void scheduleWithTwoActivities(Activity secondActivity) throws IloException{
        //schedule first activity as ILP if it was not scheduled as such before
        if(!activity.isTask() || !toBeSolvedByILP){
            isScheduledByOpt = true;
            solverILP = new IloCplex();
            startTimeILP = solverILP.numVarArray(activity.getNJobs(), 0, activity.getHP() - 1, IloNumVarType.Int);
            scheduleAsILP(activity, startTimeILP, boundsOnJobs, intsOfResConstrsJobs);
        }
        
        this.secondActivity = secondActivity;
        int numJobsToCreateResourceConstraintsFor = secondActivity.getHP() / secondActivity.getPeriod();
        secondIntervalsOfResourceConstraintsForJobs = new ArrayList<>();
        for(int i = 0; i < numJobsToCreateResourceConstraintsFor; i++) {
            secondIntervalsOfResourceConstraintsForJobs.add(new ArrayList<List<Integer>>());
        }
        secondBoundsOnJobs = new int[numJobsToCreateResourceConstraintsFor][2];
        
        periodConstraintsSetting(secondActivity, secondBoundsOnJobs);
        setPrecedenceConstraints(secondActivity, secondBoundsOnJobs);
        obtainResourceIntervals(secondBoundsOnJobs, secondActivity, secondIntervalsOfResourceConstraintsForJobs);
        
        setSchedActsAndPeriodUsingExistingInts(secondActivity, secondIntervalsOfResourceConstraintsForJobs, secondBoundsOnJobs);
        
        startTimeSecondActivity = solverILP.numVarArray(secondActivity.getNJobs(), 0, secondActivity.getHP() - 1, IloNumVarType.Int);
        scheduleAsILP(secondActivity, startTimeSecondActivity, secondBoundsOnJobs, secondIntervalsOfResourceConstraintsForJobs);
        
        int[] coefficientsForObjectiveFirstStartTimes = new int[startTimeILP.length];
        Helpers.InitializeTo(coefficientsForObjectiveFirstStartTimes, 1);
        int[] coefficientsForObjectiveSecondStartTimes = new int[startTimeSecondActivity.length];
        Helpers.InitializeTo(coefficientsForObjectiveSecondStartTimes, 1);  
        solverILP.remove(ObjectiveILP);
        ObjectiveILP = solverILP.addMinimize(
                solverILP.sum(
                        solverILP.scalProd(
                                coefficientsForObjectiveFirstStartTimes, 
                                startTimeILP
                        ),
                        solverILP.scalProd(
                                coefficientsForObjectiveSecondStartTimes, 
                                startTimeSecondActivity)
                )
        );

         setResourceConstraintsForTwoActivitiesNZJILP();
       // this.ModelToFile(Helpers.outputFileForModels);
    }
    
    public SolutionOneAct[] solveWithTwoActivities() throws IloException{
        SolutionOneAct[] problemSolutionForOneTransaction = new SolutionOneAct[2];
        
        if(solverILP.solve()){
            problemSolutionForOneTransaction[0] = new SolutionOneAct(solverILP.getValues(startTimeILP), activity);
            problemSolutionForOneTransaction[1] = new SolutionOneAct(solverILP.getValues(startTimeSecondActivity), secondActivity);
            return problemSolutionForOneTransaction;
        }
        else{
            return null;
        }
   }
    
    public void end() throws IloException{
       solverILP.end();
   }
    
    public List<Integer> getNumOfInfeasibleActivities() {
        return nInfeasActs;
    }


    protected void setPrecedenceConstraintsForJobsOfTheActivityILP(Activity act, IloNumVar[] startTime) throws IloException{
        for (int i = 0; i < act.getNJobs() - 1; i++) {
            solverILP.addGe(solverILP.diff(
                    startTime[i + 1],
                    startTime[i]
                    ),
                    act.getProcTime()
            );
            nConstr++;
        }

        solverILP.addGe(solverILP.diff(
                startTime[0],
                startTime[act.getNJobs() - 1]
                ),
                act.getProcTime() - act.getHP()
        );
        nConstr++;
    }

    protected void setResConstrNZJPairJobsILP(Activity act1, Activity act2,
                                              IloNumVar curSTFirst, IloNumVar curSTSecond, int addedValueToFirst,
                                              int bigMCoeff) throws IloException{
        IloNumVar var1 = solverILP.numVar(0, 1, IloNumVarType.Int);
        solverILP.addLe(solverILP.sum(curSTFirst,
                solverILP.prod(-1.0, curSTSecond),
                solverILP.prod(-bigMCoeff * HP, var1)
                ),
                -act1.getProcTime() - addedValueToFirst
        );

        solverILP.addLe(solverILP.sum(curSTSecond,
                solverILP.prod(-1.0, curSTFirst),
                solverILP.prod(bigMCoeff * HP, var1)
                ),
                -act2.getProcTime() + bigMCoeff * HP + addedValueToFirst
        );
        nConstr+=2;
    }

    protected void setResConstrZJPairJobsILP(Activity act1, Activity act2,
                                             IloNumVar curSTFirst, IloNumVar curSTSecond, long localHP,
                                             int nPeriodFirst, int nPeriodSecond, int addedValueToFirst,
                                             int bigMCoeff) throws IloException{

        IloNumVar var1 = solverILP.numVar(0, 1, IloNumVarType.Int);
        solverILP.addLe(solverILP.sum(curSTFirst,
                solverILP.prod(-1.0, curSTSecond),
                solverILP.prod(-bigMCoeff * localHP, var1)
                ),
                -act1.getProcTime() - nPeriodFirst * act1.getPeriod()
                        + nPeriodSecond * act2.getPeriod() - addedValueToFirst
        );

        solverILP.addLe(solverILP.sum(curSTSecond,
                solverILP.prod(-1.0, curSTFirst),
                solverILP.prod(bigMCoeff * localHP, var1)
                ),
                -act2.getProcTime() + bigMCoeff * localHP + nPeriodFirst * act1.getPeriod()
                        - nPeriodSecond * act2.getPeriod() + addedValueToFirst
        );
        nConstr+=2;
    }

    protected void setResConstrInThe2ndHPILP(Activity act1, Activity act2, IloNumVar[] startTime1,
                                             IloNumVar[] startTime2, int nFirstJob1, int nFirstJob2, int isZJ) throws IloException{
        int period1 = act1.getPeriod();
        int period2 = act2.getPeriod();
        int nJobs1 = act1.getHP() / period1;
        int nJobs2 = act2.getHP() / period2;

        if(act1.getID() == act2.getID()){
            nJobs1 = 1;
            nJobs2 = 1;
            if(act1.getNJobs() == 1){
                nJobs1 = 0;
                nJobs2 = 0;
            }
        }

        for (int i = 0; i < nJobs1; i++) {
            IloNumVar curSTFirst = startTime1[nFirstJob1 + i * (1 - isZJ)];
            IloNumVar curSTSecond = startTime2[nFirstJob2 + (act2.getNJobs() - 1) * (1 - isZJ)];
            if(curSTSecond.getUB() + (nJobs2 - 1) * period2 * isZJ -
                    act2.getHP() + act2.getProcTime() > curSTFirst.getLB() + i * period1 * isZJ){
                if(isZJ == 1){
                    setResConstrZJPairJobsILP(act1, act2, curSTFirst,
                            curSTSecond, act1.getHP(), i, nJobs2 - 1,
                            act1.getHP(), 2);
                }
                else{
                    setResConstrNZJPairJobsILP(act1, act2, curSTFirst, curSTSecond, act2.getHP(), 2);
                }

            }
        }

        for (int i = 0; i < nJobs2; i++) {
            IloNumVar curSTFirst = startTime2[nFirstJob2 + i * (1 - isZJ)];
            IloNumVar curSTSecond = startTime1[nFirstJob1 + (act1.getNJobs() - 1) * (1 - isZJ)];
            if(!(curSTSecond.getUB() + (nJobs1 - 1) * period1 * isZJ -
                    act1.getHP() + act1.getProcTime() < curSTFirst.getLB() + i * period2 * isZJ)){

                if(isZJ == 1){
                    setResConstrZJPairJobsILP(act2, act1, curSTFirst,
                            curSTSecond, act1.getHP(), i, nJobs1 - 1,
                            act1.getHP(), 2);
                }
                else{
                    setResConstrNZJPairJobsILP(act2, act1, curSTFirst,
                            curSTSecond, act2.getHP(), 2);
                }
            }
        }
    }

    public void printNConstr(){
        System.out.println(nConstr);
    }
}
