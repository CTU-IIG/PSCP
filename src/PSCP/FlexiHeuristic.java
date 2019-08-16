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

public class FlexiHeuristic {
    private List<Activity> acts;
    private List<App> apps;
    protected ProblemInstance probInst;

    int numActs;
    int nApps;
    public static long globalSTHeurOpt;

    public static class ArrayIndexComparator implements Comparator<Integer>
    {
        private final Integer[] slack;
        private final Double[] parameter;
        private final int numEls;

        public ArrayIndexComparator(Integer[] slack_) {
            slack = slack_;
            parameter = null;
            numEls = slack_.length;
        }

        public ArrayIndexComparator(Double[] parameter_) {
            slack = null;
            parameter = parameter_;
            numEls = parameter.length;
        }

        public Integer[] createIndexArray() {
            Integer[] indexes = new Integer[numEls];
            for (int i = 0; i < numEls; i++){
                indexes[i] = i;
            }
            return indexes;
        }

        @Override
        public int compare(Integer index1, Integer index2){
            if(slack != null){
                return slack[index1].compareTo(slack[index2]);
            }
            else{
                return parameter[index1].compareTo(parameter[index2]);
            }
        }
    }

    public FlexiHeuristic(ProblemInstance probInst) {
        this.probInst = probInst;
        acts = probInst.getActs();
        numActs = acts.size();
        apps = probInst.getApps();
        nApps = apps.size();
    }

    private ActivityJob getActivityJobToScheduleAndRemoveIt(List<ActivityJob> prioritiesUpdated,
                                                            ScheduledActivities schedAct){
        for (int i = 0; i < prioritiesUpdated.size(); i++) {
            ActivityJob curActJob = prioritiesUpdated.get(i);
            boolean arePredJobSched = true;
            for (int j = 0; j < curActJob.getAct().getDirectPreds().size(); j++) {
                if(curActJob.scheduleFullAct()) {
                    for (int k = 0; k < curActJob.getAct().getNJobs(); k++) {
                        if (!schedAct.isSchedJobOfAct(curActJob.getAct().getDirectPreds().get(j), k)) {
                            arePredJobSched = false;
                        }
                    }
                }
                else {
                    if (!schedAct.isSchedJobOfAct(curActJob.getAct().getDirectPreds().get(j), curActJob.getnJob())) {
                        arePredJobSched = false;
                    }
                }
            }

            if(arePredJobSched){
                ActivityJob actJobToReturn = new ActivityJob(curActJob.getAct(), curActJob.getnJob(),
                        prioritiesUpdated.get(i).scheduleFullAct());
                prioritiesUpdated.remove(i);
                return actJobToReturn;
            }
        }

        return null;
    }

