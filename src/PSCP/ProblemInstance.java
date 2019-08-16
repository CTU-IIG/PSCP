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
import java.io.*;
import java.math.BigInteger;
import java.util.*;

public class ProblemInstance {
    // core parameters
    private int nResources;
    private int nLinks;
    private List<Activity> acts;
    private List<App> apps;

    // deduced parameters
    private List<List<Integer>> resourceConstraintActs;
    private List<List<Integer>> fullResourceConstraintActs;
    private List<List<Integer>> deducedPrecedence;
    private Double[] utilization;
    public static long[][] pairwiseGCD;
    private List<Integer> uniquePeriods;
    
    //computed values
    private int HP;
    private int numMessages;
    private int maxNumMessageOccurr;
    private int maxNumJobs;
    private boolean isSchedulable;
    private int[] minPeriodOnResources;
    private int numOrdCritApps;
    private int nJobs;
    private double maxCritValue;

    private class AmaltheaFilesProcessing {
        ActivitiesCharacteristics chars;
        Integer[] processingTime;
        Integer[] periods;
        Integer[] assignementToClusters;
        Integer[] assignmentToResources;
        List<List<Integer>> precedenceAdjList;

        int numRunnables;
        int numCommunications;
        Integer[] processingTimesOfRunnables;
        Integer[] periodsOfRunnables;
        List<List<Integer>> sendLabRecOrder;
        List<List<Integer>> sendLabRecNonOrderChains;
        int[] commTOCritMes;
        List<List<Integer>> commTNOCritMes;

        private void readData(String fileName) throws FileNotFoundException {
            Scanner in = new Scanner(new File(fileName));
            int nChains = Helpers.ReadIntegerNumber(in);

            //!there is always one line between two data arrays!
            Integer[] numberOfActivitiesInChain = Helpers.readIntegerArray(in, nChains);

            int totalNumMesInChains = 0;
            int[] numMesInChains = new int[nChains];
            for(int i = 0; i < nChains; i++) {
                numMesInChains[i] = numberOfActivitiesInChain[i] -
                        ((int)Math.ceil(numberOfActivitiesInChain[i] / 2.0) - 1);
                totalNumMesInChains += numMesInChains[i];
            }

            Integer[] runnablesInChains = Helpers.readIntegerArray(in, totalNumMesInChains);
            commTNOCritMes = Helpers.read2DIntegerArray(in);
            numRunnables = commTNOCritMes.size();

            processingTimesOfRunnables = Helpers.readIntegerArray(in, numRunnables);
            periodsOfRunnables = Helpers.readIntegerArray(in, numRunnables);

            boolean[] isRunnableAPartOfCauseEffectChain = new boolean [numRunnables];
            for(int i = 0; i < runnablesInChains.length; i++) {
                isRunnableAPartOfCauseEffectChain[runnablesInChains[i] - 1] = true;
            }

            List<Integer> runnablesNotPartOfCauseEffectChain = new ArrayList<Integer>();
            for(int i = 0; i < numRunnables; i++) {
                if(isRunnableAPartOfCauseEffectChain[i] == false){
                    runnablesNotPartOfCauseEffectChain.add(i);
                }
            }

            commTOCritMes = Helpers.readIntegerArrayWithoutGivenLength(in);
            Helpers.readIntegerArrayWithoutGivenLength(in);

            sendLabRecOrder = Helpers.read2DIntegerArray(in);
            sendLabRecNonOrderChains = Helpers.read2DIntegerArray(in);
            numCommunications = sendLabRecOrder.size() +
                    sendLabRecNonOrderChains.size();
        }

        // aggregate multiple non order critical messages between the same sender and receiver into one message,
        // set computation time at 2. posiition and remove order-critical messages
        private List<List<Integer>> agregateNonOrderCriticalMes(){
            List<List<Integer>> aggregatedArray = new ArrayList<>();
            HashMap<String, List<Integer>> messages = new HashMap<String, List<Integer>>();
            int nRunSendingPrev = -1;
            int nMesOfThisRun = -1;
            for (int i = 0; i < sendLabRecNonOrderChains.size(); i++) {
                int nSending = sendLabRecNonOrderChains.get(i).get(0) - 1;
                int nReceiving = sendLabRecNonOrderChains.get(i).get(2) - 1;
                int nLabel = sendLabRecNonOrderChains.get(i).get(1);
                String curKey = Integer.toString(nSending) + "." + Integer.toString(nReceiving);

                if(nSending == nRunSendingPrev){
                    nMesOfThisRun++;
                }
                else {
                    nMesOfThisRun = 0;
                }

                // if this sender-label-receiver is in order critical, skip
                boolean skip = false;
                for (int j = 0; j < sendLabRecOrder.size(); j++) {
                    if(nSending == sendLabRecOrder.get(j).get(0) - 1
                            && nReceiving == sendLabRecOrder.get(j).get(sendLabRecOrder.get(j).size() - 1) - 1
                            && sendLabRecOrder.get(j).subList(1, sendLabRecOrder.get(j).size() - 1).contains(nLabel)) {
                        skip = true;
                        break;
                    }
                }

                if(skip) {
                    continue;
                }

                //if it is not contained in messages array, create new entry both in messages and in aggregated array
                if(!messages.containsKey(curKey)){
                    // it is a new sender-receiver pair, create new element in aggregatedArray
                    aggregatedArray.add(new ArrayList<Integer>());
                    aggregatedArray.get(aggregatedArray.size() - 1).add(nSending);
                    aggregatedArray.get(aggregatedArray.size() - 1).add(commTNOCritMes.get(nSending).get(nMesOfThisRun));
                    aggregatedArray.get(aggregatedArray.size() - 1).add(nReceiving);
                    messages.put(curKey, Arrays.asList(aggregatedArray.size() - 1, nLabel));
                }
                else {
                    if(!messages.get(curKey).contains(nLabel)){
                        // if this sender-receiver pair is already in messages array, but label is not, add execution time
                        // to the corresponding aggregated array entry
                        int numEntryInAggrArray = messages.get(curKey).get(0);
                        int curExecTime = aggregatedArray.get(numEntryInAggrArray).get(1);
                        aggregatedArray.get(numEntryInAggrArray).set(1, curExecTime + commTNOCritMes.get(nSending).get(nMesOfThisRun));
                    }
                }

                nRunSendingPrev = nSending;
            }

            return aggregatedArray;
        }

