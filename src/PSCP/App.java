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

/**
 *
 * @author annaminaeva
 */
import ilog.concert.IloException;
import java.io.IOException;
import java.util.*;

public class App implements Comparable<App>  {
    private int period;
    private int e2eLatBound;
    private List<Integer> numbersOfActivities;
    private List<Activity> activities;
    private List<Activity> tasks;
    private List<Activity> rootActs; // numbers in activities array
    private List<Activity> leafActs; // numbers in activities array
    private List<List<Integer>> precedenceAdjList;
    private List<List<Integer>> taskPrecedenceAdjList;
    private List<List<Integer>> successorsAdjList;
    private List<List<Integer>> allPredecessorsAdjList;
    private List<List<Integer>> allSuccessorsAdjList;
    private Integer[] criticalLengthsBefore;
    private Integer[] criticalLengthsAfter; //contains processing time of the activity itself
    private boolean[] occupiedResources;
    private boolean isProblematic;
    private int nApp;
    private int minE2ELatency;
    private Double[] resourceUtilization;
    private List<List<Integer>> actsOnTheSameResource;
    private double[] perfValues;
    private double[] delays;
    private Integer[] actsIdealST;
    private boolean containsVideo;
    private boolean isOnlyMessage;
    private boolean isOrderCritical;

    private ProblemInstance probInst;

    public boolean isOrderCritical() {
        return isOrderCritical;
    }

    public App(List<Activity> activities_, List<List<Integer>> precedenceAdjList_,
               List<List<Integer>> followersAdjList_, ProblemInstance prInst_, boolean isFinal)
            throws IloException, IOException {
        probInst = prInst_;
        activities = activities_;
        period = activities.get(0).getPeriod();
        e2eLatBound = 2 * period;
        nApp = activities.get(0).getAssToApp();

        if(precedenceAdjList_ != null) {
            precedenceAdjList = precedenceAdjList_;
        }
        else {
            precedenceAdjList = new ArrayList<>();
            for (int i = 0; i < activities.size(); i++) {
                precedenceAdjList.add(activities.get(i).getDirectPreds());
            }
        }

        if(successorsAdjList != null) {
            successorsAdjList = followersAdjList_;
        }
        else {
            successorsAdjList = new ArrayList<>();
            for (int i = 0; i < activities.size(); i++) {
                successorsAdjList.add(activities.get(i).getDirectPreds());
            }
        }

        isOnlyMessage = true;
        for (int i = 0; i < activities.size(); i++) {
            activities.get(i).setE2eLatBound(e2eLatBound);
            if(activities.get(i).isTask()) {
                isOnlyMessage = false;
            }
        }

        isOrderCritical = activities.size() != 1 && !isOnlyMessage;

        if(isFinal) {
            doNecessaryComputations();
            for (int i = 0; i < activities.size(); i++) {
                if(!activities.get(i).isTask() && activities.get(i).getProcTime() > Main.MAX_COMM_TIME_CONTROL){
                    containsVideo = true;
                    break;
                }
            }
        }
    }

    public void computeAndSetTailsAndHeads(){
        computeCriticalLengthBeforeAndAfterForEachActivity();
        findAllPredecessorsAndSuccessors();
        for(int i = 0; i < activities.size(); i++) {
            activities.get(i).setAllPredecessors(allPredecessorsAdjList.get(i));
            activities.get(i).setAllSuccessors(allSuccessorsAdjList.get(i));
        }
        
        improveHeadsAndTails();
        
        for(int i = 0; i < activities.size(); i++) {
            activities.get(i).setCriticalLengthBefore(criticalLengthsBefore[i]);
            activities.get(i).setCriticalLengthAfter(criticalLengthsAfter[i]);
            activities.get(i).setLB(criticalLengthsBefore[i]);
            if(activities.get(i).getDirectPreds().isEmpty() && activities.get(i).isTask())  {
                activities.get(i).setUB(period - 1);
            }
            else {
                activities.get(i).setUB(period - 1 + e2eLatBound - criticalLengthsAfter[i]);
            }
            
            activities.get(i).computeSlack();
            if(isProblematic){
                return;
            }
        }
    }

