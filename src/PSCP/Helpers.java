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

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

class Helpers {
    //------------constants-------------
    public static final double EPS = 1.0E-4;
    public static final String outputFileForModelsILP = "mod.lp";
    public static final String outputFileForModelsCP = "mod.cpo";
    public static final String outputFileForModelsSMT = "mod.smt";
    public static final String outputFileCPLEXlog = "log.dat";
    public static final String controlPerfFile = "instances/spec/control_values.txt";
    public static final String outFileFeas = "feasibility";
    public static final String outFileIter = "numberOfIterations.dat";
    public static final double toleranceHeur = 0.01;
    public static long[][] pairwiseGCD;
    public static List<Integer> uniquePeriods;
    private static FileWriter writer;

    public class ArrayIndexComparator implements Comparator<Integer> {
        private final Double[] parameter;
        private final int numEls;

        public ArrayIndexComparator(Double[] parameter_) {
            parameter = parameter_;
            numEls = parameter.length;
        }

        public Integer[] createIndexArray() {
            Integer[] indexes = new Integer[numEls];
            for (int i = 0; i < numEls; i++){
                indexes[i] = i;
            }
            return indexes;
        }

        @Override
        public int compare(Integer index1, Integer index2){
                return parameter[index1].compareTo(parameter[index2]);
        }
    }

    public static <T> void initializeTo(T[] array, T value){
        for (int i = 0; i < array.length; i++) {
            array[i] = value;
        }
    }
    
    public static int sumOverProcessingTimesInActivitiesArray(Activity[] array){
        int sum = 0;
        for (int i = 0; i < array.length; i++) {
            sum += array[i].getProcTime();
        }
        return sum;
    }
    
    public static int ReadIntegerNumber(Scanner in){
        String s = in.nextLine();
        String[] t = s.split(" ");
        String[] k = t[2].split(";");
        return Integer.valueOf(k[0]);
    }

    public static Integer[] readIntegerArray(Scanner in, int length) {
        Integer[] array = new Integer[length];
        String s = in.nextLine();

        s = in.nextLine();
        String[] t = s.split("\\[");
        String[] y = t[1].split("\\,");

        for (int i = 0; i < array.length - 1; i++) {
            array[i] = Integer.valueOf(y[i]);
        }

        t = y[array.length - 1].split("\\]");
        array[array.length - 1] = Integer.valueOf(t[0]);

        return array;
    }

    public static int[] readIntegerArrayWithoutGivenLength(Scanner in){
        List<Integer> array = new ArrayList<Integer>();
        String s = in.nextLine();

        s = in.nextLine();
        String[] t = s.split("\\[");
        String[] y = t[1].split("\\,");

        for(int j = 0; j < y.length - 1; j++) {
            array.add(Integer.valueOf(y[j]));
        }

        t = y[array.size() - 1].split("\\]");
        array.add(Integer.valueOf(t[0]));

        int[] outputArray = new int[array.size()];
        for(int i = 0; i < array.size(); i++) {
            outputArray[i] = array.get(i);
        }

        return outputArray;
    }

    public static List<List<Integer>> read2DIntegerArray(Scanner in){
        List<List<Integer>> array = new ArrayList<List<Integer>>();
        String s = in.nextLine();

        s = in.nextLine();
        String[] t = s.split("\\[");
        String[] y;
        
        for(int i = 2; i < t.length; i++) {
            array.add(new ArrayList<Integer>());
             y = t[i].split("\\,");
            if(y[0].split("\\]").length == 0){
                continue;
            }
          
            for(int j = 0; j < y.length - 1; j++) {
                array.get(i - 2).add(Integer.valueOf(y[j]));
            }
            
            String[] temp = y[y.length - 1].split("\\]");
            if(!temp[0].isEmpty()) array.get(i - 2).add(Integer.valueOf(temp[0]));
        }

        return array;
    }
    
    public static List<Activity> arrayListToActivityArray(List<Activity> list)  {
        List<Activity> ret = new ArrayList<>();
        int i = 0;
        for (Activity e : list)
            ret.add(e);
        return ret;
    }

    public static void initialize2DArrayWithValue(int[][] array, int value){
            for(int i = 0; i < array.length; i++) {
                for(int j = 0; j < array[i].length; j++) {
                    array[i][j] = value;
                }
            }
    }
    
    public static <T> ArrayList<T> getCopyOfArray(List<T> arrIn){
        ArrayList<T> arrOut = new ArrayList<>();
        for (int i = 0; i < arrIn.size(); i++) {
            arrOut.add(arrIn.get(i));
        }
        
        return arrOut;
    }
    