        private void addMessageToPrecedenceAdjList(int nSending, int nReceiving, int nMes) {
            precedenceAdjList.add(new ArrayList<Integer>());
            precedenceAdjList.get(precedenceAdjList.size() - 1).add(nSending);
            precedenceAdjList.get(nReceiving).add(nMes);
        }

        public AmaltheaFilesProcessing(String fileName) throws FileNotFoundException {
            readData(fileName);

            List<List<Integer>> aggregatedNOCArray = agregateNonOrderCriticalMes();
            int nActs = numRunnables + sendLabRecOrder.size() + aggregatedNOCArray.size();

            //fill necessary parameters
            processingTime = new Integer [nActs];
            periods = new Integer [nActs];

            // first runnables
            precedenceAdjList = new ArrayList<>();
            for (int i = 0; i < numRunnables; i++) {
                processingTime[i] = processingTimesOfRunnables[i];
                periods[i] = periodsOfRunnables[i];
                precedenceAdjList.add(new ArrayList<Integer>());
            }

            // then order-critical
            for (int i = numRunnables, j = 0; i < numRunnables + sendLabRecOrder.size(); i++, j++) {
                int nSending = sendLabRecOrder.get(j).get(0) - 1;
                int nReceiving = sendLabRecOrder.get(j).get(sendLabRecOrder.get(j).size() - 1) - 1;
                periods[i] = periodsOfRunnables[nSending];
                processingTime[i] = commTOCritMes[j];

                addMessageToPrecedenceAdjList(nSending, nReceiving, i);
            }

            // finally, non-order-critical
            int[] sendingNOCMes = new int [aggregatedNOCArray.size()];
            int[] receivingNOCMes = new int [aggregatedNOCArray.size()];
            for (int i = numRunnables + sendLabRecOrder.size(), j = 0; i < nActs; i++, j++) {
                int nSending = aggregatedNOCArray.get(j).get(0);
                sendingNOCMes[j] = nSending;
                int nReceiving = aggregatedNOCArray.get(j).get(2);
                receivingNOCMes[j] = nReceiving;

                periods[i] = periodsOfRunnables[nSending];
                processingTime[i] = aggregatedNOCArray.get(j).get(1);
                precedenceAdjList.add(new ArrayList<Integer>());
            }

            chars = new ActivitiesCharacteristics(processingTime, periods, assignementToClusters,
                    assignmentToResources, precedenceAdjList, numRunnables, aggregatedNOCArray.size(),
                    sendingNOCMes, receivingNOCMes);
        }
    }

    private class ActivitiesCharacteristics {
        private Integer[] processingTime;
        private Integer[] periods;
        private Integer[] assignementToApps;
        private Integer[] assignmentToResources;
        private List<List<Integer>> precedenceAdjList;
        private int numActs;
        private int nTasks;
        private int nNOCritMes;
        private int[] sendingNOCMes;
        private int[] receivingNOCMes;

        public ActivitiesCharacteristics(Integer[] processingTime, Integer[] periods, Integer[] assignementToApps_,
                                         Integer[] assignmentToResources, List<List<Integer>> precedenceAdjList,
                                         int nTasks_, int nNOCritMes_, int[] sendingNOCMes_, int[] receivingNOCMes_) {
            sendingNOCMes = sendingNOCMes_;
            receivingNOCMes = receivingNOCMes_;
            nNOCritMes = nNOCritMes_;
            this.processingTime = processingTime;
            this.periods = periods;
            this.assignementToApps = assignementToApps_;
            this.assignmentToResources = assignmentToResources;
            this.precedenceAdjList = precedenceAdjList;
            numActs = periods.length;
            nTasks = nTasks_;
        }
    }

    private void generateControlPerformances(double[][] delays, double[][] controlPerfValues){
        for (int i = 0; i < apps.size(); i++) {
            delays[i][0] = apps.get(i).getMinE2ELatency();
            controlPerfValues[i][0] = apps.get(i).getE2eLatBound();
            double step = (apps.get(i).getE2eLatBound() - delays[i][0]) / Main.nDiscreeteLatValues;
            for (int j = 1; j < Main.nDiscreeteLatValues - 1; j++) {
                delays[i][j] = delays[i][j - 1] + step;
                if(delays[i][j] == delays[i][j - 1]){
                    delays[i][j]++;
                }
                controlPerfValues[i][j] = controlPerfValues[i][j - 1] - step;
                if(controlPerfValues[i][j] == controlPerfValues[i][j - 1]){
                    controlPerfValues[i][j]--;
                }
            }
            delays[i][9] = apps.get(i).getE2eLatBound();
            if(delays[i][9] <= delays[i][8]){
                delays[i][9] = delays[i][8] + 1;
            }
            controlPerfValues[i][Main.nDiscreeteLatValues - 1] = 0;
        }
        
    }

    private ActivitiesCharacteristics readDataFromDAT(String fileName) throws FileNotFoundException{
        Scanner in = new Scanner(new File(fileName));
        Helpers.ReadIntegerNumber(in);
        nResources = Helpers.ReadIntegerNumber(in);
        int numActs = Helpers.ReadIntegerNumber(in);
        nLinks = Helpers.ReadIntegerNumber(in);

        // there must be always one line between two data arrays
        Integer[] assignmentToResources = Helpers.readIntegerArray(in, numActs);
        
        for (int i = 0; i < numActs; i++) {
            if(assignmentToResources[i] > nResources - nLinks){
                numMessages++;
            }
        }

        Integer[] processingTime = Helpers.readIntegerArray(in, numActs);
        Integer[] periods = Helpers.readIntegerArray(in, numActs);
        Integer[] assignementToClusters = Helpers.readIntegerArray(in, numActs);
        boolean isClusterZero = false;
        for (int i = 0; i < numActs; i++) {
            if(assignementToClusters[i] == 0){
                isClusterZero = true;
                break;
            }
        }
        if(isClusterZero){
            for (int i = 0; i < numActs; i++) {
                assignementToClusters[i]++;
            }
        }

        List<List<Integer>> precedenceAdjList = Helpers.read2DIntegerArray(in);

        ActivitiesCharacteristics chars = new ActivitiesCharacteristics(processingTime, periods,
                assignementToClusters, assignmentToResources, precedenceAdjList, numActs - numMessages, 0, null, null);

        return chars;
    }