    public void doNecessaryComputations() throws IloException, IOException {
        for (int i = 0; i < activities.size(); i++) {
            activities.get(i).setIdInDagActArray(i);
        }

        fillNumbersOfActivities();
        fillPredsAndSuccs();
        fillRootAndLeafActivities();
        computeAndSetTailsAndHeads();
        if(activities.size() > 1){
            findScheduleForApp();
            if(isProblematic){
                return;
            }
        }

        computeResourcesUtilization();
        fillOccupiedResources();
        computeMinE2ELatency();
        fillActsOnTheSameResource();
        
        if(Main.IS_DEBUG_MODE){
            checkCycleInClus();
            checkEveryActivityHasSamePeriod();
        }
    }

    public void addMessage(Activity act){
        activities.add(act);
        precedenceAdjList.add(act.getDirectPreds());
        successorsAdjList.add(act.getDirectSucc());
        act.setIdInDagActArray(activities.size() - 1);
    }

    public void removeTaskToTaskDependencies(int nTaskPred, int nTaskSucc){
        activities.get(nTaskPred).removeSuccessor(activities.get(nTaskSucc).getID());
        activities.get(nTaskSucc).removePredecessor(activities.get(nTaskPred).getID());
    }

    public void removeMessages(){
        for (int i = 0; i < activities.size(); i++) {
            if (!activities.get(i).isTask()) {
                activities.remove(i);
                i--;
            } else {
                activities.get(i).setIdInDagActArray(i);
            }
        }

        // create followers and successors list just for tasks
        precedenceAdjList = new ArrayList<List<Integer>>();
        successorsAdjList = new ArrayList<List<Integer>>();
        taskPrecedenceAdjList = new ArrayList<List<Integer>>();
        tasks = new ArrayList<Activity>();
        fillNumbersOfActivities();
        for (int i = 0; i < activities.size(); i++) {
            precedenceAdjList.add(activities.get(i).getDirectPreds());
            taskPrecedenceAdjList.add(new ArrayList<Integer>());

            for (int j = 0; j < activities.get(i).getDirectPreds().size(); j++) {
                taskPrecedenceAdjList.get(i).add(numbersOfActivities.indexOf(activities.get(i).getDirectPreds().get(j)));
            }

            successorsAdjList.add(activities.get(i).getDirectSucc());
            tasks.add(activities.get(i));
        }
    }

    private void computeResourcesUtilization(){
        resourceUtilization = new Double[probInst.getnResources()];
        Helpers.initializeTo(resourceUtilization, 0.0);
        for (int i = 0; i < activities.size(); i++) {
            resourceUtilization[activities.get(i).getAssToRes() - 1] += (activities.get(i).getProcTime() * 1.0 / period);
        }
    }

    private void fillActsOnTheSameResource(){
        actsOnTheSameResource = new ArrayList<>();
        for (int i = 0; i < activities.size(); i++) {
            actsOnTheSameResource.add(new ArrayList<Integer>());
        }
        
        for (int i = 0; i < activities.size(); i++) {
            for (int j = i + 1; j < activities.size(); j++) {
                if(activities.get(i).getAssToRes() == activities.get(j).getAssToRes()){
                    actsOnTheSameResource.get(i).add(j);
                    actsOnTheSameResource.get(j).add(i);
                }
            }
        }
        
    }
    
    private void fillNumbersOfActivities(){
        numbersOfActivities = new ArrayList<>();
        for(int i = 0; i < activities.size(); i++) {
            numbersOfActivities.add(activities.get(i).getID());
        } 
    }
    
    private void checkCycleInClus(){
        for(int i = 0; i < activities.size(); i++) {
            if(Math.abs(criticalLengthsAfter[i]) > period * 10 || Math.abs(criticalLengthsBefore[i]) > period * 10){
                System.out.println("\n\n\nThere is a cycle!\n\n\n");
                isProblematic = true;
            }
        }
    }
    
