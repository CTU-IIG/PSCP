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
import ilog.cp.*;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.List;

public class OptimalModelCP extends OptimalModel {
    protected IloCP cpFull;
    protected IloObjective Objective;
    protected IloIntervalVar[] stTasks;
    protected IloIntervalVar[][] stTasksInts;
    protected IloIntervalVar[][] stMes;
    protected IloIntervalVar[] latency;
    private IloNumToNumSegmentFunction[] obj;

    public OptimalModelCP(ProblemInstance problemInstance_, boolean[] isActInNeigborhood_, ScheduledActivities schedActs_) throws IloException {
        super(problemInstance_, isActInNeigborhood_);

        schedActs = schedActs_;
        cpFull = new IloCP();
        createVariablesAndSetBounds();

        if(Main.SOLVE_CP_INTERVAL_VARS){
            addNoOverlapConstrForTasks();
        }
        else{
            setResourceConstraintsECU();
        }

        addNoOverlapConstrForMessages();
        SetDeducedPrecConstraintsTasks();
        SetPrecedenceConstr();
        setLatencyConstrWithSpan();

        if(Main.OPTIMIZE_WITH_CRITERION && !Main.IS_INSTANCE_GENERATION_MODE){
            // CP does not have fractional variables, so it is complicated to implement it by linearizing.
            if(Main.CRITERION_SUM) {
                setPieceWiseLinearObjectiveSum();
            }
            else {
                setPieceWiseLinearObjectiveMax();
            }
        }

        if(Main.EXPORT_OPTIMAL_MODELS_TO_FILE){
            cpFull.exportModel(Helpers.outputFileForModelsCP);
        }
    }

    private void createTaskIntVar(int i, IloIntervalVar stTasks) throws IloException {
        int nTaskActArr = taskNumbers.get(i);
       
        stTasks.setStartMin(acts.get(taskNumbers.get(i)).getStartOfFeasInt(0));
        stTasks.setEndMax(acts.get(nTaskActArr).getEndOfFeasInt(0));
    }

    protected void createTaskVars() throws IloException{
        if(Main.SOLVE_CP_INTERVAL_VARS){
            stTasksInts = new IloIntervalVar[numTasks][numHPToCreateMesVars * prInst.getMaxNumJobs()];
            for (int i = 0; i < numTasks; i++) {
                int nTaskActArr = taskNumbers.get(i);
                Activity curAct = prInst.getActs().get(nTaskActArr);
                
                stTasksInts[i][0] = cpFull.intervalVar(acts.get(nTaskActArr).getProcTime());
                createTaskIntVar(i, stTasksInts[i][0]);
                
                for (int j = 1; j < numHPToCreateMesVars * curAct.getNJobs(); j++) {
                    stTasksInts[i][j] = cpFull.intervalVar(acts.get(nTaskActArr).getProcTime());

                    cpFull.add(
                            cpFull.startAtStart(
                                    stTasksInts[i][j], 
                                    stTasksInts[i][j - 1], 
                                    -curAct.getPeriod()
                            )
                    );
                }
            }
        }
        else{
            stTasks = new IloIntervalVar[numTasks];
            for (int i = 0; i < numTasks; i++) {
                int nTaskActArr = taskNumbers.get(i);
                stTasks[i] = cpFull.intervalVar(acts.get(nTaskActArr).getProcTime());
                createTaskIntVar(i, stTasks[i]);
            }
        }
    }
    
