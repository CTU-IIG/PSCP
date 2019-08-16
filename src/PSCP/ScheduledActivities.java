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
import java.util.Arrays;
import java.util.List;
import java.util.Collections;

public class ScheduledActivities {
    // Note that it makes sense to schedule by jobs only messages, so tasks are ALWAYS scheduled by activities

    private List<Integer> numSchedActs; // if at least one job of an activity is scheduled, the activity is in this list
    private boolean[][] schedJobsOfActs;
    private int[][] schedule;
    private PredecessorsCausingLateStartTime[] predsCausingLateST;
    private int[][] actST;
    private int nJobsScheduled;
    private int[] maxPredProcTime;
    List<List<List<Integer>>> intsOfResConstrs;
    private ProblemInstance prInst;
    private List<ActivityJob> problematicAct;
    private List<Activity> acts;
    private double objValue;
    private double[] appObjValues;
    private int[] e2eLatenciesOfApps;
    private int[] latestSTOfJobsInPeriod;
    private int HP;

    private class PredecessorsCausingLateStartTime {
        private List<List<Integer>> predList;
        private int[] startTime;
        private int nJobs;

        public PredecessorsCausingLateStartTime(int nJobs_, int period) {
            nJobs = nJobs_;
            predList = new ArrayList<>();
            startTime = new int [nJobs];
            for (int i = 0; i < nJobs; i++) {
                startTime[i] = i * period;
            }

            for (int i = 0; i < nJobs; i++) {
                predList.add(new ArrayList<Integer>());
            }
        }

        public void addPredToPredList(int pred, int nJob) {
            predList.get(nJob).add(pred);
        }

        public void changeStartTime(int startTime_, int nJob) {
            startTime[nJob] = startTime_;
        }

        public int getStartTime(int nJob) {
            return startTime[nJob];
        }

        public List<Integer> getPredList(int nJob) {
            return predList.get(nJob);
        }

        public void clearListOfPreds(int nJob) {
            predList.get(nJob).clear();
        }

        public int getNJobs() {
            return nJobs;
        }

        public void print() {
            System.out.println("\n\n Predecessors of the jobs are: ");
            for (int i = 0; i < predList.size(); i++) {
                System.out.print("Job " + i + ":");
                for (int j = 0; j < predList.get(i).size(); j++) {
                    System.out.print(predList.get(i).get(j) + " ");
                }
                System.out.println();
            }

            System.out.println("Earliest possible start times: ");
            for (int i = 0; i < startTime.length; i++) {
                System.out.println("Job " + i + ": " + startTime[i]);
            }
            System.out.println("\n\n");
        }
    }

    public ScheduledActivities(ProblemInstance prInst_){
        prInst =  prInst_;
        HP = prInst.getHP();
        acts = prInst.getActs();
        int nActs = prInst.getActs().size();
        maxPredProcTime = new int [nActs];
        predsCausingLateST = new PredecessorsCausingLateStartTime[nActs];
        intsOfResConstrs =  new ArrayList<>();
        actST = new int [nActs][];
        for (int i = 0; i < acts.size(); i++) {
            actST[i] = new int[acts.get(i).getNJobs()];
        }

        Helpers.initialize2DArrayWithValue(actST, -1);
        e2eLatenciesOfApps = new int [nActs];
        schedJobsOfActs = new boolean [nActs][];

        for (int i = 0; i < nActs; i++) {
            if(acts.get(i).isTask()) {
                predsCausingLateST[i] = new PredecessorsCausingLateStartTime(1, acts.get(i).getPeriod());
            }
            else {
                predsCausingLateST[i] = new PredecessorsCausingLateStartTime(acts.get(i).getNJobs(), acts.get(i).getPeriod());
            }
        }

        for(int k = 0; k < prInst.getnResources(); k++) {
            intsOfResConstrs.add(new ArrayList<List<Integer>>());
        }

        numSchedActs = new ArrayList();
        nJobsScheduled = 0;
        latestSTOfJobsInPeriod = new int [nActs];

        schedule = new int[acts.get(0).getnResources()][HP];
        for (int i = 0; i < acts.get(0).getnResources(); i++) {
            for (int j = 0; j < HP; j++) {
                schedule[i][j] = -1;
            }
        }
    }

    private void setEarliestSuccessorsSTForTask(int startTime, Activity act) {
        latestSTOfJobsInPeriod[act.getID()] = startTime;

        for (int i = 0; i < act.getDirectSucc().size(); i++) {
            int nSucc = act.getDirectSucc().get(i);

            for (int j = 0; j < predsCausingLateST[nSucc].getNJobs(); j++) {
                if(predsCausingLateST[nSucc].getStartTime(j) < startTime + act.getProcTime() + j * act.getPeriod()) {
                    predsCausingLateST[nSucc].changeStartTime(startTime + act.getProcTime() + j * act.getPeriod(), j);
                    predsCausingLateST[nSucc].clearListOfPreds(j);
                }

                if(predsCausingLateST[nSucc].getStartTime(j) == startTime + act.getProcTime() + j * act.getPeriod()) {
                    predsCausingLateST[nSucc].addPredToPredList(act.getID(), j);
                }
            }
        }
    }

    private void setEarliestSuccessorsSTForMessage(int startTime, Activity mes, int nJobCurMes) {
        latestSTOfJobsInPeriod[mes.getID()] = Math.max(latestSTOfJobsInPeriod[mes.getID()], startTime - nJobCurMes * mes.getPeriod());

        for (int i = 0; i < mes.getDirectSucc().size(); i++) {
            int nSucc = mes.getDirectSucc().get(i);

            int nJobSucc = acts.get(nSucc).isTask() ? 0 : nJobCurMes;
            int stCurMes = acts.get(nSucc).isTask() ? startTime + mes.getProcTime() - nJobCurMes * mes.getPeriod()
                    : startTime + mes.getProcTime();

            if(predsCausingLateST[nSucc].getStartTime(nJobSucc) < stCurMes) {
                predsCausingLateST[nSucc].changeStartTime(stCurMes, nJobSucc);
                predsCausingLateST[nSucc].clearListOfPreds(nJobSucc);
            }

            if(predsCausingLateST[nSucc].getStartTime(nJobSucc) == stCurMes &&
                    !predsCausingLateST[nSucc].getPredList(nJobSucc).contains(mes.getID())) {
                predsCausingLateST[nSucc].addPredToPredList(mes.getID(), nJobSucc);
            }
        }
    }

    private void addScheduledJob(int startTime, Activity act, int nJob) {
        actST[act.getID()][nJob] = startTime;
        nJobsScheduled++;

        if(schedJobsOfActs[act.getID()] == null) {
            schedJobsOfActs[act.getID()] = new boolean [act.getNJobs()];
        }
        schedJobsOfActs[act.getID()][nJob] = true;

        for (int i = 0; i < act.getProcTime(); i++) {
            if(schedule[act.getAssToRes() - 1][(startTime + i) % HP] == -1) {
                schedule[act.getAssToRes() - 1][(startTime + i) % HP] = act.getID();
            }
            else {
                System.out.println("Problem, problem! Trying to schedule job to already occupied place!");
                System.exit(0);
            }
        }

        addNewResourceIntervalsWithConcatenation(startTime, act, nJob);
    }

    private boolean addScheduledTask(int startTime, int nAct){
        Activity task = acts.get(nAct);
        numSchedActs.add(nAct);

        for(int i = 0; i < task.getNJobs(); i++) {
            addScheduledJob(startTime + i * task.getPeriod(), task, i);
        }

        setEarliestSuccessorsSTForTask(startTime, task);

        return true;
    }

    private boolean addScheduledMessageJob(int startTime, int nMes, int nJob){
        Activity mes = acts.get(nMes);

        if(schedJobsOfActs[nMes] == null) {
            numSchedActs.add(nMes);
        }

        addScheduledJob(startTime, mes, nJob);
        setEarliestSuccessorsSTForMessage(startTime, mes, nJob);

        return true;
    }

    private int getEarliestSTDueToPrecedenceConstraints(Activity curAct, ReasonOfDelay reason){
        return getEarliestSTDueToPrecedenceConstraints(curAct, reason, 0);
    }