    private void checkEveryActivityHasSamePeriod(){
        for (int i = 0; i < activities.size(); i++) {
            if(activities.get(i).getPeriod() != period){
               System.out.println("Periods in application " + nApp + " are not the same");
               System.exit(0);
            }
        }
    }
    
    private void fillOccupiedResources(){
        occupiedResources = new boolean[probInst.getnResources()];
        for (int i = 0; i < activities.size(); i++) {
            occupiedResources[activities.get(i).getAssToRes() - 1] = true;
        }
    }
    
    private void fillRootAndLeafActivities(){
        leafActs = new ArrayList<>();
        rootActs = new ArrayList<>();
        for(int i = 0; i < activities.size(); i++) {
            if(activities.get(i).getDirectPreds().isEmpty()){
                rootActs.add(activities.get(i));
            }
            
            if(activities.get(i).getDirectSucc().isEmpty()){
                leafActs.add(activities.get(i));
                activities.get(i).setIsLeaf(true);
            }
        }
    }

    private void fillPredsAndSuccs(){
        precedenceAdjList = new ArrayList<>();
        successorsAdjList = new ArrayList<>();
        for (int i = 0; i < activities.size(); i++) {
            precedenceAdjList.add(activities.get(i).getDirectPreds());
            successorsAdjList.add(activities.get(i).getDirectSucc());
        }

        findAllPredecessorsAndSuccessors();
    }

    private void computeMinE2ELatency(){
        minE2ELatency = 0;
        if(activities.size() > 1 && actsIdealST != null && actsIdealST[0] != null) {
            for (int i = 0; i < leafActs.size(); i++) {
                for (int j = 0; j < rootActs.size(); j++) {
                    int curE2E = actsIdealST[leafActs.get(i).getIdInAppActArray()] + activities.get(leafActs.get(i).getIdInAppActArray()).getProcTime() -
                            actsIdealST[rootActs.get(j).getIdInAppActArray()];
                    if (minE2ELatency < curE2E) {
                        minE2ELatency = curE2E;
                    }
                }
            }
        }
        else{
            for (int i = 0; i < rootActs.size(); i++) {
                int curE2E = rootActs.get(i).getCriticalLengthAfter();
                if (minE2ELatency < curE2E) {
                    minE2ELatency = curE2E;
                }
            }
        }
    }

    private void BFS(int numRootNode, boolean isForCriticalLengthBefore){
        List<List<Integer>> adjList;
        Integer[] criticalLength;
        if(isForCriticalLengthBefore){
            adjList = successorsAdjList;
            criticalLength = criticalLengthsBefore;
        }
        else{
            adjList = precedenceAdjList;
            criticalLength = criticalLengthsAfter;
        }
        
        Queue<Integer> Q = new PriorityQueue<>();
        int numNodeToAddCost = numRootNode;
        for(int i = 0; i < adjList.get(numRootNode).size(); i++) {
            int childNodeInActs = probInst.getActs().get(adjList.get(numRootNode).get(i)).getIdInAppActArray();
            if(!isForCriticalLengthBefore){
                numNodeToAddCost = childNodeInActs;
            }
            if(criticalLength[childNodeInActs] < criticalLength[numRootNode] + activities.get(numNodeToAddCost).getProcTime()){
                criticalLength[childNodeInActs] = criticalLength[numRootNode] + activities.get(numNodeToAddCost).getProcTime();
                Q.add(adjList.get(numRootNode).get(i));
            }
        }        
        
        while(!Q.isEmpty()){
            int parentNodeInacts = probInst.getActs().get(Q.poll()).getIdInAppActArray();
            numNodeToAddCost = parentNodeInacts;
            
            for(int i = 0; i < adjList.get(parentNodeInacts).size(); i++) {
                int childNodeInActs = probInst.getActs().get(adjList.get(parentNodeInacts).get(i)).getIdInAppActArray();
                if(!isForCriticalLengthBefore){
                    numNodeToAddCost = childNodeInActs;
                }

                if(criticalLength[childNodeInActs] > e2eLatBound){
                    System.out.println("Instance is not schedulable - either there is a cycle or precedence relations are too demanding");
                    isProblematic = true;
                    return;
                }
                
                if(criticalLength[childNodeInActs] < criticalLength[parentNodeInacts] + 
                        activities.get(numNodeToAddCost).getProcTime()){
                    criticalLength[childNodeInActs] = criticalLength[parentNodeInacts] +
                            activities.get(numNodeToAddCost).getProcTime();
                    Q.add(adjList.get(parentNodeInacts).get(i));
                }
            }
        }
        
        if(isForCriticalLengthBefore){
            criticalLengthsBefore = criticalLength;
        }
        else{
            criticalLengthsAfter = criticalLength;
        }
    }
    