    protected void createMesVars() throws IloException {
        stMes = new IloIntervalVar[numMessages][];
        for (int i = 0; i < numMessages; i++) {
            int numMessage = messageNumbers.get(i);
            stMes[i] = new IloIntervalVar[numHPToCreateMesVars * prInst.getActs().get(numMessage).getNJobs()];
            
            for (int j = 0; j < acts.get(numMessage).getNJobs(); j++) {
                stMes[i][j] = cpFull.intervalVar(acts.get(numMessage).getProcTime());
                
                int startMin = acts.get(numMessage).getStartOfFeasInt(j);
                int startMax = acts.get(numMessage).getEndOfFeasInt(j);

                stMes[i][j].setStartMin(startMin);
                stMes[i][j].setStartMax(startMax);
                
                for (int k = 1; k < numHPToCreateMesVars; k++) {
                    stMes[i][j + k * acts.get(numMessage).getNJobs()] = 
                            cpFull.intervalVar(acts.get(numMessage).getProcTime());

                    cpFull.add(
                            cpFull.startAtStart(
                                    stMes[i][j + k * acts.get(numMessage).getNJobs()], 
                                    stMes[i][j], 
                                    -k * HP
                            )
                    );    
                }
            }
        }
    }
    
    protected void createLatVars() throws IloException {
        latency = new IloIntervalVar[prInst.getNumOrdCritApps()];
        for (int i = 0; i < prInst.getNumOrdCritApps(); i++) {
                latency[i] = cpFull.intervalVar();
        }
    }
    
    private void createVariablesAndSetBounds() throws IloException{
        createTaskVars();
        createMesVars();
        createLatVars();
    }

    private int computenJobsOnResource(int nRes, int nHPtoRepeat, List<Integer> numbers){
        int nJobs = 0;
        for (int j = 0; j < numbers.size(); j++) {
            int numAct = numbers.get(j);
            if(acts.get(numAct).getAssToRes() == nRes + 1) {
                nJobs += nHPtoRepeat * acts.get(numAct).getNJobs();
            }
        }
        
        return nJobs;
    }
    
    private void addNoOverlapConstrForTasks() throws IloException{
        for (int i = 0; i < prInst.getnResources(); i++) {
            boolean isECU = false;
            for (int j = 0; j < prInst.getActs().size(); j++) {
                if(prInst.getActs().get(j).getAssToRes() == i + 1) {
                    if(prInst.getActs().get(j).isTask()) {
                        isECU = true;
                    }
                    break;
                }
            }

            if(isECU) {
                int nJobs = computenJobsOnResource(i, numHPToCreateMesVars, taskNumbers);

                IloIntervalVar[] STTasksForECU = new IloIntervalVar[nJobs];
                int count = 0;
                for (int j = 0; j < numTasks; j++) {
                    int numTask = taskNumbers.get(j);
                    if (acts.get(numTask).getAssToRes() == i + 1) {
                        for (int k = 0; k < numHPToCreateMesVars * acts.get(numTask).getNJobs(); k++) {
                            STTasksForECU[count] = stTasksInts[j][k];
                            count++;
                        }
                    }
                }

                cpFull.add(cpFull.noOverlap(STTasksForECU));
            }
        }
    }
    
    private void addNoOverlapConstrForMessages() throws IloException{
        for (int i = 0; i < prInst.getnResources(); i++) {
            boolean isECU = false;
            for (int j = 0; j < prInst.getActs().size(); j++) {
                if(prInst.getActs().get(j).getAssToRes() == i + 1) {
                    if(prInst.getActs().get(j).isTask()) {
                        isECU = true;
                    }
                    break;
                }
            }

            if(!isECU) {
                int nJobs = computenJobsOnResource(i, numHPToCreateMesVars, messageNumbers);

                IloIntervalVar[] STMessagesForLink = new IloIntervalVar[nJobs];
                int count = 0;
                for (int j = 0; j < numMessages; j++) {
                    int numMessage = messageNumbers.get(j);
                    if (acts.get(numMessage).getAssToRes() == i + 1) {
                        for (int k = 0; k < numHPToCreateMesVars * acts.get(numMessage).getNJobs(); k++) {
                            STMessagesForLink[count] = stMes[j][k];
                            count++;
                        }
                    }
                }

                cpFull.add(cpFull.noOverlap(STMessagesForLink));
            }
        }
    }
    
