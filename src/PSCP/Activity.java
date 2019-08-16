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
 * @author minaeann
 */
public class Activity implements Comparable<Activity> {
    private int assToRes; //it is from 1 to n, need to substract 1 when using
    private int assToApp;
    private int procTime;
    private int period;
    private int ID;
    private int idInAppActArray;
    private int numJobs;
    private int criticalLengthBefore;
    private int criticalLengthAfter;
    private int slack;
    private List<Integer> directPredecessors;
    private List<Integer> allPredecessors; 
    private List<Integer> directSuccessors;
    private List<Integer> allSuccessors;
    private int HP;
    private int nResources;
    private boolean isLeaf;
    private boolean isRoot;
    private int numInTaskOrMessageArrayForILP;
    private boolean isTask;
    private boolean shouldBeScheduled;

    // these fields only make sense if it is non-order-critical message, i.e. its precedence list is empty and it is a message:
    private int sendingTask;
    private int receivingTask;
    private int LB;
    private int UB;
    private int e2eLatBound;
    
    private ReasonOfDelay[] delay;

    public Activity(int assToRes_, int procTime_, int requiredPeriod,
                    int idInInputData, int numJobs, List<Integer> directPredecessors,
                    List<Integer> directSuccessors, int HP, int assToApp_, boolean isTask_, int nRes) {
        this(procTime_, requiredPeriod, idInInputData, numJobs, directPredecessors, 
                directSuccessors, HP, isTask_, 0, 0, 0);
        assToRes = assToRes_;
        assToApp = assToApp_;
        nResources = nRes;
    }

    public Activity(int procTime_, int requiredPeriod, int idInInputData, 
            int numJobs, List<Integer> directPredecessors, List<Integer> directSuccessors,
            int HP, boolean isTask_, int sendingTask_, int receivingTask_, int assToRes_) {
        sendingTask = sendingTask_;
        receivingTask = receivingTask_;
        procTime = procTime_;
        this.period = requiredPeriod;
        this.ID = idInInputData;
        this.numJobs = numJobs;
        this.directPredecessors = directPredecessors;
        this.directSuccessors = directSuccessors;
        this.HP = HP;
        isTask = isTask_;
        allPredecessors = new ArrayList<>();
        allSuccessors = new ArrayList<>();
        assToRes = assToRes_;
        if(directPredecessors.isEmpty()) {
            isRoot = true;
        }

        int nJobsForScheduling = isTask ? 1 : numJobs;
        delay = new ReasonOfDelay[nJobsForScheduling];
    }

    public Activity(int assToRes_, int procTime_, int requiredPeriod, int sendingECU_, int receivingECU_,
                    int idInInputData, int numJobs, List<Integer> directPredecessors,
                    List<Integer> directSuccessors, int HP, int assToApp_, int nRes, boolean isTask_) {
        this(assToRes_, procTime_, requiredPeriod, idInInputData, numJobs, 
                directPredecessors, directSuccessors, HP, assToApp_, isTask_, nRes);
        sendingTask = sendingECU_;
        receivingTask = receivingECU_;
    }
    
    public void computeSlack(){
        slack = e2eLatBound - criticalLengthBefore - criticalLengthAfter;
    }
    
    public void removePredecessor(int nAct){
        int nPred = directPredecessors.indexOf(nAct);
        directPredecessors.remove(nPred);
    }
    
    public void removeSuccessor(int nAct){
        int nSucc = directSuccessors.indexOf(nAct);
        directSuccessors.remove(nSucc);
    }
    
    public int compareTo(Activity activity) {
        int priorityForThisActivity = this.slack;
        int priorityForTheOtherActivity = activity.slack;

        return Integer.compare(priorityForThisActivity, priorityForTheOtherActivity);
    }

    public void printActivity(){
        System.out.print("\nID is " + ID + ", ass to app is " + assToApp
                + ", period is " + period+ ", ass to res is " + assToRes 
                + ", proc time is " + procTime + ".");
        System.out.print(" It is " + (isTask() ? "a task." : "a message.") + "\n");
        printPredecessors();
    }

    public void printPredecessors() {
        System.out.print("Predecessors of activity with ID " + ID + " are activities with IDs: \n");
        for (int i = 0; i < directPredecessors.size() - 1; i++) {
            System.out.print(directPredecessors.get(i) + ", ");
        }
        if(directPredecessors.size() > 0) {
            System.out.println(directPredecessors.get(directPredecessors.size() - 1));
        }
    }
    
    public List<Integer> getAllPredecessors() {
        return allPredecessors;
     }

    public int getCriticalLengthBefore() {
        return criticalLengthBefore;
    }

    public int getCriticalLengthAfter() {
        return criticalLengthAfter;
    }

    public int getID() {
        return ID;
    }

    public int getNJobs() {
        return numJobs;
    }

    public int getSlack() {
        return slack;
    }

    public int getAssToRes() {
        return assToRes;
    }

    public void setID(int ID) {
        this.ID = ID;
    }

    public int getProcTime() {
        return procTime;
    }

    public int getPeriod() {
        return period;
    }

    public List<Integer> getDirectPreds() {
        return directPredecessors;
    }

    public List<Integer> getDirectSucc() {
        return directSuccessors;
    }
    
    public List<Integer> getAllSucc() {
        return allSuccessors;
    }

    public int getHP() {
        return HP;
    }

    public int getnResources() {
        return nResources;
    }


    public int getIdInAppActArray() {
        return idInAppActArray;
    }
    
    public int getAssToApp() {
        return assToApp;
    }

    public ReasonOfDelay getDelay(int nJob) {
        if(nJob >= delay.length) {
            return delay[0];
        }
        return delay[nJob];
    }
    