    // precedence relations are always with the same job by definition
    private int getEarliestSTDueToPrecedenceConstraints(Activity curAct, ReasonOfDelay reason, int nJob){
        int nCurAct = curAct.getID();
        maxPredProcTime[nCurAct] = 0;

        int numPredCausingLateST = predsCausingLateST[curAct.getID()].getPredList(nJob).size();
        if(numPredCausingLateST > 0){
            reason.setDelay("Prec");
            reason.setDelayActivities(prInst, predsCausingLateST[curAct.getID()].getPredList(nJob), nJob);
            reason.setLatestFTofPred(predsCausingLateST[curAct.getID()].getStartTime(nJob));

            for (int i = 0; i < numPredCausingLateST; i++) {
                Activity actPred = acts.get(predsCausingLateST[curAct.getID()].getPredList(nJob).get(i));

                if(maxPredProcTime[nCurAct] < actPred.getProcTime()){
                    maxPredProcTime[nCurAct] = actPred.getProcTime();
                }
            }
        }

        return predsCausingLateST[curAct.getID()].getStartTime(nJob);
    }

    private ActivityJob getPredecessorOnResourceOfTheReasonAct(Activity reasonAct, String reas){
        ActivityJob result = findElemFinishingAtTime(actST[reasonAct.getID()][0], reasonAct);
        if(result.getNAct() < 0){
            System.out.println("I've reached presumably inaccessible code in getPredecessorOnResourceOfTheReasonAct()!");
            System.exit(1);
        }

        return result;
    }

    private ActivityJob findElemFinishingAtTime(int finishTime, Activity act) {
        if (schedule[act.getAssToRes() - 1][Math.floorMod(finishTime - 1, HP)] != -1) {
            Activity actAtTime = acts.get(schedule[act.getAssToRes() - 1][(finishTime - 1) % HP]);
            for (int i = 0; i < actAtTime.getNJobs(); i++) {
                if (isSchedJobOfAct(actAtTime.getID(), i)) {
                    if (twoIntervalsIntersectCyclically(
                            finishTime - 1,
                            finishTime,
                            actST[actAtTime.getID()][i],
                            actST[actAtTime.getID()][i] + actAtTime.getProcTime(),
                            HP)
                            ) {
                        return new ActivityJob(actAtTime, i);
                    }
                }
            }
        }

        if(act.isTask()) {
            for (int j = 0; j < numSchedActs.size(); j++) {
                Activity schedAct = acts.get(numSchedActs.get(j));
                if (schedAct.isTask() && schedAct.getAssToRes() == act.getAssToRes()) {
                    int gcd = prInst.getPairwiseGCD(act.getPeriod(), schedAct.getPeriod());
                    if (twoIntervalsIntersectCyclically(
                            actST[schedAct.getID()][0],
                            actST[schedAct.getID()][0] + schedAct.getProcTime(),
                            finishTime - 1,
                            finishTime,
                            gcd
                    )) {
                        return new ActivityJob(schedAct, true);
                    }
                }
            }
        }

        System.out.println("Cannot find element finished at time " + finishTime + ".");
        System.exit(0);

        return new ActivityJob(null, -1);
    }

    public boolean AddScheduledActivity(int[] startTimes_, int noOfScheduledActivity){
        nJobsScheduled += startTimes_.length;
        numSchedActs.add(noOfScheduledActivity);

        actST[noOfScheduledActivity] = new int[acts.get(noOfScheduledActivity).getNJobs()];
        for(int i = 0; i < acts.get(noOfScheduledActivity).getNJobs(); i++){
            actST[noOfScheduledActivity][i] = startTimes_[i];
        }
        AddNewResourceIntervalsWithConcatenation(startTimes_, noOfScheduledActivity);

        return true;
    }

    private void AddNewResourceIntervalsWithConcatenation(int[] startTimes, int nSchedAct){
        Activity act = prInst.getActs().get(nSchedAct);
        int nJobs = act.getNJobs();

        for(int j = 0; j < nJobs; j++) {
            for(int i = 0; i < act.getProcTime(); i++) {
                if(schedule[act.getAssToRes() - 1][(startTimes[j] + i) % HP] != -1){
                    System.out.println(schedule[act.getAssToRes() - 1][(startTimes[j] + i) % HP]);
                    // collision in the schedule
                    System.out.println("\n Collision in the schedule! Activities " +
                            nSchedAct + " and " + (schedule[act.getAssToRes() - 1][(startTimes[j] + i) % HP] - 1) + " are colliding.\n");
                    System.out.println((startTimes[j] + i) % HP);
                    System.exit(0);
                }

                schedule[act.getAssToRes() - 1][(startTimes[j] + i) % HP] = act.getID() + 1;
            }

            List<Integer> newInterval;
            if(startTimes[j] < HP && startTimes[j] + act.getProcTime() > HP){
                newInterval = Arrays.asList(startTimes[j], HP);
                ConcatenateNewIntervalWithExisting(newInterval,  act.getAssToRes());

                newInterval = Arrays.asList(0, (startTimes[j] + act.getProcTime()) % HP);
                ConcatenateNewIntervalWithExisting(newInterval, act.getAssToRes());
            }
            else{
                newInterval = Arrays.asList(startTimes[j] % HP,
                        (startTimes[j] + act.getProcTime()) % HP);
                if(startTimes[j] + act.getProcTime() == HP){
                    newInterval.set(1, HP);
                }

                ConcatenateNewIntervalWithExisting(newInterval, act.getAssToRes());
            }
        }
    }

    // output is index in the startTimes array
    public int getScheduledActivityToUnschedule(int usedResource, List<Integer> scheduledFromScratch,
                                                List<Integer> allPredecessorsOfCurSchedAct, List<Integer> numRootProblems){
        int output = -1;
        int maxNInstToSched = 0;

        for(int i = 0; i < numSchedActs.size(); i++){
            // First of all, the unscheduled activity must be located on the same resource.
            if(usedResource == prInst.getActs().get(numSchedActs.get(i)).getAssToRes() &&
                    // Thirdly, it does not make sense to unschedule predecessor of the currently scheduled activity -
                    // - actually, sometimes it is necessary to schedule the DAG from scratch
                    !allPredecessorsOfCurSchedAct.contains(numSchedActs.get(i)) &&
                    // Finally, we try to unschedule the activity with maximum possible moments to schedule
                    maxNInstToSched <= prInst.getActs().get(numSchedActs.get(i)).getSlack()
                //&& !scheduledFromScratch.contains(numbersOfScheduledActivity.get(i))
                    ){

                // Unschedule activities with no scheduled successors first
                boolean hasScheduledSuccessors = false;
                for(int j = 0; j < numSchedActs.size(); j++) {
                    if(prInst.getActs().get(numSchedActs.get(i)).getAllSucc().contains(numSchedActs.get(j))){
                        hasScheduledSuccessors = true;
                        break;
                    }
                }

                if(hasScheduledSuccessors){
                    continue;
                }
                maxNInstToSched =
                        prInst.getActs().get(numSchedActs.get(i)).getSlack();
                output = numSchedActs.get(i);;
            }
        }

        // first we relax scheduled successors requirements
        int numSucToUnschedule = prInst.getActs().size();
        maxNInstToSched = 0;
        if(output == -1){
            for(int i = 0; i < numSchedActs.size(); i++){
                List<Integer> succs = prInst.getActs().get(numSchedActs.get(i)).getAllSucc();
                int numSucToUnscheduleForAct = 0;
                for(int j = 0; j < succs.size(); j++) {
                    if(numSchedActs.contains(succs.get(j))){
                        numSucToUnscheduleForAct++;
                    }
                }

                if(usedResource == prInst.getActs().get(numSchedActs.get(i)).getAssToRes() &&
                        numSucToUnschedule >= numSucToUnscheduleForAct &&
                        !allPredecessorsOfCurSchedAct.contains(numSchedActs.get(i))
                        ){

                    for(int j = 0; j < succs.size(); j++) {
                        // if successor of this activity is hard to schedule (in numRootProblems) and already scheduled,
                        // try not to unschedule its predecessors
                        if(numSchedActs.contains(succs.get(j)) &&
                                numRootProblems.contains(succs.get(j))){
                            continue;
                        }
                    }

                    numSucToUnschedule = numSucToUnscheduleForAct;
                    output = numSchedActs.get(i);
                }
            }
        }

        //second we relax all requirements, but try to choose the activity with the largest jitter requirement
        maxNInstToSched = 0;
        if(output == -1){
            for(int i = 0; i < numSchedActs.size(); i++){
                if(usedResource == prInst.getActs().get(numSchedActs.get(i)).getAssToRes() &&
                        !allPredecessorsOfCurSchedAct.contains(numSchedActs.get(i))
                        ){

                    if(prInst.getActs().get(numSchedActs.get(i)).getSlack() >
                                    maxNInstToSched){
                        maxNInstToSched =
                                prInst.getActs().get(numSchedActs.get(i)).getSlack();
                        output = numSchedActs.get(i);
                    }
                }
            }
        }

        return output;
    }

