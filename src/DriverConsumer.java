package simulation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DriverConsumer implements Runnable {
    private final Algorithms Alg;
    private final Driver driver;
    private SFP bestSFP = null;
    private long currentBestDist = Long.MAX_VALUE;

    public DriverConsumer(Algorithms alg, Driver driver) {
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
        Set<Passenger> temp;
        List<Integer> skipIndices;
        int capLimit;
        int endIndex;
        int startIndex;
        boolean observation = false;
        Match m;
        
        //System.out.println("Driver "+driverId+": "+Thread.currentThread().getName());
        
        passengerList = new ArrayList<>(counter);
        skipIndices=  new ArrayList<>(counter*2);
        for (int i = 0; i < counter; i++) {	 // every match consists of 1 passenger at this point
            passengerList.addAll(driver.getMatches().get(i).sfp.passengers);
            skipIndices.add(i);
        }
            
        capLimit = 2;
        startIndex = 0;
		
        while (driver.getCapacity() >= capLimit) {
            endIndex = driver.getMatches().size();
            for (int i = startIndex; i < endIndex; i++) {	// grow each match
                currentMatchPassengers = driver.getMatches().get(i).sfp.passengers;
                for (int index = 0; index < passengerList.size(); index++) {        // grow each match with a passenger
                    if (index <= skipIndices.get(i))
                        continue;

                    // try to expand the current sigma set by including the new passenger
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
					
                    // check if the extendMatchPassengers has a feasible shortest path
                    m = constructMatch(driver, extendMatchPassengers);
                    if (m != null) {
                        driver.addMatch(m);
                        Alg.calculateProfit(m, driver);
                        if (driver.getMatches().size() % 5000 == 0)
                            System.out.println("Driver " + driver.getID() + ": Added " + counter + " matches and taken "+(System.currentTimeMillis()- startTime) + " milliseconds.");
                        if (driver.getMatches().size() >= SimulationParameters.maxNumMatchesPerDriver) {
                            //System.out.println("Processed: Driver "+driver.getID()+" with "+driver.getMatches().size()+" matches took "+(System.currentTimeMillis()- startTime)+" milliseconds to finish.");
                            driver.addIndexLevel(driver.getMatches().size());
                            return;
                        }
                        skipIndices.add(index);
                    }
                }
            }
            driver.addIndexLevel(driver.getMatches().size());
            //System.out.println("driver.getMatches().size() = " + driver.getMatches().size());
            if (endIndex == driver.getMatches().size()) // did not find any new sigma set
                break;							    // process next driver
            startIndex = endIndex;
            capLimit++;
        }
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
                                    (long) (Alg.ho.getDistanceBetweenTwoLocs(Alg.getPassengerODLocations(originOrDest.get(j)), Alg.getPassengerODLocations(originOrDest.get(j+1))));
            travelDuration[j+1] = (long) (Alg.travelDistance[travelDistanceIndex[j]][travelDistanceIndex[j+1]] /
                        Alg.Speed[hourIndex[j]][Alg.getPassengerRegionIndex(originOrDest.get(j))][Alg.getPassengerRegionIndex(originOrDest.get(j+1))]); // from l_j to l_{j+1}
            accDuration = accDuration + travelDuration[j+1];
            arrivedTime = driverDeparture + accDuration;     // time arrived at l_{j+1}
            
            if (!originOrDest.get(j+1).getP2()) {   // if this the origin of passenger at index j+1
                // Due to earliest departure times for all drivers and passengers are in the same interval, they all have the same travel speed in the beginning.
                //driverDeparture = Math.max(driver.getDepartureTime(), pathToBeTested.get(j+1).getP1().getDepartureTime() - accDuration);
                if (originOrDest.get(j+1).getP1().getDepartureTime() > arrivedTime) {   // there is waiting time if firstDepartureTime is used
                    //System.out.println("There is waiting time at Passegner origin: " + pathToBeTested.get(j+1).getP1().getID());
                    driverDeparture =  originOrDest.get(j+1).getP1().getDepartureTime() - accDuration;
                    arrivedTime = originOrDest.get(j+1).getP1().getDepartureTime(); // the actual time left at l_{j+1}
                    /* total duration is not changed if driverDeparture time is set to the latest (the exact time arrived at Passenger's origin) since travel time not changed */
                    //accDuration = accDuration + (pathToBeTested.get(j+1).getP1().getDepartureTime() - arrivedTime);   // the total duration is increased
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
        for (int i = 0; i < originOrDest.size(); i++) {
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
    
    private Match constructMatch(Driver driver, Set<Passenger> passengers) {
        List<Pair<Passenger, Boolean>> originOrDest = new ArrayList<>(passengers.size()*2); // <(Passenger), (false = origin, true = destination)>
        int[] indexes = new int[passengers.size()*2];
        Pair<Passenger, Boolean> temp;
        bestSFP = null;
        currentBestDist = Long.MAX_VALUE;
        
        //int numValidRoutes = 1;
        //int count = 1;
        // permutation using Heap's algorithm
        // the original permutation is not in the while-loop
        for (Passenger p : passengers) {
            originOrDest.add(new Pair(p, Boolean.FALSE));
            originOrDest.add(new Pair(p, Boolean.TRUE));
        }
        computeFeasiblePath(driver, passengers, originOrDest);

        for (int ind = 0; ind < originOrDest.size(); ind++)
            indexes[ind] = 0;
        int i = 1;
        while (i < originOrDest.size()) {
            if (indexes[i] < i) {
                if (i % 2 == 0) {
                    temp = originOrDest.get(0);
                    originOrDest.set(0, originOrDest.get(i));
                    originOrDest.set(i, temp);
                } else {
                    temp = originOrDest.get(i);
                    originOrDest.set(i, originOrDest.get(indexes[i]));
                    originOrDest.set(indexes[i], temp);
                }
                // check if this permutation route is a valid route, then compute a feasible path if it is valid
                if (Alg.isValidRoute(passengers, originOrDest)) {
                    //numValidRoutes++;
                    /*for (int ind = 0; ind < pathToBeTested.size(); ind++)
                        System.out.print("Passenger "+ pathToBeTested.get(ind).getP1().getID() + "("+pathToBeTested.get(ind).getP2()+") --");
                    System.out.println("");*/
                    computeFeasiblePath(driver, passengers, originOrDest);
                }
                
                indexes[i]++;
                i = 1;
                //count++;
            } else {
                indexes[i] = 0;
                i++;
            }
            
        }
        
        //System.out.println("Number of permutations: " + count +". Number of valid routes tested: " + numValidRoutes);
        if (bestSFP == null)
            return null;
        return new Match(bestSFP);
    }
}