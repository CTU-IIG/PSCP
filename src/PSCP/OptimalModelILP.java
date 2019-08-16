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
import ilog.cplex.*;
import java.io.*;
import java.util.*;

public class OptimalModelILP extends OptimalModel {
    protected IloCplex fullILPsolver;
    protected IloIntVar[] stTasks;
    protected IloIntVar[][] stMessages;
    private IloNumVar[] quotients;
    private IloNumVar[] latency;
    private IloNumVar[] disjVars;
    private IloNumVar[][] lambdas;
    private IloNumVar[][] gammas;
    private IloNumVar[] controlPerformances;
    private IloNumExpr[] obj;

    private int numResConstr = 0;
    private int curDisjVarNum = 0;

    private boolean isRelaxedOnIntegralityOfQuotients;

    private class LazyConstraintCallback extends IloCplex.LazyConstraintCallback {
        private class Collision {
            int colAct1;
            int colAct2;
            int colJob1;
            int colJob2;
            int nHP1;
            int nHP2;
            int stJobMes1;
            int stJobMes2;

            int collTime;

            public Collision(int collidingAct1, int collidingAct2,
                             int collidingJob1, int collidingJob2,
                             int stJobMes1, int stJobMes2, int collTime_) {
                colAct1 = collidingAct1;
                colAct2 = collidingAct2;
                colJob1 = collidingJob1;
                colJob2 = collidingJob2;
                this.stJobMes1 = stJobMes1;
                this.stJobMes2 = stJobMes2;
                collTime = collTime_;

                nHP1 = stJobMes1 / HP;
                nHP2 = stJobMes2 / HP;
            }

            private void printCollision() {
                int nAct1InActArr = messageNumbers.get(colAct1);
                int nAct2InActArr = messageNumbers.get(colAct2);
                System.out.println("Colliding activities are " + nAct1InActArr + ": job " + colJob1
                        + " and " + nAct2InActArr + " : job " + colJob2 + ". Start times are "
                        + stJobMes1 + " and " + stJobMes2);
                System.out.println("Hyperperiods are " + nHP1 + " " + nHP2);
                acts.get(nAct1InActArr).printActivity();
                acts.get(nAct2InActArr).printActivity();
            }

        }