    private List<ActivityJob> findElemsRunAtInterval(int startTime, int finishTime, Activity act) {
        List<ActivityJob> output = new ArrayList<>();
        int nResource = act.getAssToRes() - 1;

        if (!act.isTask()) {
            for (int i = startTime; i < finishTime; i++) {
                if (schedule[nResource][i % HP] != -1 &&
                        (i == startTime || schedule[nResource][i % HP] != schedule[nResource][Math.floorMod(i - 1, HP)])) {
                    // we want to add every activity running in [startTime, finishTime)
                    Activity actAtTime = acts.get(schedule[nResource][i % HP]);
                    for (int j = 0; j < actAtTime.getNJobs(); j++) {
                        if (isSchedJobOfAct(actAtTime.getID(), j)) {
                            if (twoIntervalsIntersectCyclically(
                                    i,
                                    i + 1,
                                    actST[actAtTime.getID()][j],
                                    actST[actAtTime.getID()][j] + actAtTime.getProcTime(),
                                    HP)
                                    ) {
                                output.add(new ActivityJob(actAtTime, j));
                                break;
                            }
                        }
                    }
                }
            }
        }
        else {
            for (int j = 0; j < numSchedActs.size(); j++) {
                Activity schedAct = acts.get(numSchedActs.get(j));
                 if(schedAct.getAssToRes() == act.getAssToRes()) {
                    int gcd = prInst.getPairwiseGCD(act.getPeriod(), schedAct.getPeriod());
                    if (twoIntervalsIntersectCyclically(
                            actST[schedAct.getID()][0],
                            actST[schedAct.getID()][0] + schedAct.getProcTime(),
                            startTime,
                            finishTime,
                            gcd
                    )) {
                        output.add(new ActivityJob(schedAct, true));
                    }
                }
            }
        }

        if(output.isEmpty()) {
            System.out.println("Cannot find elements running in interval (" + startTime + ", " + finishTime + ").");
            for (int i = startTime; i < finishTime; i++) {
                System.out.println("At time " + i + " activity " + schedule[nResource][i % HP] + " is scheduled");
            }
            //System.exit(0);
        }

        return output;
    }

    private int findEarliestSchedTimeOnResource(ReasonOfDelay reason, List<List<Integer>> intsOfResConstrsOneRes,
                                                Activity curAct, int earPredTime, int nJob, String reas, int etPrevJob) {
        return findEarliestSchedTimeOnResource(reason, intsOfResConstrsOneRes, curAct, earPredTime, nJob, reas, etPrevJob,
                -1);
    }

    private void addResIntsForNonHarmonic(Activity actToBeSched, Activity actScheduled, int startTime,
                                          int startFeasInt, int endFeasInt, List<List<Integer>> intsOfResConstrsOneRes){

        int gcd = prInst.getPairwiseGCD(actToBeSched.getPeriod(), actScheduled.getPeriod());
        int stGCDi = startTime % gcd;
        int minK = (int) Math.ceil((startFeasInt - (stGCDi + actScheduled.getProcTime())) * 1.0 / gcd);
        int maxK = (int) Math.floor((endFeasInt - stGCDi) / gcd);
        for (int k = minK; k <= maxK; k++) {
            int curST = stGCDi + k * gcd;
            addIntervalToIntsOfResConstr(curST, curST + actScheduled.getProcTime(),
                    intsOfResConstrsOneRes, actToBeSched.getPeriod());
        }
    }

    private void addResIntsHarmonic(Activity alreadySchedAct, int stIInPeriodJ,
                                    List<List<Integer>> intsOfResConstrsLocal, Activity curSchedAct){
        // if s_i > a_j.getPeriod(), add s_i % p_j to resource intervals - harmonic case
        int etIInPeriodJ = stIInPeriodJ + alreadySchedAct.getProcTime();
        for (int j = 0; j < curSchedAct.getNJobs(); j++) {
            addIntervalToIntsOfResConstr(stIInPeriodJ + j * curSchedAct.getPeriod(),
                    etIInPeriodJ + j * curSchedAct.getPeriod(), intsOfResConstrsLocal, curSchedAct.getPeriod());
        }
    }

    private void getResourceIntsDueToZJ(Activity curAct, List<List<Integer>> intsOfResConstrsLocal){
        int startFeasInt = 0;
        int endFeasInt = curAct.getEndOfFeasInt(acts.size());

        // go through all activities that are scheduled on the same resource
        for (int i = 0; i < numSchedActs.size(); i++) {
            // since we go only over tasks, we do not need to check whether or not the job is scheduled
            Activity alreadySchedAct = acts.get(numSchedActs.get(i));
            if(alreadySchedAct.getAssToRes() == curAct.getAssToRes()){
                // a_j is curAct and a_i is already scheduled activity
                int stSchedAct = actST[alreadySchedAct.getID()][0];
                int stIInPeriodI = stSchedAct % alreadySchedAct.getPeriod();
                int stIInPeriodJ = stSchedAct % curAct.getPeriod();
                int moduloDividing = Math.max(alreadySchedAct.getPeriod(), curAct.getPeriod())
                        % Math.min(alreadySchedAct.getPeriod(), curAct.getPeriod());
                if(moduloDividing == 0 && alreadySchedAct.getPeriod() > curAct.getPeriod()){
                    addResIntsHarmonic(alreadySchedAct, stIInPeriodJ, intsOfResConstrsLocal, curAct);
                }

                // if p_i and p_j are not harmonic, start with interval
                if(moduloDividing != 0){
                    addResIntsForNonHarmonic(curAct, alreadySchedAct, stIInPeriodI,
                            startFeasInt, endFeasInt, intsOfResConstrsLocal);
                }
            }
        }
    }

    // We need this function to handle the situation when due to ZJ constraints intervals in the next hyper-period are added
    // and some intervals from the first HP are mising, which causes collision in the final schedule
    private void addResIntsFromTheNextHPIfNecessary(List<List<Integer>> intsOfResConstrsZJ){
        for (int i = 1; i < intsOfResConstrsZJ.size(); i++) {
            if(intsOfResConstrsZJ.get(i).get(0) >= HP){
                int endThisInterval = intsOfResConstrsZJ.get(i).get(1);
                for (int j = 0; j < i; j++) {
                    int startCurrentIntervalNextHP = intsOfResConstrsZJ.get(j).get(0) + HP;
                    int endCurrentIntervalNextHP = intsOfResConstrsZJ.get(j).get(1) + HP;

                    // if the current interval in the next hyper-period (j) starts after ending of the initial
                    // current interval (i), go to the next interval (i + 1)
                    if(startCurrentIntervalNextHP >= endThisInterval){
                        break;
                    }
                    else{
                        concatenateNewIntervalWithExisting(Arrays.asList(startCurrentIntervalNextHP,
                                endCurrentIntervalNextHP), intsOfResConstrsZJ);
                    }
                }
            }
        }
    }