    private void computeCriticalLengthBeforeAndAfterForEachActivity(){
        criticalLengthsBefore = new Integer[activities.size()];
        criticalLengthsAfter = new Integer[activities.size()];
        Helpers.initializeTo(criticalLengthsBefore, 0);
        Helpers.initializeTo(criticalLengthsAfter, 0);
        
        for(int i = 0; i < activities.size(); i++) {
            if(precedenceAdjList.get(i).isEmpty()){
                //It is a root node. Start BFS in this node.
                BFS(i, true);
                if(isProblematic){
                    return;
                }
            }
        }
        
        for(int i = 0; i < activities.size(); i++) {
            if(successorsAdjList.get(i).isEmpty()){
                criticalLengthsAfter[i] = activities.get(i).getProcTime();
                BFS(i, false);
                if(isProblematic){
                    return;
                }
            }
        }

        //check that minimal e2e latency is less than the limit on it
        if(criticalLengthsBefore[0] + criticalLengthsAfter[0] >= e2eLatBound){
            isProblematic = true;
            return;
        }
    }

    private void findAllPredecessorsAndSuccessors(){
        allPredecessorsAdjList = new ArrayList<List<Integer>>();
        allSuccessorsAdjList = new ArrayList<List<Integer>>();
        for(int i = 0; i < activities.size(); i++) {
            allPredecessorsAdjList.add(new ArrayList<Integer>());
            allSuccessorsAdjList.add(new ArrayList<Integer>());
            for(int j = 0; j < precedenceAdjList.get(i).size(); j++) {
                allPredecessorsAdjList.get(i).add(precedenceAdjList.get(i).get(j));
            }
        }
        
        for(int i = 0; i < activities.size(); i++) {
            Queue<Integer> Q = new PriorityQueue<>();
            for(int j = 0; j < precedenceAdjList.get(i).size(); j++) {
                Q.add(precedenceAdjList.get(i).get(j));
            }
            
            while(!Q.isEmpty()){
                int numParent = Q.poll();
                List<Integer> preds = precedenceAdjList.get(probInst.getActs().get(numParent).getIdInAppActArray());
                for(int j = 0; j < preds.size(); j++) {
                    if(!allPredecessorsAdjList.get(i).contains(preds.get(j))){
                        allPredecessorsAdjList.get(i).add(preds.get(j));
                        Q.add(precedenceAdjList.get(probInst.getActs().get(numParent).getIdInAppActArray()).get(j));
                    }
                }
            }
            
            for(int j = 0; j < allPredecessorsAdjList.get(i).size(); j++) {
                int numAct = probInst.getActs().get(allPredecessorsAdjList.get(i).get(j)).getIdInAppActArray();
                allSuccessorsAdjList.get(numAct).add(activities.get(i).getID());
            }
        }
    }
    