        @Override
        protected void main() throws IloException {
            try {
                this.checkAndAddIfNeccesary();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private Collision isThereCollision(int[] schedule, int time, int curAct,
                                           int nJobCurAct, int[][] resSTMessages) throws IloException, IOException {
            Collision col = null;
            if (schedule[time] != 0) {
                int colAct2 = schedule[time] - 1;
                int colJob = -1;

                int nJobs = this.getValues(stMessages[colAct2]).length;

                for (int k = 0; k < nJobs - 1; k++) {
                    int stThisJob = resSTMessages[colAct2][k] % HP;
                    int stNextJob = resSTMessages[colAct2][k + 1] % HP;
                    if(stThisJob > stNextJob) {
                        stNextJob += HP;
                    }

                    if(stThisJob <= time && stNextJob > time) {
                        colJob = k;
                        break;
                    }
                }

                if(colJob == -1){
                    colJob = nJobs - 1;
                }

                Helpers.printToFile(terminalFileName,"\nTRAGEEEDIJA! " + messageNumbers.get(curAct)
                        + ", job " + nJobCurAct + " + " + messageNumbers.get(colAct2) + ", job " + colJob +
                        " are in conflict at time " + time);


                col = new Collision(curAct, colAct2, nJobCurAct, colJob, resSTMessages[curAct][nJobCurAct],
                        resSTMessages[colAct2][colJob], time);
            }

            schedule[time] = curAct + 1;
            return col;
        }

        private IloNumExpr getVarInHP(int nHPItself, int nHPsecond, int curActID, int colActID,
                                      int thisJobSt, int collJobBeg, int curJob) throws IloException {
            Activity curAct = acts.get(messageNumbers.get(curActID));
            Activity colAct = acts.get(messageNumbers.get(colActID));

            int nHPToCreate = nHPsecond - nHPItself;
            boolean intersect = false;
            while(!intersect) {
                // it's not the correct hyperperiods, change
                int curJobBeg = thisJobSt + nHPToCreate * HP;
                int curJobEnd = curJobBeg + curAct.getProcTime();
                int collJobEnd = collJobBeg + colAct.getProcTime();

                if(ScheduledActivities.doTwoOpenIntervalsIntersect(curJobBeg, curJobEnd,
                        collJobBeg, collJobEnd)) {
                    intersect = true;
                }
                else {
                    if (curJobEnd < collJobBeg) {
                        nHPToCreate++;
                    }

                    if(collJobEnd < curJobBeg) {
                        nHPToCreate--;
                    }
                }
            }

            return fullILPsolver.sum(
                    stMessages[curActID][curJob],
                    nHPToCreate * HP
            );
        }

        private boolean getVarsBetweenHPs(Collision col, int jobST1, int jobST2,
                                          IloNumExpr start0, IloNumExpr start1, IloNumExpr[] start) throws IloException {
            Activity act1 = acts.get(messageNumbers.get(col.colAct1));
            Activity act2 = acts.get(messageNumbers.get(col.colAct2));
            start[0] = start0;
            start[1] = start1;

            boolean createConstraint = false;

            if (jobST1 % HP > (jobST1 + act1.getProcTime()) % HP) {
                // if the task job starts at one HP and ends at the following
                start[1] = fullILPsolver.sum(
                        start1, fullILPsolver.constant((col.nHP2 + 1) * HP)
                );
                createConstraint = true;
            }

            if (jobST2 % HP > (jobST2 + act2.getProcTime()) % HP) {
                // constraints for in between HPs must be added
                start[0] = fullILPsolver.sum(
                        start0,
                        (col.nHP1 + 1) * HP
                );
                createConstraint = true;
            }

            return createConstraint;
        }

        private void checkAndAddIfNeccesary() throws IloException, IOException {
            int[][] schedule = new int[prInst.getNLinks()][acts.get(0).getHP()];
            int[][] resSTMessages = new int [prInst.getNumMessages()][];

            Queue<Collision> collisions = new LinkedList<>();
            for (int i = 0; i < messageNumbers.size(); i++) {
                resSTMessages[i] = Helpers.convertDoubleArrayToIntWithRound(this.getValues(stMessages[i]));
                int nCores = prInst.getnResources() - prInst.getNLinks();
                int nRes = acts.get(messageNumbers.get(i)).getAssToRes() - 1 - nCores;

                for (int j = 0; j < resSTMessages[i].length; j ++) {
                    for (int l = 0; l < acts.get(messageNumbers.get(i)).getProcTime(); l++) {
                        int t = (resSTMessages[i][j] + l) % HP;
                        Collision col = isThereCollision(schedule[nRes], t, i, j, resSTMessages);
                        if(col != null) {
                            collisions.add(col);
                            break;
                        }
                    }
                }
            }

            while(!collisions.isEmpty()){
                Collision curCol = collisions.poll();
                //debug("---- THERE IS A COLLISION, ADD CONSTRAINT ----\n");
                Activity act1 = acts.get(messageNumbers.get(curCol.colAct1));
                Activity act2 = acts.get(messageNumbers.get(curCol.colAct2));

                IloNumExpr startFirst = stMessages[curCol.colAct1][curCol.colJob1];
                IloNumExpr startSecond = stMessages[curCol.colAct2][curCol.colJob2];

                // we create a duplicate of this task job in the hyperperiod of another job
                if(curCol.nHP1 > curCol.nHP2) {
                    // if first task job is in later HP, create the second in the HP of the first
                    startSecond = getVarInHP(curCol.nHP2, curCol.nHP1, curCol.colAct2, curCol.colAct1,
                            curCol.stJobMes2, curCol.stJobMes1, curCol.colJob2);
                }
                else {
                    startFirst = getVarInHP(curCol.nHP1, curCol.nHP2, curCol.colAct1, curCol.colAct2,
                            curCol.stJobMes1, curCol.stJobMes2, curCol.colJob1);
                }

                IloRange[] out = setResConstrForTwoJobs(startFirst, startSecond,
                        act1, act2, curCol.colJob1, curCol.colJob2,
                        disjVars[curDisjVarNum], true);
                curDisjVarNum++;

                add(out[0]);
                add(out[1]);
            }
        }
    }

    private IloRange[] setResConstrForTwoJobs(IloNumExpr startFirst, IloNumExpr startSecond, Activity act1,
                                              Activity act2, int nJob1, int nJob2, IloNumVar disjVar, boolean isLazyConst) throws IloException {
        // The equations are the following:
        // s_1 - s_2 - bigM * x_{1,2} <= -e_1 and
        // s_1 - s_2 - bigM * x_{1,2} >= e_2 - bigM

        IloNumExpr expr = null;
        expr = fullILPsolver.diff(
                startFirst,
                startSecond
        );

        expr = fullILPsolver.sum(
                expr,
                fullILPsolver.prod(-bigM, disjVar)
        );

        IloRange[] out = {
                fullILPsolver.ge(
                        expr,
                        act2.getProcTime() - bigM
                ),
                fullILPsolver.le(
                        expr,
                        -act1.getProcTime()
                )
        };

        return out;
    }

    public void exportModel(String fileName) throws IloException {
        fullILPsolver.exportModel(fileName);
    }

    public OptimalModelILP(ProblemInstance problemIns, boolean _isRelaxedOnIntegralityOfQuotients,
                           boolean[] isActInNeigborhood_, Main.RunningCharacteristics chars,
                           ScheduledActivities schedActs_)
            throws IloException, IOException {
        super(problemIns, isActInNeigborhood_);
        schedActs = schedActs_;
        isRelaxedOnIntegralityOfQuotients = _isRelaxedOnIntegralityOfQuotients;

        fullILPsolver = new IloCplex();

        if(Main.SOLVE_ILP_WITH_LAZY) {
            fullILPsolver.use(new LazyConstraintCallback());
        }

        CreateVariablesAndSetBounds();
        setQuotientBounds();
        SetPrecedenceConstr();
        SetDeducedPrecConstraintsTasks();

        if(Main.SOLVE_ILP_WITH_LAZY) {
            disjVars = fullILPsolver.boolVarArray(Main.NUM_DISJ_VARS_FOR_LAZY);
        }

        //---------------------------------TASKS SETTING------------------------------------
        setResourceConstraintsECU();

        //---------------------------------MESSAGES SETTING------------------------------------
        setResourceConstraintsNetwork();

        setPrecConstrForTheSameMessage();
        setLatencyConstr();

        if(Main.OPTIMIZE_WITH_CRITERION && !Main.IS_INSTANCE_GENERATION_MODE) {
            if (Main.SOLVE_OPT_WITH_PW_LIN_EXISTING_ILP) {
                setPieceWiseLinearObjectiveWithExistFunct();
            } else {
                if(Main.CRITERION_SUM) {
                    setPieceWiseLinearObjectiveWithoutExistingSum();
                }
                else {
                    setPieceWiseLinearObjectiveWithoutExistingMax();
                }
            }
        }

        if(Main.IS_INSTANCE_GENERATION_MODE){
            // add all start times tasks to the model to retrieve them
            int[] coefficients = new int[stTasks.length];
            fullILPsolver.addMinimize(fullILPsolver.scalProd(coefficients, stTasks));
        }

        if(Main.EXPORT_OPTIMAL_MODELS_TO_FILE){
            fullILPsolver.exportModel(Helpers.outputFileForModelsILP);
        }

        Helpers.printToFile(chars.fullFileNameTerminalOut,
                "\nNumber of resource constraints is " + numResConstr + "\n");
    }

    private int getQuotientIndex(int numTask1, int numTask2) {
        return numTasks * numTask1 + numTask2 - numTask1 * (numTask1 + 1) / 2;
    }

    protected void createTaskVars() throws IloException{
        stTasks = fullILPsolver.intVarArray(numTasks, 0, 0);
        for (int i = 0; i < numTasks; i++) {
            int numTask = taskNumbers.get(i);
            stTasks[i].setLB(acts.get(numTask).getStartOfFeasInt(0));
            stTasks[i].setUB(acts.get(numTask).getEndOfFeasInt(0));
        }
    };
    protected void createMesVars() throws IloException{
        stMessages = new IloIntVar[numMessages][];

        for (int i = 0; i < numMessages; i++) {
            int numMessage = messageNumbers.get(i);
            stMessages[i] = fullILPsolver.intVarArray(
                    prInst.getActs().get(numMessage).getNJobs(), 0, 0
            );

            for (int j = 0; j < acts.get(numMessage).getNJobs(); j++) {
                stMessages[i][j].setLB(acts.get(numMessage).getStartOfFeasInt(j));
                stMessages[i][j].setUB(acts.get(numMessage).getEndOfFeasInt(j));
            }
        }
    };
    protected void createLatVars() throws IloException{
        latency = fullILPsolver.numVarArray(prInst.getNumOrdCritApps(), 0,
                2 * prInst.getHP(), IloNumVarType.Int);
    };
    private void createQuotientVars() throws IloException {
        IloNumVarType tp = IloNumVarType.Int;
        if(isRelaxedOnIntegralityOfQuotients){
            tp = IloNumVarType.Float;
        }
        quotients = fullILPsolver.numVarArray(numTasks * (numTasks + 1)/2,0,0, tp);
        setQuotientBounds();
    }
    private void setQuotientBounds() throws IloException {
        // the mapping is w = n * r + c - r * (r + 1)/2
        for (int i = 0; i < numTasks; i++) {
            for (int j = i + 1; j < numTasks; j++) {
                if(isActInNeigh[taskNumbers.get(i)] || isActInNeigh[taskNumbers.get(j)]){
                    int arrayIndex = numTasks * i + j - i * (i + 1) / 2;
                    int periodI = acts.get(taskNumbers.get(i)).getPeriod();
                    int periodJ = acts.get(taskNumbers.get(j)).getPeriod();
                    long pairwiseGcd = prInst.getPairwiseGCD(periodI, periodJ);

                    quotients[arrayIndex].setLB((stTasks[i].getLB() - stTasks[j].getUB()) / pairwiseGcd - 1
                    );
                    quotients[arrayIndex].setUB((stTasks[i].getUB() - stTasks[j].getLB()) / pairwiseGcd
                    );
                }
            }
        }
    }
    private void CreateVariablesAndSetBounds() throws IloException{
        createTaskVars();
        createMesVars();
        createLatVars();
        createQuotientVars();
    }

    protected void SetPrecedenceConstr() throws IloException{
        for (int i = 0; i < nActs; i++) {
            if(isActInNeigh[i]){
                List<Integer> preds = acts.get(i).getDirectPreds();
                if(!acts.get(i).isTask()){
                    int nMesSucc = messageNumbers.indexOf(i);
                    // it's message
                    for (int j = 0; j < preds.size(); j++) {
                        int predAct = preds.get(j);
                        // the predecessor is a message
                        for (int k = 0; k < acts.get(predAct).getNJobs(); k++) {
                            if(!acts.get(predAct).isTask()) {
                                int nMesPred = messageNumbers.indexOf(predAct);
                                fullILPsolver.addGe(
                                        fullILPsolver.diff(
                                                stMessages[nMesSucc][k],
                                                stMessages[nMesPred][k]
                                        ),
                                        acts.get(predAct).getProcTime()
                                );
                            }
                            else{
                                // predecessor is a task
                                int nTaskPred = taskNumbers.indexOf(preds.get(j));
                                fullILPsolver.addGe(fullILPsolver.diff(stMessages[nMesSucc][k],
                                        fullILPsolver.sum(
                                                stTasks[nTaskPred],
                                                acts.get(predAct).getPeriod() * k
                                        )
                                        ),
                                        acts.get(predAct).getProcTime()
                                );
                            }
                        }
                    }
                }
                else{
                    //it's task
                    int nTaskSucc = taskNumbers.indexOf(i);
                    for (int j = 0; j < preds.size(); j++) {
                        int predAct = preds.get(j);
                        if(!acts.get(predAct).isTask()){
                            //the predecessor is a message
                            for (int k = 0; k < acts.get(predAct).getNJobs(); k++) {
                                int nMesPred = messageNumbers.indexOf(predAct);

                                fullILPsolver.addGe(
                                        fullILPsolver.diff(
                                                fullILPsolver.sum(
                                                        stTasks[nTaskSucc],
                                                        acts.get(i).getPeriod() * k
                                                ),
                                                stMessages[nMesPred][k]
                                        ),
                                        acts.get(predAct).getProcTime()
                                );
                            }

                        }
                        else {
                            //the predecessor is a task
                            int nTaskPred = taskNumbers.indexOf(predAct);
                            fullILPsolver.addGe(fullILPsolver.diff(stTasks[nTaskSucc],
                                    stTasks[nTaskPred]
                                    ),
                                    prInst.getActs().get(predAct).getProcTime()
                            );
                        }

                    }
                }
            }
        }
    }

    private void setPrecConstrForTheSameMessage() throws IloException {
        for (int i = 0; i < messageNumbers.size(); i++) {
            Activity curMessage = acts.get(messageNumbers.get(i));

            for (int j = 0; j < curMessage.getNJobs(); j++) {
                int rightHandSide = curMessage.getProcTime();
                if(j == curMessage.getNJobs() - 1){
                    rightHandSide -= HP;
                }

                fullILPsolver.addGe(
                        fullILPsolver.diff(
                                stMessages[curMessage.getNumInTaskOrMessageArray()][(j + 1) % curMessage.getNJobs()],
                                stMessages[curMessage.getNumInTaskOrMessageArray()][j]
                        ),
                        rightHandSide
                );
            }
        }
    }
    protected void SetDeducedPrecConstraintsTasks() throws IloException{
        for (int i = 0; i < numTasks; i++) {
            int numITask = taskNumbers.get(i);
            for (int h = 0; h < prInst.getDeducedPrecedence().get(numITask).size(); h++){
                int numJTask = prInst.getDeducedPrecedence().get(numITask).get(h);
                int j = taskNumbers.indexOf(numJTask);

                fullILPsolver.addGe(fullILPsolver.diff(stTasks[i],
                        stTasks[j]
                        ),
                        acts.get(numJTask).getProcTime()
                );
            }
        }
    }
    private void setLatencyConstr() throws IloException{
        int appsOrdCritCounter = 0;
        for (int i = 0; i < nApps; i++) {
            App app = prInst.getApps().get(i);

            if(app.isOrderCritical()) {
                boolean shouldAppBeScheduled = app.getActs().get(0).shouldBeScheduled();
                if (shouldAppBeScheduled) {
                    if (isActInNeigh[app.getActs().get(0).getID()]) {
                        for (int j = 0; j < app.getRootActs().size(); j++) {
                            Activity activityRoot = app.getRootActs().get(j);
                            for (int k = 0; k < app.getLeafActs().size(); k++) {
                                Activity activityLeaf = app.getLeafActs().get(k);
                                //latency is maximum over all leaf-to-root pairs
                                fullILPsolver.addGe(fullILPsolver.diff(latency[appsOrdCritCounter],
                                        fullILPsolver.diff(stTasks[taskNumbers.indexOf(activityLeaf.getID())],
                                                stTasks[taskNumbers.indexOf(activityRoot.getID())]
                                        )
                                        ),
                                        activityLeaf.getProcTime()
                                );
                            }
                        }

                        fullILPsolver.addLe(
                                latency[appsOrdCritCounter],
                                app.getE2eLatBound()
                        );

                    } else {
                        latency[appsOrdCritCounter].setLB(schedActs.getE2eLatenciesOfApps()[i]);
                        latency[appsOrdCritCounter].setUB(schedActs.getE2eLatenciesOfApps()[i]);
                    }
                } else {
                    latency[appsOrdCritCounter].setLB(schedActs.getE2eLatenciesOfApps()[i]);
                    latency[appsOrdCritCounter].setUB(schedActs.getE2eLatenciesOfApps()[i]);
                }
                appsOrdCritCounter++;
            }
        }
    }
    private void setResourceConstraintsECU() throws IloException {
        for (int i = 0; i < numTasks; i++) {
            int taskINum = taskNumbers.get(i);
            for (int j = 0; j < prInst.getResourceConstraintActs().get(taskINum).size(); j++) {
                int taskJNum = prInst.getResourceConstraintActs().get(taskINum).get(j);
                int indJInSTArray = acts.get(taskJNum).getNumInTaskOrMessageArray();

                if ((isActInNeigh[taskINum] || isActInNeigh[taskJNum])) {
                    IloNumExpr expr = null;
                    if(!isActInNeigh[taskINum] && isActInNeigh[taskJNum]){
                        expr = fullILPsolver.diff(schedActs.getActST()[taskINum][0], stTasks[indJInSTArray]);
                    }

                    if(isActInNeigh[taskINum] && !isActInNeigh[taskJNum]){
                        expr = fullILPsolver.diff(stTasks[i], schedActs.getActST()[taskJNum][0]);
                    }

                    if(isActInNeigh[taskINum] && isActInNeigh[taskJNum]){
                        expr = fullILPsolver.diff(stTasks[i], stTasks[indJInSTArray]);
                    }

                    int quotientIndex = getQuotientIndex(i, indJInSTArray);
                    int gcd = (int) prInst.getPairwiseGCD(
                            acts.get(taskINum).getPeriod(),
                            acts.get(taskJNum).getPeriod()
                    );

                    IloNumExpr tmp = fullILPsolver.diff(
                            expr,
                            fullILPsolver.prod(
                                    quotients[quotientIndex],
                                    gcd
                            )
                    );

                    fullILPsolver.addGe(tmp, acts.get(taskJNum).getProcTime());
                    fullILPsolver.addLe(tmp, gcd - acts.get(taskINum).getProcTime());
                }
            }
        }
    }
    private void setResourceConstraintsNetwork() throws IloException{
        for(int i = 0; i < numMessages; i++) {
            int nMes1 = messageNumbers.get(i);
            Activity mes1 = acts.get(messageNumbers.get(i));


            for(int k = 0; k < prInst.getResourceConstraintActs().get(nMes1).size(); k++) {
                int nMes2 = prInst.getResourceConstraintActs().get(nMes1).get(k);
                Activity mes2 = acts.get(nMes2);
                int nMes2InMessageList = mes2.getNumInTaskOrMessageArray();

                if(isActInNeigh[nMes1] || isActInNeigh[nMes2]){
                    int nJobsToRun1 = acts.get(nMes1).getNJobs();
                    int nJobsToRun2 = acts.get(nMes2).getNJobs();

                    if(Main.SOLVE_ILP_WITH_LAZY) {
                        if(!isLocalNeiborhood) {
                            nJobsToRun1++;
                            nJobsToRun2++;
                        }
                    }
                    else {
                        nJobsToRun1 = numHPToCreateMesVars * acts.get(nMes1).getNJobs();
                        nJobsToRun2 = numHPToCreateMesVars * acts.get(nMes2).getNJobs();
                    }

                    for(int j = 0; j < nJobsToRun1; j++) {
                        int jInMesVars = j % acts.get(nMes1).getNJobs();
                        int nHP1 = j / acts.get(nMes1).getNJobs();
                        IloNumExpr startFirst =
                                fullILPsolver.sum(
                                        stMessages[i][jInMesVars],
                                        nHP1 * HP
                                );

                        for(int l = 0; l < nJobsToRun2; l++) {
                            int lInMesVars = l % acts.get(nMes2).getNJobs();
                            int nHP2 = l / acts.get(nMes2).getNJobs();
                            IloNumExpr startSecond = fullILPsolver.sum(
                                    stMessages[nMes2InMessageList][lInMesVars],
                                    nHP2 * HP
                            );



                            boolean notCollapsing =
                                    stMessages[i][jInMesVars].getUB() + nHP1 * HP + acts.get(nMes1).getProcTime()
                                            <= stMessages[nMes2InMessageList][lInMesVars].getLB() + nHP2 * HP ||
                                            stMessages[nMes2InMessageList][lInMesVars].getUB() + nHP2 * HP + acts.get(nMes2).getProcTime()
                                                    <= stMessages[i][jInMesVars].getLB() + nHP1 * HP;

                            if(!notCollapsing) {
                                IloRange[] out = setResConstrForTwoJobs(startFirst,
                                        startSecond, mes1, mes2, jInMesVars, lInMesVars,
                                        fullILPsolver.boolVar(), false);

                                if(j > acts.get(nMes1).getNJobs() || l > acts.get(nMes2).getNJobs()) {
                                    fullILPsolver.addLazyConstraint(out[0]);
                                    fullILPsolver.addLazyConstraint(out[1]);
                                }
                                else {
                                    fullILPsolver.add(out);
                                }
                                numResConstr++;
                            }
                        }
                    }
                }
            }
        }
    }
    private void setLambdaAndGammaForLatency(int nPoints) throws IloException{
        int appsOrdCritCounter = 0;
        for (int i = 0; i < nApps; i++) {
            App app = prInst.getApps().get(i);

            if(app.isOrderCritical()) {
                IloNumExpr[] expr = new IloNumExpr [nPoints];

                for (int j = 0; j < nPoints - 1; j++) {
                    expr[j] = fullILPsolver.sum(
                            fullILPsolver.prod(
                                    lambdas[appsOrdCritCounter][j],
                                    app.getDelays()[j]
                            ),
                            fullILPsolver.prod(
                                    gammas[appsOrdCritCounter][j],
                                    app.getDelays()[j + 1] - app.getDelays()[j]
                            )
                    );
                }

                expr[nPoints - 1] = fullILPsolver.prod(
                        lambdas[appsOrdCritCounter][nPoints - 1],
                        app.getDelays()[nPoints - 1]
                );

                fullILPsolver.addEq(
                        fullILPsolver.diff(
                                fullILPsolver.sum(expr),
                                latency[appsOrdCritCounter]
                        ),
                        0
                );

                appsOrdCritCounter++;
            }
        }
    }
    private void setControlPerformanceValue(int nPoints) throws IloException{
        int appsOrdCritCounter = 0;
        for (int i = 0; i < nApps; i++) {
            IloNumExpr[] expr = new IloNumExpr [nPoints];
            App curApp = prInst.getApps().get(i);

            if(curApp.isOrderCritical()) {
                for (int j = 0; j < nPoints - 1; j++) {
                    expr[j] = fullILPsolver.sum(
                            fullILPsolver.prod(
                                    lambdas[appsOrdCritCounter][j],
                                    curApp.getPerfValues()[j]
                            ),
                            fullILPsolver.prod(
                                    gammas[appsOrdCritCounter][j],
                                    curApp.getPerfValues()[j + 1] - curApp.getPerfValues()[j]
                            )
                    );
                }

                expr[nPoints - 1] =
                        fullILPsolver.prod(
                                lambdas[appsOrdCritCounter][nPoints - 1],
                                curApp.getPerfValues()[nPoints - 1]
                        );

                fullILPsolver.addEq(
                        fullILPsolver.diff(
                                fullILPsolver.sum(expr),
                                controlPerformances[appsOrdCritCounter]
                        ),
                        0);
                appsOrdCritCounter++;
            }
        }
    }
    private void setPieceWiseLinearObjectiveWithoutExisting() throws IloException{
        int nOrdCritApps = prInst.getNumOrdCritApps();
        int nPoints = prInst.getApps().get(0).getDelays().length;
        lambdas = new IloNumVar[nOrdCritApps][nPoints];
        gammas = new IloNumVar[nOrdCritApps][nPoints];
        controlPerformances = fullILPsolver.numVarArray(nOrdCritApps, 0, 1, IloNumVarType.Float);

        int appsOrdCritCounter = 0;
        for (int i = 0; i < prInst.getApps().size(); i++) {
            if(prInst.getApps().get(i).isOrderCritical()) {
                controlPerformances[appsOrdCritCounter].setUB(prInst.getApps().get(i).getPerfValues()[nPoints - 1]);
                appsOrdCritCounter++;
                if(schedActs != null && schedActs.getE2eLatenciesOfApps()[i] < prInst.getApps().get(i).getDelays()[0]) {
                    prInst.getApps().get(i).setDelay(0, schedActs.getE2eLatenciesOfApps()[i]);
                }
            }

        }

        for (int i = 0; i < nOrdCritApps; i++) {
            lambdas[i] = fullILPsolver.numVarArray(nPoints, 0, 1, IloNumVarType.Int);
            gammas[i] = fullILPsolver.numVarArray(nPoints - 1, 0, 1 - Helpers.EPS, IloNumVarType.Float);
        }

        for (int i = 0; i < nOrdCritApps; i++) {
            // sum of lambdas is equal to 1 for each application
            // since the value of e2e latency is exactly at one interval
            fullILPsolver.addEq(fullILPsolver.sum(lambdas[i]), 1);
        }

        // gammas is from 0 to lambda
        for (int i = 0; i < nOrdCritApps; i++) {
            for (int j = 0; j < nPoints - 1; j++) {
                fullILPsolver.addLe(gammas[i][j], lambdas[i][j]);
            }
        }

        setLambdaAndGammaForLatency(nPoints);
        setControlPerformanceValue(nPoints);
    }
    private void setPieceWiseLinearObjectiveWithoutExistingSum() throws IloException{
        setPieceWiseLinearObjectiveWithoutExisting();

        if(Main.SOLVE_ILP_WITH_LAZY) {
            int[] coeffs = new int[Main.NUM_DISJ_VARS_FOR_LAZY];
            for (int i = 0; i < Main.NUM_DISJ_VARS_FOR_LAZY; i++) {
                coeffs[i] = 0;
            }

            fullILPsolver.addObjective(
                    IloObjectiveSense.Minimize,
                    fullILPsolver.sum(
                            fullILPsolver.sum(controlPerformances),
                            fullILPsolver.scalProd(disjVars, coeffs)
                    )
            );
        }
        else {
            fullILPsolver.addObjective(
                    IloObjectiveSense.Minimize,
                    fullILPsolver.sum(controlPerformances)
            );
        }
    }
    private void setPieceWiseLinearObjectiveWithoutExistingMax() throws IloException{
        setPieceWiseLinearObjectiveWithoutExisting();
        IloNumVar maxValue = fullILPsolver.numVar(0, prInst.getMaxCritValue());

        for (int i = 0; i < prInst.getNumOrdCritApps(); i++) {
            fullILPsolver.addGe(maxValue, controlPerformances[i]);
        }

        if(Main.SOLVE_ILP_WITH_LAZY) {
            int[] coeffs = new int[Main.NUM_DISJ_VARS_FOR_LAZY];
            for (int i = 0; i < Main.NUM_DISJ_VARS_FOR_LAZY; i++) {
                coeffs[i] = 0;
            }

            fullILPsolver.addObjective(
                    IloObjectiveSense.Minimize,
                    fullILPsolver.sum(
                            maxValue,
                            fullILPsolver.scalProd(disjVars, coeffs)
                    )
            );
        }
        else {
            fullILPsolver.addObjective(
                    IloObjectiveSense.Minimize,
                    maxValue
            );
        }
    }
    private void setPieceWiseLinearObjectiveWithExistFunct() throws IloException{
        int nApps = prInst.getApps().size();
        int nPoints = prInst.getApps().get(0).getDelays().length;
        obj = new IloNumExpr[prInst.getNumOrdCritApps()];

        int ordCritCount = 0;
        for (int i = 0; i < nApps; i++) {
            App curApp = prInst.getApps().get(i);
            if(curApp.isOrderCritical()) {
                double[] slopes = new double[nPoints + 1];
                slopes[0] = 0;
                for (int j = 1; j < nPoints; j++) {
                    slopes[j] = (curApp.getPerfValues()[j] - curApp.getPerfValues()[j - 1]) * 1.0 /
                            (curApp.getDelays()[j] - curApp.getDelays()[j - 1]);
                }
                slopes[nPoints] = 0;

                obj[ordCritCount] = fullILPsolver.piecewiseLinear(
                        latency[ordCritCount],
                        curApp.getDelays(),
                        slopes,
                        curApp.getDelays()[0], curApp.getPerfValues()[0]
                );
                ordCritCount++;
            }
        }

        if(!Main.IS_INSTANCE_GENERATION_MODE) {
            if(Main.SOLVE_ILP_WITH_LAZY) {
                int[] coeffs = new int[Main.NUM_DISJ_VARS_FOR_LAZY];
                for (int i = 0; i < Main.NUM_DISJ_VARS_FOR_LAZY; i++) {
                    coeffs[i] = 0;
                }

                fullILPsolver.addObjective(
                        IloObjectiveSense.Minimize,
                        fullILPsolver.sum(
                                fullILPsolver.max(obj),
                                fullILPsolver.scalProd(disjVars, coeffs)
                        )
                );
            }
            else {
                fullILPsolver.addObjective(IloObjectiveSense.Minimize, fullILPsolver.max(obj));
            }
        }
    }
    protected int[][] storeTasksAndMessages() throws IloException{
        int[][] startTimes = new int[nActs][prInst.getMaxNumJobs()];
        for (int i = 0; i < numTasks; i++) {
            for (int j = 0; j < acts.get(taskNumbers.get(i)).getNJobs(); j++) {
                if(isActInNeigh[taskNumbers.get(i)]){
                    startTimes[taskNumbers.get(i)][j] = (int) Math.round(fullILPsolver.getValue(stTasks[i]))
                            + acts.get(taskNumbers.get(i)).getPeriod() * j;
                }
                else{
                    startTimes[taskNumbers.get(i)][j] = schedActs.getActST()[taskNumbers.get(i)][j];
                }
            }
        }

        for (int i = 0; i < numMessages; i++) {
            for (int j = 0; j < acts.get(messageNumbers.get(i)).getNJobs(); j++) {
                if(isActInNeigh[messageNumbers.get(i)]){
                    startTimes[messageNumbers.get(i)][j] =
                            (int) Math.round(fullILPsolver.getValue(stMessages[i][j]));
                }
                else{
                    startTimes[messageNumbers.get(i)][j] =  schedActs.getActST()[messageNumbers.get(i)][j];
                }
            }
        }

        setSTOfUnscheduledActivities(startTimes);

        return startTimes;
    }
    private void printArray(IloNumVar[][] array, String s) throws IloException{
        System.out.println("");
        for (int i = 0; i < nApps; i++) {
            System.out.println("");
            System.out.println("Application " + i + ":");
            int n2ndDim = prInst.getApps().get(0).getDelays().length;
            if(s == "Gammas "){
                n2ndDim--;
            }

            for (int j = 0; j < n2ndDim; j++) {
                System.out.println(s + j + " is " + fullILPsolver.getValue(array[i][j]));
            }
        }
    }
    protected void setParamsToSolver(long timeLimit, Main.RunningCharacteristics chars) throws IloException, IOException{
        if(Main.PRINT_OPT_OUTPUT_TO_TERMINAL_FILE) {
            fullILPsolver.setOut(new FileOutputStream(chars.fullFileNameTerminalOut, true));
        }

        fullILPsolver.setParam(IloCplex.IntParam.MIPDisplay, 3);
        fullILPsolver.setParam(IloCplex.IntParam.MIPInterval, -400000);
        fullILPsolver.setParam(IloCplex.IntParam.TimeLimit, timeLimit);

        if(isLocalNeiborhood){
            if(!Main.PRINT_LOCAL_NEIGH_RUN_BY_OPT_APPROACH) {
                fullILPsolver.setOut(null);
            }

            fullILPsolver.setParam(IloCplex.DoubleParam.EpGap, 0.001);
            fullILPsolver.setParam(IloCplex.IntParam.TimeLimit, Main.time_limit_opt_heur_one_step);
            fullILPsolver.setParam(IloCplex.IntParam.Probe, -1);
        }

        fullILPsolver.setParam(IloCplex.DoubleParam.EpInt, 1.0E-7);
    }

    public Solution solve(Main.RunningCharacteristics chars) throws IloException, IOException{
        return solve(0, chars);
    }

    @Override
    protected void endSolver() {
        fullILPsolver.end();
    }

    @Override
    protected boolean solveModel() throws IloException {
        boolean s = fullILPsolver.solve();
        return s;
    }

    @Override
    protected int getLatency(int i) throws IloException {
        return (int) Math.round(fullILPsolver.getValue(latency[i]));
    }

    @Override
    protected double[] getControlPerformanceValues() throws IloException {
        double[] controlPerfValues = new double[prInst.getNumOrdCritApps()];

        for (int i = 0; i < prInst.getNumOrdCritApps(); i++) {
            controlPerfValues[i] = fullILPsolver.getValue(controlPerformances[i]);
        }

        return controlPerfValues;
    }

    @Override
    protected boolean solverProveInfeasibility() throws IloException {
        return fullILPsolver.getStatus().toString() == "Infeasible";
    }

    @Override
    protected double getObjValue() throws IloException {
        return fullILPsolver.getObjValue();
    }

    @Override
    protected void exportModel() throws IloException {
        fullILPsolver.exportModel(Helpers.outputFileForModelsILP);
    }
}