    // returns true if ZJ is the reason of lateness and false otherwise
    private int findEarliestSTDueToZJ(Activity curAct, int earliestTimeResOrPred, ReasonOfDelay reason){
        int nRes = curAct.getAssToRes() - 1;
        int earliestST = earliestTimeResOrPred;

        List<List<Integer>> intsOfResConstrsZJ = new ArrayList<>();
        getResourceIntsDueToZJ(curAct, intsOfResConstrsZJ);

        // continue to find earliest start time add resource intervals to local that lie in interval [a,b]
        for (int i = 0; i < intsOfResConstrs.get(nRes).size(); i++) {
            concatenateNewIntervalWithExisting(intsOfResConstrs.get(nRes).get(i), intsOfResConstrsZJ);
        }

        if(intsOfResConstrsZJ.isEmpty()){
            // if the resource is free, we return current earliest predecessor start time
            return earliestTimeResOrPred;
        }

        addResIntsFromTheNextHPIfNecessary(intsOfResConstrsZJ);

        int nJob = 0;
        earliestST = findEarliestSchedTimeOnResource(reason, intsOfResConstrsZJ,
                curAct, earliestTimeResOrPred, nJob, "ZJ", 0);

        return earliestST;
    }

    private void printStatRes(Activity curAct, int earPredTime) {
        Helpers.printEmptyLines(2);
        System.out.println("-----------------------------------------------------------------");

        // print info if the reason is resource constraints
        System.out.println();
        System.out.println("Currently scheduled activity is " + curAct.getID());
        System.out.println("Earliest start time due to precedence constraints is " + earPredTime);
        System.out.println("Processing time of this activity is "+ curAct.getProcTime());

        System.out.println("Resource intervals before scheduling the activity are:");
        printResourceIntervals(intsOfResConstrs.get(curAct.getAssToRes() - 1));
    }

    private List<ActivityJob> getDelaysOfRootActivities(List<Activity> rootInApp) {
        List<ActivityJob> output = new ArrayList(rootInApp.size());
        for (int i = 0; i < rootInApp.size(); i++) {
            output.addAll(rootInApp.get(i).getDelay(0).getDelayActs());
        }
        return output;
    }

    private List<List<Integer>> getMKthIntsAfterGivenTime(int time, List<List<Integer>> intsOfResConstrsOneRes,
                                                          int K, int M) {
        List<List<Integer>> startsAndFinishesOfIntervals = new ArrayList<>();
        int numOfHP = (int) Math.floor(time / HP);
        for (int i = 0; i < intsOfResConstrsOneRes.size(); i++) {
            int numInts = intsOfResConstrsOneRes.size();
            int endOfPreviousInt = ((i > 0) ? intsOfResConstrsOneRes.get(i - 1).get(0) + numOfHP * HP :
                    (numOfHP == 0) ? 0 : intsOfResConstrsOneRes.get(numInts - 1).get(1) + (numOfHP - 1) * HP);
            int endOfCurrentInt = intsOfResConstrsOneRes.get(i).get(1) + numOfHP * HP;
            if (time < endOfCurrentInt && time >= endOfPreviousInt) {
                // time is in [startOfCurrentInt, endOfCurrentInt]
                for (int j = K; j < K + M; j++) {
                    int beginning = time;
                    int numOfHPcur = numOfHP + (int) Math.floor(i + j / intsOfResConstrsOneRes.size());
                    int end = intsOfResConstrsOneRes.get((i + j) % intsOfResConstrsOneRes.size()).get(1) +
                            numOfHPcur * HP;
                    if (j > 1) {
                        beginning = intsOfResConstrsOneRes.get((i + j) % intsOfResConstrsOneRes.size()).get(0) +
                                numOfHPcur * HP;
                    }

                    startsAndFinishesOfIntervals.add(new ArrayList<>(Arrays.asList(beginning, end)));
                }
            }

            if (startsAndFinishesOfIntervals.isEmpty() && i == intsOfResConstrsOneRes.size() - 1) {
                numOfHP++;
                i = -1;
            }
        }


        if(startsAndFinishesOfIntervals.isEmpty()) {
            System.out.println("Houston, problem while finding delay element in getMKthIntsAfterGivenTime!");
            System.out.println();
            System.exit(0);
        }

        return startsAndFinishesOfIntervals;
    }

    private void setDelayElementROrZJConstrInfeasible(ReasonOfDelay delay, int earSTAfterPred,
                                                      Activity act, List<List<Integer>> intsOfResConstrsOneRes) {
        delay.setDelay("Infeasible");
        List<ActivityJob> delayElements = new ArrayList<>();
        switch(Main.WAY_TO_SET_DELAY_ELEMENT_INFEASIBLE) {
            case 0:
                // set to elements (or their gcd projection) scheduled at time [earSTAfterPred, earSTAfterPred + procTime]
                delayElements =
                        findElemsRunAtInterval(earSTAfterPred, earSTAfterPred + act.getProcTime(), act);
                break;
            case 1:
                // set to the elements in the first interval after earSTAfterPred
                List<List<Integer>> ints = getMKthIntsAfterGivenTime(earSTAfterPred, intsOfResConstrsOneRes,1, 1);
                // there is always one element in ints in this case as M = 1
                delayElements.addAll(findElemsRunAtInterval(ints.get(0).get(0), ints.get(0).get(1), act));
                break;
            case 2:
                // set to the elements in the m intervals starting from the k-th interval after earSTAfterPred
                ints = getMKthIntsAfterGivenTime(earSTAfterPred, intsOfResConstrsOneRes,
                        Main.NUMBER_INTERVAL_TO_SET_DELAY_ELEMENT, Main.AMOUNT_INTERVALS_TO_SET_DELAY_ELEMENT);
                for (int i = 0; i < ints.size(); i++) {
                    delayElements.addAll(findElemsRunAtInterval(ints.get(i).get(0), ints.get(i).get(1), act));
                }
                break;
            default:
                throw new AssertionError();
        }

        delay.setDelayActivities(delayElements);
    }

    private int getEarliestSTOfRootActivitiesOfApp(int nApp) {
        int out = Integer.MAX_VALUE;
        for (int i = 0; i < prInst.getApps().get(nApp).getRootActs().size(); i++) {
            int nAct = prInst.getApps().get(nApp).getRootActs().get(i).getID();
            if(out > actST[nAct][0]) {
                out = actST[nAct][0];
            }
        }
        return out;
    }