    public int getNumInTaskOrMessageArray() {
        return numInTaskOrMessageArrayForILP;
    }

    public int getReceivingTask() {
        return receivingTask;
    }

    public int getSendingTask() {

        return sendingTask;
    }
    
    public int getEndOfFeasInt(int nJob) {
        return nJob * period + UB;
    }
    
    public int getStartOfFeasInt(int nJob) {
        return nJob * period + LB;
    }

    public boolean isLeaf() {
        return isLeaf;
    }

    public boolean isTask() {
        return isTask;
    }

    public void setAllSuccessors(List<Integer> allSuccessors) {
        this.allSuccessors = allSuccessors;
    }

    public void setProcTime(int processingTimes) {
        this.procTime = processingTimes;
    }
    
    public void setCriticalLengthBefore(int criticalLengthBefore) {
        this.criticalLengthBefore = criticalLengthBefore;
    }

    public void setCriticalLengthAfter(int criticalLengthAfter) {
        this.criticalLengthAfter = criticalLengthAfter;
    }

    public void setAllPredecessors(List<Integer> allPredecessors) {
        this.allPredecessors = allPredecessors;
    }

    public void setAssToApp(int assToApp) {
        this.assToApp = assToApp;
    }

    public void setIdInDagActArray(int idInDagActArray) {
        this.idInAppActArray = idInDagActArray;
    }

    public void setHP(int HP) {
        this.HP = HP;
    }

    public void setNumJobs(int numJobs) {
        this.numJobs = numJobs;
        delay = new ReasonOfDelay[isTask ? 1 : numJobs];
    }

    public void setPeriod(int period) {
        this.period = period;
    }

    public void addDelay(ReasonOfDelay delay_, int nJob) {
        delay[nJob] = delay_;
    }

    public void setIsLeaf(boolean isLeaf) {
        this.isLeaf = isLeaf;
    }

    public void setNumInTaskOrMessageArrayForILP(int numInTaskOrMessageArrayForILP) {
        this.numInTaskOrMessageArrayForILP = numInTaskOrMessageArrayForILP;
        if(numInTaskOrMessageArrayForILP >= 0){
            shouldBeScheduled = true;
        }
    }

    public void setAssToRes(int assToRes) {
        this.assToRes = assToRes;
    }

    public void setIdInInputData(int idInInputData) {
        ID = idInInputData;
    }

    public void setDirectPredecessors(List<Integer> directPredecessors) {
        this.directPredecessors = directPredecessors;
    }

    public void setDirectSuccessors(List<Integer> directSuccessors) {
        this.directSuccessors = directSuccessors;
    }

    public void setnResources(int nResources) {
        this.nResources = nResources;
    }

    public boolean shouldBeScheduled() {
        return shouldBeScheduled;
    }

    public void addSucc(int succ){
        directSuccessors.add(succ);
    }

    public void addPred(int pred){
        directPredecessors.add(pred);
    }

    public void setLB(int LB) {
        this.LB = LB;
    }

    public void setUB(int UB) {
        this.UB = UB;
    }

    public int getLB() {
        return LB;
    }

    public int getUB() {
        return UB;
    }

    public boolean isRoot() {
        return isRoot;
    }

    public List<Integer> isInfeasible(boolean scheduleFullActivity, int nJob) {
        List<Integer> numJobsInfeasible = new ArrayList<>();

        if(scheduleFullActivity) {
            for (int i = 0; i < delay.length; i++) {
                if (delay != null && delay[i].getDelay() == "Infeasible") {
                    numJobsInfeasible.add(i);
                }
            }
        }
        else{
            if (delay != null && delay[nJob].getDelay() == "Infeasible") {
                numJobsInfeasible.add(nJob);
            }
        }

        return numJobsInfeasible;
    }

    public void changePredecessorsAndSuccessorsAfterSorting(int[] idInArray){
        List<Integer> newDirectPredecessors = new ArrayList<Integer>();
        for(int j = 0; j < directPredecessors.size(); j++) {
            newDirectPredecessors.add(idInArray[directPredecessors.get(j)]);
        }
        directPredecessors = newDirectPredecessors;

        List<Integer> newAllPredecessors = new ArrayList<Integer>();
        for(int j = 0; j < allPredecessors.size(); j++) {
            newAllPredecessors.add(idInArray[allPredecessors.get(j)]);
        }
        allPredecessors = newAllPredecessors;

        List<Integer> newDirectSuccessors = new ArrayList<Integer>();
        for(int j = 0; j < directSuccessors.size(); j++) {
            newDirectSuccessors.add(idInArray[directSuccessors.get(j)]);
        }
        directSuccessors = newDirectSuccessors;

        List<Integer> newAllSuccessors = new ArrayList<Integer>();
        for(int j = 0; j < allSuccessors.size(); j++) {
            newAllSuccessors.add(idInArray[allSuccessors.get(j)]);
        }
        allSuccessors = newAllSuccessors;

    }

    public boolean ArePredecessorsScheduled(List<Integer> scheduledActivities){
        boolean[] isPredSched = new boolean [directPredecessors.size()];
        for(int i = 0; i < scheduledActivities.size(); i++) {
            int indexOfDP = directPredecessors.indexOf(scheduledActivities.get(i));
            if(indexOfDP >= 0){
                isPredSched[indexOfDP] = true;
            }
        }

        for(int i = 0; i < directPredecessors.size(); i++) {
            if(!isPredSched[i]){
                return false;
            }
        }

        return true;
    }

    public void setE2eLatBound(int e2eLatBound_) {
        e2eLatBound = e2eLatBound_;
    }

    public int getE2eLatBound() {
        return e2eLatBound;
    }
}