    protected void SetDeducedPrecConstraintsTasks() throws IloException{
        if(prInst.getDeducedPrecedence() != null) {
            for (int i = 0; i < numTasks; i++) {
                int numITask = taskNumbers.get(i);
                for (int h = 0; h < prInst.getDeducedPrecedence().get(numITask).size(); h++) {
                    int numJTask = prInst.getDeducedPrecedence().get(numITask).get(h);
                    int j = taskNumbers.indexOf(numJTask);

                    if (Main.SOLVE_CP_INTERVAL_VARS) {
                        cpFull.add(
                                cpFull.endBeforeStart(
                                        stTasksInts[j][0],
                                        stTasksInts[i][0]
                                )
                        );
                    } else {
                        cpFull.add(
                                cpFull.endBeforeStart(
                                        stTasks[j],
                                        stTasks[i]
                                )
                        );
                    }
                }
            }
        }
    }
    
    protected void setPrecConstrTwoIntJobs(IloIntervalVar stPred, 
            IloIntervalVar stSucc) throws IloException {
        cpFull.add(
                cpFull.endBeforeStart(
                        stPred, 
                        stSucc
                )
        );
    }
    
    protected void SetPrecedenceConstr() throws IloException{
        for (int i = 0; i < nActs; i++) {
            if (isActInNeigh[i]) {
                List<Integer> preds = acts.get(i).getDirectPreds();
                if (!acts.get(i).isTask()) {
                    int nMesSucc = messageNumbers.indexOf(i);
                    //it's message
                    for (int j = 0; j < preds.size(); j++) {
                        int predAct = preds.get(j);
                        for (int k = 0; k < acts.get(predAct).getNJobs(); k++) {
                            //the predecessor is a message
                            if(!acts.get(predAct).isTask()) {
                                int nMesPred = messageNumbers.indexOf(predAct);
                                
                                setPrecConstrTwoIntJobs(stMes[nMesPred][k], 
                                        stMes[nMesSucc][k]);
                            }
                            else{
                                // predecessor is a task
                                int nTaskPred = taskNumbers.indexOf(preds.get(j));
                                if(Main.SOLVE_CP_INTERVAL_VARS){
                                    setPrecConstrTwoIntJobs(stTasksInts[nTaskPred][k], 
                                         stMes[nMesSucc][k]);
                                }
                                else{
                                    cpFull.addGe(
                                            cpFull.diff(
                                                    cpFull.startOf(stMes[nMesSucc][k]),
                                                    cpFull.sum(
                                                            cpFull.startOf(stTasks[nTaskPred]), 
                                                            acts.get(predAct).getPeriod() * k
                                                    )
                                            ),
                                            acts.get(predAct).getProcTime()
                                    );
                                }
                            }
                        }
                    }
                } else {
                    //it's task
                    int nTaskSucc = taskNumbers.indexOf(i);
                    for (int j = 0; j < preds.size(); j++) {
                        int predAct = preds.get(j);
                        if (!acts.get(predAct).isTask()) {
                            //the predecessor is a message
                            for (int k = 0; k < acts.get(predAct).getNJobs(); k++) {
                                int nMesPred = messageNumbers.indexOf(predAct);
                                if(Main.SOLVE_CP_INTERVAL_VARS) {
                                    setPrecConstrTwoIntJobs(stMes[nMesPred][k], 
                                         stTasksInts[nTaskSucc][k]);
                                }
                                else {
                                    cpFull.addGe(
                                            cpFull.diff(
                                                    cpFull.sum(
                                                            cpFull.startOf(stTasks[nTaskSucc]), 
                                                            acts.get(nTaskSucc).getPeriod() * k
                                                    ),
                                                    cpFull.startOf(stMes[nMesPred][k])
                                            ),
                                            acts.get(predAct).getProcTime()
                                    );
                                }
                            }
                        } else {
                            //the predecessor is a task
                            int nTaskPred = taskNumbers.indexOf(predAct);
                            if(Main.SOLVE_CP_INTERVAL_VARS){
                                setPrecConstrTwoIntJobs(stTasksInts[nTaskPred][0], 
                                         stTasksInts[nTaskSucc][0]);
                            }
                            else{
                                setPrecConstrTwoIntJobs(stTasks[nTaskPred], 
                                         stTasks[nTaskSucc]);
                            }
                        }
                    }
                }
            }
        }
    }