    //---------------------------------------------------- Core --------------------------------------------------------
    private int findEarliestSchedTimeOnResource(ReasonOfDelay delay, List<List<Integer>> intsOfResConstrsOneRes,
                                                Activity act, int earliestSTAfterPredecessor, int nJob, String reas,
                                                int finishTimePreviousJob, int startTimeNextJob){
        if(intsOfResConstrsOneRes.isEmpty()){         // resource is free
            return earliestSTAfterPredecessor;
        }

        // if job nJob-1 has not finished yet at earliestSTAfterPredecessor, change earliest possible start
        // time to finish time of the job nJob-1
        if(finishTimePreviousJob > earliestSTAfterPredecessor){
            earliestSTAfterPredecessor = finishTimePreviousJob;
        }

        int startTime = -1;
        List<ActivityJob> predActJob = new ArrayList<ActivityJob>();
        boolean isEarlPredSTChanged = false;
        int currentStartTime = earliestSTAfterPredecessor;
        int endOfFeasibleInt = nJob * act.getPeriod() + getEarliestSTOfRootActivitiesOfApp(act.getAssToApp() - 1)
                + act.getE2eLatBound() - act.getCriticalLengthAfter();


        if(startTimeNextJob != -1 &&
                earliestSTAfterPredecessor + act.getProcTime() > startTimeNextJob) {
            // cannot be scheduled due to next job
            delay.setDelay("Infeasible");
            delay.setDelayActivities(new ActivityJob(act, nJob+1));
            return -1;
        }

        if(startTimeNextJob >= 0  && endOfFeasibleInt > startTimeNextJob - act.getProcTime()) {
            endOfFeasibleInt = startTimeNextJob - act.getProcTime(); // if job nJob+1 is already scheduled, we schedule
            // this job before
        }

        int numOfHP = (int) Math.floor(earliestSTAfterPredecessor / HP);

        if(earliestSTAfterPredecessor > endOfFeasibleInt) {
            setDelayElementROrZJConstrInfeasible(delay, earliestSTAfterPredecessor, act, intsOfResConstrsOneRes);
            startTime = -1;
        }
        else {
            // find interval to which earliest predecessor time belongs
            for (int i = 0; i < intsOfResConstrsOneRes.size(); i++) {
                int startOfCurrentInt = intsOfResConstrsOneRes.get(i).get(0) + numOfHP * HP;
                int endOfCurrentInt = intsOfResConstrsOneRes.get(i).get(1) + numOfHP * HP;
                // since intervals are sorted from left to right, we can just check whether our value is
                // before end of the current interval
                if (currentStartTime < endOfCurrentInt) {
                    if (currentStartTime + act.getProcTime() <= startOfCurrentInt) {
                        // there is a place to schedule element at currentStartTime
                        startTime = currentStartTime;
                        if (isEarlPredSTChanged) {
                            // element is not scheduled at earliestSTAfterPredecessor, add new delay activities and new reason
                            delay.setDelay(reas);
                            delay.setDelayActivities(findElemsRunAtInterval(earliestSTAfterPredecessor,
                                    earliestSTAfterPredecessor + act.getProcTime(), act));
                        }
                        break;
                    } else {
                        // element does not fit to this interval, go to the next interval,
                        currentStartTime = endOfCurrentInt;
                        if (!isEarlPredSTChanged) {
                            isEarlPredSTChanged = true;
                        }
                    }
                }

                // it is after deadline, does not make sense to continue
                if (endOfCurrentInt > endOfFeasibleInt) {
                    setDelayElementROrZJConstrInfeasible(delay, earliestSTAfterPredecessor, act, intsOfResConstrsOneRes);
                    startTime = -1;
                    break;
                }

                if (i == intsOfResConstrsOneRes.size() - 1) {
                    int localEndOfFeasInt = (numOfHP + 1) * HP;

                    if (localEndOfFeasInt - currentStartTime >= act.getProcTime()) {
                        // we can schedule it - there is enough free time at the end
                        startTime = currentStartTime;
                        if (!isEarlPredSTChanged) {
                            return startTime;
                        }
                    } else {
                        numOfHP++;
                        i = -1;
                    }
                }
            }
        }

        return startTime;
    }

    public ReasonOfDelay scheduleTaskAndReturnReason(Activity task){
        ReasonOfDelay reason = new ReasonOfDelay();

        int earPredTime = getEarliestSTDueToPrecedenceConstraints(task, reason, 0);
        int earliestSTResConstr = findEarliestSchedTimeOnResource(reason, intsOfResConstrs.get(task.getAssToRes() - 1),
                task, earPredTime, 0, "Resource", 0);

        if(Main.IS_DEBUG_MODE){
            printStatRes(task, earPredTime);
        }

        if(earliestSTResConstr == -1){
            return reason;
        }

        int earliestSTDueToZJ = findEarliestSTDueToZJ(task, earliestSTResConstr, reason);
        if(reason.getDelay() == "Infeasible"){
            return reason;
        }

        //<editor-fold defaultstate="collapsed" desc="comment">
        if(reason.getDelay() == "ZJ" && Main.IS_DEBUG_MODE){
            System.out.println("");
            System.out.println("Earliest time due to ZJ constraints is " + earliestSTDueToZJ);
            System.out.println("Period of the currently scheduled activity is " + task.getPeriod());
            System.out.println("Period of the activity that is the reason of lateness is "
                    + acts.get(reason.getDelayActs().get(0).getNAct()).getPeriod());

            for (int i = 0; i < numSchedActs.size(); i++) {
                Activity act = acts.get(numSchedActs.get(i));
                if(numSchedActs.get(i) == acts.get(reason.getDelayActs().get(0).getNAct()).getID()){
                    System.out.println("Start time of the reason activity is " + actST[act.getID()][0]);
                }
            }
            System.out.println("");
        }
        //</editor-fold>

        addScheduledTask(earliestSTDueToZJ, task.getID());

        return reason;
    }

    public ReasonOfDelay scheduleEntireMessageAndReturnReason(Activity mes) {
        ReasonOfDelay reason = new ReasonOfDelay();

        int earPredTime = getEarliestSTDueToPrecedenceConstraints(mes, reason);

        int[] stMessages = new int [mes.getNJobs()];
        for (int i = 0; i < mes.getNJobs(); i++) {
            int predEndTime = (i > 0) ? stMessages[i - 1] + mes.getProcTime() : -1;
            int stSuccJob = (i == mes.getNJobs() - 1 && mes.getNJobs() != 1) ? stMessages[0] + HP : -1;
            stMessages[i] = findEarliestSchedTimeOnResource(reason, intsOfResConstrs.get(mes.getAssToRes() - 1),
                    mes, earPredTime + i * mes.getPeriod(), i, "Resource", predEndTime, stSuccJob);

            if(stMessages[i] == -1){
                return reason;
            }

            addScheduledMessageJob(stMessages[i], mes.getID(), i);
        }

        //<editor-fold defaultstate="collapsed" desc="comment">
        if(reason.getDelay() == "Resource" && Main.IS_DEBUG_MODE){
            printStatRes(mes, earPredTime);
        }
        //</editor-fold>

        return reason;
    }

    public ReasonOfDelay scheduleMessageJobAndReturnReason(Activity mes, int nJob){
        int idMes = mes.getID();

        ReasonOfDelay delay = new ReasonOfDelay();
        int earPredTime = getEarliestSTDueToPrecedenceConstraints(mes, delay, nJob);

        int predEndTime = (nJob > 0 && schedJobsOfActs[idMes] != null && schedJobsOfActs[idMes][nJob - 1])
                ? actST[idMes][nJob - 1] + mes.getProcTime() : -1;

        int stSuccJob = (
                schedJobsOfActs[idMes] != null
                        && nJob < mes.getNJobs() - 1
                        && schedJobsOfActs[idMes][nJob + 1]
        )
                ? actST[idMes][nJob + 1] : -1;

        int earliestSTResConstr = findEarliestSchedTimeOnResource(
                delay,
                intsOfResConstrs.get(mes.getAssToRes() - 1),
                mes,
                earPredTime,
                nJob,
                "Resource",
                predEndTime,
                stSuccJob
        );

        if(Main.IS_DEBUG_MODE){
            printStatRes(mes, earPredTime);
        }

        if(delay.getDelay() == "Infeasible") {
            return delay;
        }

        addScheduledMessageJob(earliestSTResConstr, mes.getID(), nJob);

        return delay;
    }

    public void scheduleAndSetDelay(ActivityJob curAct) {
        if(curAct.getAct().isTask()) {
            curAct.getAct().addDelay(scheduleTaskAndReturnReason(curAct.getAct()), 0);
        }
        else {
            if(!curAct.scheduleFullAct()) {
                curAct.getAct().addDelay(scheduleMessageJobAndReturnReason(curAct.getAct(), curAct.getnJob()), curAct.getnJob());
            }
            else {
                for (int i = 0; i < curAct.getAct().getNJobs(); i++) {
                    curAct.getAct().addDelay(scheduleMessageJobAndReturnReason(curAct.getAct(), i), i);
                }
            }
        }
    }

    private void printStartTimesOfPredActs(Activity act, int nJob) {
        for (int i = 0; i < act.getDirectPreds().size(); i++) {
            int idOfPred = act.getDirectPreds().get(i);
            System.out.println("End time of predecessor with ID " + idOfPred + " is "
                    + (actST[idOfPred][nJob] + acts.get(idOfPred).getProcTime()));
        }
    }

    //-------------------------------------------------- Objective -----------------------------------------------------
    public double computeObjectiveSum(){
        int nApps = prInst.getApps().size();
        appObjValues = new double [nApps];
        int nPoints = prInst.getApps().get(0).getDelays().length;
        objValue = 0;
        for (int i = 0; i < nApps; i++) {
            App curApp = prInst.getApps().get(i);

            if(!curApp.isOrderCritical()){
                appObjValues[i] = 0;
                continue;
            }

            int curLat = (int) Math.max(curApp.getDelays()[0], e2eLatenciesOfApps[i]);

            for (int j = 0; j < nPoints - 1; j++) {
                double lambda_j = curApp.getDelays()[j];
                double lambda_j_1 = curApp.getDelays()[j + 1];
                if(curLat < lambda_j_1 && curLat >= lambda_j){
                    // end-to-end latency value is in this interval
                    double gamma = (curLat - lambda_j) * 1.0 / (lambda_j_1 - lambda_j);
                    double appObj = curApp.getPerfValues()[j] -
                            gamma * (curApp.getPerfValues()[j] - curApp.getPerfValues()[j + 1]);
                    appObjValues[i] = appObj;
                    objValue += appObj;
                    break;
                }
            }

            if(curLat > curApp.getDelays()[nPoints - 1]){
                appObjValues[i] = (Integer.MAX_VALUE / 2);
                objValue += appObjValues[i];
            }
        }

        return objValue;
    }