    private void changeInstanceForLargestSet() throws IOException, IloException {
        nResources *= 2;
        nLinks *= 2;
        int nActsInit = acts.size();
        for (int i = 0; i < nActsInit; i++) {
            acts.get(i).setnResources(nResources);
            acts.add(Helpers.duplicateActivityDoubles(acts.get(i), nActsInit, nResources/2, apps.size()));
        }

        int initSizeApps = apps.size();
        for (int i = 0; i < initSizeApps; i++) {
            App newApp = duplicateApplication(apps.get(i), nActsInit);
            newApp.setControlValues(apps.get(i).getDelays(), apps.get(i).getPerfValues());
            apps.add(newApp);
        }
    }

    public App duplicateApplication(App app, int nActsInit) throws IOException, IloException {
        List<Activity> actsInApp = new ArrayList<>();
        for (int i = 0; i < app.getActs().size(); i++) {
            actsInApp.add(acts.get(app.getActs().get(i).getID() + nActsInit));
        }

        return new App(actsInApp, null, null, this, true);
    }

    //ATTENTION! Numbers of acts in dependencies MUST BE in the range from 0 to n-1 (not from 1)
    public ProblemInstance(String fileName, Main.RunningCharacteristics runningChars, double required_utilization)
            throws IloException, IOException{
        ActivitiesCharacteristics actChars;
        isSchedulable = true;

        if(runningChars.isGenerating) {
            AmaltheaFilesProcessing proc = new AmaltheaFilesProcessing(fileName);
            actChars = proc.chars;
        }
        else {
            if(!Main.RUN_GENERAL_USE_CASE) {
                actChars = readDataFromDAT(fileName);
                if (required_utilization > 0.1) {
                    scaleResWithMaxUtAndUtMoreThanGivenToRequiredUt(required_utilization, actChars);
                }
            }
            else{
                //actChars = readDataFromOpenDSEXML(fileName);
            }
        }

        createActivities(actChars, runningChars);

        if(Main.IS_INSTANCE_GENERATION_MODE && runningChars.isGenerating) {
            mapToTTEthernetSynthetic(runningChars);
        }

        if(Main.RUN_GENERAL_USE_CASE) {
            mapToTTEthernetUseCase(runningChars);
        }

        if(runningChars.nSet == 7) {
            changeInstanceForLargestSet();
        }

        numOrdCritApps = 0;
        for (int i = 0; i < apps.size(); i++) {
            if(apps.get(i).isOrderCritical()) {
                numOrdCritApps++;
            }
        }

        utilization = computeTotalUtilizationOnEachResource();
        printUtilisations(utilization, runningChars);
        if(Main.IS_DEBUG_MODE) {
            Helpers.printArrayElementPerRow(utilization);
        }

        computeMaxNumMessageOccurr();
        if(!Main.RUN_EMS_USE_CASE) {
            fillDisjunctiveActivities();
        }
        createPairwiseGCD(actChars);
        //IsInstanceSchedulable();

        parseControlPerformanceAndSetToApps();
    }
    
    public static long gcd(double a, int b) {
        return BigInteger.valueOf((int) Math.round(a)).gcd(BigInteger.valueOf(b)).intValue();
    }

    public Double[] computeTotalUtilizationOnEachResource(){
        int nRes = acts.get(0).getnResources();
        utilization = new Double [nRes];

        for(int i = 0; i < nRes; i++){
            utilization[i] = 0.;
        }

        for(int j = 0; j < acts.size(); j++) {
            utilization[acts.get(j).getAssToRes() - 1] +=
                    acts.get(j).getProcTime() * 1.0 / acts.get(j).getPeriod();
        }
        
        return utilization;
    }
    
    public void printUtilisations(Double[] utilization, Main.RunningCharacteristics chars) throws IOException{
        String s;
        s = "\n\n Utilization on the resources is:";
        for(int i = 0; i < nResources; i++) {
            s = s + "\n" + utilization[i];
        }
        
        Helpers.printToFile(chars.fullFileNameTerminalOut, s);
    }
    
    private Double[] computeTotalUtilizationOnEachResource(ActivitiesCharacteristics chars){
        utilization = new Double [nResources];

        for(int i = 0; i < nResources; i++){
            utilization[i] = 0.;
        }

        for(int j = 0; j < chars.numActs; j++) {
            utilization[chars.assignmentToResources[j] - 1] +=
                    chars.processingTime[j] * 1.0 / chars.periods[j];
        }
        
        return utilization;
    }
    
    private void createPairwiseGCD(ActivitiesCharacteristics chars){
        uniquePeriods = new ArrayList<Integer>();
        for (int i = 0; i < chars.numActs; i++) {
            if(!uniquePeriods.contains(chars.periods[i])) {
                uniquePeriods.add(chars.periods[i]);
            }
        }
        Helpers.uniquePeriods = uniquePeriods;
        
        int numUniquePeriods = uniquePeriods.size();
        pairwiseGCD = new long[numUniquePeriods][numUniquePeriods];
        for (int i = 0; i < numUniquePeriods; i++) {
            for (int j = i; j < numUniquePeriods; j++) {
                pairwiseGCD[i][j] = pairwiseGCD[j][i] = gcd(uniquePeriods.get(i),uniquePeriods.get(j));
            }
        }
        Helpers.pairwiseGCD = pairwiseGCD;
    }

