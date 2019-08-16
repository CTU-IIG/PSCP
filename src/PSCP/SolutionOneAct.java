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

public class SolutionOneAct {
    int[] startTimes;
    private Activity activity;

    public SolutionOneAct(double[] startTimes, Activity activity) {
        this.startTimes = new int[startTimes.length];
        for(int i = 0; i < startTimes.length; i++){
            this.startTimes[i] = (int) Math.round(startTimes[i]);
        }
        this.activity = activity;
    }

    public int[] getStartTimes() {
        return startTimes;
    }

    public Activity getActivity() {
        return activity;
    }
    
    public void PrintSchedule(){
        System.out.println();
        for(int i = 0; i < startTimes.length; i++) {
            System.out.print(startTimes[i] + ", ");
        }
        System.out.println();
        System.out.println();
    }
}