    public double computeObjectiveMax(){
        int nApps = prInst.getApps().size();
        appObjValues = new double [nApps];
        int nPoints = prInst.getApps().get(0).getDelays().length;
        objValue = 0;
        for (int i = 0; i < nApps; i++) {
            App curApp = prInst.getApps().get(i);

            if(!curApp.isOrderCritical()){
                appObjValues[i] = 0;
                continue;
            }

            int curLat = (int) Math.max(curApp.getDelays()[0], e2eLatenciesOfApps[i]);

            for (int j = 0; j < nPoints - 1; j++) {
                double lambda_j = curApp.getDelays()[j];
                double lambda_j_1 = curApp.getDelays()[j + 1];
                if(curLat < lambda_j_1 && curLat >= lambda_j){
                    double gamma = (curLat - lambda_j) * 1.0 / (lambda_j_1 - lambda_j);
                    double appObj = curApp.getPerfValues()[j] -
                            gamma * (curApp.getPerfValues()[j] - curApp.getPerfValues()[j + 1]);
                    appObjValues[i] = appObj;
                    if(objValue < appObjValues[i]) {
                        objValue = appObjValues[i];
                    }
                    break;
                }
            }

            if(curLat > curApp.getDelays()[nPoints - 1]){
                appObjValues[i] = (Integer.MAX_VALUE / 2);
                objValue = appObjValues[i];
            }
        }

        return objValue;
    }

    public double computeObjective(){
        if(Main.CRITERION_SUM) return computeObjectiveSum();
        else return computeObjectiveMax();
    }

    public void computeAndSetResultingE2ELatencies(){
        for (int i = 0; i < prInst.getApps().size(); i++) {
            App curApp = prInst.getApps().get(i);
            if(!curApp.isOrderCritical()){
                e2eLatenciesOfApps[i] = 0;
            }
            else {
                e2eLatenciesOfApps[i] = 0;
                for (int k = 0; k < curApp.getRootActs().size(); k++) {
                    int rootActST = actST[curApp.getRootActs().get(k).getID()][0];
                    for (int j = 0; j < curApp.getLeafActs().size(); j++) {
                        int leafActProcTime = curApp.getLeafActs().get(j).getProcTime();
                        int leafActST = actST[curApp.getLeafActs().get(j).getID()][0];

                        int curE2ELat = leafActST + leafActProcTime - rootActST;

                        if (e2eLatenciesOfApps[i] < curE2ELat) {
                            e2eLatenciesOfApps[i] = curE2ELat;
                        }
                    }
                }
            }
        }
    }


    //--------------------------------------------------- Intervals ----------------------------------------------------
    private void removeFollowingIntsThatFinishEarlierThanGiven(int numInt, List<List<Integer>> intsOfResConstrs){
        while(true) {
            if(numInt + 1 != intsOfResConstrs.size() &&
                    intsOfResConstrs.get(numInt).get(1) >= intsOfResConstrs.get(numInt + 1).get(1)){
                intsOfResConstrs.remove(numInt + 1);
            }
            else{
                break;
            }
        }
    }

    private void concatenateNewIntervalWithExisting(List<Integer> newInterval, List<List<Integer>> intsOfResConstrs){
        int intNumForNew;
        for(intNumForNew = 0; intNumForNew < intsOfResConstrs.size(); intNumForNew++) {
            int a = newInterval.get(0);
            int b = newInterval.get(1);
            int c = intsOfResConstrs.get(intNumForNew).get(0);
            int d = intsOfResConstrs.get(intNumForNew).get(1);
            if(doTwoClosedIntervalsIntersect(a, b, c, d)){
                if(a == c && b == d){
                    intsOfResConstrs.remove(intNumForNew);
                    break;
                }

                //the existing interval can be changed since the new one extends the old one
                intsOfResConstrs.get(intNumForNew).set(0, Math.min(a, c));
                intsOfResConstrs.get(intNumForNew).set(1, Math.max(b, d));

                // if the current interval ends after the following ends, we can remove the following
                removeFollowingIntsThatFinishEarlierThanGiven(intNumForNew, intsOfResConstrs);

                boolean isNotLastIndex = (intNumForNew + 1 != intsOfResConstrs.size());
                //concatenate new interval with the next one if possible
                if(isNotLastIndex && intsOfResConstrs.get(intNumForNew).get(1) >= intsOfResConstrs.get(intNumForNew + 1).get(0)){
                    intsOfResConstrs.get(intNumForNew).set(1, intsOfResConstrs.get(intNumForNew + 1).get(1));
                    intsOfResConstrs.remove(intNumForNew + 1);
                }

                return;
            }

            if(c >= b && d >= b){
                // we are already beyond the interval order
                break;
            }
        }
        intsOfResConstrs.add(intNumForNew, newInterval);
    }

    public static boolean doTwoClosedIntervalsIntersect(int int1_beg, int int1_end,
                                                  int int2_beg, int int2_end){
        if(int1_beg <= int2_beg) return int1_end >= int2_beg;
        else return int2_end >= int1_beg;
    }

    public static boolean doTwoOpenIntervalsIntersect(int int1_beg, int int1_end,
                                                        int int2_beg, int int2_end){
        if(int1_beg <= int2_beg) return int1_end > int2_beg;
        else return int2_end > int1_beg;
    }

    private boolean twoIntervalsIntersectCyclically(int int1_beg, int int1_end,
                                                    int int2_beg, int int2_end, int cycleLength) {
        int int1_beg_in_cycle = int1_beg % cycleLength;
        int int1_end_in_cycle = int1_end % cycleLength;
        int int2_beg_in_cycle = int2_beg % cycleLength;
        int int2_end_in_cycle = int2_end % cycleLength;

        boolean doIntersect = false;
        if(int1_beg_in_cycle >= int1_end_in_cycle) {
            if(int2_beg_in_cycle >= int2_end_in_cycle) {
                // both go through the cycle end. they intersect
                doIntersect = true;
            }
            else {
                if(int2_beg_in_cycle < int1_end_in_cycle || int1_beg_in_cycle < int2_end_in_cycle) {
                    // the first goes through cycle, while the second does not.
                    // Intersect when one begins earlier than another ends
                    doIntersect = true;
                }
            }
        }
        else {
            if(int2_beg_in_cycle >= int2_end_in_cycle) {
                if(int1_beg_in_cycle < int2_end_in_cycle || int2_beg_in_cycle < int1_end_in_cycle) {
                    // the second goes through cycle, while the first does not.
                    // Intersect when one begins earlier than another ends
                    doIntersect = true;
                }
            }
            else {
                doIntersect = doTwoOpenIntervalsIntersect(int1_beg_in_cycle, int1_end_in_cycle, int2_beg_in_cycle, int2_end_in_cycle);
            }
        }

        if(doIntersect && Main.IS_DEBUG_MODE) {
            System.out.println("Two intervals (" + int1_beg + ", " + int1_end + "), and (" + int2_beg + ", " + int2_end +
                    ") intersect in cycle of length " + cycleLength);
        }

        return doIntersect;
    }

    private void addIntervalToIntsOfResConstr(int int_beg, int int_end,
                                              List<List<Integer>> intsOfResConstrs, int modulo_value){
        List<Integer> newInterval;
        if(int_beg < modulo_value && int_end > modulo_value
                || int_beg % HP > int_end % HP){
            newInterval = Arrays.asList(0, (int_end) % modulo_value);
            concatenateNewIntervalWithExisting(newInterval, intsOfResConstrs);
        }

        if(int_beg % HP < int_end % HP){
            newInterval = Arrays.asList(int_beg % HP, int_end % HP);
        }
        else{
            newInterval = Arrays.asList(int_beg % HP, HP);
        }

        concatenateNewIntervalWithExisting(newInterval, intsOfResConstrs);
    }