    private void setPrecConstraintsForTwoJobsOfTheSameMessage() throws IloException{
        for (int i = 0; i < messageNumbers.size(); i++) {
            Activity curMessage = acts.get(messageNumbers.get(i));
            
            for (int j = 0; j < curMessage.getNJobs(); j++) {
                setPrecConstrTwoIntJobs(stMes[i][j], stMes[i][j + 1]);
            }
        }
    }

    private void setLatencyConstrWithSpan() throws IloException{
        int OCcounter = 0;
        for (int i = 0; i < nApps; i++) {
            App curApp = prInst.getApps().get(i);

            if (curApp.isOrderCritical()) {
                if (!curApp.getActs().get(0).shouldBeScheduled()) {
                    latency[OCcounter].setLengthMin(schedActs.getE2eLatenciesOfApps()[i]);
                    continue;
                }

                IloIntervalVar[] leafAndRootActs = new IloIntervalVar[curApp.getRootActs().size()
                        + curApp.getLeafActs().size()];
                int countOfLeafAndRootActs = 0;
                for (int j = 0; j < curApp.getRootActs().size(); j++) {
                    int nTaskRoot = curApp.getRootActs().get(j).getID();
                    if (Main.SOLVE_CP_INTERVAL_VARS) {
                        leafAndRootActs[countOfLeafAndRootActs] = stTasksInts[taskNumbers.indexOf(nTaskRoot)][0];
                    } else {
                        leafAndRootActs[countOfLeafAndRootActs] = stTasks[taskNumbers.indexOf(nTaskRoot)];
                    }
                    countOfLeafAndRootActs++;
                }

                for (int j = 0; j < curApp.getLeafActs().size(); j++) {
                    int nTaskLeaf = curApp.getLeafActs().get(j).getID();
                    if (Main.SOLVE_CP_INTERVAL_VARS) {
                        leafAndRootActs[countOfLeafAndRootActs] = stTasksInts[taskNumbers.indexOf(nTaskLeaf)][0];
                    } else {
                        leafAndRootActs[countOfLeafAndRootActs] = stTasks[taskNumbers.indexOf(nTaskLeaf)];
                    }
                    countOfLeafAndRootActs++;
                }

                cpFull.add(
                        cpFull.span(
                                latency[OCcounter], 
                                leafAndRootActs)
                );

                latency[OCcounter].setLengthMax(curApp.getE2eLatBound());
                OCcounter++;
            }
        }
    }

    private void setPieceWiseLinearObjectiveSum() throws IloException {
        int nApps = prInst.getApps().size();
        int nPoints = prInst.getApps().get(0).getDelays().length;
        obj = new IloNumToNumSegmentFunction[prInst.getNumOrdCritApps()];

        IloNumExpr expr[] = new IloNumExpr[prInst.getNumOrdCritApps()];
        int OCcounter = 0;
        for (int i = 0; i < nApps; i++) {
            App curApp = prInst.getApps().get(i);
            if (curApp.isOrderCritical()) {
                double[] slopes = new double[nPoints + 1];
                slopes[0] = 0;
                for (int j = 1; j < nPoints; j++) {
                    slopes[j] = (curApp.getPerfValues()[j] - curApp.getPerfValues()[j - 1]) * 1.0 /
                            (curApp.getDelays()[j] - curApp.getDelays()[j - 1]);
                }
                slopes[nPoints] = 0;

                obj[OCcounter] = cpFull.piecewiseLinearFunction(
                        curApp.getDelays(),
                        slopes,
                        curApp.getDelays()[0], curApp.getPerfValues()[0]);

                expr[OCcounter] = cpFull.lengthEval(latency[OCcounter], obj[OCcounter]);
                OCcounter++;
            }
        }

        cpFull.addObjective(IloObjectiveSense.Minimize, cpFull.sum(expr));
    }