    public static void printEmptyLines(int nEmptyLines) {
        for (int i = 0; i < nEmptyLines; i++) {
            System.out.println("");
        }
    }
    
    public static <T> void printArrayOneRow(T[] array){
        System.out.println("");
        System.out.println("");
        for (int i = 0; i < array.length; i++) {
            System.out.print(array[i] + " ");
        }
        System.out.println("");
    }

    public static <T> void printArrayElementPerRow(T[] array){
        System.out.println();
        for (int i = 0; i < array.length; i++) {
            System.out.println(array[i]);
        }
        System.out.println();
    }
    
    public static void printListArray(List<Integer> array){
        System.out.println("");
        System.out.println("");
        for (int i = 0; i < array.size(); i++) {
            System.out.print(array.get(i) + " ");
        }
        System.out.println("");
    }
    
    public static int getNumEqualElsInArraysOnSamePlace(boolean[] arr1, boolean[] arr2){
        int count = 0;
        for (int i = 0; i < arr1.length; i++) {
            if(arr1[i] == arr2[i]){
                count++;
            } 
        }
        return count;
    }

    // n is the number of necessary indexes, m is the range, n < m
    public static List<Integer> generateNNumbersRandomly(int n, int m){
        Random rng = new Random();
        List<Integer> list = new ArrayList<> ();
        for(int i = 0; i < m; i++) {
            list.add(i);
        }
        Collections.shuffle(list);
        
        for (int i = m - 1; i >= n; i--) {
            list.remove(list.size() - 1);
        }
        
        return list;
    }

    public static List<Integer> sortAppsAccordingToControlValue(ProblemInstance probInst, ScheduledActivities schedActs,
                                                              int nWay){
        List<Integer> sortedApps = new ArrayList<Integer>();
        FlexiHeuristic.ArrayIndexComparator comparator = null;

        Double[] contrPerfValues = new Double[probInst.getApps().size()];
        for (int i = 0; i < probInst.getApps().size(); i++) {
            App app = probInst.getApps().get(i);
            if(!app.isOrderCritical()) {
                contrPerfValues[i] = 0.0;
            }
            else {
                contrPerfValues[i] = (nWay == 1) ?
                        (app.getPerfValues()[0] - schedActs.getAppObjValues()[i]) :
                        -probInst.getApps().get(i).getPerfValues()[0];
            }

        }

        comparator = new FlexiHeuristic.ArrayIndexComparator(contrPerfValues);
        sortedApps.addAll(Arrays.asList(comparator.createIndexArray()));
        Collections.sort(sortedApps, comparator);


        return sortedApps;
    }
    
    public static List<Integer> returnListOfActsSharingLargestNumRes(ProblemInstance probInst, int nInitApp, int n){
        List<Integer> res = new ArrayList<> (n);
        res.add(nInitApp);
        int nApps = probInst.getApps().size();
        boolean[] isChosen = new boolean [nApps];
        isChosen[nInitApp] = true;
        
        for (int i = 0; i < n; i++) {
            double curMax = 0;
            int curInd = -1;
            for (int j = 0; j < nApps; j++) {
                App curApp = probInst.getApps().get(j);
                if(!isChosen[j]) {
                    int curNumOfSharedRes = 
                            Helpers.getNumEqualElsInArraysOnSamePlace(
                                    probInst.getApps().get(nInitApp).getOccupiedResources(), 
                                    curApp.getOccupiedResources()
                            );
                    if(curNumOfSharedRes > curMax){
                        curMax = curNumOfSharedRes;
                        curInd = j;
                    }
                }
            }
            
            isChosen[curInd] = true;
            res.add(curInd);
        }
        return res;
    }

    public static boolean createTheNextCombinationOfKFromN(Integer[] curCombination, int k){
        int N = curCombination.length;
        int indOfLastChosen = -1;
        for (int i = 0; i < N; i++) {
            if(curCombination[i] == 1){
                indOfLastChosen = i;
            }
        }
        
        if(indOfLastChosen == -1){
            for (int i = 0; i < k; i++) {
                curCombination[i] = 1;
            }
            return true;
        }
        
        if(indOfLastChosen < N - 1){
            curCombination[indOfLastChosen] = 0;
            curCombination[indOfLastChosen + 1] = 1;
        }
        else{
            int numOfOnesAfterTheLastGap = 1;
            int indexOfLastChosenAfterSpace = -1;
            boolean wasSpace = false;
            for (int i = N - 2; i >= 0; i--) {
                if(!wasSpace && curCombination[i] == 0){
                    wasSpace = true;
                }
                
                if(!wasSpace){
                    numOfOnesAfterTheLastGap++;
                }
                
                if(wasSpace && curCombination[i] == 1){
                    indexOfLastChosenAfterSpace = i;
                    break;
                }
            }
            
            if(indexOfLastChosenAfterSpace == -1){
                return false;
//                System.out.println("No further combination, increase NUM_APPS_IN_NEIGBORHOOD!");
//                System.exit(1);
            }
            
            curCombination[indexOfLastChosenAfterSpace] = 0;
            curCombination[indexOfLastChosenAfterSpace + 1] = 1;
            
            for (int i = indexOfLastChosenAfterSpace + 2;
                    i < indexOfLastChosenAfterSpace + 2 + numOfOnesAfterTheLastGap; i++) {
                curCombination[i] = 1;
            }
            for (int i = indexOfLastChosenAfterSpace + 2 + numOfOnesAfterTheLastGap; i < N; i++) {
                curCombination[i] = 0;
            }
        }
        return true;
    }
    