    private void addNewResourceIntervalsWithConcatenation(int startTime, Activity act, int nJob){
        int nRes = act.getAssToRes() - 1;

        addIntervalToIntsOfResConstr(startTime, startTime + act.getProcTime(),
                intsOfResConstrs.get(nRes), HP);
    }

    //attention, here you give not an actual number of activity, but the order number of activity in startTimes array
    public List<Integer> UnscheduleActivity(int nUnschedAct){
        List<Integer> ListToUnschedule = new ArrayList<>();
        List<Integer> outputList = new ArrayList<>();

        ListToUnschedule.add(nUnschedAct);
        outputList.add(nUnschedAct);
        for(int i = 0; i < prInst.getActs().get(nUnschedAct).getAllSucc().size(); i++) {
            int numSuccAct = prInst.getActs().get(nUnschedAct).getAllSucc().get(i);
            if(numSchedActs.contains(numSuccAct)) {
                ListToUnschedule.add(numSuccAct);
                outputList.add(numSuccAct);
            }
        }

        Collections.sort(ListToUnschedule);
        for(int i = ListToUnschedule.size() - 1; i >= 0; i--) {
            int numActToUnschedule = ListToUnschedule.get(i);

            nJobsScheduled -= (prInst.getActs().get(numActToUnschedule).getNJobs());
            DeleteResourceIntervals(actST[numActToUnschedule], numActToUnschedule);

            actST[numActToUnschedule] = new int[acts.get(numActToUnschedule).getNJobs()];
            if(numSchedActs.indexOf(numActToUnschedule) != -1) {
                numSchedActs.remove(numSchedActs.indexOf(numActToUnschedule));
            }
            else{
                System.out.println("Houstone, problem! Activity to unschedule is not in numSchedActs!");
                System.exit(0);
            }
        }
        return outputList;
    }

    public void DeleteResourceIntervals(int[] startTimes, int noOfUnscheduledActivity){
        Activity act = prInst.getActs().get(noOfUnscheduledActivity);

        int numJobsToUnschedule = act.getHP() / act.getPeriod();
        for(int j = 0; j < numJobsToUnschedule; j++) {
            for(int i = 0; i < act.getProcTime(); i++) {
                schedule[act.getAssToRes() - 1][(startTimes[j] + i) % prInst.getHP()] = -1;
            }

            List<Integer> oldInterval;
            if(startTimes[j] < prInst.getHP() && startTimes[j] + act.getProcTime() > prInst.getHP()){
                oldInterval = Arrays.asList(startTimes[j], prInst.getHP());
                ConcatenateNewIntervalWithExisting(oldInterval, act.getAssToRes());

                oldInterval = Arrays.asList(0, (startTimes[j] + act.getProcTime()) % prInst.getHP());
                DeleteOldIntervalFromExisting(oldInterval, act.getAssToRes());
            }
            else{
                oldInterval = Arrays.asList(startTimes[j] % HP,
                        (startTimes[j] + act.getProcTime()) % HP);
                if(startTimes[j] + act.getProcTime() == HP){
                    oldInterval.set(1, HP);
                }

                DeleteOldIntervalFromExisting(oldInterval,  act.getAssToRes());
            }
        }
    }

    private void ConcatenateNewIntervalWithExisting(List<Integer> newInterval, int numOfResource){
        numOfResource = numOfResource - 1;
        int numOfIntervalForTheNew;
        for(numOfIntervalForTheNew = 0; numOfIntervalForTheNew < intsOfResConstrs.get(numOfResource).size(); numOfIntervalForTheNew++) {
            int a = newInterval.get(0);
            int b = newInterval.get(1);
            int c = intsOfResConstrs.get(numOfResource).get(numOfIntervalForTheNew).get(0);
            int d = intsOfResConstrs.get(numOfResource).get(numOfIntervalForTheNew).get(1);
            if(c <= a && d >= a || a <= c && b >= c){
                //the existing interval can be changed since the new one extends the old one
                intsOfResConstrs.get(numOfResource).get(numOfIntervalForTheNew).set(0, Math.min(a, c));
                intsOfResConstrs.get(numOfResource).get(numOfIntervalForTheNew).set(1, Math.max(b, d));

                //concatenate new interval with the next one if possible
                if(numOfIntervalForTheNew + 1 != intsOfResConstrs.get(numOfResource).size() &&
                        intsOfResConstrs.get(numOfResource).get(numOfIntervalForTheNew).get(1) >=
                                intsOfResConstrs.get(numOfResource).get(numOfIntervalForTheNew + 1).get(0)){
                    intsOfResConstrs.get(numOfResource).get(numOfIntervalForTheNew).set(1, intsOfResConstrs.get(numOfResource).get(numOfIntervalForTheNew + 1).get(1));
                    intsOfResConstrs.get(numOfResource).remove(numOfIntervalForTheNew + 1);
                }

                return;
            }

            if(c >= b && d >= b){
                //we are already beyond the interval order
                break;
            }
        }
        intsOfResConstrs.get(numOfResource).add(numOfIntervalForTheNew, newInterval);
    }

    private void DeleteOldIntervalFromExisting(List<Integer> oldInterval, int numOfResource){
        int numOfInterval;
        numOfResource = numOfResource - 1;
        for(numOfInterval = 0; numOfInterval < intsOfResConstrs.get(numOfResource).size(); numOfInterval++) {
            int a = oldInterval.get(0);
            int b = oldInterval.get(1);
            int c = intsOfResConstrs.get(numOfResource).get(numOfInterval).get(0);
            int d = intsOfResConstrs.get(numOfResource).get(numOfInterval).get(1);

            if(c <= a && b <= d){
                if(c == a){
                    intsOfResConstrs.get(numOfResource).get(numOfInterval).set(0, b);
                    if(intsOfResConstrs.get(numOfResource).get(numOfInterval).get(0).equals(
                            intsOfResConstrs.get(numOfResource).get(numOfInterval).get(1))){
                        intsOfResConstrs.get(numOfResource).remove(numOfInterval);
                    }
                    return;
                }

                if(b == d){
                    intsOfResConstrs.get(numOfResource).get(numOfInterval).set(1, a);
                    if(intsOfResConstrs.get(numOfResource).get(numOfInterval).get(0).equals(
                            intsOfResConstrs.get(numOfResource).get(numOfInterval).get(1))){
                        intsOfResConstrs.get(numOfResource).remove(numOfInterval);
                    }
                    return;
                }

                //if the old interval does not lie on the edge of interval [b,c] two new intervals must be created - [c,a] and [b,d]
                intsOfResConstrs.get(numOfResource).get(numOfInterval).set(1, a);
                intsOfResConstrs.get(numOfResource).add(numOfInterval + 1, Arrays.asList(b,d));

                return;
            }
        }
    }

    //------------------------------------------------ Other auxillary methods -----------------------------------------
    public ScheduledActivities getHardCopy() {
        ScheduledActivities newSchedActs;

        newSchedActs = new ScheduledActivities(prInst);
        newSchedActs.setActST(actST);
        newSchedActs.computeAndSetResultingE2ELatencies();
        if(Main.CRITERION_SUM) {
            newSchedActs.computeObjectiveSum();
        }
        else {
            newSchedActs.computeObjectiveMax();
        }

        return newSchedActs;
    }

    private void checkAllPredecessorsAreScheduled(Activity curAct){
        for (int i = 0; i < curAct.getDirectPreds().size(); i++) {
            if(!numSchedActs.contains(curAct.getDirectPreds().get(i))){
                System.out.println("Some of the predecessors is not scheduled!!!");
                System.exit(1);
            }
        }
    }

