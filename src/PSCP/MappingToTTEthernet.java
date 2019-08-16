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
import java.util.*;

public class MappingToTTEthernet {
    private List<Activity> acts;
    private List<App> apps;
    private int nResources;
    private int nNetworks;
    private int nECUs;
    private int nDomains;
    private int[] nECUsInDomain;
    private int nECUsPerDomain;
    private int nAppsWithComm;
    private int nAppsCauseEffectAndTasks;
    private Map<String, Integer> procTimesMessages;

    ProblemInstance prInst;
    
    // mapping of indexes to resources in assignmentToResources is following: first nECUs are ECUs,
    // from nECUs + 1 to 2 * nECUs are links from ECUs to switches,
    // from 2 * nECUs+1 to 3 * nECUs are links from switches to ECUS,
    // from 3 * nECUs + 1 to 3 * nECUs + (nDomains - 1) are links 
    // between the switches to the right that are organized
    // in a chain, so only one routing is possible
    // from 3 * nECUs + (nDomains - 1) + 1 to 3 * nECUs + 2 * (nDomains - 1)
    // are links between switches to the left

    public void convertSomeAppsToHighDemandingOnNetwork(Main.RunningCharacteristics chars) throws IOException {
        prInst.findMinPeriodOnEachResource(acts);

        //compute how many there are applications with communication
        nAppsWithComm = 0;
        List<Integer> appsWithComm = new ArrayList<>();
        for (int i = 0; i < apps.size(); i++) {
            if(apps.get(i).getActs().size() > 1){
                nAppsWithComm++;
                appsWithComm.add(i);
            }
        }

        int nConvApps = (int) Math.round(Main.PERCENT_VIDEO_APPS * nAppsWithComm);
        Set<Integer> nAppsWithVideoTraffic = new HashSet<>();
        while(nAppsWithVideoTraffic.size() != nConvApps && nAppsWithVideoTraffic.size() < nAppsWithComm){
            int nApp = (int) Math.floor(Math.random() * nAppsWithComm);
            nAppsWithVideoTraffic.add(appsWithComm.get(nApp));
        }

        if(Main.IS_INSTANCE_GENERATION_MODE && chars.isGenerating) {
            Helpers.printToFile(chars.fullFileNameTerminalOut, "\nNumber of apps with video traffic is " +
                    nAppsWithVideoTraffic.size() + " out of " + nAppsWithComm);
        }

        Integer[] numsOfApps = nAppsWithVideoTraffic.toArray(new Integer[nAppsWithVideoTraffic.size()]);
        for (int i = 0; i < nConvApps; i++) {
            apps.get(numsOfApps[i]).convertToContainVideoTraffic(prInst.getMinPeriodOnRes());
        }
    }

    public MappingToTTEthernet(ProblemInstance prInst_){
        acts = prInst_.getActs();
        nResources = prInst_.getnResources();
        nNetworks = prInst_.getNLinks();
        apps = prInst_.getApps();
        prInst = prInst_;
    }

    public int doMappingSyntheticProbInst(Main.RunningCharacteristics chars) throws IOException, IloException {
        doMappingOfTasks(chars);
        createRoutingAndAddMessages();
        convertSomeAppsToHighDemandingOnNetwork(chars);

        if(Main.IS_DEBUG_MODE) {
            System.out.println("nECUs is " + nECUs);
        }

        return nNetworks;
    }

    public int doMappingUseCase(Main.RunningCharacteristics chars) throws IOException, IloException {
        int nECUsInDomain = 8;
        nECUs = prInst.getnResources() - prInst.getNLinks();
        nDomains = (int) Math.ceil(nECUs * 1.0 / nECUsInDomain);
        nNetworks = 2 * nECUs + 2 * (nDomains - 1);
        nAppsCauseEffectAndTasks = prInst.getApps().size();

        removeMessagesWithDependencies();
        createRoutingAndAddMessages();

        return nNetworks;
    }