    private void improveHeadsAndTails(){
        // compute the processing time of the activities in predecessors (successors)
        // that are assigned to the same resource
        for(int i = 0; i < activities.size(); i++) {
            int sumProcTimesOnTheSameResourceBefore = 0;
            int sumProcTimesOnTheSameResourceAfter = activities.get(i).getProcTime();
            for(int j = 0; j < activities.size(); j++) {
                if(j != i && allPredecessorsAdjList.get(i).contains(activities.get(j).getID()) &&
                        activities.get(j).getAssToRes() == activities.get(i).getAssToRes()){
                    sumProcTimesOnTheSameResourceBefore += activities.get(j).getProcTime();
                }
            }
            
            for(int j = 0; j < activities.size(); j++) {
                if(j != i && allSuccessorsAdjList.get(i).contains(activities.get(j).getID()) &&
                        activities.get(j).getAssToRes() == activities.get(i).getAssToRes()){
                    sumProcTimesOnTheSameResourceAfter += activities.get(j).getProcTime();
                }
            }
            if(sumProcTimesOnTheSameResourceBefore > criticalLengthsBefore[i]){
                criticalLengthsBefore[i] = sumProcTimesOnTheSameResourceBefore;
            }
            if(sumProcTimesOnTheSameResourceAfter > criticalLengthsAfter[i]){
                criticalLengthsAfter[i] = sumProcTimesOnTheSameResourceAfter;
            }
        }
    }
    
    private void findScheduleForApp() throws IloException, IOException {
        boolean isScheduleEmpty = true;
        ILPOneApp model = new ILPOneApp(this, isScheduleEmpty, null);
        List<Integer> problematicResources = null;
        Solution sol = model.Solve(problematicResources);

        actsIdealST = new Integer[activities.size()];
        // if there is no solution, all start times has 0 start times as it is used only for minimum e2e latency computation
        if(sol != null) {
            for (int i = 0; i < activities.size(); i++) {
                actsIdealST[i] = sol.getStartTimes()[i][0];
            }

            for(int i = 0; i < activities.size(); i++) {
                if(activities.get(i).getDirectPreds().isEmpty()){
                    if(criticalLengthsAfter[i] < sol.getStartTimes()[i][0]){
                        criticalLengthsAfter[i] = sol.getStartTimes()[i][0];
                    }
                }

                if(activities.get(i).getDirectSucc().isEmpty()){
                    if(criticalLengthsBefore[i] + criticalLengthsAfter[i] < sol.getStartTimes()[i][0]){
                        criticalLengthsBefore[i] = sol.getStartTimes()[i][0] - activities.get(i).getProcTime();
                    }
                }
            }
        }
    }

    // creates a transitive closure of the dependency graph
    private void makeTransitiveClosureOfTheDAG(){
        // sometimes, precedence constraints are duplicate, i.e. there is an 
        // explicit edge in the place, where there is longer precedence dependency
        // we want to get rid of such edges
        for (int i = 0; i < activities.size(); i++) {
            for (int j = 0; j < precedenceAdjList.get(i).size(); j++) {
                for (int k = j + 1; k < precedenceAdjList.get(i).size(); k++) {
                    int predKofI = probInst.getActs().get(precedenceAdjList.get(i).get(k)).getIdInAppActArray();
                    int predJofI = probInst.getActs().get(precedenceAdjList.get(i).get(j)).getIdInAppActArray();
                    if(allPredecessorsAdjList.get(predKofI).contains(precedenceAdjList.get(i).get(j))){
                        if(activities.get(i).getDirectPreds().contains(precedenceAdjList.get(i).get(j))){
                            activities.get(i).removePredecessor(precedenceAdjList.get(i).get(j));
                            activities.get(predJofI).removeSuccessor(activities.get(i).getID());
                        }
                    }
                    
                    if(allPredecessorsAdjList.get(predJofI).contains(precedenceAdjList.get(i).get(k))){                      
                        if(activities.get(i).getDirectPreds().contains(precedenceAdjList.get(i).get(k))){
                            activities.get(i).removePredecessor(precedenceAdjList.get(i).get(k));
                            activities.get(predKofI).removeSuccessor(activities.get(i).getID());
                        }
                    }
                }
            }
        }
    }
    