    private List<ActivityJob> sortActivitiesAccordingToThePriorityRule(int nWay){
        List<Integer> priorities = new ArrayList<Integer>();
        ArrayIndexComparator comparator = null;
        switch(nWay) {
            case 1:
                // slacks
                Integer[] slack = new Integer[numActs];
                for (int i = 0; i < numActs; i++) {
                    slack[i] = probInst.getActs().get(i).getSlack();
                }

                comparator = new ArrayIndexComparator(slack);
                break;
            case 2:
                // control performance differences
                Double[] diffs = new Double[numActs];
                for (int i = 0; i < numActs; i++) {
                    int nCluster = probInst.getActs().get(i).getAssToApp() - 1;

                    if(apps.get(acts.get(i).getAssToApp() - 1).getActs().size() > 1){
                        diffs[i] = - (apps.get(nCluster).getPerfValues()[Main.nDiscreeteLatValues - 1] -
                                apps.get(nCluster).getPerfValues()[0]) - 1;
                    }
                    else{
                        diffs[i] = -acts.get(i).getProcTime() * 1.0 / acts.get(i).getPeriod();
                    }
                }

                comparator = new ArrayIndexComparator(diffs);
                break;
            case 3:
                // first video messages, then slacks
                Double[] slack1 = new Double[numActs];
                for (int i = 0; i < numActs; i++) {
                    App app = probInst.getApps().get(probInst.getActs().get(i).getAssToApp() - 1);
                    if(app.containsVideo()) {
                        slack1[i] = probInst.getActs().get(i).getSlack() * 1.0 / 10000;
                    }
                    else {
                        slack1[i] = probInst.getActs().get(i).getSlack() * 1.0;
                    }
                }
                comparator = new ArrayIndexComparator(slack1);
                break;
            case 4:
                // Longest processing time first
                Integer[] processingTimes = new Integer[numActs];
                for (int i = 0; i < numActs; i++) {
                    processingTimes[i] = acts.get(i).getProcTime();
                }
                comparator =  new ArrayIndexComparator(processingTimes);
                break;
            case 5:
                // resource equivalent duration
                Integer[] RED_values_apps = new Integer[nApps];
                for (int i = 0; i < nApps; i++) {
                    int sumResourceUtil = 0;
                    for (int j = 0; j < probInst.getnResources(); j++) {
                        sumResourceUtil += Math.ceil(apps.get(i).getResourceUtilization()[j] * 10);
                    }

                    RED_values_apps[i] = -sumResourceUtil *
                            (apps.get(i).getCriticalLengthsAfter()[0] + apps.get(i).getCriticalLengthsBefore()[0]);
                }

                Integer[] RED_values_act = new Integer [numActs];
                for (int i = 0; i < numActs; i++) {
                    RED_values_act[i] = RED_values_apps[acts.get(i).getAssToApp() - 1];
                }
                comparator =  new ArrayIndexComparator(RED_values_act);
                break;
            default:
                throw new AssertionError();
        }


        priorities.addAll(Arrays.asList(comparator.createIndexArray()));
        Collections.sort(priorities, comparator);

        List<ActivityJob> prioritiesJob = new ArrayList<>();
        for (int i = 0; i < priorities.size(); i++) {
            prioritiesJob.add(new ActivityJob(acts.get(priorities.get(i)), 0, true));
        }

        return prioritiesJob;
    }

    private void changePriorityArrayByPrioritizingOneActOverAnother(List<ActivityJob> priorities,
                                                                    int indOfCurAct, int indOfDelayAct,
                                                                    ActivityJob curAct){
        if(indOfCurAct > indOfDelayAct){
            for (int j = indOfCurAct - 1; j >= indOfDelayAct; j--) {
                priorities.set(j + 1, priorities.get(j));
            }
            priorities.set(indOfDelayAct, curAct);
        }
    }

    private void splitActOnJobsInPriorityList(List<ActivityJob> priorities, int nActToSplit) {
        for (int i = 0; i < priorities.size(); i++) {
            if(priorities.get(i).getNAct() == nActToSplit) {
                Activity act = acts.get(priorities.get(i).getNAct());
                if(priorities.get(i).scheduleFullAct()) {
                    priorities.remove(i);
                    for (int j = act.getNJobs() - 1; j >= 0; j--) {
                        priorities.add(i, new ActivityJob(act, j, false));
                    }
                }
                return;
            }
        }
    }

    private void printPriorities(List<ActivityJob> priorities, int size) {
        int sizeLoc = priorities.size();
        if(sizeLoc != 0) {
            sizeLoc = size;
        }

        for (int i = 0; i < sizeLoc; i++) {
            System.out.print("[" + priorities.get(i).getNAct() + ", " + priorities.get(i).getnJob() + "]");
        }
        System.out.println();
        System.out.println();
    }

    private int getIndexOfActJobInPriorityList(List<ActivityJob> priorities, ActivityJob actJob) {
        for (int i = 0; i < priorities.size(); i++) {
            if(priorities.get(i).equals(actJob)) {
                return i;
            }
        }

        printPriorities(priorities, 0);
        actJob.print();
        System.out.println("Cannot find activity job in priorities, ERROR!");
        System.exit(1);
        return -1;
    }

    private void isEveryActivityJobInPriorityList(List<ActivityJob> priorities){
        boolean[][] actJobInPrior = new boolean[acts.size()][];

        // fill which jobs of activities are in priorities
        for (int i = 0; i < priorities.size(); i++) {
            Activity act = acts.get(priorities.get(i).getNAct());
            int nJob = priorities.get(i).getnJob();

            if(priorities.get(i).scheduleFullAct()) {
                actJobInPrior[act.getID()] = new boolean [1];
                actJobInPrior[act.getID()][0] = true;
            }
            else{
                actJobInPrior[act.getID()] = new boolean [act.getNJobs()];
                actJobInPrior[act.getID()][nJob] = true;
            }
        }

        // check that all jobs of activities are in priorities
        for (int i = 0; i < acts.size(); i++) {
            if(actJobInPrior[i] == null) {
                System.out.println("Activity with id " + i + " is not in the priority list!");
                System.exit(1);
            }

            for (int j = 0; j < actJobInPrior[i].length; j++) {
                if(!actJobInPrior[i][j]) {
                    System.out.println("Activity job " + j + " with id " + i + " is not in the priority list!");
                    System.exit(1);
                }
            }
        }
    }