    private void copyActsAndAppsToNewArray(List<Activity> newActs, List<App> newApps, List<Activity> acts,
                                           List<App> apps) throws IOException, IloException {
        for (int i = 0; i < acts.size(); i++) {
            newActs.add(Helpers.createNewActivityCopyOfGiven(acts.get(i)));
        }

        for (int i = 0; i < apps.size(); i++) {
            List<Activity> actsForApp = new ArrayList<>();
            for (int j = 0; j < apps.get(i).getActs().size(); j++) {
                actsForApp.add(newActs.get(apps.get(i).getActs().get(j).getID()));
            }

            newApps.add(Helpers.createNewAppCopyOfGiven(apps.get(i)));
            newApps.get(i).setActivities(actsForApp);
        }
    }

    public void mapToTTEthernetSynthetic(Main.RunningCharacteristics chars) throws IOException, IloException {
            List<Activity> oldActs = acts;
            List<App> oldApps = apps;

            // make a hard copy of intial acts and store to newActs and newApps
            List<Activity> newActs = new ArrayList<>(oldActs.size());
            List<App> newApps = new ArrayList<>(oldApps.size());
            copyActsAndAppsToNewArray(newActs, newApps, oldActs, oldApps);

            acts = newActs;
            apps = newApps;
            MappingToTTEthernet mapping = new MappingToTTEthernet(this);
            nLinks = mapping.doMappingSyntheticProbInst(chars);
            computeTotalUtilizationOnEachResource();

            nResources = acts.get(0).getnResources();
            nLinks = nResources - Main.NUM_ECUs;

            for (int i = 0; i < apps.size(); i++) {
                apps.get(i).doNecessaryComputations();
                if(apps.get(i).isProblematic()){
                    isSchedulable = false;
                    return;
                }
            }
    }

    public void mapToTTEthernetUseCase(Main.RunningCharacteristics chars) throws IOException, IloException {
        List<Activity> oldActs = acts;
        List<App> oldApps = apps;

        List<Activity> newActs = new ArrayList<>(oldActs.size());
        List<App> newApps = new ArrayList<>(oldApps.size());
        copyActsAndAppsToNewArray(newActs, newApps, oldActs, oldApps);

        acts = newActs;
        apps = newApps;
        MappingToTTEthernet mapping = new MappingToTTEthernet(this);
        nLinks = mapping.doMappingUseCase(chars);
        computeTotalUtilizationOnEachResource();

        nResources = acts.get(0).getnResources();

        for (int i = 0; i < apps.size(); i++) {
            apps.get(i).doNecessaryComputations();
            if(apps.get(i).isProblematic()){
                isSchedulable = false;
                return;
            }
        }

        numMessages = 0;
        for (int i = 0; i < acts.size(); i++) {
            if(!acts.get(i).isTask()) {
                numMessages++;
            }
        }
    }

    public void createActivities(ActivitiesCharacteristics actChars, Main.RunningCharacteristics runningChars)
            throws IloException, IOException{
        HP = computeHP(actChars.periods);
        acts = new ArrayList<>();
        List<List<Integer>> followersAdjList = createFollowersAdjList(actChars.precedenceAdjList);

        for(int i = 0; i < actChars.numActs; i++){
            int numJobs = HP / actChars.periods[i];

            int sendingTask = -1;
            int receivingTask = -1;
            if(i >= actChars.numActs - actChars.nNOCritMes){
                sendingTask = actChars.sendingNOCMes[i - (actChars.numActs - actChars.nNOCritMes)];
                receivingTask = actChars.receivingNOCMes[i - (actChars.numActs - actChars.nNOCritMes)];
            }

            if(Main.IS_INSTANCE_GENERATION_MODE && runningChars.isGenerating) {
                acts.add(new Activity(actChars.processingTime[i],
                        actChars.periods[i], i, numJobs, actChars.precedenceAdjList.get(i),
                        followersAdjList.get(i), HP, i < actChars.nTasks, sendingTask, receivingTask, 0));
            }
            else {
                acts.add(new Activity(actChars.assignmentToResources[i], actChars.processingTime[i], actChars.periods[i], i,
                        numJobs, actChars.precedenceAdjList.get(i), followersAdjList.get(i), HP, actChars.assignementToApps[i],
                        actChars.assignmentToResources[i] <= nResources - nLinks, nResources));
            }
        }
        
        computeMaximumNumberOfJobs();
        createDAGS(actChars.precedenceAdjList, followersAdjList, runningChars);
    }
    
    private void computeMaxNumMessageOccurr(){
        for (int i = 0; i < acts.size(); i++) {
            if(acts.get(i).getAssToRes() > nResources - nLinks &&
                    maxNumMessageOccurr < acts.get(i).getNJobs()){
                maxNumMessageOccurr = acts.get(i).getNJobs();
            }
        } 
    }
    
    private int computeHP(Integer[] periods) {
        long localHP = periods[0];
        for (int i = 1; i < periods.length; i++) {
            if(localHP != periods[i]){
                localHP = localHP * periods[i] / gcd(localHP,periods[i]);
            }
        }
        
        return (int) localHP;
    }
     
    public List<List<Integer>> createFollowersAdjList(List<List<Integer>> precedenceAdjList){
        List<List<Integer>> followersAdjList = new ArrayList<List<Integer>>();
        for(int i = 0; i < precedenceAdjList.size(); i++){
            followersAdjList.add(new ArrayList<Integer>());
        }
        
        for(int i = 0; i < precedenceAdjList.size(); i++){
            for(int j = 0; j < precedenceAdjList.get(i).size(); j++) {
                followersAdjList.get(precedenceAdjList.get(i).get(j)).add(i);
            }
        }

        return followersAdjList;
    }
    
