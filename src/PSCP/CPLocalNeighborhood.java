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

public class CPLocalNeighborhood extends OptimalModelCP {
    private ScheduledActivities schedActs;

    public CPLocalNeighborhood(ProblemInstance probIns,
                               boolean[] isActInNeigborhood_, ScheduledActivities schedActs_) throws IloException{
        super(probIns, isActInNeigborhood_, schedActs_);
        schedActs = schedActs_;
        isLocalNeiborhood = true;
        
        SetExistingSchedActs();
    }
    
    private void SetExistingSchedActs() throws IloException{
        for (int i = 0; i < taskNumbers.size(); i++) {
            if(!isActInNeigh[taskNumbers.get(i)]) {
                if(Main.SOLVE_CP_INTERVAL_VARS) {
                    stTasksInts[i][0].setStartMin(schedActs.getActST()[taskNumbers.get(i)][0]);
                    stTasksInts[i][0].setStartMax(schedActs.getActST()[taskNumbers.get(i)][0]);
                }
                else {
                    stTasks[i].setStartMin(schedActs.getActST()[taskNumbers.get(i)][0]);
                    stTasks[i].setStartMax(schedActs.getActST()[taskNumbers.get(i)][0]);
                }
            }
        }
        
        for (int i = 0; i < messageNumbers.size(); i++) {
            for (int j = 0; j < prInst.getActs().get(messageNumbers.get(i)).getNJobs(); j++) {
                if(!isActInNeigh[messageNumbers.get(i)]) {
                    stMes[i][j].setStartMin(schedActs.getActST()[messageNumbers.get(i)][j]);
                    stMes[i][j].setStartMax(schedActs.getActST()[messageNumbers.get(i)][j]);
                }
            }
        }
    }
}