    private void splitMessageIntoJobsIfNecessary(List<ActivityJob> priorities, ActivityJob curActJob) {
        if(curActJob.scheduleFullAct()) {
            splitActOnJobsInPriorityList(priorities, curActJob.getNAct());
            curActJob.setScheduleFullAct(false);
        }

    }

    private boolean isPredecessorFullyScheduledInPriorityArray(int nPred, List<ActivityJob> priorities) {
        for (int i = 0; i < priorities.size(); i++) {
            if(priorities.get(i).getNAct() == nPred) {
                if(priorities.get(i).scheduleFullAct()) {
                    return true;
                }
                else {
                    return false;
                }
            }
        }

        System.out.println("Predecessor of the current problematic activity is not " +
                "in the priority array! ");
        System.exit(1);
        return false;
    }

    public boolean isOneOfPredActsTask(Activity curAct) {
        for (int i = 0; i < curAct.getAllPredecessors().size(); i++) {
            if(acts.get(curAct.getAllPredecessors().get(i)).isTask()) {
                return true;
            }
        }
        return false;
    }

    private void insertActBeforeDelayElementInPriority(ActivityJob curActJob, ActivityJob reasonActJob,
                                                 List<ActivityJob> priorities, boolean splitIntoJobs){
        splitIntoJobs = splitIntoJobs && !isOneOfPredActsTask(curActJob.getAct());

        // we split into jobs both current problematic activity and the reason if they are messages
        if(splitIntoJobs && !curActJob.getAct().isTask()) {
            splitMessageIntoJobsIfNecessary(priorities, curActJob);
        }

        int indOfCurActJobInPrior = getIndexOfActJobInPriorityList(priorities, curActJob);
        int indOfReasonActInPrior = getIndexOfActJobInPriorityList(priorities, reasonActJob);

        // insert current activity before its reason into the priority list
        changePriorityArrayByPrioritizingOneActOverAnother(priorities,
                indOfCurActJobInPrior, indOfReasonActInPrior, curActJob);

        // insert predecessors of the current activity before the reason
        for (int j = 0; j < curActJob.getAct().getAllPredecessors().size(); j++) {
            Activity actPred = acts.get(curActJob.getAct().getAllPredecessors().get(j));

            // split predecessor message into jobs
            boolean isPredFullySched = isPredecessorFullyScheduledInPriorityArray(actPred.getID(), priorities);
            if(splitIntoJobs && !curActJob.getAct().isTask() && !actPred.isTask() && isPredFullySched) {
                splitMessageIntoJobsIfNecessary(
                        priorities,
                        new ActivityJob(
                                actPred,
                                curActJob.getnJob(),
                                isPredFullySched
                        )
                );

                isPredFullySched = false;
            }

            ActivityJob predActJob =
                    new ActivityJob(
                            acts.get(curActJob.getAct().getAllPredecessors().get(j)),
                            curActJob.getnJob(),
                            isPredFullySched
                    );

            // insert only if it is after the reason on the priority list
            int indexOfPredInPrior = getIndexOfActJobInPriorityList(priorities, predActJob);
            changePriorityArrayByPrioritizingOneActOverAnother(priorities,
                    indexOfPredInPrior, indOfReasonActInPrior, predActJob);
        }

    }

    private List<ActivityJob> findDelayParentActs(ActivityJob curProblematicAct, int levelOfDelay) {
        List<ActivityJob> delayActivities = new ArrayList<>();
        List<ActivityJob> listOfDelayActJobs = new ArrayList<>();
        delayActivities.add(curProblematicAct);

        // going through the parents of the current activity, we create a list of activities that are scheduled late
        // because of resource constraints or ZJ constraints
        while(!delayActivities.isEmpty() && listOfDelayActJobs.size() <= levelOfDelay){
            ActivityJob curActJob = delayActivities.get(0);
            delayActivities.remove(0);

            Activity curAct = curActJob.getAct();
            ReasonOfDelay delayActJob = curAct.getDelay(curActJob.getnJob());

            if(delayActJob.getDelay() == "No reason") {
                // we are in a root of the tree, finish
                break;
            }

            for (int i = 0; i < delayActJob.getDelayActs().size(); i++) {
                // if the current activity is scheduled right after its predecessors, it does not
                // make sense to prioritize it on its resource
                if(delayActJob.getDelay() != "Prec"){
                    listOfDelayActJobs.add(delayActJob.getDelayActs().get(i));
                }

                delayActivities.add(delayActJob.getDelayActs().get(i));
            }
        }

        return listOfDelayActJobs;
    }