    private void computeMaximumNumberOfJobs(){
        maxNumJobs = 0;
        for(int i = 0; i < acts.size(); i++){
            if(maxNumJobs < acts.get(i).getNJobs()){
                maxNumJobs = acts.get(i).getNJobs();
            }
        }
    }
   
    
    public void createDAGS(List<List<Integer>> precedenceAdjList, List<List<Integer>> followersAdjList,
                           Main.RunningCharacteristics chars) throws IloException, IOException{
        boolean[] includedInDAG = new boolean [acts.size()];
        apps = new ArrayList<>();
        for(int i = 0; i < acts.size(); i++) {
            if(!includedInDAG[i] && (!precedenceAdjList.get(i).isEmpty() || !followersAdjList.isEmpty())){
                //we can create new DAG
                List<Activity> actsForDAG = new ArrayList<Activity>();
                List<List<Integer>> precedenceAdjListForDAG = new ArrayList<List<Integer>>();
                List<List<Integer>> followersAdjListForDAG = new ArrayList<List<Integer>>();
                List<Integer> numberOfTasksInThisDAG = new ArrayList<Integer>();
                
                //add current activity to the DAG
                addTaskToDAG(i, includedInDAG, actsForDAG, precedenceAdjListForDAG, followersAdjListForDAG,
                        numberOfTasksInThisDAG, precedenceAdjList, followersAdjList);
                
                addOtherTasksToDAG(includedInDAG, actsForDAG, precedenceAdjListForDAG,
                        followersAdjListForDAG, numberOfTasksInThisDAG, precedenceAdjList, followersAdjList);

                boolean isFinal = true;
                if(Main.IS_INSTANCE_GENERATION_MODE && chars.isGenerating){
                    isFinal = false;
                }

                for (int j = 0; j < actsForDAG.size(); j++) {
                    actsForDAG.get(j).setAssToApp(apps.size() + 1);
                }

                apps.add(new App(Helpers.arrayListToActivityArray(actsForDAG),
                        precedenceAdjListForDAG, followersAdjListForDAG, this, isFinal));
                
                if(apps.get(apps.size() - 1).isProblematic() && Main.IS_INSTANCE_GENERATION_MODE){
                    isSchedulable = false;
                    return;
                }
            }
        }

        for (int i = 0; i < apps.size(); i++) {
            for (int j = 0; j < apps.get(i).getActs().size(); j++) {
                apps.get(i).getActs().get(j).setAssToApp(i + 1);
            }
        }

        double[][] delays = new double[apps.size()][Main.nDiscreeteLatValues];
        double[][] controlPerfValues = new double[apps.size()][Main.nDiscreeteLatValues];
        generateControlPerformances(delays, controlPerfValues);

        for (int i = 0; i < apps.size(); i++) {
            apps.get(i).setControlValues(delays[i],controlPerfValues[i]);
        }
    }
    
    private void addTaskToDAG(int numTask, boolean[] includedInDAG, List<Activity> actsForDAG,
                              List<List<Integer>> precedenceAdjListForDAG, List<List<Integer>> followersAdjListForDAG,
                              List<Integer> numberOfTasksInThisDAG, List<List<Integer>> precedenceAdjList,
                              List<List<Integer>> followersAdjList){
        includedInDAG[numTask] = true;
        actsForDAG.add(acts.get(numTask));
        precedenceAdjListForDAG.add(new ArrayList<Integer>());
        followersAdjListForDAG.add(new ArrayList<Integer>());
        int numCurrentTaskInDAG = precedenceAdjListForDAG.size() - 1;
        for(int j = 0; j < precedenceAdjList.get(numTask).size(); j++) {
            precedenceAdjListForDAG.get(numCurrentTaskInDAG).add(precedenceAdjList.get(numTask).get(j));
        }
        for(int j = 0; j < followersAdjList.get(numTask).size(); j++) {
            followersAdjListForDAG.get(numCurrentTaskInDAG).add(followersAdjList.get(numTask).get(j));
        }
        numberOfTasksInThisDAG.add(numTask);
    }
    
    private void addOtherTasksToDAG(boolean[] isIncluded, List<Activity> actsForDAG,
                                    List<List<Integer>> precedenceAdjListForDAG, List<List<Integer>> followersAdjListForDAG,
                                    List<Integer> numberOfTasksInThisDAG, List<List<Integer>> precedenceAdjList, List<List<Integer>> followersAdjList){
        int numCurrentTaskInTaskArray = precedenceAdjListForDAG.size() - 1;
        for(int i = 0; i < precedenceAdjListForDAG.get(numCurrentTaskInTaskArray).size(); i++) {
            int currentPredecessor = precedenceAdjListForDAG.get(numCurrentTaskInTaskArray).get(i);
            if(!numberOfTasksInThisDAG.contains(currentPredecessor)){
                addTaskToDAG(currentPredecessor, isIncluded, actsForDAG, precedenceAdjListForDAG,
                        followersAdjListForDAG, numberOfTasksInThisDAG, precedenceAdjList, followersAdjList);
                 addOtherTasksToDAG(isIncluded, actsForDAG, precedenceAdjListForDAG,
                        followersAdjListForDAG, numberOfTasksInThisDAG, precedenceAdjList, followersAdjList);
            }
        }
        
        for(int i = 0; i < followersAdjListForDAG.get(numCurrentTaskInTaskArray).size(); i++) {
            int currentFollower = followersAdjListForDAG.get(numCurrentTaskInTaskArray).get(i);
            if(!numberOfTasksInThisDAG.contains(currentFollower)){
                addTaskToDAG(currentFollower, isIncluded, actsForDAG,
                        precedenceAdjListForDAG, followersAdjListForDAG, numberOfTasksInThisDAG,
                        precedenceAdjList, followersAdjList);
                addOtherTasksToDAG(isIncluded, actsForDAG, precedenceAdjListForDAG,
                        followersAdjListForDAG, numberOfTasksInThisDAG, precedenceAdjList,
                        followersAdjList);
            }
        }
    }