    private void doMappingOfTasks(Main.RunningCharacteristics chars) throws IOException, IloException {
        nAppsCauseEffectAndTasks = -1;
        for (int i = 0; i < apps.size(); i++) {
            if(apps.get(i).isOnlyMessages()){
                nAppsCauseEffectAndTasks = i;
                break;
            }
        }

        nECUs = Main.NUM_ECUs;
        nDomains = (int) Math.ceil(nECUs * 1.0 / Main.NUM_ECUS_IN_DOMAIN);
        Integer[] assOfAppsToDomains = new Integer[nAppsCauseEffectAndTasks];
        nECUsInDomain = new int[nDomains];
        int[] firstECUInDomain = new int[nDomains];
        nNetworks = 2 * nECUs + 2 * (nDomains - 1);
        nResources = nECUs + nNetworks;

        // The mapping of ECUs to domains is simple - ECU i is mapped to domain floor(i / NUM_ECUS_IN_DOMAIN)

        // There are nECUsPerDomain ECUs in the first (nDomains - 1) domains, while the last domain consists of the
        // rest ECUs so that the total number of ECUs is nECUs
        nECUsPerDomain = (int) Math.ceil(nECUs * 1.0 / nDomains);
        for (int i = 0; i < nDomains - 1; i++) {
            nECUsInDomain[i] = nECUsPerDomain;
            firstECUInDomain[i] = i * nECUsPerDomain;
        }
        nECUsInDomain[nDomains - 1] = nECUs - (nDomains - 1) * nECUsPerDomain;
        firstECUInDomain[nDomains - 1] = (nDomains - 1) * nECUsPerDomain;

        // we assign whole application to the same domain so that the interdomain communication is schedulable, since
        // the network will be the bottleneck
        int assOfCurrentAppToDomain = 0;
        for (int i = 0; i < nAppsCauseEffectAndTasks; i++) {
            assOfAppsToDomains[i] = assOfCurrentAppToDomain + 1;
            assOfCurrentAppToDomain = (assOfCurrentAppToDomain + 1) % nDomains;
        }

        removeMessagesWithDependencies();

        // For each task in app decide whether or not it will be in the same domain with others, then map it to
        // some ECU according to its utilization.

        //Helpers.PrintArray(assOfAppsToDomains);
        double[] resourceUtilization = new double[nECUs];
        int numTasksAssToDiffDomain = 0;

        // first, assign apps with more activities and only afterwards with single activity
        for (int numApp = 0; numApp < nAppsCauseEffectAndTasks; numApp++) {
            if (apps.get(numApp).getActs().size() > 1) {
                numTasksAssToDiffDomain += assignTasksInAppToECUs(numApp, assOfAppsToDomains, nDomains,
                        resourceUtilization, firstECUInDomain, nECUsInDomain);
            }
        }

        for (int numApp = 0; numApp < nAppsCauseEffectAndTasks; numApp++) {
            if (apps.get(numApp).getActs().size() == 1) {
                assignTasksInAppToECUs(numApp, assOfAppsToDomains, nDomains,
                        resourceUtilization, firstECUInDomain, nECUsInDomain);
            }
        }

        if(chars.isGenerating) {
            Helpers.printToFile(chars.fullFileNameTerminalOut, "\nNumber of interdomain communications is " + numTasksAssToDiffDomain);
        }
    }

    private void addMessage(int nResToAssign, Activity curAct, int predActID, int succActID, int nApp, App curApp, int procTime){
        boolean isTask = false;
        List<Integer> predList = new ArrayList<>();
        List<Integer> succList = new ArrayList<>();
        if(predActID >= 0) {
            predList = Arrays.asList(predActID);
        }

        if(succActID >= 0) {
            succList = Arrays.asList(succActID);
        }

        Activity mes = new Activity(nResToAssign, procTime, curAct.getPeriod(), acts.size(), curAct.getNJobs(),
                predList, succList, prInst.getHP(), nApp + 1, isTask, 2 * nECUs + (nDomains - 1));
        acts.add(mes);
        curApp.addMessage(mes);
    }