    public void checkPrecedenceConstraints(){
        for (int i = 0; i < acts.size(); i++) {
            Activity curAct = acts.get(i);
            for (int j = 0; j < curAct.getDirectPreds().size(); j++) {
                Activity predAct = acts.get(curAct.getDirectPreds().get(j));
                for (int k = 0; k < predAct.getNJobs(); k++) {
                    if(actST[curAct.getID()][k] < actST[predAct.getID()][k] + predAct.getProcTime()){
                        System.out.println("TRAGEEEEDIJA");
                        System.out.println("Precedence relation is not satisfied for predecessor " + predAct.getID() + " and successor " + curAct.getID());
                        System.out.println("Start time of successor is " + actST[curAct.getID()][k] + ", finish time of predecessor is " + (actST[predAct.getID()][k] + predAct.getProcTime()));
                        System.exit(1);
                    }
                }
            }
        }
    }

    private void checkAllActsSched() {
        System.out.print("");
        for (int i = 0; i < schedJobsOfActs.length; i++) {
            for (int j = 0; j < schedJobsOfActs[i].length; j++) {
                if (!schedJobsOfActs[i][j]) {
                    System.out.println("NOT ALL ACTIVITIES ARE SCHEDULED");
                    System.exit(1);
                }
            }
        }

        if (numSchedActs.size() != prInst.getActs().size()) {
            System.out.println("NOT ALL ACTIVITIES ARE SCHEDULED");
            System.exit(1);
        }
    }

    private void checkE2ELatencies() {
        for (int i = 0; i < prInst.getApps().size(); i++) {
            if(e2eLatenciesOfApps[i] > prInst.getApps().get(i).getE2eLatBound()){
                System.out.println("\nApplication " + i + " is scheduled with larger latency delay!\n");
                System.out.println();
            }
        }
    }

    public void checkSolutionIsValid(){
        checkAllActsSched();
        checkPrecedenceConstraints();
        checkE2ELatencies();
        constructSchedule();
    }

    public Solution convertToSolution(){
        return new Solution(actST, prInst, objValue, null, e2eLatenciesOfApps);
    }

    private int[][] constructSchedule() {
        int[][] schedule = new int[prInst.getnResources()][HP];
        for (int i = 0; i < acts.size(); i++) {
            Activity curAct = acts.get(i);
            for (int k = 0; k < curAct.getNJobs(); k++) {
                int stTime = actST[i][k];
                for (int j = 0; j < curAct.getProcTime(); j++) {
                    if(schedule[curAct.getAssToRes() - 1][(stTime + j) % HP] != 0) {
                        System.out.println("Houston, we have a problem. Collision on resource " + (curAct.getAssToRes() - 1) +
                                " of activity " + schedule[curAct.getAssToRes() - 1][(stTime + j) % HP] + " and activity " +
                                (curAct.getID() + 1) + " job " + k + " at time " + ((stTime + j) % HP));
                        acts.get(schedule[curAct.getAssToRes() - 1][(stTime + j) % HP] - 1).printActivity();
                        curAct.printActivity();
                        System.exit(0);
                    }
                    schedule[curAct.getAssToRes() - 1][(stTime + j) % HP] = curAct.getID() + 1;
                }
            }

        }
        return schedule;
    }

    public void printFinalSchedule(){
        int[][] schedule = constructSchedule();
        for(int i = 0; i < prInst.getnResources(); i++) {
            System.out.println("Resource "+ (i + 1) + ":");

            for(int j = 0; j < HP; j++) {
                System.out.format("%3d|", schedule[i][j]);
            }

            System.out.println();
            System.out.println();
        }
    }

    public void printSchedActivitiesOnResource(int nResource){
        System.out.println("\n\nScheduled activities on the problematic resource number " + nResource + " are: ");
        for (int i = 0; i < numSchedActs.size(); i++) {
            if(acts.get(numSchedActs.get(i)).getAssToRes() == nResource){
                acts.get(numSchedActs.get(i)).printActivity();
                for (int j = 0; j < schedJobsOfActs[numSchedActs.get(i)].length; j++) {
                    System.out.println("Job " + j + " is " + ((schedJobsOfActs[numSchedActs.get(i)][j]) ? "scheduled" : "not scheduled"));
                }
            }
        }
    }

    public void printResourceIntervals(List<List<Integer>> intsOfResConstrsOneRes) {
        System.out.println("Intervals of resource constraints are:");
        for (int i = 0; i < intsOfResConstrsOneRes.size(); i++) {
            System.out.print("[" + intsOfResConstrsOneRes.get(i).get(0)
                    + ", " + intsOfResConstrsOneRes.get(i).get(1) + "], ");
        }
        System.out.println("");
    }

    //------------------------------------------------ Getters and setters -----------------------------------------
    public List<List<List<Integer>>> getIntsOfResConstrs() {
        return intsOfResConstrs;
    }

    public List<Integer> getNumsOfSchedActs() {
        return numSchedActs;
    }

    public int getnJobsScheduled() {
        return nJobsScheduled;
    }

    public int[][] getActST() {
        return actST;
    }

    public double[] getAppObjValues() {
        return appObjValues;
    }

    public int[] getE2eLatenciesOfApps() {
        return e2eLatenciesOfApps;
    }

    public ProblemInstance getPrInst() {
        return prInst;
    }

    public List<ActivityJob> getProblematicAct() {
        return problematicAct;
    }

    public double getObjValue() {
        return objValue;
    }

    public boolean isSchedJobOfAct(int nAct, int nJob) {
        if(schedJobsOfActs[nAct] == null) {
            return false;
        }
        return schedJobsOfActs[nAct][nJob];
    }

    public void setProblematicAct(int nAct, List<Integer> nJobs, boolean schedFullAct) {
        problematicAct = new ArrayList<>();
        for (int i = 0; i < nJobs.size(); i++) {
            problematicAct.add(new ActivityJob(acts.get(nAct), nJobs.get(i), schedFullAct));
        }
    }

    public void printStartTimesOfActivityPredecessors(Activity curAct){
        for (int i = 0; i < curAct.getDirectPreds().size(); i++) {
            int nJobs = acts.get(curAct.getDirectPreds().get(i)).isTask()
                    ? 1 : acts.get(curAct.getDirectPreds().get(i)).getNJobs();
            for (int j = 0; j < nJobs; j++) {
                System.out.print("Predecessor " + curAct.getDirectPreds().get(i) + " job " + j +
                        " start time is " + actST[curAct.getDirectPreds().get(i)][j] + ", processing time is " + acts.get(curAct.getDirectPreds().get(i)).getProcTime());
            }
        }
    }

    public void setActST(int[][] actST) {
        this.actST = actST;
    }

    public void UnscheduleAllActivitiesButPreviouslyScheduledAndPreceeding(List<Integer> PreviouslyScheduled, Activity activity1, Activity activity2){
        nJobsScheduled = 0;
        int[][] newStartTimes = new int[acts.size()][];
        List<Integer> newNumbersOfScheduledActivities = new ArrayList<Integer>();
        intsOfResConstrs =  new ArrayList<List<List<Integer>>>();
        for(int k = 0; k < prInst.getnResources(); k++) {
            intsOfResConstrs.add(new ArrayList<List<Integer>>());
        }
        Helpers.Initialize2dArrayWithValue(schedule, -1);

        for(int i = 0; i < numSchedActs.size(); i++) {
            // schedule if it was not previously scheduled from scratch
            if( (PreviouslyScheduled.contains(numSchedActs.get(i)) &&
                    numSchedActs.get(i) != activity2.getID() &&
                    numSchedActs.get(i) != activity1.getID() )
                    || activity1.getAllPredecessors().contains(numSchedActs.get(i))
                    || activity2.getAllPredecessors().contains(numSchedActs.get(i)))
            //|| problemInstanceScheduling.getActivities()[numbersOfScheduledActivity.get(i)].getJitter() == 0)
            {
                int nAct = numSchedActs.get(i);
                newStartTimes[nAct] = actST[nAct];
                nJobsScheduled += actST[nAct].length;
                newNumbersOfScheduledActivities.add(numSchedActs.get(i));
                AddNewResourceIntervalsWithConcatenation(actST[nAct], numSchedActs.get(i));
            }
        }
        numSchedActs = newNumbersOfScheduledActivities;
        actST = newStartTimes;
    }

}