    private void fillDisjunctiveActivities(){
        resourceConstraintActs = new ArrayList<>();
        fullResourceConstraintActs = new ArrayList<>();
        deducedPrecedence = new ArrayList<>();
        for (int i = 0; i < acts.size(); i++) {
            resourceConstraintActs.add(new ArrayList());
            fullResourceConstraintActs.add(new ArrayList());
            deducedPrecedence.add(new ArrayList());
        }
        
        for (int i = 0; i < acts.size(); i++) {
            for (int j = i + 1; j < acts.size(); j++) {
                if(acts.get(i).getAssToRes() == acts.get(j).getAssToRes()){
                    if(acts.get(i).getPeriod() != acts.get(j).getPeriod() ||
                            acts.get(i).getLB() + acts.get(i).getProcTime() <= acts.get(j).getUB() || 
                            acts.get(j).getLB() + acts.get(j).getProcTime() <= acts.get(i).getUB()){
                        resourceConstraintActs.get(i).add(j);
                        fullResourceConstraintActs.get(i).add(j);
                        fullResourceConstraintActs.get(j).add(i);
                    }

                    if(acts.get(i).getLB() + acts.get(i).getProcTime() > acts.get(j).getUB() && 
                            acts.get(i).getPeriod() == acts.get(j).getPeriod()){
                        deducedPrecedence.get(i).add(j);
                    }
                    
                    if(acts.get(j).getLB() + acts.get(j).getProcTime() > acts.get(i).getUB() && 
                            acts.get(i).getPeriod() == acts.get(j).getPeriod()){
                        deducedPrecedence.get(j).add(i);
                    }
                }
            }
        }
    }


    public void scaleToRequiredUtilization(double required_utilization, boolean areActsCreated, ActivitiesCharacteristics chars){
        Double[] utilization = computeTotalUtilizationOnEachResource(chars);
        
        double[] coefficientForScalingECU = new double[utilization.length];
        for (int i = 0; i < utilization.length; i++) {
            coefficientForScalingECU[i] = required_utilization / utilization[i];
        }
        
        for(int i = 0; i < chars.numActs; i++) {
            if(chars.assignmentToResources[i] <= nResources - nLinks){
                chars.processingTime[i] = (int) (chars.processingTime[i] * coefficientForScalingECU[chars.assignmentToResources[i] - 1]);
                if(areActsCreated){
                    acts.get(i).setProcTime(chars.processingTime[i]);
                }
            }
            else{
                break;
            }
        }

        for(int j = nResources - nLinks; j < nResources; j++) {
            double coefficientForScalingNetwork = required_utilization / utilization[j];
            for(int k = 0; k < chars.numActs; k++) {
                if(chars.assignmentToResources[k] == j + 1){
                    chars.processingTime[k] = (int) (chars.processingTime[k] * coefficientForScalingNetwork);
                    if(areActsCreated){
                        acts.get(k).setProcTime(chars.processingTime[k]);
                    }
                }
            }
        }
    }

    public void scaleResWithMaxUtAndUtMoreThanGivenToRequiredUt(double required_utilization, ActivitiesCharacteristics chars){
        Double[] utilization = computeTotalUtilizationOnEachResource(chars);

        int indMax = 0;
        for (int i = 0; i < utilization.length; i++) {
            if (utilization[indMax] < utilization[i]) {
                indMax = i;
                continue;
            }
        }

        for (int i = 0; i < utilization.length; i++) {
            if (utilization[i] > required_utilization || i == indMax) {
                double scalCoefThisResource = required_utilization / utilization[i];
                for (int j = 0; j < chars.numActs; j++) {
                    if (chars.assignmentToResources[j] == i + 1) {
                        chars.processingTime[j] = (int) (chars.processingTime[j] * scalCoefThisResource);
                    }
                }
            }
        }
    }

    public void scaleResWithMaxUtAndUtMoreThanGivenToRequiredUt(double required_utilization){
        int indMax = 0;
        for (int i = 0; i < utilization.length; i++) {
            if (utilization[indMax] < utilization[i]) {
                indMax = i;
                continue;
            }
        }

        for (int i = 0; i < utilization.length; i++) {
            if (utilization[i] > required_utilization || i == indMax) {
                double scalCoefThisResource = required_utilization / utilization[i];
                for (int j = 0; j < acts.size(); j++) {
                    if (acts.get(j).getAssToRes() == i + 1) {
                        int prTime = (int) (acts.get(j).getProcTime() * scalCoefThisResource);
                        acts.get(j).setProcTime(prTime);
                    }
                }
            }
        }
    }

    public void exportForControlPerformanceGeneration(String Filename) throws IOException{
        FileWriter writer = new FileWriter(Filename, true);
        for (int i = 0; i < apps.size(); i++) {
            writer.write(i + " " + apps.get(i).getMinE2ELatency() + " " + 2 * apps.get(i).getPeriod()
                    + " " + apps.get(i).getPeriod() + "\n");
        }
        writer.close();
    }