    private void setPieceWiseLinearObjectiveMax() throws IloException {
        int nApps = prInst.getApps().size();
        int nPoints = prInst.getApps().get(0).getDelays().length;
        obj = new IloNumToNumSegmentFunction[prInst.getNumOrdCritApps()];

        IloNumExpr expr[] = new IloNumExpr[prInst.getNumOrdCritApps()];
        int OCcounter = 0;
        for (int i = 0; i < nApps; i++) {
            App curApp = prInst.getApps().get(i);
            if (curApp.isOrderCritical()) {
                double[] slopes = new double[nPoints + 1];
                slopes[0] = 0;
                for (int j = 1; j < nPoints; j++) {
                    slopes[j] = (curApp.getPerfValues()[j] - curApp.getPerfValues()[j - 1]) * 1.0 /
                            (curApp.getDelays()[j] - curApp.getDelays()[j - 1]);
                }
                slopes[nPoints] = 0;

                obj[OCcounter] = cpFull.piecewiseLinearFunction(
                        curApp.getDelays(),
                        slopes,
                        curApp.getDelays()[0], curApp.getPerfValues()[0]);

                expr[OCcounter] = cpFull.lengthEval(latency[OCcounter], obj[OCcounter]);
                OCcounter++;
            }
        }

        cpFull.addObjective(IloObjectiveSense.Minimize, cpFull.max(expr));
    }

    private void setResConstr(boolean isGe, int rightHandPart, IloIntExpr expr, int gcd) throws IloException{
        IloNumExpr tmp;

        tmp = cpFull.modulo(
                cpFull.sum(
                        HP,
                        expr
                ),
                gcd
        );

        if(isGe){
            cpFull.addGe(tmp, rightHandPart);
        }
        else{
            cpFull.addLe(tmp, rightHandPart);
        }
    }

    private void setResourceConstraintsECU() throws IloException{
        for (int i = 0; i < numTasks; i++) {
            int taskINum = taskNumbers.get(i);
            for (int j = 0; j < prInst.getResourceConstraintActs().get(taskINum).size(); j++) {
                int taskJNum = prInst.getResourceConstraintActs().get(taskINum).get(j);
                int indJInSTArray = acts.get(taskJNum).getNumInTaskOrMessageArray();

                if((isActInNeigh[taskINum] || isActInNeigh[taskJNum])){// && taskINum != 27 && taskJNum != 27 ){
                    setResourceConstraintsECUForPairOfActs(i, indJInSTArray);
                }
            }
        }
    }

    private void setResourceConstraintsECUForPairOfActs(int indIInSTArray,
                                                        int indJInSTArray) throws IloException{
        int taskINum = taskNumbers.get(indIInSTArray);
        int taskJNum = taskNumbers.get(indJInSTArray);

        int periodI = acts.get(taskINum).getPeriod();
        int periodJ = acts.get(taskJNum).getPeriod();

        IloIntExpr expr = null;
        if(!isActInNeigh[taskINum] && isActInNeigh[taskJNum]){
            expr = cpFull.diff(schedActs.getActST()[taskINum][0], cpFull.startOf(stTasks[indJInSTArray]));
        }

        if(isActInNeigh[taskINum] && !isActInNeigh[taskJNum]){
            expr = cpFull.diff(cpFull.startOf(stTasks[indIInSTArray]), schedActs.getActST()[taskJNum][0]);
        }

        if(isActInNeigh[taskINum] && isActInNeigh[taskJNum]){
            expr = cpFull.diff(cpFull.startOf(stTasks[indIInSTArray]), cpFull.startOf(stTasks[indJInSTArray]));
        }

        boolean isGe = true;
        setResConstr(isGe, prInst.getActs().get(taskJNum).getProcTime(), expr, (int) prInst.getPairwiseGCD(periodI, periodJ));

        isGe = false;
        setResConstr(isGe, (int) prInst.getPairwiseGCD(periodI, periodJ) - prInst.getActs().get(taskINum).getProcTime(),
                expr, (int) prInst.getPairwiseGCD(periodI, periodJ));
    }