    public static boolean[] createBoolArrayValues(int n, boolean value) {
        boolean[] res = new boolean[n];
        for (int i = 0; i < n; i++) {
            res[i] = value;
        }
        return res;
    }

    public static Activity createNewActivityCopyOfGiven(Activity act){
        return new Activity(act.getProcTime(), act.getPeriod(), act.getID(), act.getNJobs(),
                new ArrayList<>(act.getDirectPreds()), new ArrayList<>(act.getDirectSucc()), act.getHP(), act.isTask(),
                act.getSendingTask(), act.getReceivingTask(), act.getAssToRes());
    }

    public static App createNewAppCopyOfGiven(App app) throws IOException, IloException {
        return new App(app.getActs(), app.getPrecedenceAdjList(), app.getSuccessorsAdjList(), app.getPrInst(),
                false);
    }
    
    public static Double getMax(Number[] array){
        double max = Double.MIN_VALUE;
        for (int i = 0; i < array.length; i++) {
            if(array[i].doubleValue() > max){
                max = array[i].doubleValue();
            }
        }
        
        return max;
    }
    
    public static void printToFile(String outFile, String content) throws IOException{
        writer = new FileWriter(outFile, true);
        writer.write(content);
        writer.close();
    }
    
    public static void printScheduleByActs(int[][] schedule, int nRes, int HP) {
        System.out.println("");
        int startCurrent = 0;
        int numPrevAct = -1;
        for (int i = 0; i < HP; i++) {
            if(schedule[nRes][i] - 1 != numPrevAct) {
                if(numPrevAct != -1) {
                    System.out.println(numPrevAct + ": [" + startCurrent + ", " + (i - 1) + "]");
                }
                else {
                    if(i != 0) {
                        System.out.println("EMPTY : [" + startCurrent + ", " + (i - 1) + "]");
                    }
                }

                startCurrent = i;
                numPrevAct = schedule[nRes][i] - 1;
            }
        }
    }
    
    public static int[] convertDoubleArrayToIntWithRound(double[] array) {
        int[] res = new int [array.length];
        for (int i = 0; i < array.length; i++) {
            res[i] = (int) Math.round(array[i]);
        }
        
        return res;
    }

    public static void printInstanceDone(int nInstance, String fullFileNameTerminal) throws IOException {
        printToFile(fullFileNameTerminal,"\n\n\n -------- Instance " + nInstance + " done! ----------- \n\n\n");
    }

    public static void Initialize2dArrayWithValue(int[][] array, int value){
        for(int i = 0; i < array.length; i++) {
            for(int j = 0; j < array[i].length; j++) {
                array[i][j] = value;
            }
        }
    }

    public static void InitializeTo(int[] array, int value){
        for (int i = 0; i < array.length; i++) {
            array[i] = value;
        }
    }

    public static boolean ArrayListContainsValue(List<Integer> array1, int value){
        for(int i = 0; i < array1.size(); i++) {
            if(array1.get(i) == value){
                return true;
            }
        }
        return false;
    }

    private static void changeListToAddNActs(List<Integer> list, int nActs) {
        for (int i = 0; i < list.size(); i++) {
            list.set(i, list.get(i) + nActs);
        }
    }

    public static Activity duplicateActivityDoubles(Activity activity, int nActsInit, int nResourcesInit, int nAppsInit) {
        Activity activityOut = createNewActivityCopyOfGiven(activity);
        activityOut.setID(activity.getID() + nActsInit);
        activityOut.setAssToRes(activity.getAssToRes() + nResourcesInit);
        activityOut.setAssToApp(activity.getAssToApp() + nAppsInit);
        changeListToAddNActs(activityOut.getDirectPreds(), nActsInit);
        changeListToAddNActs(activityOut.getDirectSucc(), nActsInit);

        return activityOut;
    }
}