    public void exportProblemInstanceWithTTEthernet(String fileName) throws IOException{
        FileWriter writer = new FileWriter(fileName, false);
        writer.write("nApps = " + apps.size() + "\n");
        writer.write("nRes = " + nResources + "\n");
        writer.write("nActs = " + acts.size() + "\n");
        writer.write("nNetworks = " + nLinks + "\n\n");

        writer.write("assignmentToResources = [");
        for (int i = 0; i < acts.size() - 1; i++) {
            writer.write(acts.get(i).getAssToRes() + ",");
        }
        writer.write(acts.get(acts.size() - 1).getAssToRes() + "];\n\n");

        writer.write("processingTimes = [");
        for (int i = 0; i < acts.size() - 1; i++) {
            writer.write(acts.get(i).getProcTime() + ",");
        }
        writer.write(acts.get(acts.size() - 1).getProcTime() + "];\n\n");

        writer.write("periods = [");
        for (int i = 0; i < acts.size() - 1; i++) {
            writer.write(acts.get(i).getPeriod() + ",");
        }
        writer.write(acts.get(acts.size() - 1).getPeriod() + "];\n\n");

        writer.write("assignmentToClusters = [");
        for (int i = 0; i < acts.size() - 1; i++) {
            writer.write(acts.get(i).getAssToApp() + ",");
        }
        writer.write(acts.get(acts.size() - 1).getAssToApp() + "];\n\n");

        writer.write("precedenceAdjList = [");
        for (int i = 0; i < acts.size(); i++) {
            writer.write("[");
            for (int j = 0; j < acts.get(i).getDirectPreds().size() - 1; j++) {
                writer.write(acts.get(i).getDirectPreds().get(j) + ",");
            }
            if (acts.get(i).getDirectPreds().isEmpty()) {
                writer.write("]");
            } else {
                writer.write(acts.get(i).getDirectPreds().get(acts.get(i).getDirectPreds().size() - 1) + "]");
            }

            if (i < acts.size() - 1) {
                writer.write(",");
            } else {
                writer.write("];");
            }
        }

        writer.close();

        /*
        nApps = 13
        nRes = 6
        nActs = 23
        nNetworks = 3

        assignmentToResources = [1,1,1,1,2,3,1,1,1,1,1,1,1,2,1,2,1,1,1,2,5,4,5];

        processingTimes = [17,7,17,16,12,16,10,5,18,16,12,6,12,12,18,7,19,14,16,16,1,1,1];

        periods = [20000,20000,20000,20000,1000,1000,10000,10000,10000,10000,20000,10000,20000,20000,10000,10000,20000,20000,20000,20000,10000,10000,20000];

        assignmentToClusters = [0,1,2,3,4,5,6,6,6,6,7,6,8,1,6,6,9,10,11,12,6,6,1];

        precedenceAdjList = [[],[],[],[],[],[],[],[9,8],[6],[21,14],[],[6],[],[22],[],[20],[],[],[],[],[11],[15],[1]];
        */
    }

    public void printPredecessors(){
        System.out.println("");
        System.out.println("");
        for (int i = 0; i < acts.size(); i++) {
            System.out.print("Activity "+ (i + 1) + " has the following predecessors: " );
            for (int j = 0; j < acts.get(i).getDirectPreds().size(); j++) {
                System.out.print((acts.get(i).getDirectPreds().get(j) + 1) + " ");
            }
            System.out.println("");
        }
    }

    public void findMinPeriodOnEachResource(List<Activity> acts){
        for (int i = 0; i < acts.size(); i++) {
            if(nResources < acts.get(i).getAssToRes()){
                nResources = acts.get(i).getAssToRes();
            }
        }

        minPeriodOnResources = new int [nResources];
        for (int i = 0; i < nResources; i++) {
            minPeriodOnResources[i] = Integer.MAX_VALUE;
        }

        for (int i = 0; i < acts.size(); i++) {
            int nRes = acts.get(i).getAssToRes() - 1;
            if(minPeriodOnResources[nRes] > acts.get(i).getPeriod()){
                minPeriodOnResources[nRes] = acts.get(i).getPeriod();
            }
        }
    }

    public void findMinPeriodOnEachResource(ActivitiesCharacteristics chars){
        minPeriodOnResources = new int [nResources];
        for (int i = 0; i < nResources; i++) {
            minPeriodOnResources[i] = Integer.MAX_VALUE;
        }

        for (int i = 0; i < chars.numActs; i++) {
            int nRes = chars.assignmentToResources[i] - 1;
            if(minPeriodOnResources[nRes] > chars.periods[i]){
                minPeriodOnResources[nRes] = chars.periods[i];
            }
        }
    }

    public int isUtilizationInBoundsAndInstanceSchedulable() {
        if(Helpers.getMax(utilization) > 1) {
            return -1;
        }
        if(Helpers.getMax(utilization) <= Main.MIN_UTILIZATION){
            return 1;
        }
        if(!isSchedulable) {
            return 2;
        }
        return 0;
    }

    public int computeNJobs() {
        nJobs = 0;
        for (int i = 0; i < acts.size(); i++) {
            nJobs += acts.get(i).getNJobs();
        }
        return nJobs;
    }

    public double computeMaxCritValue() {
        if(apps.get(0) != null) {
            maxCritValue = 0;
            for (int i = 0;  i < apps.size();  i++) {
                int nValues = apps.get(i).getPerfValues().length;
                if(maxCritValue <= apps.get(i).getPerfValues()[nValues - 1]) {
                    maxCritValue = apps.get(i).getPerfValues()[nValues - 1];
                }
            }
        }
        else {
            System.out.println("Control performance values are not yet set.");
            System.exit(0);
        }

        return maxCritValue;
    }

    private void parseControlPerformanceAndSetToApps() throws IOException, IloException {
        Scanner in = new Scanner(new File(Helpers.controlPerfFile));
        int nAppsControl = Helpers.ReadIntegerNumber(in);

        int[] appIndexes = new int[] {17, 2, 3, 0, 1, 16, 14, 8, 11, 7, 18, 12, 15, 9, 6, 13, 10, 19, 20, 4, 5}; // straightforwardly for the use-case
        int nApps = apps.size();
        int[] minE2E = new int[nAppsControl];
        int[] appPeriods = new int[nAppsControl];
        double[][] delays = new double [nAppsControl][Main.nDiscreeteLatValues];
        double[][] perfValues = new double [nAppsControl][Main.nDiscreeteLatValues];

        int ind = 0;
        while(true) {
            if(!in.hasNext()){
                break;
            }

            String s = in.nextLine();
            if(!s.contains("Case")){
                s = in.nextLine();
            }

            // Got to the "Case n:... " line
            String[] t = s.split("\\[");
            t = t[1].split("\\ ");
            minE2E[ind] = Integer.valueOf(t[0]);
            appPeriods[ind] = Integer.valueOf(t[2].split("]")[0]);

            // To the "Delay..."
            s = in.nextLine();
            while(s.isEmpty()){
                s = in.nextLine();
            }

            t = s.split("\\t");
            for (int i = 1; i < Main.nDiscreeteLatValues + 1; i++) {
                delays[ind][i - 1] = Double.valueOf(t[i]) * 1000;
            }

            s = in.nextLine();
            t = s.split("\\t");
            for (int i = 1; i < Main.nDiscreeteLatValues + 1; i++) {
                perfValues[ind][i - 1] = Double.valueOf(t[i]);
            }

            if(Main.RUN_GENERAL_USE_CASE) {
                apps.get(appIndexes[ind]).setControlValues(delays[ind], perfValues[ind]);
            }

            if(ind > appIndexes.length){
                System.out.println("Ooops! There is more control values then applications");
            }
            s = in.nextLine();

            ind++;
        }

        if(Main.RUN_GENERAL_USE_CASE) {
            // it's required to change the period of app#7 to 50 due to performance requirements
            apps.get(7).setPeriod(50);
            apps.get(7).squeezeProcTimes(1.2);
        }
        else{
            setControlDelaysToApps(nApps, nAppsControl, apps, appPeriods, minE2E, delays, perfValues);
            computeMaxCritValue();
        }
    }

