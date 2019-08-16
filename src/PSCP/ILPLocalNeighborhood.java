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

public class ILPLocalNeighborhood extends OptimalModelILP {
    private ScheduledActivities schedActs;
    
    public ILPLocalNeighborhood(ProblemInstance probIns, boolean[] isActInNeigborhood_,
                                Main.RunningCharacteristics chars, ScheduledActivities schedActs_)
            throws IloException, IOException {
        super(probIns, false, isActInNeigborhood_, chars, schedActs_);
        schedActs = schedActs_;
        isLocalNeiborhood = true;
        
        SetExistingSchedActs();
    }
    
    private void SetExistingSchedActs() throws IloException{
        for (int i = 0; i < taskNumbers.size(); i++) {
            if(!isActInNeigh[taskNumbers.get(i)]) {
                stTasks[i].setLB(schedActs.getActST()[taskNumbers.get(i)][0]);
                stTasks[i].setUB(schedActs.getActST()[taskNumbers.get(i)][0]);
            }
        }
        
        for (int i = 0; i < messageNumbers.size(); i++) {
            for (int j = 0; j < prInst.getActs().get(messageNumbers.get(i)).getNJobs(); j++) {
                if(!isActInNeigh[messageNumbers.get(i)]) {
                    stMessages[i][j].setLB(schedActs.getActST()[messageNumbers.get(i)][j]);
                    stMessages[i][j].setUB(schedActs.getActST()[messageNumbers.get(i)][j]);
                }
            }
        }
    }
    
}