    private void createRoutingForMessageAmongDomains(int ECU_act, int ECU_pred, Activity curAct, int nApp,
                                                     App curApp, int procTime, int firstPredID){

        int domainAct = (int) Math.ceil(ECU_act * 1.0 / nECUsPerDomain);
        int domainPred = (int) Math.ceil(ECU_pred * 1.0 / nECUsPerDomain);

        if(domainAct != domainPred){
            // Add messages on link form ECU_pred to its switch, from the switch to another switch to the left
            // if domainPred > domainAct, otherwise to the right. And from switch in domain domainAct to ECU_act
            int nLinkToAssign;
            if(domainPred > domainAct){
                // go left
                nLinkToAssign = 3 * nECUs + (nDomains - 1) + domainPred - 1;
            }
            else{
                // go right
                nLinkToAssign = 3 * nECUs + domainPred;
            }

            for (int k = 0; k < Math.abs(domainPred - domainAct); k++) {
                addMessage(nLinkToAssign, curAct, firstPredID,acts.size() + 1, nApp,
                        curApp, procTime);

                firstPredID = acts.size() - 1;

                if(domainPred > domainAct){
                    nLinkToAssign--;
                }
                else{
                    nLinkToAssign++;
                }
            }
        }
    }

    private void createRoutingAndAddMessages(){
        // first, do it for cause-effect chains and tasks
        for (int nApp = 0; nApp < nAppsCauseEffectAndTasks; nApp++) {
            App curApp = apps.get(nApp);
            List<Activity> actsInApp = curApp.getActs();
            int numTasks = actsInApp.size();
            for (int i = 0; i < numTasks; i++) {
                Activity curAct = actsInApp.get(i);

                int nPredTasks = curAct.getDirectPreds().size();
                for (int j = 0; j < nPredTasks; j++) {
                    Activity predAct = acts.get(curAct.getDirectPreds().get(j));

                    int curProcTime;
                    if(!Main.RUN_GENERAL_USE_CASE) {
                        curProcTime = procTimesMessages.get(Integer.toString(predAct.getID()) + "." +
                                Integer.toString(curAct.getID()));
                    }
                    else{
                        curProcTime = 1;
                    }

                    if(predAct.getAssToRes() > nECUs){
                        continue;
                    }

                    int ECU_act = curAct.getAssToRes();
                    int ECU_pred = predAct.getAssToRes();

                    if(ECU_act == ECU_pred){
                        continue;
                    }

                    predAct.addSucc(acts.size());

                    // Add two messages - one from ECU_pred to the switch here and one from the switch to ECU_act afterwards.
                    addMessage(nECUs + ECU_pred, curAct, predAct.getID(),acts.size() + 1, nApp,
                            curApp, curProcTime);
                    createRoutingForMessageAmongDomains(ECU_act, ECU_pred, curAct, nApp, curApp, curProcTime, acts.size() - 1);
                    addMessage(2 * nECUs + ECU_act, curAct, acts.size() - 1, curAct.getID(), nApp,
                            curApp, curProcTime);

                    // add predecessor message to task
                    curAct.addPred(acts.size() - 1);
                    curApp.removeTaskToTaskDependencies(predAct.getIdInAppActArray(), curAct.getIdInAppActArray());
                    j--;
                    nPredTasks--;
                }
            }
        }

        // then, do it for just messages
        for (int i = nAppsCauseEffectAndTasks; i < apps.size(); i++) {
            Activity curAct = apps.get(i).getActs().get(0);
            int ECU_sending = acts.get(curAct.getSendingTask()).getAssToRes();
            int ECU_receiving = acts.get(curAct.getReceivingTask()).getAssToRes();

            if(ECU_sending != ECU_receiving) {
                // Change the message to be the first in the route
                curAct.setAssToRes(nECUs + ECU_sending);
                curAct.setDirectSuccessors(Arrays.asList(acts.size()));
                createRoutingForMessageAmongDomains(ECU_receiving, ECU_sending, curAct, i, apps.get(i),
                        curAct.getProcTime(), curAct.getID());
                // Add message for the last in the route
                addMessage(2 * nECUs + ECU_receiving, curAct, curAct.getID(), -1, i,
                        apps.get(i), curAct.getProcTime());
            }
            else{
                apps.remove(i);
                i--;
            }
        }

        for (int i = 0; i < apps.size(); i++) {
            for (int j = 0; j < apps.get(i).getActs().size(); j++) {
                apps.get(i).getActs().get(j).setAssToApp(i + 1);
            }
        }

        removeUnnecessaryActsAndChangePredsAccordingly();
    }