    public int compareTo(App clus) {
        return Integer.compare(this.nApp, clus.nApp);
    }

    public void squeezeProcTimes(double coeff) throws IOException, IloException {
        for (int i = 0; i < activities.size(); i++) {
            activities.get(i).setProcTime(Math.max((int) Math.floor(activities.get(i).getProcTime() / coeff), 1));
        }

        computeCriticalLengthBeforeAndAfterForEachActivity();
        for(int i = 0; i < activities.size(); i++) {
            activities.get(i).setCriticalLengthBefore(criticalLengthsBefore[i]);
            activities.get(i).setCriticalLengthAfter(criticalLengthsAfter[i]);
            activities.get(i).computeSlack();
        }

        improveHeadsAndTails();
        computeMinE2ELatency();
    }

    public void convertToContainVideoTraffic(int[] minPeriodOnResources){
        findAllPredecessorsAndSuccessors();

        int procTime = (int) (period * Main.DESIRABLE_UTILIZATION_OF_VIDEO_MESSAGE);
        // It is important not to allow messages to be longer than minimal period present on the link
        // for this purpose, find minimal period on all the used resources

        // set minimum period among all used links
        int minPeriod = Integer.MAX_VALUE;
        for (int i = 0; i < activities.size(); i++) {
            if(!activities.get(i).isTask()) {
                int curUsedRes = activities.get(i).getAssToRes() - 1;
                if(minPeriod > minPeriodOnResources[curUsedRes]) {
                    minPeriod = minPeriodOnResources[curUsedRes];
                }
            }
        }

        if (procTime > (int) (minPeriod * Main.MAX_UTILIZATION_IN_MIN_PERIOD)) {
            procTime = (int) (minPeriod * Main.MAX_UTILIZATION_IN_MIN_PERIOD);
        }

        for (int i = 0; i < activities.size(); i++) {
            if(!activities.get(i).isTask()) {
                activities.get(i).setProcTime(procTime);
            }
        }
    }

    public void addSuccessorToTask(int nActInAppActArray, int nSucc){
        activities.get(nActInAppActArray).addSucc(nSucc);
        successorsAdjList.get(nActInAppActArray).add(nSucc);
    }

    public void addPredecessorToTask(int nActInAppActArray, int nPred){
        activities.get(nActInAppActArray).addPred(nPred);
        precedenceAdjList.get(nActInAppActArray).add(nPred);
    }

    public void printApp(){
        System.out.println();
        System.out.println("-----------------------------------------------New application------------------------------------------------");
        System.out.println("Numbers of activities are " + numbersOfActivities);
        System.out.println("Period is " + period);
        System.out.println("Number of cluster is " + nApp);

        if(resourceUtilization != null) {
            System.out.print("Resource utilizations are [");
            for (int i = 0; i < activities.get(0).getnResources() - 1; i++) {
                System.out.print(resourceUtilization[i] + " ");
            }
            System.out.print(resourceUtilization[activities.get(0).getnResources() - 1] + "]\n\n");
        }

        if(rootActs != null && leafActs != null) {
            System.out.print("Root activities are [");
            for (int i = 0; i < rootActs.size() - 1; i++) {
                System.out.print(numbersOfActivities.get(rootActs.get(i).getID()) + " ");
            }
            System.out.print(numbersOfActivities.get(rootActs.get(rootActs.size() - 1).getID()) + "]\n\n");

            System.out.print("Leaf activities are [");
            for (int i = 0; i < leafActs.size() - 1; i++) {
                System.out.print(numbersOfActivities.get(leafActs.get(i).getID()) + " ");
            }
            System.out.print(numbersOfActivities.get(leafActs.get(leafActs.size() - 1).getID()) + "]\n\n");
        }

        System.out.println("Precedence relations are the following: ");
        for (int i = 0; i < activities.size(); i++) {
            System.out.println("Activity " + numbersOfActivities.get(i) + " has the following predecessors: " + precedenceAdjList.get(i));
        }


    }
    public void printDelayValues() {
        System.out.println("Delays of application " + nApp + " are:");
        for (int i = 0; i < delays.length; i++) {
            System.out.print(delays[i] + " ");
        }
        System.out.println();
    }

      
    public List<Integer> getActivitiesNumbers() {
        return numbersOfActivities;
    }