    private static void setControlDelaysToApps(int nApps, int nAppsControl, List<App> apps, int[] appPeriods,
                                               int[] minE2E, double[][] delays, double[][] perfValues){
        int[] convertedPeriods = new int [nApps];
        for (int i = 0; i < nApps; i++) {
            convertedPeriods[i] = apps.get(i).getPeriod();
            while(convertedPeriods[i] > 200 || convertedPeriods[i] < 50){
                convertedPeriods[i] /= 10;
            }
        }

        for (int i = 0; i < nApps; i++) {
            if (!apps.get(i).isOnlyMessages()) {
                double distToClosestE2EValueForTheSamePeriod = Double.MAX_VALUE;
                int index = -1;
                for (int j = 0; j < nAppsControl; j++) {
                    // to set control values for SC instances, periods should be converted
                    if (convertedPeriods[i] == appPeriods[j] && apps.get(i).getMinE2ELatency() >= minE2E[j]) {
                        double dist = apps.get(i).getMinE2ELatency() - minE2E[j];
                        if (dist < distToClosestE2EValueForTheSamePeriod) {
                            distToClosestE2EValueForTheSamePeriod = dist;
                            index = j;
                        }
                    }
                }

                if (index == -1) {
                    for (int j = 0; j < nAppsControl; j++) {
                        if (convertedPeriods[i] == appPeriods[j]) {
                            double dist = (apps.get(i).getMinE2ELatency() - minE2E[j]) * (apps.get(i).getMinE2ELatency() - minE2E[j]);
                            if (dist < distToClosestE2EValueForTheSamePeriod) {
                                distToClosestE2EValueForTheSamePeriod = dist;
                                index = j;
                            }
                        }
                    }
                }

                double[] delaysConverted = new double[delays[0].length];
                delaysConverted[0] = apps.get(i).getMinE2ELatency();
                int step = (2 * apps.get(i).getPeriod() - apps.get(i).getMinE2ELatency()) / (delaysConverted.length - 1);
                for (int j = 1; j < delays[index].length - 1; j++) {
                    delaysConverted[j] = delaysConverted[j - 1] + step;
                }
                delaysConverted[delaysConverted.length - 1] = 2 * apps.get(i).getPeriod();

                double[] resultPerf = perfValues[index];
                if (apps.get(i).getActs().size() == 1) {
                    double[] perfValuesNew = new double[perfValues[index].length];

                    for (int j = 0; j < perfValuesNew.length; j++) {
                        perfValuesNew[j] = 0;
                    }

                    resultPerf = perfValuesNew;
                }
                else {
                    double minPerf = resultPerf[0];
                    for (int j = 0; j < resultPerf.length; j++) {
                        resultPerf[j] /= minPerf;
                    }
                }

                apps.get(i).setControlValues(delaysConverted, resultPerf);
            }
        }
    }

// -------------------------------------SETTERY & GETTERY----------------------------------------------------------------
    public int getNLinks() {
        return nLinks;
    }

    public int getHP() {
        return HP;
    }

    public List<Activity> getActs() {
        return acts;
    }

    public int getnResources() {
        return nResources;
    }

    public List<Integer> getUniquePeriods() {
        return uniquePeriods;
    }

    public int getNumMessages() {
        return numMessages;
    }

    public int getMaxNumMesJob() {
        return maxNumMessageOccurr;
    }

    public List<App> getApps() {
        return apps;
    }

    public List<List<Integer>> getResourceConstraintActs() {
        return resourceConstraintActs;
    }

    public List<List<Integer>> getDeducedPrecedence() {
        return deducedPrecedence;
    }

    public int getPairwiseGCD(int period1, int period2) {
        return (int) pairwiseGCD[uniquePeriods.indexOf(period1)][uniquePeriods.indexOf(period2)];
    }

    public Double[] getUtilizations() {
        return utilization;
    }

    public int getMaxNumJobs() {
        return maxNumJobs;
    }

    public int getNumOrdCritApps() {
        return numOrdCritApps;
    }

    public List<List<Integer>> getFullResourceConstraintActs() {
        return fullResourceConstraintActs;
    }

    public boolean IsInstanceSchedulable(ActivitiesCharacteristics chars) throws IOException{
        utilization = computeTotalUtilizationOnEachResource(chars);

        System.out.println("");
        System.out.println("Utilization on the resources is:");
        for(int i = 0; i < utilization.length; i++) {
            System.out.println(utilization[i]);
        }

        for(int i = 0; i < chars.numActs; i++) {
            if(acts.get(i).getSlack( )<= 0){
                System.out.println("Some activity cannot be scheduled!");
                return false;
            }
        }

        return true;
    }

    public boolean isSchedulable() {
        return isSchedulable;
    }

    public int[] getMinPeriodOnRes(){
        return minPeriodOnResources;
    }

    public void printActsMappedToResource(int nRes) {
        System.out.println("Activities assigned to resource " + (nRes + 1) + ": \n");
        for (int i = 0; i < acts.size(); i++) {
            if(acts.get(i).getAssToRes() - 1 == nRes){
                acts.get(i).printActivity();
            }
        }
    }

    public void printNumOfMessagesAndTasks(){
        System.out.println("Number of messages is " + numMessages + ", number of tasks is " + (acts.size() - numMessages));
    }

    public double getMaxCritValue() {
        return maxCritValue;
    }


}