    private void removeUnnecessaryActsAndChangePredsAccordingly(){
        Map<Integer, Integer> oldIDToNewID = new HashMap<>();

        // check that activity IDs correspond to their order in acts array
        for (int i = 0; i < acts.size(); i++) {
            if(acts.get(i).getID() != i) {
                System.out.println("ID of activity on place " + i + " is " + acts.get(i).getID());
            }
        }

        int curNumActivityInResultArray = 0;
        for (int i = 0; i < acts.size(); i++) {
            Activity curAct = acts.get(i);
            if(curAct.getAssToRes() > 0) {
                oldIDToNewID.put(i, curNumActivityInResultArray);
                curNumActivityInResultArray++;
            }
        }

        // remove unnecessary activities and change predecessors and successors
        for (int i = 0; i < acts.size(); i++) {
            Activity curAct = acts.get(i);
            if(curAct.getAssToRes() == 0) {
                acts.remove(i);
                i--;
                continue;
            }

            curAct.setID(i);
            for (int j = 0; j < curAct.getDirectPreds().size(); j++) {
                int nPred = curAct.getDirectPreds().get(j);
                curAct.getDirectPreds().set(j, oldIDToNewID.get(nPred));
            }

            for (int j = 0; j < curAct.getDirectSucc().size(); j++) {
                int nSucc = curAct.getDirectSucc().get(j);
                curAct.getDirectSucc().set(j, oldIDToNewID.get(nSucc));
            }
        }

        // check that all preds are set correctly
        for (int i = 0; i < apps.size(); i++) {
            apps.get(i).checkPrecRelsInApp();
        }
    }

    // assign task to the ECU with the least utilization
    private int assignTaskToECUFromDomain(double[] res_utilizations, int firstECUInDomain, int nECUsInDomain){
        double leastUtilization = Double.MAX_VALUE;
        int index = -1;
        for (int i = 0; i < nECUsInDomain; i++) {
            if(leastUtilization > res_utilizations[firstECUInDomain + i]){
                leastUtilization = res_utilizations[firstECUInDomain + i];
                index = firstECUInDomain + i + 1;
            }
        }

        return index;
    }

    private int assignTasksInAppToECUs(int numApp, Integer[] assOfAppsToDomains, int nDomains, double[] resourceUtilization,
                                       int[] firstECUInDomain, int[] nECUsInDomain){
        int numTasksInDifDomain = 0;
        List<Activity> actsInApp = apps.get(numApp).getActs();
        for (int j = 0; j < actsInApp.size(); j++) {
            Activity curAct = actsInApp.get(j);
            Random rand = new Random();
            if(curAct.getAssToRes() <= nResources - nNetworks) {
                // we assign to resources only tasks
                int nDomain = assOfAppsToDomains[numApp] - 1;
                if (nDomains > 1 && actsInApp.size() > 1
                        && rand.nextDouble() < Main.PROBABILITY_INTERDOMAIN_COMMUNICATION) {
                    // task must be assigned to another domain to create interdomain communication
                    numTasksInDifDomain++;
                    while (true) {
                        nDomain = rand.nextInt(nDomains);
                        if (nDomain != assOfAppsToDomains[numApp] - 1) {
                            break;
                        }
                    }
                }

                curAct.setAssToRes(assignTaskToECUFromDomain(resourceUtilization,
                        firstECUInDomain[nDomain], nECUsInDomain[nDomain]));
                curAct.setProcTime(curAct.getProcTime() * Main.COEFF_TO_SCALE_ON_CORES);
                resourceUtilization[curAct.getAssToRes() - 1] += curAct.getProcTime() * 1.0 / curAct.getPeriod();
            }
        }

        return numTasksInDifDomain;
    }

