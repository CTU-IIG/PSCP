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

public class ReasonOfDelay {
    private String reason; // "Infeasible", "No reason", "Resource", "Prec", "ZJ"
    private List<ActivityJob> delayActivities; // activities that are reason of lateness of this activity
    private int latestFTofPred;

    private ProblemInstance prInst;

    public ReasonOfDelay() {
        reason = "No reason";
    }

    public String getDelay() {
        return reason;
    }

    public List<ActivityJob> getDelayActs() {
        return delayActivities;
    }
    
    public int getLatestFTofPred() {
        return latestFTofPred;
    }
    
    public void setDelay(String reason) {
        this.reason = reason;
    }

    public void setDelayActivities(ActivityJob actJob) {
        delayActivities = new ArrayList<>();
        delayActivities.add(actJob);
    }

    public void setDelayActivities(List<ActivityJob> actJob) {
        delayActivities = actJob;
    }

    public void setDelayActivities(ProblemInstance prInst_, List<Integer> pred, int nJob) {
        prInst = prInst_;
        delayActivities = new ArrayList<>();
        for (int i = 0; i < pred.size(); i++) {
            delayActivities.add(new ActivityJob(prInst.getActs().get(pred.get(i)), nJob));
        }

    }

    public void setLatestFTofPred(int latestSTofPred) {
        this.latestFTofPred = latestSTofPred;
    }


    public void addDelayActs(ActivityJob reasActJob){
        delayActivities.add(reasActJob);
    }

    public boolean delayActsContainAct(int numAct) {
        for (int i = 0; i < delayActivities.size(); i++) {
            if(delayActivities.get(i).getNAct() == numAct) {
                return true;
            }
        }
        return false;
    }
    
    public void clearReasonActivities(){
        delayActivities.clear();
    }

    public void print(int ID) {
        System.out.println("Reason for activity with ID " + ID + " is " + reason);
        System.out.print("Reason activities are: ");
        for (int i = 0; i < delayActivities.size(); i++) {
            System.out.print(delayActivities.get(i) + " ");
        }
        System.out.println("\nLatest finish time of predecessor is " + latestFTofPred);
    }
}
