package simulation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// this class is created for experimental purpose only (to test whether Manhattan distance is reasonable to use)
public class DriverConsumerDPEstimate implements Runnable {
    private final Algorithms Alg;
    private final Driver driver;
    //private long startTime = 0L;
    private SFP bestSFP = null;
    private long currentBestDist = Long.MAX_VALUE;

    public DriverConsumerDPEstimate(Algorithms alg, Driver driver) {
        Alg = alg;
        this.driver = driver;
    }
    
    @Override
    public void run() {
        int counter = driver.getMatches().size();
        if (counter == 0 || driver.getCapacity() < 2)
            return;
        else if (counter >= SimulationParameters.maxNumMatchesPerDriver)
            return;
        
        long startTime = System.currentTimeMillis();
        List<Passenger> passengerList;
        Set<Passenger> currentMatchPassengers;
        Set<Passenger> extendMatchPassengers;
        HashMap<Integer, List<FeasiblePath>> feasiblePathsForMatchAtIndex;
        List<Integer> skipIndices;
        int capLimit;
        int endIndex;
        int startIndex;
        boolean observation = false;
        Pair<Match, List<FeasiblePath>> matchAndPaths;
        Set<Passenger> temp;
        FeasiblePath feasiblePath;
        
        passengerList = new ArrayList<>(counter);
        skipIndices=  new ArrayList<>(counter*2);
        feasiblePathsForMatchAtIndex = new HashMap<>(counter);
        for (int i = 0; i < counter; i++) {	 // every match consists of 1 passenger at this point
            passengerList.addAll(driver.getMatches().get(i).sfp.passengers);
            feasiblePathsForMatchAtIndex.put(i, new ArrayList<>(1));
            feasiblePath = new FeasiblePath();
            feasiblePath.addLocation(new Pair(passengerList.get(i),false));
            feasiblePath.addLocation(new Pair(passengerList.get(i),true));
            feasiblePathsForMatchAtIndex.get(i).add(feasiblePath);
            skipIndices.add(i);
        }

        capLimit = 2;
        startIndex = 0;
        
        while (driver.getCapacity() >= capLimit) {
            endIndex = driver.getMatches().size();
            for (int i = startIndex; i < endIndex; i++) {	// grow each match, based on the previous computed/stored feasible paths
                currentMatchPassengers = driver.getMatches().get(i).sfp.passengers;
                for (int index = 0; index < passengerList.size(); index++) {        // grow each match with a passenger
                    if (index <= skipIndices.get(i))
                        continue;

                    extendMatchPassengers = new HashSet<>(currentMatchPassengers);
                    extendMatchPassengers.add(passengerList.get(index));

                    temp = new HashSet<>(extendMatchPassengers);
                    for (Passenger p: currentMatchPassengers) { // check (currentSet \setminus pID \cup passenger), and it must be true to be considered 
                        observation = false;
                        temp.remove(p);
                        for (int j = startIndex; j < endIndex; j++) {
                            if (temp.equals(driver.getMatches().get(j).sfp.passengers)) {
                                observation = true;
                                break;
                            }
                        }
                        if (!observation)
                            break;
                        temp.add(p);
                    }
                    if (!observation)		// observation does not hold for this passenger
                        continue;		// skips this passenger

                    if (feasiblePathsForMatchAtIndex.get(i) == null) {
                        System.out.println("Match at index (" +i+ ")");
                    }
                    // check if the extendMatchPassengers has a feasible shortest path                        
                    matchAndPaths = constructMatch(driver, extendMatchPassengers, passengerList.get(index), feasiblePathsForMatchAtIndex.get(i));
                    if (matchAndPaths != null) {
                        driver.addMatch(matchAndPaths.getP1());
                        Alg.calculateProfit(matchAndPaths.getP1(), driver);
                        if (driver.getMatches().size() % 5000 == 0)
                            System.out.println("Driver " + driver.getID() + ": Added " + counter + " matches and taken "+(System.currentTimeMillis()- startTime) + " milliseconds.");
                        if (driver.getMatches().size() >= SimulationParameters.maxNumMatchesPerDriver) {
                            driver.addIndexLevel(driver.getMatches().size());
                            return;
                        }
                        skipIndices.add(index);
                        feasiblePathsForMatchAtIndex.put(driver.getMatches().size()-1, matchAndPaths.getP2());
                    }
                }
                feasiblePathsForMatchAtIndex.remove(i);
            }
            //System.out.println("driver.getMatches().size() = " + driver.getMatches().size());
            driver.addIndexLevel(driver.getMatches().size());
            if (endIndex == driver.getMatches().size()) // did not find any new sigma set
                break;								    // process next driver
            startIndex = endIndex;
            capLimit++;
        }
    }
    