    protected void setParamsToSolver(long timeLimit, Main.RunningCharacteristics chars) throws IloException, IOException{
        if(Main.PRINT_OPT_OUTPUT_TO_TERMINAL_FILE) {
            cpFull.setOut(new FileOutputStream(chars.fullFileNameTerminalOut, true));
        }

        cpFull.setParameter(IloCP.IntParam.Workers, 1);
        cpFull.setParameter(IloCP.DoubleParam.TimeLimit, timeLimit);
        cpFull.setParameter(IloCP.IntParam.LogPeriod, 300000);
        if(isLocalNeiborhood){
            if(!Main.PRINT_LOCAL_NEIGH_RUN_BY_OPT_APPROACH) {
                cpFull.setOut(null);
            }
            cpFull.setParameter(IloCP.DoubleParam.TimeLimit, Main.time_limit_opt_heur_one_step);
            cpFull.setParameter(IloCP.DoubleParam.RelativeOptimalityTolerance, 0.001);
        }

    }

    public Solution solve(Main.RunningCharacteristics chars) throws IloException, FileNotFoundException, IOException {
        return solve(0, chars);
    }

    protected int[][] storeTasksAndMessages() throws IloException{
        int[][] startTimes = new int[nActs][prInst.getMaxNumJobs()];
        //first store tasks
        for (int i = 0; i < numTasks; i++) {
            for (int j = 0; j < prInst.getActs().get(taskNumbers.get(i)).getNJobs(); j++) {
                if(isActInNeigh[taskNumbers.get(i)]){
                    if(Main.SOLVE_CP_INTERVAL_VARS){
                        startTimes[taskNumbers.get(i)][j] = (int) Math.round(cpFull.getStart(stTasksInts[i][0]))
                        + prInst.getActs().get(taskNumbers.get(i)).getPeriod() * j;
                    }
                    else{
                        startTimes[taskNumbers.get(i)][j] = (int) Math.round(cpFull.getStart(stTasks[i]))
                        + prInst.getActs().get(taskNumbers.get(i)).getPeriod() * j;
                    }
                }
                else{
                    startTimes[taskNumbers.get(i)][j] = schedActs.getActST()[taskNumbers.get(i)][j];
                }
            }
        }

        //store messages
        for (int i = 0; i < numMessages; i++) {
            for (int j = 0; j < prInst.getActs().get(messageNumbers.get(i)).getNJobs(); j++) {
                if(isActInNeigh[messageNumbers.get(i)]){
                    startTimes[messageNumbers.get(i)][j] =
                            (int) Math.round(cpFull.getStart(stMes[i][j]));
                }
                else{
                    startTimes[messageNumbers.get(i)][j] = schedActs.getActST()[messageNumbers.get(i)][j];
                }

            }
        }

        setSTOfUnscheduledActivities(startTimes);
        return startTimes;
    }

    @Override
    protected boolean solveModel() throws IloException {
        return cpFull.solve();
    }

    @Override
    protected void endSolver() {
        cpFull.end();
    }

    @Override
    protected int getLatency(int i) throws IloException{
        return (int) Math.round(cpFull.getStart(latency[i]));
    }

    @Override
    protected double[] getControlPerformanceValues() throws IloException {
        double[] controlPerfValues = new double[prInst.getNumOrdCritApps()];

        for (int i = 0; i < prInst.getNumOrdCritApps(); i++) {
            controlPerfValues[i] = cpFull.getValue(cpFull.startEval(latency[i], obj[i]));
        }

        return controlPerfValues;
    }

    @Override
    protected boolean solverProveInfeasibility() throws IloException {
        return cpFull.getInfo(IloCP.IntInfo.FailStatus) != 14;
    }

    @Override
    protected double getObjValue() throws IloException {
        return cpFull.getObjValue();
    }

    @Override
    protected void exportModel() throws IloException {
        cpFull.exportModel(Helpers.outputFileForModelsCP);
    }
}