    private void addDelayToProbActIfNotSplitting(List<ActivityJob> probActJobs) {
        Activity act = acts.get(probActJobs.get(0).getNAct());
        for (int i = 1; i < probActJobs.size(); i++) {
            int nJob = probActJobs.get(i).getnJob();

            ActivityJob delayActForJob = act.getDelay(nJob).getDelayActs().get(0);
            if(!act.getDelay(0).delayActsContainAct(delayActForJob.getNAct())) {
                act.getDelay(0).addDelayActs(delayActForJob);
            }
        }
    }

    private List<ActivityJob> getDelayActJob(ActivityJob probActJob, List<ActivityJob> delayParentActs, int levelOfDelay) {
        Activity act = acts.get(probActJob.getNAct());
        List<ActivityJob> delayActJob = new ArrayList<>();
        int jobProbAct = probActJob.getnJob();
        int firstDelayActivity = act.getDelay(jobProbAct).getDelayActs().get(0).getNAct();

        if (delayParentActs.size() > levelOfDelay) {
            delayActJob.add(delayParentActs.get(levelOfDelay));
        } else {
            App appOfDelayAct = probInst.getApps().get(acts.get(firstDelayActivity).getAssToApp() - 1);
            for (int i = 0; i < appOfDelayAct.getRootActs().size(); i++) {
                Activity rootAct = appOfDelayAct.getRootActs().get(i);
                delayActJob.add(new ActivityJob(rootAct, 0));
            }
        }

        return delayActJob;
    }

    private ScheduledActivities createScheduleFromPriorities(List<ActivityJob> priorities){
        ScheduledActivities schedActs = new ScheduledActivities(probInst);
        List<ActivityJob> prioritiesCopy = new ArrayList<>(priorities.size());

        for (int i = 0; i < priorities.size(); i++) {
            prioritiesCopy.add(priorities.get(i));
        }

        while(!prioritiesCopy.isEmpty()){
            ActivityJob actJobToSchedule = getActivityJobToScheduleAndRemoveIt(prioritiesCopy, schedActs);
            Activity curAct = probInst.getActs().get(actJobToSchedule.getNAct());

            schedActs.scheduleAndSetDelay(actJobToSchedule);

            //<editor-fold defaultstate="collapsed" desc="comment">
            if(Main.IS_DEBUG_MODE){
                Helpers.printEmptyLines(1);
                System.out.println("Currently scheduled activity is: ");
                curAct.printActivity();
                schedActs.printStartTimesOfActivityPredecessors(curAct);

                if(!actJobToSchedule.scheduleFullAct()) {
                    System.out.println(", job " + actJobToSchedule.getnJob());
                }

                System.out.print("\n\nScheduled times are: ");
                int nJobs = curAct.isTask() ? 1 : curAct.getNJobs();
                for (int i = 0; i < nJobs; i++) {
                    System.out.print(schedActs.getActST()[curAct.getID()][i]);
                    if(i != nJobs - 1) {
                        System.out.print(", ");
                    }
                    else{
                        System.out.print("\n\n");
                    }
                }

                System.out.println("The delays are: ");
                for (int i = 0; i < curAct.getNJobs(); i++) {
                    System.out.print("For " + i + "-th job: "+ curAct.getDelay(i).getDelay());
                    if(curAct.getDelay(i).getDelay() != "No delay") {
                        for (int j = 0; j < curAct.getDelay(i).getDelayActs().size(); j++) {
                            System.out.print(", delay activity ID is " +
                                    curAct.getDelay(i).getDelayActs().get(j).getNAct() +
                                    " with start time " +
                                    schedActs.getActST()[curAct.getDelay(i).getDelayActs().get(j).getNAct()][0]
                                    + " and processing time " +
                                    curAct.getDelay(i).getDelayActs().get(j).getAct().getProcTime());
                        }
                    }
                    System.out.println();
                }

                System.out.println("\nFinish time of the latest predecessors are: ");
                for (int i = 0; i < curAct.getNJobs(); i++) {
                    System.out.println("For " + i + "-th job: "+ curAct.getDelay(i).getLatestFTofPred());
                }
            }
            //</editor-fold>

            List<Integer> numInfeasibleJobs = curAct.isInfeasible(actJobToSchedule.scheduleFullAct(), actJobToSchedule.getnJob());

            if(!numInfeasibleJobs.isEmpty()){
                schedActs.setProblematicAct(
                        curAct.getID(),
                        numInfeasibleJobs,
                        actJobToSchedule.scheduleFullAct()
                );

                break;
            }
        }

        return schedActs;
    }