    private Pair<Match, List<FeasiblePath>> constructMatch(Driver driver, Set<Passenger> passengers, Passenger passenger, List<FeasiblePath> feasiblePaths) {
        int size = feasiblePaths.get(0).originOrDest.size() + 2;
        List<Pair<Passenger, Boolean>> pathToBeTested = new ArrayList<>(size); // <(Passenger), (false = origin, true = destination)>
        List<FeasiblePath> newFeasiblePaths = new ArrayList<>();
        FeasiblePath feasiblePath;
        bestSFP = null;
        currentBestDist = Long.MAX_VALUE;
        //int count = 1;
        int startIndex;
        int endIndex;
        Pair<Passenger, Boolean> startNewPassenger = new Pair(passenger, Boolean.FALSE);
        Pair<Passenger, Boolean> endNewPassenger = new Pair(passenger, Boolean.TRUE);
        for (int i = 0; i < size; i++)
            pathToBeTested.add(startNewPassenger);
            
        // process each feasible path
        for (FeasiblePath fp : feasiblePaths) {            
            startIndex = 0;
            while (startIndex < size-1) {
                for (int i = 0; i < startIndex; i++) {
                    pathToBeTested.set(i, fp.originOrDest.get(i));
                }
                pathToBeTested.set(startIndex, startNewPassenger);
                for (endIndex = startIndex + 1; endIndex < size; endIndex++) {
                    for (int i = startIndex + 1; i < endIndex; i++)
                        pathToBeTested.set(i, fp.originOrDest.get(i-1));

                    pathToBeTested.set(endIndex, endNewPassenger);
                    for (int i = endIndex + 1; i < size; i++)
                        pathToBeTested.set(i, fp.originOrDest.get(i-2));
                    
                    if (computeFeasiblePath(driver, passengers, pathToBeTested)) {
                        feasiblePath = new FeasiblePath(size);
                        for (Pair pair : pathToBeTested)
                            feasiblePath.addLocation(new Pair(pair.getP1(), pair.getP2()));
                        newFeasiblePaths.add(feasiblePath);
                    }
                }
                startIndex++;
            }
        }
        
        //System.out.println("Number of permutations: " + count +". Number of valid routes tested: " + numValidRoutes);
        if (bestSFP == null)
            return null;
        return new Pair(new Match(bestSFP), newFeasiblePaths);
    }
    
