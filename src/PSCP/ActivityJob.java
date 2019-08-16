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

public class ActivityJob{
    private int nAct;
    private int nJob;
    private boolean scheduleFullAct;

    private Activity act;

    public ActivityJob(Activity act, boolean scheduleFullAct_) {
        scheduleFullAct = scheduleFullAct_;
        if(act == null) {
            this.nAct = -1;
        }
        else {
            this.nAct = act.getID();
            this.act = act;
        }
    }

    public ActivityJob(Activity act, int nJob, boolean scheduleFullAct_) {
        this.nAct = act.getID();
        this.act = act;
        this.nJob = nJob;
        scheduleFullAct = scheduleFullAct_;
    }

    public ActivityJob(Activity act, int nJob) {
        this.nJob = nJob;
        if(act == null) {
            this.nAct = -1;
        }
        else {
            this.nAct = act.getID();
            this.act = act;
        }
        scheduleFullAct = true;
    }

    public int getNAct() {
        return nAct;
    }

    public int getnJob() {
        return nJob;
    }

    public Activity getAct() {
        return act;
    }

    public boolean scheduleFullAct() {
        return scheduleFullAct;
    }

    public boolean equals(ActivityJob actJob) {
        if(scheduleFullAct) {
            return nAct == actJob.getNAct();
        }
        else {
            return nAct == actJob.getNAct() && nJob == actJob.getnJob();
        }

    }

    public void setScheduleFullAct(boolean scheduleFullAct) {
        this.scheduleFullAct = scheduleFullAct;
    }

    public void print() {
        System.out.println("Number of activity is " + nAct + ", number of job is " + nJob);
    }
}