    private List<List<ActivityJob>> changePriors(List<ActivityJob> probActJobs, int levelOfDelay,
                                                 List<ActivityJob> priorities, boolean splitMesToJobs) {
        List<List<ActivityJob>> delayActJobs = new ArrayList<>();

        for (int j = 0; j < probActJobs.size(); j++) {
            delayActJobs.add(new ArrayList<ActivityJob>());
            if(levelOfDelay == 0) {
                int nJob = probActJobs.get(j).getnJob();
                Activity problemAct = probActJobs.get(j).getAct();

                delayActJobs.get(j).addAll(problemAct.getDelay(nJob).getDelayActs());
            }
            else {
                List<ActivityJob> delayParentActs = findDelayParentActs(probActJobs.get(j), levelOfDelay);
                delayActJobs.get(j).addAll(getDelayActJob(probActJobs.get(j), delayParentActs, levelOfDelay));
            }

            for (int i = 0; i < delayActJobs.get(j).size(); i++) {
                insertActBeforeDelayElementInPriority(probActJobs.get(j), delayActJobs.get(j).get(i),
                        priorities, splitMesToJobs);
            }
        }

        return delayActJobs;
    }

    private List<List<ActivityJob>> changePriorsWithDelayGraph(
            List<ActivityJob> priorities, List<ActivityJob> probActJobs, int levelOfDelay){
        Activity act = acts.get(probActJobs.get(0).getNAct());

        boolean splitMesToJobs = true;
        if(probActJobs.size() == probActJobs.get(0).getAct().getNJobs()) {
            // different jobs may be delayed by different activities. Therefore, if we decide not to split activity
            // into jobs, we need to consider all of its jobs predecessors
            addDelayToProbActIfNotSplitting(probActJobs);

            probActJobs = new ArrayList<>();
            probActJobs.add(new ActivityJob(act, 0, true));
            splitMesToJobs = false;
        }

        List<List<ActivityJob>> numOfInterchangingActs = changePriors(probActJobs, levelOfDelay, priorities, splitMesToJobs);

        if(numOfInterchangingActs == null){
            System.out.println("Currently changing activities array is null, something is wrong!");
            System.exit(1);
        }

        return numOfInterchangingActs;
    }