    private boolean computeFeasiblePath(Driver driver, Set<Passenger> passengers, List<Pair<Passenger, Boolean>> originOrDest) {
        int size = originOrDest.size();
        int[] travelDistanceIndex = new int[size];
        int[] hourIndex = new int[size];
        long[] travelDuration = new long[size];
        int driverIndex = Alg.tripIDtoTravelDistanceIndex.get(driver.getID());
        int passengerIndex;
        long driverDeparture;
        long arrivedTime;
        long accDuration;
        
        for (int i = 0; i < size; i++) {
            passengerIndex = Alg.tripIDtoTravelDistanceIndex.get(originOrDest.get(i).getP1().getID());
            if (originOrDest.get(i).getP2())  // destination of passenger at index j
                travelDistanceIndex[i] = passengerIndex+Alg.passengerSize;
            else                     // origin of passenger at index j
                travelDistanceIndex[i] = passengerIndex;
        }
        
        travelDuration[0] = (long) (Alg.travelDistance[driverIndex][travelDistanceIndex[0]] / 
                                        Alg.Speed[Alg.currentHourIndex][driver.getStartRegion()][originOrDest.get(0).getP1().getStartRegion()]);
        accDuration = travelDuration[0];
        
        driverDeparture = Math.max(driver.getDepartureTime(), originOrDest.get(0).getP1().getDepartureTime() - travelDuration[0]);    
        arrivedTime = driverDeparture + travelDuration[0];
        hourIndex[0] = Math.min((int)(arrivedTime / 3600.0), 23) - SimulationParameters.startHour;
        int j;
        for (j = 0; j < size-1; j++) {
            if (Alg.travelDistance[travelDistanceIndex[j]][travelDistanceIndex[j+1]] == 0)
                Alg.travelDistance[travelDistanceIndex[j]][travelDistanceIndex[j+1]] = 
                                    (long)(Alg.ManhattanDistance(Alg.getPassengerODLocations(originOrDest.get(j)), Alg.getPassengerODLocations(originOrDest.get(j+1))));
            travelDuration[j+1] = (long) (Alg.travelDistance[travelDistanceIndex[j]][travelDistanceIndex[j+1]] /
                        Alg.Speed[hourIndex[j]][Alg.getPassengerRegionIndex(originOrDest.get(j))][Alg.getPassengerRegionIndex(originOrDest.get(j+1))]); // from l_j to l_{j+1}
            accDuration = accDuration + travelDuration[j+1];
            arrivedTime = driverDeparture + accDuration;     // time arrived at l_{j+1}
            
            if (!originOrDest.get(j+1).getP2()) {   // if this the origin of passenger at index j+1
                if (originOrDest.get(j+1).getP1().getDepartureTime() > arrivedTime) {
                    driverDeparture =  originOrDest.get(j+1).getP1().getDepartureTime() - accDuration;
                    arrivedTime = originOrDest.get(j+1).getP1().getDepartureTime(); // the actual time left at l_{j+1}
                }
            }
            hourIndex[j+1] = Math.min((int)(arrivedTime / 3600.0), 23) - SimulationParameters.startHour;
        }

        long driverDur = accDuration + (long) (Alg.travelDistance[travelDistanceIndex[j]][driverIndex] /
                                Alg.Speed[hourIndex[j]][Alg.getPassengerRegionIndex(originOrDest.get(j))][driver.getEndRegion()]);
       
        if (driverDur > driver.getMaxTravelDuration() || driverDeparture + driverDur > driver.getArrivalTime()) {
            return false;
        }
        // driver is okay, now check each passenger
        HashMap<Passenger,Integer> passengerStartIndex = new HashMap<>();
        HashMap<Passenger,Integer> passengerEndIndex  = new HashMap<>();
        for (int i = 0; i < size; i++) {
            if (originOrDest.get(i).getP2())
                passengerEndIndex.put(originOrDest.get(i).getP1(), i+1);
            else
                passengerStartIndex.put(originOrDest.get(i).getP1(), i+1);
        }
        long passengerDur;
        for (Passenger p : passengers) {
            passengerDur = 0L;
            for (int i = passengerStartIndex.get(p); i < passengerEndIndex.get(p); i++)
                passengerDur = passengerDur + travelDuration[i];
            if (passengerDur > p.getMaxTravelDuration())
                return false;
            
            passengerDur = passengerDur + driverDeparture;
            for (int i = 0; i < passengerStartIndex.get(p); i++)
                passengerDur = passengerDur + travelDuration[i];
            if (passengerDur > p.getArrivalTime())
                return false;
        }
        
        //
        if (driverDur < currentBestDist) {
            List<Pair<Passenger, Boolean>> originOrDestCopy = new ArrayList<>(originOrDest.size());
            for (Pair pair : originOrDest)
                originOrDestCopy.add(new Pair(pair.getP1(), pair.getP2()));

            currentBestDist = driverDur;
            bestSFP = new SFP(passengers, originOrDestCopy, travelDistanceIndex, hourIndex, driverDeparture);
        }
        return true;
    }
}