    public int getPeriod() {
        return period;
    }

    public int getNumActs() {
        return activities.size();
    }

    public List<Activity> getActs() {
        return activities;
    }

    public ProblemInstance getPrInst() {
        return probInst;
    }

    public boolean isProblematic() {
        return isProblematic;
    }

    public boolean[] getOccupiedResources() {
        return occupiedResources;
    }

    public List<Activity> getRootActs() {
        return rootActs;
    }

    public List<Activity> getLeafActs() {
        return leafActs;
    }

    public Double[] getResourceUtilization() {
        return resourceUtilization;
    }

    public List<List<Integer>> getActsOnSameRes() {
        return actsOnTheSameResource;
    }
    
    public int getMaxProcTimeOnResourse(int nRes) {
        int maxExTime = 0;
        for (int i = 0; i < activities.size(); i++) {
            if(activities.get(i).getAssToRes() - 1 == nRes && activities.get(i).getProcTime() > maxExTime){
                maxExTime = activities.get(i).getProcTime();
            }
        }
        return maxExTime;
    }

    public int getNApp() {
        return nApp;
    }

    public boolean isOnlyMessages() {
        return isOnlyMessage;
    }

    public double getTotalResourceUtilization(){
        double ut = 0;
        for (int i = 0; i < resourceUtilization.length; i++) {
            ut += resourceUtilization[i];
        }
        return ut;
    }

    public List<List<Integer>> getSuccessorsAdjList() {
        return successorsAdjList;
    }

    public List<List<Integer>> getPrecedenceAdjList() {
        return precedenceAdjList;
    }

    public void setActivities(List<Activity> activities) {
        this.activities = activities;
    }

    //latency is maximum over all leaf-to-root pairs
    public int getMinE2ELatency() {
        return minE2ELatency;
    }

    public void setControlValues(double[] delays_, double[] perfValues_){
        delays = delays_;
        perfValues = perfValues_;
    }

    public void setDelay(int i, double value) {
        delays[i] = value;
    }

    public double[] getPerfValues(){
        return perfValues;
    }

    public double[] getDelays(){
        return delays;
    }

    public void setPeriod(int period_) {
        period = period_;

        for (int i = 0; i < activities.size(); i++) {
            activities.get(i).setPeriod(period);
            activities.get(i).computeSlack();
            activities.get(i).setNumJobs(probInst.getHP() / period);

        }
    }

    public boolean containsVideo() {
        return containsVideo;
    }

    public void removeFirstActivity() {
        activities.remove(0);
        precedenceAdjList.remove(0);
        successorsAdjList.remove(0);
    }
    
    public void checkPrecRelsInApp(){
        numbersOfActivities = new ArrayList<>();
        for (int i = 0; i < activities.size(); i++) {
            numbersOfActivities.add(activities.get(i).getID());
        }
        
        for (int i = 0; i < activities.size(); i++) {
            for (int j = 0; j < activities.get(i).getDirectPreds().size(); j++) {
                if(!numbersOfActivities.contains(activities.get(i).getDirectPreds().get(j))) {
                    System.out.println("In application " + nApp + " activity with ID " + activities.get(i).getID() + " has predecessor "
                    + activities.get(i).getDirectPreds().get(j) + ", which is not in this application");
                    System.exit(0);
                }
            }
        }
    }

    public Integer[] getCriticalLengthsAfter() {
        return criticalLengthsAfter;
    }

    public Integer[] getCriticalLengthsBefore() {
        return criticalLengthsBefore;
    }

    public int getE2eLatBound() {
        return e2eLatBound;
    }
}