    private boolean priorityQueuesContainsQueue(List<List<ActivityJob>> priorityQueues, List<ActivityJob> queue) {
        for (int i = 0; i < priorityQueues.size(); i++) {
            for (int j = 0; j < queue.size(); j++) {
                if(!priorityQueues.get(priorityQueues.size() - i - 1).get(j).equals(queue.get(j))) {
                    j = queue.size();
                }
                if(j == queue.size() - 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean arrayContainsElement(List<ActivityJob> array1, ActivityJob element) {
        boolean contains = false;
        for (int i = 0; i < array1.size(); i++) {
            if(array1.get(i).equals(element)) {
                contains = true;
                break;
            }
        }
        return contains;
    }

    private boolean setEqualsToSet(List<ActivityJob> array1, List<ActivityJob> array2) {
        if(array1.size() != array2.size()) return false;
        for (int i = 0; i < array1.size(); i++) {
            if(!arrayContainsElement(array1, array2.get(i))) {
                return false;
            }
        }

        return true;
    }

    private ScheduledActivities FindSomeFeasibleSolution(int nWay) throws IOException {
        ScheduledActivities schedActs = null;
        List<List<ActivityJob>> priorityQueues = new ArrayList<>();
        List<ActivityJob> priorityQueue = sortActivitiesAccordingToThePriorityRule(nWay);
        int numberIterations = 0;
        boolean isSolutionFound = false;
        boolean failedToFindSolution = false;
        boolean isLoop = false;

        int curDelayLevel = 0;
        List<ActivityJob> prevProbAct = null;
        List<List<ActivityJob>> prevDelayActs = null;
        List<List<ActivityJob>> delayActs;
        int sizeOfPreviousPriorityQueue = 0;

        while(!isSolutionFound && !failedToFindSolution && !isLoop && numberIterations < Main.max_num_iterations_heur_feasible){

            schedActs = createScheduleFromPriorities(priorityQueue);

            if(schedActs.getProblematicAct() == null){
                isSolutionFound = true;
            }
            else {
                List<ActivityJob> probAct = schedActs.getProblematicAct();

                sizeOfPreviousPriorityQueue = priorityQueue.size();
                //printPriorities(priorityQueue, 800);
                delayActs = changePriorsWithDelayGraph(priorityQueue, schedActs.getProblematicAct(), curDelayLevel);
                //printPriorities(priorityQueue, 800);

                if(numberIterations != 0 && setEqualsToSet(probAct, prevProbAct)
                        && delayActs.get(0).get(0).equals(prevDelayActs.get(0).get(0))) {
                    curDelayLevel++;
                    prevDelayActs = delayActs;
                    delayActs = changePriorsWithDelayGraph(priorityQueue, schedActs.getProblematicAct(), curDelayLevel);

                    if(delayActs.get(0).get(0).equals(prevDelayActs.get(0).get(0))) {
                        failedToFindSolution = true;
                        continue;
                    }
                }
                else {
                    curDelayLevel = 0;
                }

                if(numberIterations != 0 && sizeOfPreviousPriorityQueue != priorityQueue.size()) {
                    priorityQueues = new ArrayList<>();
                    System.gc();
                }
                else {
                    if (priorityQueuesContainsQueue(priorityQueues, priorityQueue)) {
                        isLoop = true;
                        continue;
                    }
                }

                priorityQueues.add(new ArrayList<>(priorityQueue));

                prevProbAct = probAct;
                prevDelayActs = delayActs;
            }
            numberIterations++;
        }

        if(isSolutionFound == false) {
            schedActs = null;
        }

        if(schedActs != null) {
            schedActs.computeAndSetResultingE2ELatencies();
            schedActs.computeObjective();
            if(Main.CHECK_SOLUTION_CORRECTNESS) {
                schedActs.checkSolutionIsValid();
            }
        }

        return schedActs;
    }

    private ScheduledActivities FindBestFeasibleSolution(Main.RunningCharacteristics chars)
            throws IOException{
        ScheduledActivities schedActsFinal = null;

        String s = "\n\n Objective function values are:";
        for (int nWay = 2; nWay <= 2; nWay++) {
            System.out.println("\n\nPriority assignment strategy " + nWay + " is running!");
            ScheduledActivities schedActsHeur = FindSomeFeasibleSolution(nWay);

            if(schedActsHeur != null) {
                s += "\n Strategy " + nWay + " " + schedActsHeur.getObjValue();
                if(Main.IS_INSTANCE_GENERATION_MODE) {
                    return schedActsHeur;
                }

                if (schedActsFinal == null) {
                    schedActsFinal = schedActsHeur;
                }
                else {
                    if(schedActsHeur.getObjValue() < schedActsFinal.getObjValue()){
                        schedActsFinal = schedActsHeur;
                    }
                }
            }
        }
        Helpers.printToFile(chars.fullFileNameTerminalOut, s);
        System.out.println(s);

        return schedActsFinal;
    }

    private int SetNeighborhood(List<List<Integer>> numOfChosenApps,
                                boolean[][] actsInNeigborhood, ScheduledActivities schedActs){
        int nApps = probInst.getApps().size();
        List<Integer> SortedApps = Helpers.sortAppsAccordingToControlValue(
                probInst, schedActs, 1);

        int numAppsToChoose = Main.HEUR_NUM_APPS_TO_CHOSE_GRAPH_HEUR;
        if(numAppsToChoose > nApps){
            numAppsToChoose = nApps;
        }

        int numAppsInNei = Main.HEUR_NUM_APPS_IN_NEIGBORHOOD;
        if(numAppsInNei > nApps){
            numAppsInNei = nApps;
        }

        Integer[] curCombination = new Integer[numAppsToChoose];
        for (int i = 0; i < numAppsToChoose; i++) {
            curCombination[i] = 0;
        }

        int numNeighbors = Main.HEUR_MAX_NUM_SOL_IN_NEIGBORHOOD;

        for (int i = 0; i < Main.HEUR_MAX_NUM_SOL_IN_NEIGBORHOOD; i++) {
            switch (Main.HEUR_WAY_OF_CHOOSING_NEIBORHOOD) {
                case 1:
                    // choose randomly
                    numOfChosenApps.add(Helpers.generateNNumbersRandomly(numAppsInNei, nApps));
                    break;
                case 2:
                    // choose the ones with the highest potential
                    if(!Helpers.createTheNextCombinationOfKFromN(curCombination, numAppsInNei)){
                        // it's not possible to generate more neighbors
                        numNeighbors = i;
                        i = Main.HEUR_MAX_NUM_SOL_IN_NEIGBORHOOD;
                        continue;
                    }
                    List<Integer> curList = new ArrayList<Integer>();
                    for (int j = 0; j < curCombination.length; j++) {
                        if(curCombination[j] == 1){
                            curList.add(SortedApps.get(j));
                        }
                    }
                    numOfChosenApps.add(curList);
                    break;

                case 3:
                    //choose one with the highest potential
                    int appWithMaxDiff = SortedApps.get(i);
                    // and others as sharing the most resources
                    numOfChosenApps.add(Helpers.returnListOfActsSharingLargestNumRes(probInst,
                            appWithMaxDiff, numAppsInNei));
                    if(i + 1 == probInst.getApps().size()){
                        i = Main.HEUR_MAX_NUM_SOL_IN_NEIGBORHOOD;
                    }
                    break;

                default:
                    throw new AssertionError();
            }
        }

        // form boolean array of indicators whether or not to schedule given activity
        for (int i = 0; i < numNeighbors; i++) {
            for (int j = 0; j < numAppsInNei; j++) {
                App app = probInst.getApps().get(numOfChosenApps.get(i).get(j));
                for (int k = 0; k < app.getActs().size(); k++) {
                    actsInNeigborhood[i][app.getActs().get(k).getID()] = true;
                }
            }
        }
        return numNeighbors;
    }

    private Solution Minimize(ScheduledActivities inSchedActs, Main.RunningCharacteristics chars, boolean useCP) throws IloException, IOException{
        ScheduledActivities schedActs = inSchedActs.getHardCopy();
        Solution bestSol = schedActs.convertToSolution();
        double prevObjValue = schedActs.getObjValue();
        boolean[][] actsInNeigborhood = new boolean [Main.HEUR_MAX_NUM_SOL_IN_NEIGBORHOOD][numActs];
        int it = 0;
        boolean isGapSmallEnough = false;
        while(!isGapSmallEnough){
            it++;
            Helpers.printToFile(chars.fullFileNameTerminalOut, "\n" + it + "-th iteration");
            List<List<Integer>> numOfChosenApps = new ArrayList<>();
            int numNeighboors = SetNeighborhood(numOfChosenApps, actsInNeigborhood, schedActs);
            for (int i = 0; i < numNeighboors; i++) {
                Solution sol;
                if(Main.IS_DEBUG_MODE){
                    System.out.println(numOfChosenApps.get(i));
                }

                globalSTHeurOpt = System.currentTimeMillis();
                if(useCP) {
                    CPLocalNeighborhood LNmodelCP = new CPLocalNeighborhood(probInst,
                            actsInNeigborhood[i], schedActs);
                    sol = LNmodelCP.solve(chars);
                }
                else{
                    ILPLocalNeighborhood LNmodelLP = new ILPLocalNeighborhood(probInst,
                            actsInNeigborhood[i], chars, schedActs);
                    sol = LNmodelLP.solve(chars);
                }

                if(sol.failedToFind()){
                    continue;
                }

                if(bestSol.getObjValue() > sol.getObjValue()){
                    bestSol = sol;
                }
            }

            if(Main.IS_DEBUG_MODE){
                System.out.println("");
                System.out.println(bestSol.getObjValue());
                System.out.println("");
            }

            if(bestSol.getObjValue() < Integer.MAX_VALUE){
                bestSol.getSchedActsFromSolConverted(schedActs);
            }
            else{
                return schedActs.convertToSolution();
            }

            if(prevObjValue - bestSol.getObjValue() >= Helpers.toleranceHeur){
                prevObjValue = bestSol.getObjValue();
            }
            else{
                isGapSmallEnough = true;
            }
        }
        return bestSol;
    }

    public Solution Solve(Main.StatisticsOfApproaches stat, Main.RunningCharacteristics chars) throws IloException, IOException{
        long startTimeFeas = System.currentTimeMillis();
        ScheduledActivities schedActs = FindBestFeasibleSolution(chars);

        long runTime = (System.currentTimeMillis() - startTimeFeas);
            stat.runTimeFeasibleHeur[Main.WAY_TO_SET_DELAY_ELEMENT_INFEASIBLE] = runTime;

        if(schedActs == null){
            stat.critFeasHeur[Main.WAY_TO_SET_DELAY_ELEMENT_INFEASIBLE] = -1;
            Helpers.printToFile(chars.fullFileNameTerminalOut, "Finally, heuristic failed to find a feasible schedule!");
            return null;
        }
        else{
            stat.critFeasHeur[Main.WAY_TO_SET_DELAY_ELEMENT_INFEASIBLE] = schedActs.getObjValue();
        }

        Helpers.printToFile(chars.fullFileNameTerminalOut, "\n \nFirst feasible solution: \n" + "Objective function value is " +                     schedActs.computeObjective() + "\n" + "Time is " + runTime + "\n");

        Solution sol = null;
        if(Main.OPTIMIZE_WITH_CRITERION && !Main.IS_INSTANCE_GENERATION_MODE) {
            if(Main.IS_DEBUG_MODE) {
                CPLocalNeighborhood model = new CPLocalNeighborhood(probInst,
                        Helpers.createBoolArrayValues(probInst.getActs().size(), false), schedActs);
                sol = model.solve(Main.TIME_LIMIT_OPT_SOL, chars);
                sol.checkCollisions();

                Helpers.printToFile(chars.fullFileNameTerminalOut,
                        "\nChecked objective value is " + sol.getObjValue() + "\n");
            }

            int useCP = 0;
            if(!Main.HEUR_MINIMIZE_USING_ILP) {
                useCP = 1;
            }

            for (int useCPInHeur = useCP; useCPInHeur < 2; useCPInHeur++) {
                long startTimeOpt = System.currentTimeMillis();
                sol = Minimize(schedActs, chars, (useCPInHeur > 0));

                if(useCPInHeur > 0) {
                    stat.runTimeOptPhaseCPHeur[Main.WAY_TO_SET_DELAY_ELEMENT_INFEASIBLE] = System.currentTimeMillis() - startTimeOpt;
                    stat.critOptCPHeur[Main.WAY_TO_SET_DELAY_ELEMENT_INFEASIBLE] = sol.getObjValue();

                }
                else {
                    stat.runTimeOptPhaseILPHeur[Main.WAY_TO_SET_DELAY_ELEMENT_INFEASIBLE] = System.currentTimeMillis() - startTimeOpt;
                    stat.critOptILPHeur[Main.WAY_TO_SET_DELAY_ELEMENT_INFEASIBLE] = sol.getObjValue();
                }

                Helpers.printToFile(chars.fullFileNameTerminalOut,
                        "\n\n\n Final heuristic solution:\n Objective value is "+ sol.getObjValue() +
                                "\n Runtime of optimization is " + (System.currentTimeMillis() - startTimeOpt));

            }
        }
        else{
            sol = schedActs.convertToSolution();
        }

        return sol;
    }

    private void PrintActsInPriorityOrder(List<Integer> priorities){
        System.out.println("");
        System.out.println("Activities in the order of scheduling are:");

        for (int i = 0; i < numActs; i++) {
            System.out.println("id: " + probInst.getActs().get(priorities.get(i)).getID() +
                    "| period: " + probInst.getActs().get(priorities.get(i)).getPeriod() +
                    "| processing time: " + probInst.getActs().get(priorities.get(i)).getProcTime() +
                    "| assignment to resources: " + probInst.getActs().get(priorities.get(i)).getAssToRes() +
                    " | assignment to cluster: " + probInst.getActs().get(priorities.get(i)).getAssToApp());
        }

        System.out.println("");
    }
}