    private void changePrecedenceAndFollowersLists(Map<Integer, Integer> mapPrevToNewIdTasks, List<Activity> oldActs){
        for (int i = 0; i < oldActs.size(); i++) {
            if (oldActs.get(i).isTask()) {
                List<Integer> predListLocal = new ArrayList<>();
                for (int j = 0; j < oldActs.get(i).getDirectPreds().size(); j++) {
                    int nPred = oldActs.get(i).getDirectPreds().get(j);
                    if (mapPrevToNewIdTasks.containsKey(nPred)) {
                        predListLocal.add(mapPrevToNewIdTasks.get(nPred));
                    }

                    if(!oldActs.get(nPred).isTask()) {
                        for (int k = 0; k < oldActs.get(nPred).getDirectPreds().size(); k++) {
                            int nPredOfPred = oldActs.get(nPred).getDirectPreds().get(k);
                            if (mapPrevToNewIdTasks.containsKey(nPredOfPred)) {
                                predListLocal.add(mapPrevToNewIdTasks.get(nPredOfPred));
                            }
                        }
                    }
                }

                oldActs.get(i).setDirectPredecessors(predListLocal);

                List<Integer> succListLocal = new ArrayList<>();
                for (int j = 0; j < oldActs.get(i).getDirectSucc().size(); j++) {
                    int nSucc = oldActs.get(i).getDirectSucc().get(j);
                    if (mapPrevToNewIdTasks.containsKey(nSucc)) {
                        succListLocal.add(mapPrevToNewIdTasks.get(nSucc));
                    }

                    if(!oldActs.get(nSucc).isTask()) {
                        for (int k = 0; k < oldActs.get(nSucc).getDirectSucc().size(); k++) {
                            int nSuccOfSucc = oldActs.get(nSucc).getDirectSucc().get(k);
                            if (mapPrevToNewIdTasks.containsKey(nSuccOfSucc)) {
                                succListLocal.add(mapPrevToNewIdTasks.get(nSuccOfSucc));
                            }
                        }
                    }
                }
                oldActs.get(i).setDirectSuccessors(succListLocal);
            }
        }
    }

    private void fillProcTimeMessagesArray(Map<String, Integer> procTimesMessages,
                                           Map<Integer, Integer> mapPrevToNewIdTasks, List<Activity> oldActs) {
        for (int i = 0; i < acts.size(); i++) {
            Activity predTask = acts.get(i);
            for (int j = 0; j < predTask.getDirectSucc().size(); j++) {
                Activity message = oldActs.get(predTask.getDirectSucc().get(j));
                if(!message.isTask()){
                    // if successor is a message, add this processing time to the array
                    Activity succTask = oldActs.get(message.getDirectSucc().get(0));
                    int nPredTaskInNewActArray = mapPrevToNewIdTasks.get(predTask.getID());
                    int nSuccTaskInNewActArray = mapPrevToNewIdTasks.get(succTask.getID());
                    procTimesMessages.put(Integer.toString(nPredTaskInNewActArray) + "." +
                            Integer.toString(nSuccTaskInNewActArray), message.getProcTime());
                }
            }
        }
    }

    private void removeMessagesWithDependencies() throws IOException, IloException {
        Map<Integer, Integer> mapPrevToNewIdTasks = new HashMap<Integer, Integer>(prInst.getActs().size() - prInst.getNumMessages());
        // procTimesMessages has "ID sending task" + "." + "ID receiving task" as a key and processing time as a value
        procTimesMessages = new HashMap<String, Integer>();

        // remove messages that are part of cause-effect-chain from acts array
        List<Activity> oldActs = Helpers.getCopyOfArray(acts);
        for (int i = 0; i < acts.size(); i++) {
            if(!acts.get(i).isTask() && !acts.get(i).getDirectPreds().isEmpty()){
                acts.remove(i);
                i--;
            }
            else{
                mapPrevToNewIdTasks.put(acts.get(i).getID(), i);
            }
        }

        fillProcTimeMessagesArray(procTimesMessages, mapPrevToNewIdTasks, oldActs);
        changePrecedenceAndFollowersLists(mapPrevToNewIdTasks, oldActs);

        for (int i = 0; i < acts.size(); i++) {
            acts.get(i).setIdInInputData(i);
            acts.get(i).setnResources(3 * nECUs + 2 * (nDomains - 1));
        }

        // remove messages from apps
        for (int i = 0; i < nAppsCauseEffectAndTasks; i++) {
            apps.get(i).removeMessages();
            if (apps.get(i).getNumActs() == 0) {
                apps.remove(i);
                i--;
            }
        }
    }

    private void CheckSuccAndPred(){
        for (int i = 0; i < prInst.getActs().size(); i++) {
            Activity curAct = acts.get(i);
            for (int j = 0; j < curAct.getDirectPreds().size(); j++) {
                int nPred = acts.get(i).getDirectPreds().get(j);
                if(!acts.get(nPred).getDirectSucc().contains(i)){
                    System.out.println("Something is wrong with successor and predecessor of activities!! Activity " + i + " is not in successor list of activity " + nPred);
                }
            }

        }
    }

}
