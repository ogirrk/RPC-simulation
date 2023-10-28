package simulation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
//import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.PriorityQueue;
//import java.util.Queue;
import java.util.Set;
//import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

public class Algorithms {
    public final HopperOperation ho;
    public final double[][][] Speed;
    private final double[][][] SurgePriceFactor;
    private final HashMap<Integer,Double> AverageTip;
    private final double BaseFare = 1.8;
    private final double PerMinuteCost = 0.27;
    private final double PerMeterCost = 0.8 / Utility.MileToKM / 1000;  // from 0.8 per-mile to per-meter, in dollars
    private final double smallSedanCostPerMeter = 0.1251 / Utility.MileToKM / 1000 ;   // in dollars
    private final double mediumSedanCostPerMeter = 0.1437 / Utility.MileToKM / 1000;
    //private final double mediumSUVCostPerKM = 0.1889 / Utility.MileToKM / 1000;
    private final double SmallSedanMaintenance = 0.0887 / Utility.MileToKM / 1000;
    private final double MediumSedanMaintenance = 0.1064 / Utility.MileToKM / 1000;
    private final double SmallSedanDepreciation15 = (2528.0/15000.0) / Utility.MileToKM / 1000; // from cost per-mile to per-meter, in dollars
    private final double MediumSedanDepreciation15 = (3703.0/15000.0) / Utility.MileToKM / 1000;
    private final double SmallSedanDepreciation20 = (2528.0+1174.0)/20000.0 / Utility.MileToKM / 1000; // from cost per-mile to per-meter, in dollars
    private final double MediumSedanDepreciation20 = (3703.0+1306.0)/20000.0 / Utility.MileToKM / 1000;
    private double MinRadius; // in kilometers
    private double distanceCandidateConst = 2.0;
    //private final Location Airport_Midway = new Location(-87.74175010393411, 41.78850894013623);
    
    private final double Radian = Math.PI / 180.0;
    public long[][] travelDistance;         // travel distance (in meter) by vehicles for [D.source + P.source + P.dest] x [D.dest + P.source + P.dest]
    public HashMap<Integer, Integer> tripIDtoTravelDistanceIndex = null;
    public int driverSize = 0;		// use for getting the index of travelDistance
    public int passengerSize = 0;	// use for getting the index of travelDistance
    public volatile int matchID = 0;
    public int currentHourIndex = 0;
    private long currentBestDist = Long.MAX_VALUE;
    private SFP bestSFP = null;
    private int largestMatchSize = 0;
    
    private ExecutorService executor;
    //public final Object IdLock = new Object();
    private long startTime;
    private long endTime;
    
    public Algorithms(HopperOperation ho, double[][][] speed, double[][][] priceFactor, HashMap<Integer,Double> averageTip) {
        this.ho = ho;
        this.Speed = speed;
        this.SurgePriceFactor = priceFactor;
        this.AverageTip = averageTip;
        if (SimulationParameters.candidateTest == 1)
            this.MinRadius = SimulationParameters.distanceRadius;
        else
            this.MinRadius = SimulationParameters.distanceRadius * Utility.MileToKM; // convert distanceRadius to KM from mile
    }
    
    public long computeAllMatches(List<Driver> drivers, List<Passenger> passengers) {
        long computeDuration;
        matchID = 0;
        if (SimulationParameters.useMultiThread) {
            computeDuration = computeAllMatchesThreads(drivers);
            startTime = System.currentTimeMillis();
            setMatchIDs(drivers);
            endTime = System.currentTimeMillis();
            computeDuration = computeDuration + endTime - startTime;
        } else {
            System.out.println("computeAllMatches() is called with drivers = " + (drivers.size()) + " and passengers = " + (passengers.size()));
            startTime = System.currentTimeMillis();
            List<Passenger> passengerList;
            Set<Passenger> currentMatchPassengers;
            Set<Passenger> extendMatchPassengers;
            Set<Passenger> temp;
            List<Integer> skipIndices;
            int counter;
            int capLimit;
            int endIndex;
            int startIndex;
            boolean observation = false;
            Match m;

            for (Driver driver : drivers) {
                counter = driver.getMatches().size();
                if (counter == 0 || driver.getCapacity() < 2) // process next driver
                    continue;
                else if (counter >= SimulationParameters.maxNumMatchesPerDriver)
                    continue;

                passengerList = new ArrayList<>(counter);
                skipIndices=  new ArrayList<>(counter*2);
                for (int i = 0; i < counter; i++) {	 // every match consists of 1 passenger at this point
                    passengerList.addAll(driver.getMatches().get(i).sfp.passengers);
                    skipIndices.add(i);
                }

                capLimit = 2;
                startIndex = 0;

                outerWhile:
                while (driver.getCapacity() >= capLimit) {
                    endIndex = driver.getMatches().size();
                    for (int i = startIndex; i < endIndex; i++) {	// grow each match
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

                            // check if the extendMatchPassengers has a feasible shortest path
                            m = constructMatch(driver, extendMatchPassengers);
                            if (m != null) {
                                driver.addMatch(m);
                                calculateProfit(m, driver);
                                matchID++;
                                if (driver.getMatches().size() % 5000 == 0)
                                    System.out.println("Driver " + driver.getID() + ": Added " + counter + " sets");
                                if (driver.getMatches().size() >= SimulationParameters.maxNumMatchesPerDriver) {
                                    driver.addIndexLevel(driver.getMatches().size());
                                    break outerWhile;
                                }
                                skipIndices.add(index);
                            }
                        }
                    }
                    //System.out.println("driver.getMatches().size() = " + driver.getMatches().size());
                    driver.addIndexLevel(driver.getMatches().size());
                    if (endIndex == driver.getMatches().size()) // did not find any new sigma set
                        break;								    // process next driver
                    startIndex = endIndex;
                    capLimit++;
                }
            }
            endTime = System.currentTimeMillis();
            computeDuration = endTime- startTime;
            System.out.println("computeAllMatchesDP completed. It took " + (computeDuration/1000) + " seconds.");
        }
        return computeDuration;
    }
    
    private long computeAllMatchesThreads(List<Driver> drivers) {
        System.out.println("computeAllMatchesThreads() is called with drivers = " + (driverSize) + " and passengers = " + (passengerSize) );
        startTime = System.currentTimeMillis();

        executor = Executors.newFixedThreadPool(SimulationParameters.nThreads);
        for (Driver driver: drivers)
            executor.execute( new DriverConsumer(this, driver) );

        executor.shutdown();
        try {
            executor.awaitTermination(360000, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.out.println(e.toString());
            return 360000;
        }

        endTime = System.currentTimeMillis();
        System.out.println("computeAllMatchesThreads completed. It took " + ((endTime-startTime)/1000) + " seconds.");
        return endTime - startTime;
    }
    
    public int getPassengerRegionIndex(Pair<Passenger, Boolean> OD) {
        if (OD.getP2())
            return OD.getP1().getEndRegion();
        return OD.getP1().getStartRegion();
    }
    
    public Location getPassengerODLocations(Pair<Passenger, Boolean> OD) {
        if (OD.getP2())
            return new Location(OD.getP1().getEndLongitude(), OD.getP1().getEndLatitude());
        return new Location(OD.getP1().getStartLongitude(), OD.getP1().getStartLatitude());
    }
    
    public boolean computeFeasiblePath(Driver driver, Set<Passenger> passengers, List<Pair<Passenger, Boolean>> originOrDest) {
        int size = originOrDest.size();
        int[] travelDistanceIndex = new int[size];
        int[] hourIndex = new int[size];
        long[] travelDuration = new long[size];
        int driverIndex = tripIDtoTravelDistanceIndex.get(driver.getID());
        int passengerIndex;
        long driverDeparture;
        long arrivedTime;
        long accDuration;
        
        for (int i = 0; i < size; i++) {
            passengerIndex = tripIDtoTravelDistanceIndex.get(originOrDest.get(i).getP1().getID());
            if (originOrDest.get(i).getP2())  // destination of passenger at index j
                travelDistanceIndex[i] = passengerIndex+passengerSize;
            else                     // origin of passenger at index j
                travelDistanceIndex[i] = passengerIndex;
        }
            
        travelDuration[0] = (long) (travelDistance[driverIndex][tripIDtoTravelDistanceIndex.get(originOrDest.get(0).getP1().getID())] / 
                                        Speed[currentHourIndex][driver.getStartRegion()][originOrDest.get(0).getP1().getStartRegion()]);
        accDuration = travelDuration[0];
        
        driverDeparture = Math.max(driver.getDepartureTime(), originOrDest.get(0).getP1().getDepartureTime() - travelDuration[0]);   
        arrivedTime = driverDeparture + travelDuration[0];
        hourIndex[0] = Math.min((int)(arrivedTime / 3600.0), 23) - SimulationParameters.startHour;
        int j;
        for (j = 0; j < size-1; j++) {
            if (travelDistance[travelDistanceIndex[j]][travelDistanceIndex[j+1]] == 0)
                travelDistance[travelDistanceIndex[j]][travelDistanceIndex[j+1]] = 
                                    (long) (ho.getDistanceBetweenTwoLocs(getPassengerODLocations(originOrDest.get(j)), getPassengerODLocations(originOrDest.get(j+1))));
            travelDuration[j+1] = (long) (travelDistance[travelDistanceIndex[j]][travelDistanceIndex[j+1]] /
                        Speed[hourIndex[j]][getPassengerRegionIndex(originOrDest.get(j))][getPassengerRegionIndex(originOrDest.get(j+1))]); // from l_j to l_{j+1}
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
        
        /* Due to earliest departure times for all drivers and passengers are in the same interval, they all have the same travel speed in the beginning.
       // the following binary search is not needed.
        // there are some room to adjust the departure time of the driver
        // find one such that there is no waiting time for every one
        long bestDeparture = originalDeparture;
        if (latestDepartureTime > firstDepartureTime) {
            long dt;
            while (firstDepartureTime <= latestDepartureTime) {
                dt = (firstDepartureTime + latestDepartureTime) / 2;
                if (isDepartureForDriverValid(pathToBeTested, dt, travelDuration, travelDistanceIndex)) {
                    bestDeparture = dt;
                    latestDepartureTime = dt - 1;
                } else {
                    firstDepartureTime = dt + 1;
                }
            }
            
            // calculate travel duration using the updated driverDeparture
            if (bestDeparture > originalDeparture) {
                arrivedTime = bestDeparture + travelDuration[0];
                accDuration = travelDuration[0];
                hourIndex[0] = Math.min((int)(arrivedTime / 3600.0) - startHour, 23);
                 for (j = 0; j < size-1; j++) {
                    travelDuration[j+1] = (long) (travelDistance[travelDistanceIndex[j]][travelDistanceIndex[j+1]] /
                                Speed[hourIndex[j]][getPassengerRegionIndex(pathToBeTested.get(j))][getPassengerRegionIndex(pathToBeTested.get(j+1))]); // from l_j to l_{j+1}
                    accDuration = accDuration + travelDuration[j+1];
                    arrivedTime = firstDepartureTime + accDuration;     // time arrived at l_{j+1}
                    if (!pathToBeTested.get(j+1).getP2()) {   // if this the origin of passenger at index j+1
                        if (pathToBeTested.get(j+1).getP1().getDepartureTime() > arrivedTime) {
                            arrivedTime = pathToBeTested.get(j+1).getP1().getDepartureTime();  // the actual time left at l_{j+1}
                            System.out.println("Should not reach here. Driver id: "+ driver.getID());
                            System.out.println("Passenger: "+ pathToBeTested.get(j+1).getP1().toStringAll());
                        }
                    }
                    hourIndex[j+1] = Math.min((int)(arrivedTime / 3600.0) - startHour, 23);
                }
            }
            System.out.println("Found better depature than original ("+originalDeparture+") for driver: " + bestDeparture);
        }*/
        
        long driverDur = accDuration + (long) (travelDistance[travelDistanceIndex[j]][driverIndex] /
                                Speed[hourIndex[j]][getPassengerRegionIndex(originOrDest.get(j))][driver.getEndRegion()]);

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
    
    public boolean isDepartureForDriverValid(List<Pair<Passenger, Boolean>> originOrDest, long dt, long[] travelDuration, int[] travelDistanceIndex) {
        long arrivedTime = dt + travelDuration[0];
        int hourIndexTemp;
        long accDuration = travelDuration[0];
        for (int j = 0; j < originOrDest.size()-1; j++) {
            hourIndexTemp = Math.min((int)(arrivedTime / 3600.0), 23) - SimulationParameters.startHour;
            travelDuration[j+1] = (long) (travelDistance[travelDistanceIndex[j]][travelDistanceIndex[j+1]] /
                        Speed[hourIndexTemp][getPassengerRegionIndex(originOrDest.get(j))][getPassengerRegionIndex(originOrDest.get(j+1))]); // from l_j to l_{j+1}
            accDuration = accDuration + travelDuration[j+1];
            arrivedTime = dt + accDuration;

            if (!originOrDest.get(j+1).getP2()) {   // if this the origin of passenger at index j+1
                if ( (arrivedTime < originOrDest.get(j+1).getP1().getDepartureTime() - 5) )
                    return false;      // this depature time $dt$ of driver is not acceptable
                //arrivedTime = Math.max(pathToBeTested.get(j+1).getP1().getDepartureTime(), arrivedTime);  // the actual time left at l_{j+1}
            }
        }
        return true;
    }
    
    public Match constructMatch(Driver driver, Set<Passenger> passengers) {
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
                if (isValidRoute(passengers, originOrDest)) {
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
        return new Match(matchID, bestSFP);
    }
    
    public SFP compareTwoSFP(Driver driver, SFP currentBest, SFP newSFP) {
        if (newSFP == null)
            return currentBest;
        else if (currentBest == null)
            return newSFP;
        
        // compare them
        int driverIndex = tripIDtoTravelDistanceIndex.get(driver.getID());
        int[] dist;
        int i;
        if (currentBestDist == Long.MAX_VALUE) {
            dist = currentBest.travelDistanceIndex;
            currentBestDist = travelDistance[driverIndex][dist[0]];
            for (i = 0; i < dist.length-1; i++)
                currentBestDist = currentBestDist + travelDistance[dist[i]][dist[i+1]];
            currentBestDist = currentBestDist + travelDistance[dist[i]][driverIndex];
        }
        
        long newSFPDist;
        dist = newSFP.travelDistanceIndex;
        newSFPDist = travelDistance[driverIndex][dist[0]];
        for (i = 0; i < dist.length-1; i++)
            newSFPDist = newSFPDist + travelDistance[dist[i]][dist[i+1]];
        newSFPDist = newSFPDist + travelDistance[dist[i]][driverIndex];
        
        if (newSFPDist > currentBestDist) {
            currentBestDist = newSFPDist;
            return newSFP;
        }
        return currentBest;
    }
    
    public boolean isValidRoute(Set<Passenger> passengers, List<Pair<Passenger, Boolean>> originOrDest) {
        HashMap<Passenger, Integer> test = new HashMap<>();
        for (Passenger p : passengers)
            test.put(p, 0);
        
        for (int i = 0; i < originOrDest.size(); i++) {
            if (test.get(originOrDest.get(i).getP1()) == 0) {
                if (originOrDest.get(i).getP2()) {
                    return false;
                } else {
                    test.put(originOrDest.get(i).getP1(), 1);
                }
            }
        }
        
        return true;
    }
    
    public long computeAllMatchesDP(List<Driver> drivers, List<Passenger> passengers, boolean estimate) {
        long computeDuration;
        matchID = 0;
        if (SimulationParameters.useMultiThread) {
            computeDuration = computeAllMatchesDPThreads(drivers, estimate);
            startTime = System.currentTimeMillis();
            setMatchIDs(drivers);
            endTime = System.currentTimeMillis();
            computeDuration = computeDuration + endTime - startTime;
        } else {
            System.out.println("computeAllMatchesDP() is called with drivers = " + (drivers.size()) + " and passengers = " + (passengers.size()));
            startTime = System.currentTimeMillis();
            List<Passenger> passengerList;
            Set<Passenger> currentMatchPassengers;
            Set<Passenger> extendMatchPassengers;
            HashMap<Integer, List<FeasiblePath>> feasiblePathsForMatchAtIndex;
            List<Integer> skipIndices;
            int counter;
            int capLimit;
            int endIndex;
            int startIndex;
            boolean observation = false;
            Pair<Match, List<FeasiblePath>> matchAndPaths;
            Set<Passenger> temp;
            FeasiblePath feasiblePath;

            for (Driver driver : drivers) {
                //System.out.println("Processing Drvier "+ driver.getID() + " with capacity and stop = " + driver.getCapacity() + " : " + driver.getMaxNStop());
                counter = driver.getMatches().size();
                if (counter == 0 || driver.getCapacity() < 2) // process next driver
                    continue;
                else if (counter >= SimulationParameters.maxNumMatchesPerDriver)
                    continue;

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

                outerWhile:
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
                            matchAndPaths = constructMatchDP(driver, extendMatchPassengers, passengerList.get(index), feasiblePathsForMatchAtIndex.get(i));
                            if (matchAndPaths != null) {
                                driver.addMatch(matchAndPaths.getP1());
                                calculateProfit(matchAndPaths.getP1(), driver);
                                matchID++;
                                if (driver.getMatches().size() % 5000 == 0)
                                    System.out.println("Driver " + driver.getID() + ": Added " + counter + " sets");
                                if (driver.getMatches().size() >= SimulationParameters.maxNumMatchesPerDriver) {
                                    driver.addIndexLevel(driver.getMatches().size());
                                    break outerWhile;
                                }
                                skipIndices.add(index);
                                feasiblePathsForMatchAtIndex.put(driver.getMatches().size()-1, matchAndPaths.getP2());
                            }
                        }
                        feasiblePathsForMatchAtIndex.remove(i);
                    }
                    driver.addIndexLevel(driver.getMatches().size());
                    //System.out.println("driver.getMatches().size() = " + driver.getMatches().size());
                    if (endIndex == driver.getMatches().size()) // did not find any new sigma set
                        break;								    // process next driver
                    startIndex = endIndex;
                    capLimit++;
                }
            }
            endTime = System.currentTimeMillis();
            computeDuration = endTime- startTime;
            System.out.println("computeAllMatchesDP completed. It took " + (computeDuration/1000) + " seconds.");
        }
        return computeDuration;
    }
    
    private long computeAllMatchesDPThreads(List<Driver> drivers, boolean estimate) {
        System.out.println("computeAllMatchesDPThreads() is called with drivers = " + (driverSize) + " and passengers = " + (passengerSize) + " and estimation: " + estimate);
        startTime = System.currentTimeMillis();

        executor = Executors.newFixedThreadPool(SimulationParameters.nThreads);
        if (estimate) {
            for (Driver driver: drivers)
                executor.execute( new DriverConsumerDPEstimate(this, driver) );
        } else {
            for (Driver driver: drivers)
                executor.execute( new DriverConsumerDP(this, driver) );
        }

        executor.shutdown();
        try {
            executor.awaitTermination(360000, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.out.println(e.toString());
            return 360000;
        }

        endTime = System.currentTimeMillis();
        System.out.println("computeAllMatchesDPThreads completed. It took " + ((endTime-startTime)/1000) + " seconds.");
        return endTime - startTime;
    }
    
    public Pair<Match, List<FeasiblePath>> constructMatchDP(Driver driver, Set<Passenger> passengers, Passenger passenger, List<FeasiblePath> feasiblePaths) {
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
        return new Pair(new Match(matchID, bestSFP), newFeasiblePaths);
    }
    
	// this is for testing Manhattan distance only
    public long constructBaseMatchesMD(List<Driver> drivers, List<Passenger> passengers, BiFunction<Driver, Passenger, Boolean> candidateMethod) {
        long computeDuration;
        matchID = 0;
        if (SimulationParameters.useMultiThread) {
            computeDuration = constructBaseMatchesMDPreprocessingThreads(passengers);
            computeDuration = computeDuration + constructBaseMatchesMDThreads(drivers, passengers, candidateMethod);
            startTime = System.currentTimeMillis();
            setPassengerInNumMatches(drivers);
            setMatchIDs(drivers);
            endTime = System.currentTimeMillis();
            computeDuration = computeDuration + endTime - startTime;
        } else {
            computeDuration = constructBaseMatchesMDPreprocessing(passengers);
            System.out.println("constructBaseMatches()......");
            int driverIndex ;
            int passengerIndex;
            long duration;
            long durationDriver;
            long arrivedTime;
            long driverDeparture;
            SFP sfp;
            Set<Passenger> passengersInMatch;
            List<Pair<Passenger, Boolean>> originOrDest;
            int[] travelDistanceIndex;
            int[] hourIndex;
            startTime = System.currentTimeMillis();
            for (Driver driver : drivers) {
                driverIndex = tripIDtoTravelDistanceIndex.get(driver.getID());
                for (Passenger passenger : passengers) {
                    // check driver and passenger are within some radius
                    if (candidateMethod.apply(driver, passenger)) {
                        passengersInMatch = new HashSet<>(1);
                        originOrDest = new ArrayList<>(2);
                        travelDistanceIndex = new int[2];
                        hourIndex = new int[2];
                        passengerIndex = tripIDtoTravelDistanceIndex.get(passenger.getID());
                        
                        // from driver's origin to passenger's origin
                        travelDistance[driverIndex][passengerIndex] = (long)(ManhattanDistance(driver.getStartLatitude(), driver.getStartLongitude(),
                                                                                        passenger.getStartLatitude(), passenger.getStartLongitude()));
                        duration = (long) (travelDistance[driverIndex][passengerIndex] / Speed[currentHourIndex][driver.getStartRegion()][passenger.getStartRegion()]);
                        durationDriver = duration;
                        
                        // time arrived at passenger's origin
                        driverDeparture = Math.max(driver.getDepartureTime(), passenger.getDepartureTime() - duration);
                        arrivedTime = driverDeparture + duration;
                        hourIndex[0] = Math.min((int)(arrivedTime / 3600.0), 23) - SimulationParameters.startHour;
                        originOrDest.add(new Pair<>(passenger, Boolean.FALSE));
                        travelDistanceIndex[0] = passengerIndex;
                        
                        // from passenger's origin to passenger's destination
                        duration = (long) (travelDistance[passengerIndex][passengerIndex+passengerSize] / Speed[hourIndex[0]][passenger.getStartRegion()][passenger.getEndRegion()]);
                        arrivedTime = arrivedTime + duration;
                        
                        // see if passenger is okay, second condition is unnecessary
                        if (arrivedTime <= passenger.getArrivalTime() && duration <= passenger.getMaxTravelDuration()) {
                            hourIndex[1] = Math.min((int)(arrivedTime / 3600.0), 23) - SimulationParameters.startHour;
                            durationDriver = durationDriver + duration;     // duration from driver's origin to passenger's destination
                            // from passenger's destination to driver's destination
                            travelDistance[passengerIndex+passengerSize][driverIndex] = (long)(ManhattanDistance(passenger.getEndLatitude(), driver.getEndLongitude(),
                                                                                                driver.getEndLatitude(), driver.getEndLongitude()));
                            duration = (long) (travelDistance[passengerIndex+passengerSize][driverIndex] / Speed[hourIndex[1]][passenger.getEndRegion()][driver.getEndRegion()]);
                            durationDriver = durationDriver + duration;
                            // see if driver is okay
                            if (arrivedTime + duration <= driver.getArrivalTime() && durationDriver <= driver.getMaxTravelDuration()) {
                                passengersInMatch.add(passenger);
                                originOrDest.add(new Pair<>(passenger, Boolean.TRUE));
                                travelDistanceIndex[1] = passengerIndex+passengerSize;
                                sfp = new SFP(passengersInMatch, originOrDest, travelDistanceIndex, hourIndex, driverDeparture);
                                driver.addMatch(new Match(matchID, sfp));
                                calculateProfit(driver.getMatches().get(driver.getMatches().size()-1), driver);
                                passenger.incrementNAssignments();
                                matchID++;
                            }
                        }
                    }
                }
                driver.addIndexLevel(driver.getMatches().size());
            }
            
            endTime = System.currentTimeMillis();
            computeDuration = computeDuration + endTime - startTime;
        }
        
        System.out.println("Constructed base matches. - matchID: " + matchID + " - total sets: " + countTotalNumMatches(drivers).getP1() + 
                                        ". It took " + (computeDuration) + " milliseconds.");
        return computeDuration;
    }
    
    public long constructBaseMatches(List<Driver> drivers, List<Passenger> passengers, BiFunction<Driver, Passenger, Boolean> candidateMethod) {
        long computeDuration;
        matchID = 0;
        if (SimulationParameters.useMultiThread) {
            computeDuration = constructBaseMatchesPreprocessingThreads(passengers);
            computeDuration = computeDuration + constructBaseMatchesThreads(drivers, passengers, candidateMethod);
            startTime = System.currentTimeMillis();
            setPassengerInNumMatches(drivers);
            setMatchIDs(drivers);
            endTime = System.currentTimeMillis();
            computeDuration = computeDuration + endTime - startTime;
        } else {
            computeDuration = constructBaseMatchesPreprocessing(passengers);
            System.out.println("constructBaseMatches()......");
            int driverIndex ;
            int passengerIndex;
            long duration;
            long durationDriver;
            long arrivedTime;
            long driverDeparture;
            SFP sfp;
            Set<Passenger> passengersInMatch;
            List<Pair<Passenger, Boolean>> originOrDest;
            int[] travelDistanceIndex;
            int[] hourIndex;
            startTime = System.currentTimeMillis();
            for (Driver driver : drivers) {
                driverIndex = tripIDtoTravelDistanceIndex.get(driver.getID());
                for (Passenger passenger : passengers) {
                    // check driver and passenger are within some radius
                    if (candidateMethod.apply(driver, passenger)) {
                    //if (testCandidate(driver, passenger)) {
                        passengersInMatch = new HashSet<>(1);
                        originOrDest = new ArrayList<>(2);
                        travelDistanceIndex = new int[2];
                        hourIndex = new int[2];
                        passengerIndex = tripIDtoTravelDistanceIndex.get(passenger.getID());
                        
                        // from driver's origin to passenger's origin
                        travelDistance[driverIndex][passengerIndex] = (long) (ho.getDistanceBetweenTwoLocs(driver.getStartLongitude(), driver.getStartLatitude(), 
                                                                                    passenger.getStartLongitude(), passenger.getStartLatitude()));
                        duration = (long) (travelDistance[driverIndex][passengerIndex] / Speed[currentHourIndex][driver.getStartRegion()][passenger.getStartRegion()]);
                        durationDriver = duration;
                        
                        // time arrived at passenger's origin
                        driverDeparture = Math.max(driver.getDepartureTime(), passenger.getDepartureTime() - duration);
                        arrivedTime = driverDeparture + duration;
                        hourIndex[0] = Math.min((int)(arrivedTime / 3600.0), 23) - SimulationParameters.startHour;
                        originOrDest.add(new Pair<>(passenger, Boolean.FALSE));
                        travelDistanceIndex[0] = passengerIndex;
                        
                        // from passenger's origin to passenger's destination
                        duration = (long) (travelDistance[passengerIndex][passengerIndex+passengerSize] / Speed[hourIndex[0]][passenger.getStartRegion()][passenger.getEndRegion()]);
                        arrivedTime = arrivedTime + duration;
                        
                        // see if passenger is okay, second condition is unnecessary
                        if (arrivedTime <= passenger.getArrivalTime() && duration <= passenger.getMaxTravelDuration()) {
                            hourIndex[1] = Math.min((int)(arrivedTime / 3600.0), 23) - SimulationParameters.startHour;
                            durationDriver = durationDriver + duration;     // duration from driver's origin to passenger's destination
                            // from passenger's destination to driver's destination
                            travelDistance[passengerIndex+passengerSize][driverIndex] = (long) (ho.getDistanceBetweenTwoLocs(passenger.getEndLongitude(), passenger.getEndLatitude(), 
                                                                                                        driver.getEndLongitude(), driver.getEndLatitude()));
                            duration = (long) (travelDistance[passengerIndex+passengerSize][driverIndex] / Speed[hourIndex[1]][passenger.getEndRegion()][driver.getEndRegion()]);
                            durationDriver = durationDriver + duration;
                            // see if driver is okay
                            if (arrivedTime + duration <= driver.getArrivalTime() && durationDriver <= driver.getMaxTravelDuration()) {
                                passengersInMatch.add(passenger);
                                originOrDest.add(new Pair<>(passenger, Boolean.TRUE));
                                travelDistanceIndex[1] = passengerIndex+passengerSize;
                                sfp = new SFP(passengersInMatch, originOrDest, travelDistanceIndex, hourIndex, driverDeparture);
                                driver.addMatch(new Match(matchID, sfp));
                                calculateProfit(driver.getMatches().get(driver.getMatches().size()-1), driver);
                                passenger.incrementNAssignments();
                                matchID++;
                            }
                        }
                    }
                }
                driver.addIndexLevel(driver.getMatches().size());
            }
            
            endTime = System.currentTimeMillis();
            computeDuration = computeDuration + endTime - startTime;
        }
        System.out.println("Constructed base matches. - matchID: " + matchID + " - total sets: " + countTotalNumMatches(drivers).getP1() + 
                                        ". It took " + (computeDuration/1000) + " seconds.");
        return computeDuration;
    }
    
    private long constructBaseMatchesMDThreads(List<Driver> drivers, List<Passenger> passengers, BiFunction<Driver, Passenger, Boolean> candidateMethod) {
        System.out.println("constructBaseMatchesMDThreads()......");
        
        startTime = System.currentTimeMillis();
        executor = Executors.newFixedThreadPool(SimulationParameters.nThreads);
        for (Driver driver : drivers) {
                executor.submit(() -> {
                    int driverIndex = tripIDtoTravelDistanceIndex.get(driver.getID());
                    int passengerIndex;
                    long duration;
                    long durationDriver;
                    long arrivedTime;
                    long driverDeparture;
                    SFP sfp;
                    Set<Passenger> passengersInMatch;
                    List<Pair<Passenger, Boolean>> originOrDest;
                    int[] travelDistanceIndex;
                    int[] hourIndex;
                    for (Passenger passenger : passengers) {
                        if (candidateMethod.apply(driver, passenger)) {
                            passengersInMatch = new HashSet<>(1);
                            originOrDest = new ArrayList<>(2);
                            travelDistanceIndex = new int[2];
                            hourIndex = new int[2];
                            passengerIndex = tripIDtoTravelDistanceIndex.get(passenger.getID());

                            // from driver's origin to passenger's origin
                            travelDistance[driverIndex][passengerIndex] = (long)(ManhattanDistance(driver.getStartLatitude(), driver.getStartLongitude(),
                                                                                        passenger.getStartLatitude(), passenger.getStartLongitude()));
                            //System.out.format("travelDistance[%d][%d] = %d%n", driverIndex, passengerIndex, travelDistance[driverIndex][passengerIndex]);
                            duration = (long) (travelDistance[driverIndex][passengerIndex] / Speed[currentHourIndex][driver.getStartRegion()][passenger.getStartRegion()]);
                            durationDriver = duration;

                            // time arrived at passenger's origin
                            driverDeparture = Math.max(driver.getDepartureTime(), passenger.getDepartureTime() - duration);
                            arrivedTime = driverDeparture + duration;
                            hourIndex[0] = Math.min((int)(arrivedTime / 3600.0), 23) - SimulationParameters.startHour;
                            originOrDest.add(new Pair<>(passenger, Boolean.FALSE));
                            travelDistanceIndex[0] = passengerIndex;

                            // from passenger's origin to passenger's destination
                            duration = (long) (travelDistance[passengerIndex][passengerIndex+passengerSize] / Speed[hourIndex[0]][passenger.getStartRegion()][passenger.getEndRegion()]);
                            arrivedTime = arrivedTime + duration;

                            // see if passenger is okay, second condition is unnecessary?
                            if (arrivedTime <= passenger.getArrivalTime() && duration <= passenger.getMaxTravelDuration()) {
                                hourIndex[1] = Math.min((int)(arrivedTime / 3600.0), 23) - SimulationParameters.startHour;
                                durationDriver = durationDriver + duration;         // duration from driver's origin to passenger's destination
                                // from passenger's destination to driver's destination
                                travelDistance[passengerIndex+passengerSize][driverIndex] = (long)(ManhattanDistance(passenger.getEndLatitude(), driver.getEndLongitude(),
                                                                                        driver.getEndLatitude(), driver.getEndLongitude()));

                                duration = (long) (travelDistance[passengerIndex+passengerSize][driverIndex] / Speed[hourIndex[1]][passenger.getEndRegion()][driver.getEndRegion()]);
                                durationDriver = durationDriver + duration;
                                // see if driver is okay
                                if (arrivedTime + duration <= driver.getArrivalTime() && durationDriver <= driver.getMaxTravelDuration()) {
                                    passengersInMatch.add(passenger);
                                    originOrDest.add(new Pair<>(passenger, Boolean.TRUE));
                                    travelDistanceIndex[1] = passengerIndex+passengerSize;
                                    sfp = new SFP(passengersInMatch, originOrDest, travelDistanceIndex, hourIndex, driverDeparture);
                                    driver.addMatch(new Match(sfp));
                                    calculateProfit(driver.getMatches().get(driver.getMatches().size()-1), driver);
                                }
                            }
                        }
                    }
                    driver.addIndexLevel(driver.getMatches().size());
                    //System.out.println("Driver " + driver.getID() + " is completed.");
                });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.out.println(e.toString());
        }
        endTime = System.currentTimeMillis();
        return (endTime - startTime);
    }
    
    private long constructBaseMatchesThreads(List<Driver> drivers, List<Passenger> passengers, BiFunction<Driver, Passenger, Boolean> candidateMethod) {
        System.out.println("constructBaseMatchesThreads()......");
        
        startTime = System.currentTimeMillis();
        executor = Executors.newFixedThreadPool(SimulationParameters.nThreads);
        for (Driver driver : drivers) {
                executor.submit(() -> {
                    int driverIndex = tripIDtoTravelDistanceIndex.get(driver.getID());
                    int passengerIndex;
                    long duration;
                    long durationDriver;
                    long arrivedTime;
                    long driverDeparture;
                    SFP sfp;
                    Set<Passenger> passengersInMatch;
                    List<Pair<Passenger, Boolean>> originOrDest;
                    int[] travelDistanceIndex;
                    int[] hourIndex;
                    for (Passenger passenger : passengers) {
                        if (candidateMethod.apply(driver, passenger)) {
                            passengersInMatch = new HashSet<>(1);
                            originOrDest = new ArrayList<>(2);
                            travelDistanceIndex = new int[2];
                            hourIndex = new int[2];
                            passengerIndex = tripIDtoTravelDistanceIndex.get(passenger.getID());

                            // from driver's origin to passenger's origin
                            travelDistance[driverIndex][passengerIndex] = (long) (ho.getDistanceBetweenTwoLocs(driver.getStartLongitude(), driver.getStartLatitude(), 
                                                                                        passenger.getStartLongitude(), passenger.getStartLatitude()));
                            //System.out.format("travelDistance[%d][%d] = %d%n", driverIndex, passengerIndex, travelDistance[driverIndex][passengerIndex]);
                            duration = (long) (travelDistance[driverIndex][passengerIndex] / Speed[currentHourIndex][driver.getStartRegion()][passenger.getStartRegion()]);
                            durationDriver = duration;

                            // time arrived at passenger's origin
                            driverDeparture = Math.max(driver.getDepartureTime(), passenger.getDepartureTime() - duration);
                            arrivedTime = driverDeparture + duration;
                            hourIndex[0] = Math.min((int)(arrivedTime / 3600.0), 23) - SimulationParameters.startHour;
                            originOrDest.add(new Pair<>(passenger, Boolean.FALSE));
                            travelDistanceIndex[0] = passengerIndex;

                            // from passenger's origin to passenger's destination
                            duration = (long) (travelDistance[passengerIndex][passengerIndex+passengerSize] / Speed[hourIndex[0]][passenger.getStartRegion()][passenger.getEndRegion()]);
                            arrivedTime = arrivedTime + duration;

                            // see if passenger is okay, second condition is unnecessary?
                            if (arrivedTime <= passenger.getArrivalTime() && duration <= passenger.getMaxTravelDuration()) {
                                hourIndex[1] = Math.min((int)(arrivedTime / 3600.0), 23) - SimulationParameters.startHour;
                                durationDriver = durationDriver + duration;         // duration from driver's origin to passenger's destination
                                // from passenger's destination to driver's destination
                                travelDistance[passengerIndex+passengerSize][driverIndex] = (long) (ho.getDistanceBetweenTwoLocs(passenger.getEndLongitude(), passenger.getEndLatitude(), 
                                                                                                            driver.getEndLongitude(), driver.getEndLatitude()));
                                duration = (long) (travelDistance[passengerIndex+passengerSize][driverIndex] / Speed[hourIndex[1]][passenger.getEndRegion()][driver.getEndRegion()]);
                                durationDriver = durationDriver + duration;
                                // see if driver is okay
                                if (arrivedTime + duration <= driver.getArrivalTime() && durationDriver <= driver.getMaxTravelDuration()) {
                                    passengersInMatch.add(passenger);
                                    originOrDest.add(new Pair<>(passenger, Boolean.TRUE));
                                    travelDistanceIndex[1] = passengerIndex+passengerSize;
                                    sfp = new SFP(passengersInMatch, originOrDest, travelDistanceIndex, hourIndex, driverDeparture);
                                    driver.addMatch(new Match(sfp));
                                    calculateProfit(driver.getMatches().get(driver.getMatches().size()-1), driver);
                                }
                            }
                        }
                    }
                    driver.addIndexLevel(driver.getMatches().size());
                    //System.out.println("Driver " + driver.getID() + " is completed.");
                });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.out.println(e.toString());
        }
        endTime = System.currentTimeMillis();
        return (endTime - startTime);
    }
    
    public int reduceFeasibleMatches(List<Driver> drivers, List<Passenger> passengers, int reduceMethod) {
        int numMatchesRemoved = 0;
        Passenger tempPassenger;

        for (Driver driver : drivers) {
            for (int j = driver.getMatches().size()-1; j >= 0; j--) {   // there is a reason why it is not j >= minNumBaseMatchesPerDriver
                if (driver.getMatches().size() > SimulationParameters.minNumBaseMatchesPerDriver) {
                    tempPassenger = driver.getMatches().get(j).sfp.passengers.iterator().next();
                    if (tempPassenger.getNAssignments() > SimulationParameters.thresholdMatchesForEachPassenger) {
                        tempPassenger.decrementNAssignments();
                        driver.getMatches().remove(j);
                        numMatchesRemoved++;
                    }
                } else {
                    break;
                }
            }
            
            if (driver.getMatches().size() > SimulationParameters.maxNumBaseMatchesPerDriver) {
                driver.getMatches().sort(new PassengerMatchesMinComparator());
                for (int j = driver.getMatches().size()-1; j >= 0; j--) {
                    tempPassenger = driver.getMatches().get(j).sfp.passengers.iterator().next();
                    if (tempPassenger.getNAssignments() > SimulationParameters.thresholdMatchesForEachPassenger/2) {
                        tempPassenger.decrementNAssignments();
                        driver.getMatches().remove(j);
                        numMatchesRemoved++;
                    }
                    if (driver.getMatches().size() <= SimulationParameters.maxNumBaseMatchesPerDriver)
                        break;
                }
            }
            try {
                if (!driver.getMatches().isEmpty())
                    driver.setIndexLevel(0, driver.getMatches().size());
            } catch (Exception e) {
                System.out.println(e.getMessage());
                System.out.println("Driver ID: " + driver.getID()+ ", NumMatches = "+ driver.getMatches().size());
            }
        }
        
        return numMatchesRemoved;
    }
    
    // this is a workaround for RPC1 (RPC+ should not call this)
    public double computeTravelDistance(Match match, Driver driver, int driverIndex) {
        SFP s = match.sfp;
        int lastIndex = s.originOrDest.size()-1;
        Passenger passenger = s.passengers.iterator().next();
        if (travelDistance[driverIndex][s.travelDistanceIndex[0]] == 0)
            travelDistance[driverIndex][s.travelDistanceIndex[0]] = (long) (ho.getDistanceBetweenTwoLocs(driver.getStartLongitude(), driver.getStartLatitude(), 
                                                                                    passenger.getStartLongitude(), passenger.getStartLatitude()));
        double accumalativeDistance = travelDistance[driverIndex][s.travelDistanceIndex[0]];
        
        if (travelDistance[s.travelDistanceIndex[0]][s.travelDistanceIndex[1]] == 0)
            travelDistance[s.travelDistanceIndex[0]][s.travelDistanceIndex[1]] = (long) (ho.getDistanceBetweenTwoLocs(passenger.getStartLongitude(), passenger.getStartLatitude(), 
                                                                                    passenger.getEndLongitude(), passenger.getEndLatitude()));
        if (travelDistance[s.travelDistanceIndex[lastIndex]][driverIndex] == 0)
            travelDistance[s.travelDistanceIndex[lastIndex]][driverIndex] = (long) (ho.getDistanceBetweenTwoLocs(passenger.getEndLongitude(), passenger.getEndLatitude(), 
                                                                                    driver.getEndLongitude(), driver.getEndLatitude()));
        
        for (int i = 0; i < lastIndex; i++) {
            // from s.travelDistanceIndex[j] to s.travelDistanceIndex[j+1]
            accumalativeDistance = accumalativeDistance + travelDistance[s.travelDistanceIndex[i]][s.travelDistanceIndex[i+1]];
        }
        return accumalativeDistance + travelDistance[s.travelDistanceIndex[lastIndex]][driverIndex];
    }
    
    public void decreaseProfitByIncreasingCost(List<Driver> drivers, List<Passenger> passengers, double increaseAmountInPercentage, int operatingCost) {
        double travelDistanceOfMatch;
        if (operatingCost == 1 || operatingCost == 2)
            BaseMatchSetup(drivers, passengers);
        
        switch(operatingCost) {
            case 1:
                for (Driver driver : drivers) {
                    for (Match match : driver.getMatches()) {
                        match.cost = match.cost * increaseAmountInPercentage;
                        travelDistanceOfMatch = computeTravelDistance(match, driver, tripIDtoTravelDistanceIndex.get(driver.getID()));
                        if (driver.getCostPerMeter()+0.0000001 > mediumSedanCostPerMeter)
                            match.cost = match.cost + travelDistanceOfMatch * MediumSedanMaintenance + travelDistanceOfMatch * MediumSedanDepreciation15;
                        else if (driver.getCostPerMeter()+0.0000001 > smallSedanCostPerMeter)
                            match.cost = match.cost + travelDistanceOfMatch * SmallSedanMaintenance + travelDistanceOfMatch * SmallSedanDepreciation15;
                        else
                            System.out.println("Driver (" + driver.getID()+") has vehicle cost " + driver.getCostPerMeter());
                    }
                }
                break;
            case 2:
                for (Driver driver : drivers) {
                    for (Match match : driver.getMatches()) {
                        match.cost = match.cost * increaseAmountInPercentage;
                        travelDistanceOfMatch = computeTravelDistance(match, driver, tripIDtoTravelDistanceIndex.get(driver.getID()));
                        if (driver.getCostPerMeter()+0.0000001 > mediumSedanCostPerMeter)
                            match.cost = match.cost + travelDistanceOfMatch * MediumSedanMaintenance + travelDistanceOfMatch * MediumSedanDepreciation20;
                        else if (driver.getCostPerMeter()+0.0000001 > smallSedanCostPerMeter)
                            match.cost = match.cost + travelDistanceOfMatch * SmallSedanMaintenance + travelDistanceOfMatch * SmallSedanDepreciation20;
                        else
                            System.out.println("Driver (" + driver.getID()+") has vehicle cost " + driver.getCostPerMeter());
                    }
                }
                break;
        }
        
        calculateProfitOnly(drivers);
    }
    
    public void decreaseProfitByIncreasingCost(List<Driver> drivers, List<Passenger> passengers, double increaseAmountInPercentage, double chance, double fixedAmount, int operatingCost) {
        double travelDistanceOfMatch;
        if (operatingCost == 1 || operatingCost == 2)
            BaseMatchSetup(drivers, passengers);
        
        for (Driver driver : drivers) {
            for (Match match : driver.getMatches()) {
                if (Utility.random.nextDouble() < chance)
                    match.cost = match.cost * increaseAmountInPercentage + fixedAmount;
                else
                    match.cost = match.cost * increaseAmountInPercentage;
            }
        }
        
        switch(operatingCost) {
            case 1:
                for (Driver driver : drivers) {
                    if (driver.getCostPerMeter()+0.0000001 > mediumSedanCostPerMeter) {
                        for (Match match : driver.getMatches()) {
                            travelDistanceOfMatch = computeTravelDistance(match, driver, tripIDtoTravelDistanceIndex.get(driver.getID()));
                            //System.out.println("travelDistanceOfMatch * MediumSedanDepreciation15 = " + (travelDistanceOfMatch) + " * "+ (MediumSedanDepreciation15) + " = "+travelDistanceOfMatch * MediumSedanDepreciation15);
                            //System.out.print("match.cost = " + match.cost + " + "+(travelDistanceOfMatch * MediumSedanMaintenance + travelDistanceOfMatch * MediumSedanDepreciation15));
                            match.cost = match.cost + travelDistanceOfMatch * MediumSedanMaintenance + travelDistanceOfMatch * MediumSedanDepreciation15;
                            //System.out.println(" = " + match.cost);
                        }
                    } else if (driver.getCostPerMeter()+0.0000001 > smallSedanCostPerMeter) {
                        for (Match match : driver.getMatches()) {
                            travelDistanceOfMatch = computeTravelDistance(match, driver, tripIDtoTravelDistanceIndex.get(driver.getID()));
                            //System.out.println("travelDistanceOfMatch * SmallSedanDepreciation15 = " + (travelDistanceOfMatch) + " * "+ (SmallSedanDepreciation15) + " = "+ travelDistanceOfMatch * SmallSedanDepreciation15);
                            //System.out.print("match.cost = " + match.cost + " + "+(travelDistanceOfMatch * MediumSedanMaintenance + travelDistanceOfMatch * SmallSedanDepreciation15));
                            match.cost = match.cost + travelDistanceOfMatch * SmallSedanMaintenance + travelDistanceOfMatch * SmallSedanDepreciation15;
                            //System.out.println(" = " + match.cost);
                        }
                    } else
                        System.out.println("Driver (" + driver.getID()+") has vehicle cost " + driver.getCostPerMeter());
                }
                break;
            case 2:
                for (Driver driver : drivers) {
                    if (driver.getCostPerMeter()+0.0000001 > mediumSedanCostPerMeter) {
                        for (Match match : driver.getMatches()) {
                            travelDistanceOfMatch = computeTravelDistance(match, driver, tripIDtoTravelDistanceIndex.get(driver.getID()));
                            //System.out.println("travelDistanceOfMatch * MediumSedanDepreciation20 = " + (travelDistanceOfMatch) + " * "+ (MediumSedanDepreciation20) + " = "+ travelDistanceOfMatch * MediumSedanDepreciation20);
                            //System.out.print("match.cost = " + match.cost + " + "+(travelDistanceOfMatch * MediumSedanMaintenance + travelDistanceOfMatch * MediumSedanDepreciation20));
                            match.cost = match.cost + travelDistanceOfMatch * MediumSedanMaintenance + travelDistanceOfMatch * MediumSedanDepreciation20;
                            //System.out.println(" = " + match.cost);
                        }
                    } else if (driver.getCostPerMeter()+0.0000001 > smallSedanCostPerMeter) {
                        for (Match match : driver.getMatches()) {
                            travelDistanceOfMatch = computeTravelDistance(match, driver, tripIDtoTravelDistanceIndex.get(driver.getID()));
                            //System.out.println("travelDistanceOfMatch * SmallSedanDepreciation20 = " + (travelDistanceOfMatch) + " * "+ (SmallSedanDepreciation20) + " = "+ travelDistanceOfMatch * SmallSedanDepreciation20);
                            //System.out.print("match.cost = " + match.cost + " + "+(travelDistanceOfMatch * MediumSedanMaintenance + travelDistanceOfMatch * SmallSedanDepreciation20));
                            match.cost = match.cost + travelDistanceOfMatch * SmallSedanMaintenance + travelDistanceOfMatch * SmallSedanDepreciation20;
                            //System.out.println(" = " + match.cost);
                        }
                    } else
                        System.out.println("Driver (" + driver.getID()+") has vehicle cost " + driver.getCostPerMeter());
                }
                break;
        }
        calculateProfitOnly(drivers);
    }
    
    public void decreaseProfitByReducingRevenue(List<Driver> drivers, double remainingRevenueInPercentage) {
        for (Driver driver : drivers) {
            for (Match match : driver.getMatches())
                match.revenue = match.revenue * remainingRevenueInPercentage;
        }
        calculateProfitOnly(drivers);
    }
    
    public void calculateCost(List<Driver> drivers) {
        for (Driver driver : drivers) {
            for (Match match : driver.getMatches())
                calculateCost(match, driver, tripIDtoTravelDistanceIndex.get(driver.getID()));
        }
    }
    
    public void calculateRevenue(List<Driver> drivers) {
        for (Driver driver : drivers) {
            for (Match match : driver.getMatches())
                calculateRevenue(match, driver);
        }
    }
    
    public void calculateProfit(List<Driver> drivers, int startHour) {
        double temp;
        for (Driver driver : drivers) {
            for (Match match : driver.getMatches()) {
                calculateRevenue(match, driver);
                calculateCost(match, driver, tripIDtoTravelDistanceIndex.get(driver.getID()));
                temp = Math.round((match.revenue - match.cost)*100.0) / 100.0;
                match.profit = (int) (temp*100.0);
            }
        }
    }
    
    public void calculateProfitOnly(List<Driver> drivers) {
        double temp;
        for (Driver driver : drivers) {
            for (Match match : driver.getMatches()) {
                temp = Math.round((match.revenue - match.cost)*100.0) / 100.0 ;
                match.profit = (int) (temp*100.0);
            }
        }
    }
    
    public void calculateProfit(Match match, Driver driver) {
        calculateRevenue(match, driver);
        calculateCost(match, driver, tripIDtoTravelDistanceIndex.get(driver.getID()));
        double temp = Math.round((match.revenue - match.cost)*100.0) / 100.0;
        match.profit = (int) (temp*100.0);
    }
    
    public void calculateRevenue(Match match, Driver driver) {
        HashMap<Passenger,Integer> passengerStartIndex;     // this is to get the acumalative duration index
        HashMap<Passenger,Integer> passengerEndIndex;       //
        int differentNumPassengers;     // dp(r_j, R_i) in paper
        double discountRate;
        double takeRate;
        int minRange;
        int maxRange;
        double cost;
        double revenue = 0d;
        double numPassengers;
        long timeArrivedAtPassengerOrigin;
        double distance;
        passengerStartIndex = new HashMap<>();
        passengerEndIndex = new HashMap<>();
        SFP s = match.sfp;
        long[] accumalativeDuration = new long[s.originOrDest.size()];
        
        for (int i = 0; i < accumalativeDuration.length-1; i++) {
            // from s.travelDistanceIndex[j] to s.travelDistanceIndex[j+1]
            if (s.originOrDest.get(i).getP2()) { // this is the destination of the passenger in index j of SFP
                if (s.originOrDest.get(i+1).getP2())
                    accumalativeDuration[i+1] = (long) (accumalativeDuration[i] + travelDistance[s.travelDistanceIndex[i]][s.travelDistanceIndex[i+1]] / 
                                Speed[s.hourIndex[i]][s.originOrDest.get(i).getP1().getEndRegion()][s.originOrDest.get(i+1).getP1().getEndRegion()]);
                else
                    accumalativeDuration[i+1] = (long) (accumalativeDuration[i] + travelDistance[s.travelDistanceIndex[i]][s.travelDistanceIndex[i+1]] / 
                                Speed[s.hourIndex[i]][s.originOrDest.get(i).getP1().getEndRegion()][s.originOrDest.get(i+1).getP1().getStartRegion()]);
            } else {    // this is the origin of the passenger in index j of SFP
                if (s.originOrDest.get(i+1).getP2())
                    accumalativeDuration[i+1] = (long) (accumalativeDuration[i] + travelDistance[s.travelDistanceIndex[i]][s.travelDistanceIndex[i+1]] / 
                                Speed[s.hourIndex[i]][s.originOrDest.get(i).getP1().getStartRegion()][s.originOrDest.get(i+1).getP1().getEndRegion()]);
                else
                    accumalativeDuration[i+1] = (long) (accumalativeDuration[i] + travelDistance[s.travelDistanceIndex[i]][s.travelDistanceIndex[i+1]] / 
                                Speed[s.hourIndex[i]][s.originOrDest.get(i).getP1().getStartRegion()][s.originOrDest.get(i+1).getP1().getStartRegion()]);
            }
        }
        accumalativeDuration[0] = (long) (travelDistance[tripIDtoTravelDistanceIndex.get(driver.getID())][s.travelDistanceIndex[0]] /
                            Speed[Math.min((int)(s.departureTimeOfDriver / 3600.0),23)-SimulationParameters.startHour][driver.getStartRegion()][s.originOrDest.get(0).getP1().getStartRegion()]);
        
        for (int i = 0; i < s.originOrDest.size(); i++) {
            if (s.originOrDest.get(i).getP2())
                passengerEndIndex.put(s.originOrDest.get(i).getP1(), i);
            else
                passengerStartIndex.put(s.originOrDest.get(i).getP1(), i);
        }
        
        int passengersOnVehicle;
        for (Passenger passenger : s.passengers) {
            // need to consider the passengers that are already in the car too
            passengersOnVehicle = 0;
            for (int i = 0; i < passengerStartIndex.get(passenger); i++) {
                if (s.originOrDest.get(i).getP2())
                    passengersOnVehicle--;
                else
                    passengersOnVehicle++;
            }
            
            differentNumPassengers = 0;    // dp(r_j, R_i) in paper
            for (int i = passengerStartIndex.get(passenger)+1; i < passengerEndIndex.get(passenger); i++)
                if (!s.originOrDest.get(i).getP2())
                    differentNumPassengers++;
            differentNumPassengers = differentNumPassengers + passengersOnVehicle;
            
            discountRate = Math.max(1-0.2*differentNumPassengers, 0.2);
            // takeRate: [max(20*discountRate, 5), max(25*dp, 10)]
            minRange = (int) (Math.max(0.2*discountRate, 0.05) * 100000000);
            maxRange = (int) (Math.max(0.25*discountRate, 0.1) * 100000000);
            takeRate = (Utility.random.nextInt(maxRange - minRange + 1) + minRange)/100000000.0;
            
            cost = 0d;
            distance = 0d;
            numPassengers = passengersOnVehicle;
            for (int i = passengerStartIndex.get(passenger); i < passengerEndIndex.get(passenger); i++) {
                if (s.originOrDest.get(i).getP2())
                    numPassengers--;
                else
                    numPassengers++;
                
                distance = distance + travelDistance[s.travelDistanceIndex[i]][s.travelDistanceIndex[i+1]];                
                if (s.originOrDest.get(i).getP2()) { // this is the destination of the passenger in index j of SFP
                    if (s.originOrDest.get(i+1).getP2())
                        cost = cost + (PerMinuteCost/60.0 * 
                            (long) (travelDistance[s.travelDistanceIndex[i]][s.travelDistanceIndex[i+1]]/Speed[s.hourIndex[i]][s.originOrDest.get(i).getP1().getEndRegion()][s.originOrDest.get(i+1).getP1().getEndRegion()])
                                + PerMeterCost*travelDistance[s.travelDistanceIndex[i]][s.travelDistanceIndex[i+1]])/numPassengers;
                    else
                        cost = cost + (PerMinuteCost/60.0 * 
                            (long) (travelDistance[s.travelDistanceIndex[i]][s.travelDistanceIndex[i+1]]/Speed[s.hourIndex[i]][s.originOrDest.get(i).getP1().getEndRegion()][s.originOrDest.get(i+1).getP1().getStartRegion()])
                                + PerMeterCost*travelDistance[s.travelDistanceIndex[i]][s.travelDistanceIndex[i+1]])/numPassengers;
                } else {
                    if (s.originOrDest.get(i+1).getP2())
                        cost = cost + (PerMinuteCost/60.0 * 
                            (long) (travelDistance[s.travelDistanceIndex[i]][s.travelDistanceIndex[i+1]]/Speed[s.hourIndex[i]][s.originOrDest.get(i).getP1().getStartRegion()][s.originOrDest.get(i+1).getP1().getEndRegion()])
                                + PerMeterCost*travelDistance[s.travelDistanceIndex[i]][s.travelDistanceIndex[i+1]])/numPassengers;
                    else
                        cost = cost + (PerMinuteCost/60.0 * 
                            (long) (travelDistance[s.travelDistanceIndex[i]][s.travelDistanceIndex[i+1]]/Speed[s.hourIndex[i]][s.originOrDest.get(i).getP1().getStartRegion()][s.originOrDest.get(i+1).getP1().getStartRegion()])
                                + PerMeterCost*travelDistance[s.travelDistanceIndex[i]][s.travelDistanceIndex[i+1]])/numPassengers;
                }
            }
            cost = cost + BaseFare;
            if (passengerStartIndex.get(passenger) == 0)
                timeArrivedAtPassengerOrigin = s.departureTimeOfDriver + accumalativeDuration[0];
            else
                timeArrivedAtPassengerOrigin = s.departureTimeOfDriver + accumalativeDuration[0] + accumalativeDuration[passengerStartIndex.get(passenger)];
            
            timeArrivedAtPassengerOrigin = Math.min((long)(timeArrivedAtPassengerOrigin / 3600.0), 23) - SimulationParameters.startHour;
            cost = (1-takeRate) * SurgePriceFactor[(int) timeArrivedAtPassengerOrigin][passenger.getStartRegion()][passenger.getEndRegion()] * discountRate
                                  * cost + AverageTip.get(roundDistanceForTip(distance));
            revenue += cost;
        }
        match.revenue = revenue;
    }
    
    public void calculateCost(Match match, Driver driver, int driverIndex) {
        SFP s = match.sfp;
        int lastIndex = s.originOrDest.size()-1;
        double accumalativeDistance = travelDistance[driverIndex][s.travelDistanceIndex[0]];
        for (int i = 0; i < lastIndex; i++) {
            // from s.travelDistanceIndex[j] to s.travelDistanceIndex[j+1]
            accumalativeDistance = accumalativeDistance + travelDistance[s.travelDistanceIndex[i]][s.travelDistanceIndex[i+1]];
        }
        accumalativeDistance = accumalativeDistance + travelDistance[s.travelDistanceIndex[lastIndex]][driverIndex];
        match.cost = driver.getCostPerMeter() * accumalativeDistance;
    }
    
    public Pair<Integer,Integer> setMatchIDs(List<Driver> drivers, int start) {
        int count = 0;
        int driverWithMatch = 0;
        matchID = start;
        for (Driver d : drivers) {
            if (!d.getMatches().isEmpty()) {
                driverWithMatch++;
                count = count + d.getMatches().size();
                for (Match m : d.getMatches()) {
                    m.id = matchID;
                    matchID++;
                }
            }
        }
        return new Pair(count, driverWithMatch);
    }
    
    public void setMatchIDs(List<Driver> drivers) {
        for (Driver d : drivers) {
            for (Match m : d.getMatches()) {
                m.id = matchID;
                matchID++;
            }
        }
    }
    
    public void setMatchIDandDisplayDriverCounts(List<Driver> drivers) {
        int driverWithMatch = 0;
        for (Driver d : drivers) {
            if (!d.getMatches().isEmpty()) {
                driverWithMatch++;
                for (Match m : d.getMatches()) {
                    m.id = matchID;
                    matchID++;
                }
            }
        }
        System.out.println("Number of driverw with at least one match: " + driverWithMatch);
    }
    
    public int countCurrentMatches(List<Driver> drivers) {
        int count = 0;
        largestMatchSize = 1;
        for (Driver driver : drivers) {
            if (!driver.getMatches().isEmpty()) {
                count = count + driver.getMatches().size();
                if (!driver.getIndexLevel().isEmpty()) {
                    if (driver.getMatches().get(driver.getIndexLevel().get(driver.getIndexLevel().size()-1)-1).sfp.passengers.size() > largestMatchSize)
                        largestMatchSize = driver.getMatches().get(driver.getIndexLevel().get(driver.getIndexLevel().size()-1)-1).sfp.passengers.size();
                }
            }
        }
        return count;
    }
    
    public int numDriversWithMatch(List<Driver> drivers) {
        int driverWithMatch = 0;
        for (Driver d : drivers) {
            if (!d.getMatches().isEmpty())
                driverWithMatch++;
        }
        return driverWithMatch;
    }
    
    public void setPassengerInNumMatches(List<Driver> drivers) {
        for (Driver d : drivers) {
            for (Match match : d.getMatches())
                for (Passenger passenger : match.sfp.passengers)
                    passenger.incrementNAssignments();
        }
    }
    
    public Pair<Integer,Integer> countTotalNumMatches(List<Driver> drivers) {
        int count = 0;
        int driverWithMatch = 0;
        for (Driver driver : drivers) {
            if (!driver.getMatches().isEmpty()) {
                driverWithMatch++;
                count = count + driver.getMatches().size();
            }
        }
        return new Pair(count, driverWithMatch);
    }
    
    // The haversine formula determines the great-circle distance between two points on a sphere given their longitudes and latitudes in km
    public double distance(double lat1, double lon1, double lat2, double lon2) {
        double a = 0.5 - Math.cos((lat2 - lat1) * Radian)/2 +  Math.cos(lat1 * Radian) * Math.cos(lat2 * Radian) * (1 - Math.cos((lon2 - lon1) * Radian))/2;
        return 12742 * Math.asin(Math.sqrt(a)); // 2 * R; R = 6371 km (mean radius of Earth)
    }
    
    // return ManhattanDistance in meters
    public double ManhattanDistance(double lat1, double lon1, double lat2, double lon2) {
        double hinge_lat = lat1;
        double hinge_lon = lon2;
        return (distance(lat1, lon1, hinge_lat, hinge_lon) + distance(hinge_lat, hinge_lon, lat2, lon2)) * 1000.0;
    }
    
    // return ManhattanDistance in meters
    public double ManhattanDistance(Location loc1, Location loc2) {
        double hinge_lat = loc1.getLatitude();
        double hinge_lon = loc2.getLongitude();
        return (distance(loc1.getLatitude(), loc1.getLongitude(), hinge_lat, hinge_lon) + distance(hinge_lat, hinge_lon, loc2.getLatitude(), loc2.getLongitude())) * 1000.0;
    }
    
    public double ManhattanDistanceWithRotation(double lat1, double lon1, double lat2, double lon2, double rotationDegree) {
        return 0;
    }
    
    public boolean verifyMatches(List<Driver> drivers, boolean checkDuplicate) {
        boolean valid = true;
        int driverIndex;
        SFP s;
        HashMap<Passenger,Integer> passengerStartIndex;     // this is to get the acumalative duration index
        HashMap<Passenger,Integer> passengerEndIndex;       //
        long duration;
        long timeArrivedAtPassengerDest;
        long[] accumalativeDuration;
        int lastIndex;
        int hourIndex;
        for (Driver d : drivers) {
            driverIndex = tripIDtoTravelDistanceIndex.get(d.getID());
            for (Match match : d.getMatches()) {
                s = match.sfp;
                // accumalative travel duration from first location to the last location in SFP
                // accumalativeDistance[0] is driver to first passenger's origin
                lastIndex = s.originOrDest.size()-1;
                accumalativeDuration = new long[s.originOrDest.size()];
                
                for (int i = 0; i < accumalativeDuration.length-1; i++) {
                    // from s.travelDistanceIndex[j] to s.travelDistanceIndex[j+1]
                    if (s.originOrDest.get(i).getP2()) { // this is the destination of the passenger in index j of SFP
                        if (s.originOrDest.get(i+1).getP2())
                            accumalativeDuration[i+1] = (long) (accumalativeDuration[i] + travelDistance[s.travelDistanceIndex[i]][s.travelDistanceIndex[i+1]] / 
                                        Speed[s.hourIndex[i]][s.originOrDest.get(i).getP1().getEndRegion()][s.originOrDest.get(i+1).getP1().getEndRegion()]);
                        else
                            accumalativeDuration[i+1] = (long) (accumalativeDuration[i] + travelDistance[s.travelDistanceIndex[i]][s.travelDistanceIndex[i+1]] / 
                                        Speed[s.hourIndex[i]][s.originOrDest.get(i).getP1().getEndRegion()][s.originOrDest.get(i+1).getP1().getStartRegion()]);
                    } else {    // this is the origin of the passenger in index j of SFP
                        if (s.originOrDest.get(i+1).getP2())
                            accumalativeDuration[i+1] = (long) (accumalativeDuration[i] + travelDistance[s.travelDistanceIndex[i]][s.travelDistanceIndex[i+1]] / 
                                        Speed[s.hourIndex[i]][s.originOrDest.get(i).getP1().getStartRegion()][s.originOrDest.get(i+1).getP1().getEndRegion()]);
                        else
                            accumalativeDuration[i+1] = (long) (accumalativeDuration[i] + travelDistance[s.travelDistanceIndex[i]][s.travelDistanceIndex[i+1]] / 
                                        Speed[s.hourIndex[i]][s.originOrDest.get(i).getP1().getStartRegion()][s.originOrDest.get(i+1).getP1().getStartRegion()]);
                    }
                }
                
                hourIndex = Math.min((int)(s.departureTimeOfDriver / 3600.0), 23) - SimulationParameters.startHour;
                accumalativeDuration[0] = (long) (travelDistance[driverIndex][s.travelDistanceIndex[0]] /
                                                        Speed[hourIndex][d.getStartRegion()][s.originOrDest.get(0).getP1().getStartRegion()]);    
                
                // driver max travel duration
                long lastDestToDriverDest = (long) (travelDistance[s.travelDistanceIndex[lastIndex]][driverIndex] / 
                                                                    Speed[s.hourIndex[lastIndex]][s.originOrDest.get(lastIndex).getP1().getEndRegion()][d.getEndRegion()]);
                duration = accumalativeDuration[0] + accumalativeDuration[lastIndex] + lastDestToDriverDest;
                
                if (duration == 0 || duration > d.getMaxTravelDuration() || s.departureTimeOfDriver + duration > d.getArrivalTime()) {
                    System.out.print("MatchID :"+match.id);
                    System.out.println(" [Either] Arrive time ("+(s.departureTimeOfDriver+duration)+") later than ArrivalTime = "+d.getArrivalTime()+" of Driver: "+d.getID() + " (driverIndex="+driverIndex+")");
                    System.out.println("[or] Travel duration ("+duration+") incorrect: 0 or longer than MaxTravelDuration="+d.getMaxTravelDuration());
                    System.out.println("Driver departure time: " + s.departureTimeOfDriver +", durationFromLastPassengerDestToDriverDest = "+ lastDestToDriverDest);
                    System.out.println("travelDistance[driverIndex][s.travelDistanceIndex[0]] = "+travelDistance[driverIndex][s.travelDistanceIndex[0]]+
                                            ", s.travelDistanceIndex[0]="+s.travelDistanceIndex[0]);
                    System.out.println("Speed[hourIndex][d.getStartRegion()][s.originOrDest.get(0).getP1().getStartRegion()] = "+Speed[hourIndex][d.getStartRegion()][s.originOrDest.get(0).getP1().getStartRegion()]);
                    System.out.println(Arrays.toString(accumalativeDuration));
                    int ind;
                    System.out.print("[[ ");
                    for (ind = 0; ind < s.originOrDest.size()-1; ind++)
                        System.out.print("Passenger "+ s.originOrDest.get(ind).getP1().getID() + "("+s.originOrDest.get(ind).getP2()+") -- ");
                    System.out.println("Passenger "+ s.originOrDest.get(ind).getP1().getID() + "("+s.originOrDest.get(ind).getP2()+") ]]");
                    valid = false;
                }
                
                passengerStartIndex = new HashMap<>();
                passengerEndIndex = new HashMap<>();
                for (int i = 0; i < s.originOrDest.size(); i++) {
                    if (s.originOrDest.get(i).getP2())
                        passengerEndIndex.put(s.originOrDest.get(i).getP1(), i);
                    else
                        passengerStartIndex.put(s.originOrDest.get(i).getP1(), i);
                }
                
                for (Passenger passenger : s.passengers) {
                    // calcualte travel duration for passenger
                    // the duration to reach passenger's dest - the duration to reach passenger's origin
                    if (passengerStartIndex.get(passenger) > 0)
                        duration = accumalativeDuration[passengerEndIndex.get(passenger)] - (accumalativeDuration[passengerStartIndex.get(passenger)]);
                    else
                        duration = accumalativeDuration[passengerEndIndex.get(passenger)];

                    // calculate arrival time at destination: arrival time at passenger's origin + duration from passenger's origin to their destination
                    if (passengerStartIndex.get(passenger) > 0)
                        timeArrivedAtPassengerDest = s.departureTimeOfDriver + accumalativeDuration[0] + accumalativeDuration[passengerStartIndex.get(passenger)] + duration;
                    else
                        timeArrivedAtPassengerDest = s.departureTimeOfDriver + accumalativeDuration[0] + duration;
                    if (timeArrivedAtPassengerDest > passenger.getArrivalTime() || duration == 0 || duration > passenger.getMaxTravelDuration()) {
                        valid = false;
                        System.out.print("MatchID :"+match.id + ", Driver Id: "+d.getID() + " (driverIndex="+driverIndex+")");
                        System.out.println(" [Either] Arrive time ("+ timeArrivedAtPassengerDest+ ") later than ArrivalTime="+passenger.getArrivalTime()+" of Passenger "+passenger.getID());
                        System.out.println("[Or] Travel duration ("+duration+") incorrect: 0 or longer than MaxTravelDuration="+passenger.getMaxTravelDuration()+" of Passenger: "+passenger.getID());
                        int ind;
                        System.out.print("[[ ");
                        for (ind = 0; ind < s.originOrDest.size()-1; ind++)
                            System.out.print("Passenger "+ s.originOrDest.get(ind).getP1().getID() + "("+s.originOrDest.get(ind).getP2()+") -- ");
                        System.out.println("Passenger "+ s.originOrDest.get(ind).getP1().getID() + "("+s.originOrDest.get(ind).getP2()+") ]]");
                        System.out.println(Arrays.toString(accumalativeDuration));
                        System.out.println("Passenger: " + passenger.toStringAll());
                        break;
                    }
                }
            }
        }
        
        if (checkDuplicate)
            if (isThereAnyDuplicateMatch(drivers)) {
                System.out.println("There are duplicate matches.");
                return false;
            }
        return valid;
    }
    
    public void potentialPairsFixedRadius(List<Driver> drivers, List<Passenger> passengers) {
        startTime = System.currentTimeMillis();
        int candidates = 0;
        for (Driver driver : drivers)
            for (Passenger passenger : passengers)
                if (testCandidateFixedRadius(driver, passenger))
                    candidates++;
        endTime = System.currentTimeMillis();
        System.out.println("Number of driver-passenger pairs: " + (drivers.size()*passengers.size()) + 
                            ". Number of candidates (using radius "+MinRadius+"): "+candidates +
                            ". Time it took: "+ (endTime-startTime) + " milliseconds.");
    }
    
    public void potentialPairsDriverDetour(List<Driver> drivers, List<Passenger> passengers) {
        startTime = System.currentTimeMillis();
        int candidates = 0;
        for (Driver driver : drivers)
            for (Passenger passenger : passengers)
                if (testCandidate(driver, passenger))
                    candidates++;
        endTime = System.currentTimeMillis();
        System.out.println("Number of driver-passenger pairs: " + (drivers.size()*passengers.size())+ ". Number of candidates (using driver's detour distance): "+candidates
                            +". Time it took: "+ (endTime-startTime) + " milliseconds.");
    }
    
    public boolean testCandidateFixedRadius(Driver driver, Passenger passenger) {
        return distance(driver.getStartLatitude(),driver.getStartLongitude(), passenger.getStartLatitude(),passenger.getStartLongitude()) +
                distance(passenger.getStartLatitude(),passenger.getStartLongitude(), passenger.getEndLatitude(),passenger.getEndLongitude()) +
                distance(passenger.getEndLatitude(),passenger.getEndLongitude(), driver.getEndLatitude(),driver.getEndLongitude()) <= MinRadius;
    }
    
    public boolean testCandidate(Driver driver, Passenger passenger) {
        double dist = (distance(driver.getStartLatitude(),driver.getStartLongitude(), passenger.getStartLatitude(),passenger.getStartLongitude()) + 
                        distance(passenger.getEndLatitude(),passenger.getEndLongitude(), driver.getEndLatitude(),driver.getEndLongitude())) * 1000 + 
                        passenger.getDistance(); // in meters
        // if driver's maximum travel distance is greater than the estimated travel distance for serving passegner times some constant coef (distanceCandidateConst),
        // then this driver and passenger are a candidate pair.
        return driver.getMaxTravelDuration() * Speed[currentHourIndex][driver.getStartRegion()][driver.getEndRegion()] >= distanceCandidateConst*dist;
    }
    
    public void BaseMatchSetup(List<Driver> drivers, List<Passenger> passengers, TimePeriod currentTimePeriod, int currentHourIndex) {
        setDistanceCandidateConst(currentTimePeriod);
        largestMatchSize = 0;
        this.currentHourIndex = currentHourIndex;
        BaseMatchSetup(drivers, passengers);
    }
    
    private void BaseMatchSetup(List<Driver> drivers, List<Passenger> passengers) {
        passengerSize = passengers.size();
        driverSize = drivers.size();
        travelDistance = new long[driverSize+2*passengerSize][driverSize+2*passengerSize];
        tripIDtoTravelDistanceIndex = new HashMap<>(drivers.size()+passengers.size());
        // always drivers before passengers for fixed order for indices.
        int index = 0;
        for (Driver d : drivers) {
            tripIDtoTravelDistanceIndex.put(d.getID(), index);
            index++;
        }
        for (Passenger p : passengers) {
            tripIDtoTravelDistanceIndex.put(p.getID(), index);
            index++;
        }
    }
    
    public void matchLoadedSetup(List<Driver> drivers, List<Passenger> passengers, int currentHourIndex) {
        this.currentHourIndex = currentHourIndex;
        passengerSize = passengers.size();
        driverSize = drivers.size();
    }
    
    public boolean isThereAnyDuplicateMatch(List<Driver> drivers) {
        Driver driver = null;
        int endIndex = 0;
        Set<Passenger> passengersInMatch = null;
        try {
            for (int n = 0; n < drivers.size(); n++) {
                driver = drivers.get(n);
                for (int i = 0; i < driver.getMatches().size(); i++) {
                    passengersInMatch = driver.getMatches().get(i).sfp.passengers;
                    endIndex = driver.getIndexLevel().get(passengersInMatch.size()-1);
                    for (int j = i+1; j < endIndex; j++) {
                        if (driver.getMatches().get(j).sfp.passengers.equals(passengersInMatch))
                            return true;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            if (driver != null && passengersInMatch != null)
                System.out.println("Current match: (" + driver.getID() + ", {"+passengersInMatch.toString()+"})");
            if (driver != null)
                System.out.println("driver.getMatches().size() = "+ driver.getMatches().size() + ", endIndex = " + endIndex +
                        ", driver.getIndexLevel().size() = "+driver.getIndexLevel().size());
        }
        return false;
    }
    
    public HashMap<Driver,List<Match>> getNegativeMatches(List<Driver> drivers) {
        HashMap<Driver,List<Match>> negativeMatches = new HashMap<>();
        for (Driver driver: drivers) {
            if (!driver.getMatches().isEmpty()) {
                List<Match> matches = new ArrayList<>(2);
                for (Match match : driver.getMatches()) {
                    if (match.profit < 0)
                        matches.add(match);
                }
                if (!matches.isEmpty())
                    negativeMatches.put(driver, matches);
            }
        }
        
        return negativeMatches;
    }
    
    public int getNumNegativeMatches(List<Driver> drivers) {
        int num = 0;
        for (Driver driver: drivers) {
            for (Match match : driver.getMatches()) {
                if (match.profit < 0)
                    num++;
            }
        }
        return num;
    }
    
    // IF Manhattan distance was used, which might under-estimate some distances,
    // then validate the solution using shortest paths and remove any match that is actually not feasible
    public void validateSolutionMD(HashMap<Driver, Match> solution) {
        System.out.println("Number of matches before validation: " + solution.size());
        //int driverIndex;
        SFP sfp;
        HashMap<Passenger,Integer> passengerStartIndex;     // this is to get the acumalative duration index
        HashMap<Passenger,Integer> passengerEndIndex;       //
        long accumalativeDuration;
        long[] travelDuration;
        int[] hourIndex;
        Driver d;
        long dist;
        long driverDeparture;
        long arrivedTime;
        int size;
        
        Entry<Driver, Match> tempMatch;
        Iterator<Entry<Driver, Match>> matchIter = solution.entrySet().iterator();
        boolean invalid;
        
        while(matchIter.hasNext()) {
            tempMatch = matchIter.next();
            d = tempMatch.getKey();
            sfp = tempMatch.getValue().sfp;
            size = sfp.originOrDest.size();
            // accumalative travel duration from first location to the last location in SFP
            // accumalativeDistance[0] is driver to first passenger's origin
            travelDuration = new long[size];
            accumalativeDuration = 0L;
            hourIndex = new int[size];
            
            dist = (long)(ho.getDistanceBetweenTwoLocs(d.getStartLongitude(), d.getStartLatitude(),
                                                       sfp.originOrDest.get(0).getP1().getStartLongitude(), sfp.originOrDest.get(0).getP1().getStartLatitude()));
            travelDuration[0] = (long) (dist / Speed[currentHourIndex][d.getStartRegion()][sfp.originOrDest.get(0).getP1().getStartRegion()]);
            
            driverDeparture = Math.max(d.getDepartureTime(), sfp.originOrDest.get(0).getP1().getDepartureTime() - travelDuration[0]);
            arrivedTime = driverDeparture + travelDuration[0];
            hourIndex[0] = Math.min((int)(arrivedTime / 3600.0), 23) - SimulationParameters.startHour;
            
            for (int i = 0; i < size-1; i++) {
                // from s.travelDistanceIndex[i] to s.travelDistanceIndex[i+1]
                if (sfp.originOrDest.get(i).getP2()) { // this is the destination of the passenger in index i of SFP
                    if (sfp.originOrDest.get(i+1).getP2()) {  // the destination of the passenger in index i+1 of SFP
                        dist = (long)(ho.getDistanceBetweenTwoLocs(sfp.originOrDest.get(i).getP1().getEndLongitude(), sfp.originOrDest.get(i).getP1().getEndLatitude(),
                                                    sfp.originOrDest.get(i+1).getP1().getEndLongitude(), sfp.originOrDest.get(i+1).getP1().getEndLatitude()));
                        travelDuration[i+1] = (long) (dist / Speed[hourIndex[i]][sfp.originOrDest.get(i).getP1().getEndRegion()][sfp.originOrDest.get(i+1).getP1().getEndRegion()]);
                    } else {    // the origin of the passenger in index i+1 of SFP
                        dist = (long)(ho.getDistanceBetweenTwoLocs(sfp.originOrDest.get(i).getP1().getEndLongitude(), sfp.originOrDest.get(i).getP1().getEndLatitude(),
                                                    sfp.originOrDest.get(i+1).getP1().getStartLongitude(), sfp.originOrDest.get(i+1).getP1().getStartLatitude()));
                        travelDuration[i+1] = (long) (dist / Speed[hourIndex[i]][sfp.originOrDest.get(i).getP1().getEndRegion()][sfp.originOrDest.get(i+1).getP1().getStartRegion()]);
                    }
                } else {    // this is the origin of the passenger in index i of SFP
                    if (sfp.originOrDest.get(i+1).getP2()) {    // the destination of the passenger in index i+1 of SFP
                        dist = (long)(ho.getDistanceBetweenTwoLocs(sfp.originOrDest.get(i).getP1().getStartLongitude(), sfp.originOrDest.get(i).getP1().getStartLatitude(),
                                                    sfp.originOrDest.get(i+1).getP1().getEndLongitude(), sfp.originOrDest.get(i+1).getP1().getEndLatitude()));
                        travelDuration[i+1] = (long) (dist / Speed[hourIndex[i]][sfp.originOrDest.get(i).getP1().getStartRegion()][sfp.originOrDest.get(i+1).getP1().getEndRegion()]);
                    } else {    // the destination of the passenger in index i+1 of SFP
                        dist = (long)(ho.getDistanceBetweenTwoLocs(sfp.originOrDest.get(i).getP1().getStartLongitude(), sfp.originOrDest.get(i).getP1().getStartLatitude(),
                                                    sfp.originOrDest.get(i+1).getP1().getStartLongitude(), sfp.originOrDest.get(i+1).getP1().getStartLatitude()));
                        travelDuration[i+1] = (long) (dist / Speed[hourIndex[i]][sfp.originOrDest.get(i).getP1().getStartRegion()][sfp.originOrDest.get(i+1).getP1().getStartRegion()]);
                    }
                }
                
                accumalativeDuration = accumalativeDuration + travelDuration[i+1];
                arrivedTime = driverDeparture + accumalativeDuration;     // time arrived at l_{j+1}
                
                if (!sfp.originOrDest.get(i+1).getP2()) { 
                    if (sfp.originOrDest.get(i+1).getP1().getDepartureTime() > arrivedTime) {   // there is waiting time if firstDepartureTime is used
                        //System.out.println("There is waiting time at Passegner origin: " + pathToBeTested.get(j+1).getP1().getID());
                        driverDeparture = sfp.originOrDest.get(i+1).getP1().getDepartureTime() - accumalativeDuration;
                        arrivedTime = sfp.originOrDest.get(i+1).getP1().getDepartureTime(); // the actual time left at l_{j+1}
                        /* total duration is not changed if driverDeparture time is set to the latest (the exact time arrived at Passenger's origin) since travel time not changed */
                        //accDuration = accDuration + (pathToBeTested.get(j+1).getP1().getDepartureTime() - arrivedTime);   // the total duration is increased
                    }
                }
                hourIndex[i+1] = Math.min((int)(arrivedTime / 3600.0), 23) - SimulationParameters.startHour;
            }
            
            // driver max travel duration
            long driverDur = accumalativeDuration + (long)(ho.getDistanceBetweenTwoLocs(sfp.originOrDest.get(size-1).getP1().getEndLongitude(),sfp.originOrDest.get(size-1).getP1().getEndLatitude(),d.getEndLongitude(), d.getEndLatitude())
                                                           /Speed[hourIndex[size-1]][sfp.originOrDest.get(size-1).getP1().getEndRegion()][d.getEndRegion()]);
            if (driverDur > d.getMaxTravelDuration() || driverDeparture + driverDur > d.getArrivalTime()) {
                //System.out.println("Match ("+tempMatch.getValue().id + ") is not valid due to Driver's constraint and is removed from the solution.");
                matchIter.remove();
                continue;
            }
            
            // driver is okay, now check each passenger
            passengerStartIndex = new HashMap<>();
            passengerEndIndex = new HashMap<>();
            for (int i = 0; i < size; i++) {
                if (sfp.originOrDest.get(i).getP2())
                    passengerEndIndex.put(sfp.originOrDest.get(i).getP1(), i+1);
                else
                    passengerStartIndex.put(sfp.originOrDest.get(i).getP1(), i+1);
            }
            long passengerDur;
            invalid = false;
            for (Passenger p : sfp.passengers) {
                // calcualte travel duration for passenger
                passengerDur = 0L;
                for (int i = passengerStartIndex.get(p); i < passengerEndIndex.get(p); i++)
                    passengerDur = passengerDur + travelDuration[i];
                if (passengerDur > p.getMaxTravelDuration()) {
                    //System.out.println("Match ("+tempMatch.getValue().id + ") is not valid due to Passenger's TravelDuration constraint and is removed from the solution.");
                    invalid = true;
                    break;
                }
                
                passengerDur = passengerDur + driverDeparture;
                for (int i = 0; i < passengerStartIndex.get(p); i++)
                    passengerDur = passengerDur + travelDuration[i];
                if (passengerDur > p.getArrivalTime()) {
                    //System.out.println("Match ("+tempMatch.getValue().id + ") is not valid due to Passenger's ArrivalTime constraint and is removed from the solution.");
                    invalid = true;
                    break;
                }
            }
            if (invalid)
                matchIter.remove();
        }
        System.out.println("Number of matches after validation: " + solution.size());
    }
    
    public void verifySolution(List<Pair<Driver, Match>> solution) {
        Set<Driver> driversInSln = new HashSet<>();
        Set<Passenger> passengersInSln = new HashSet<>();
        System.out.println("solution size = " + solution.size());
        for (Pair<Driver, Match> match : solution) {
            if (driversInSln.contains(match.getP1())) {
                System.out.println("Incorrect solution - multiple drivers.");
            } else {
                driversInSln.add(match.getP1());
            }
            for (Passenger p : match.getP2().sfp.passengers) {
                if (passengersInSln.contains(p)) {
                    System.out.println("Incorrect solution - multiple passengers.");
                } else {
                    passengersInSln.add(p);
                }
            }
        }
    }
    
    public void verifySolution(HashMap<Driver, Match> solution) {
        Set<Passenger> passengersInSln = new HashSet<>();
        System.out.println("solution size = " + solution.size());
        for (Map.Entry<Driver, Match> entry : solution.entrySet()) {
            for (Passenger p : entry.getValue().sfp.passengers) {
                if (passengersInSln.contains(p)) {
                    System.out.println("Incorrect solution - multiple passengers.");
                } else {
                    passengersInSln.add(p);
                }
            }
        }
    }
    
    public int profitOfSolution(List<Pair<Driver, Match>> solution) {
        if (solution == null)
            return Integer.MIN_VALUE;
        
        int profit = 0;
        for (int i = 0; i < solution.size(); i++)
            profit = profit + solution.get(i).getP2().profit;
        return profit;
    }
    
    public int profitOfSolution(Solution solution) {
        return profitOfSolution(solution.matches);
    }
    
    public int profitOfSolution(HashMap<Driver, Match> solution) {
        if (solution == null)
            return Integer.MIN_VALUE;
        int profit = 0;
        for (Match entry : solution.values())
            profit = profit + entry.profit;
        return profit;
    }
    
    public void displayData() {
        System.out.format("size of tripIDtoTravelDistanceIndex = %d%n", tripIDtoTravelDistanceIndex.size());
        for (Map.Entry<Integer, Integer> entry : tripIDtoTravelDistanceIndex.entrySet())
            System.out.print("("+entry.getKey() +": "+ entry.getValue()+") ");
        
        System.out.println("\ntravelDistance:");
        for (int i = 0; i < travelDistance.length; i++) {
            for (int j = 0; j < travelDistance[i].length; j++)
                System.out.format("[%d][%d] = %d ", i,j, travelDistance[i][j]);
            System.out.println("");
        }
    }
    
    public void sortDriversBasedOnMatches(List<Driver> drivers, int sort) {
        switch (sort) {
        // sort based on the number of matches
        case 0:
            drivers.sort(new DriverMatchesMaxComparator());
            break;
        // sort based on the capacity
        case 1:
            drivers.sort(new DriverCapacityMaxComparator());
            break;
        // sort based on both matches and capacity
        case 2:
            drivers.sort(new DriverMatchesCapacityMaxComparator());
            break;
        default:
            break;
        }
    }

    public void setMinRadius(double radius) {
        MinRadius = radius;
    }

    public int getLargestMatchSize() {
        return largestMatchSize;
    }
    
    ///////// privates  /////////
    private void setDistanceCandidateConst(TimePeriod currentTimePeriod) {
        if (SimulationParameters.candidateTest != 1)
            return;
        
        if (SimulationParameters.problemVariant == 1) {
            switch(currentTimePeriod) {
                case Initial:
                case Evening:
                    distanceCandidateConst = 0.75 + MinRadius;
                    break;
                case MorningPeak:
                case AfternoonPeak:
                    distanceCandidateConst = 1.0 + MinRadius;
                    break;
                default:
                    distanceCandidateConst = 0.5 + MinRadius;
                    break;
            }
        } else {
            switch(currentTimePeriod) {
                case Initial:
                case Evening:
                    distanceCandidateConst = 0.5 + MinRadius;
                    break;
                case MorningPeak:
                case AfternoonPeak:
                    distanceCandidateConst = 0.75 + MinRadius;
                    break;
                default:
                    distanceCandidateConst = 0.25 + MinRadius;
                    break;
            }
        }
    }
    
    private long constructBaseMatchesMDPreprocessingThreads(List<Passenger> passengers) {
        System.out.println("constructBaseMatchesMDPreprocessingThreads is called with drivers = " + (driverSize) + " and passengers = " + (passengerSize));
        startTime = System.currentTimeMillis();
        executor = Executors.newFixedThreadPool(SimulationParameters.nThreads);

        for (Passenger p: passengers) {
            executor.submit(() -> {
                    int passengerIndex = tripIDtoTravelDistanceIndex.get(p.getID());
                    // from passenger's origin to passenger's destination
                    travelDistance[passengerIndex][passengerIndex+passengerSize] = (long) ManhattanDistance(p.getStartLatitude(), p.getStartLongitude(),
                                                                                                            p.getEndLatitude(), p.getEndLongitude());
            });
        }
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.out.println(e.toString());
        }
        endTime = System.currentTimeMillis();
        return (endTime - startTime);
    }
    
    private long constructBaseMatchesPreprocessingThreads(List<Passenger> passengers) {
        System.out.println("constructBaseMatchesPreprocessingThreads is called with drivers = " + (driverSize) + " and passengers = " + (passengerSize));
        startTime = System.currentTimeMillis();
        executor = Executors.newFixedThreadPool(SimulationParameters.nThreads);

        for (Passenger p: passengers) {
            executor.submit(() -> {
                    int passengerIndex = tripIDtoTravelDistanceIndex.get(p.getID());
                    // from passenger's origin to passenger's destination
                    travelDistance[passengerIndex][passengerIndex+passengerSize] = (long) p.getDistance();
            });
        }
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.out.println(e.toString());
        }
        endTime = System.currentTimeMillis();
        return (endTime - startTime);
    }
    
    private long constructBaseMatchesMDPreprocessing(List<Passenger> passengers) {
        System.out.println("constructBaseMatchesMDPreprocessing is called with drivers = " + (driverSize) + " and passengers = " + (passengerSize));
        startTime = System.currentTimeMillis();
        int passengerIndex;
        for (Passenger p: passengers) {
            passengerIndex = tripIDtoTravelDistanceIndex.get(p.getID());
            // from passenger's origin to passenger's destination
            travelDistance[passengerIndex][passengerIndex+passengerSize] = (long) ManhattanDistance(p.getStartLatitude(), p.getStartLongitude(),
                                                                                                            p.getEndLatitude(), p.getEndLongitude());
        }
        endTime = System.currentTimeMillis();
        return (endTime - startTime);
    }
    
    private long constructBaseMatchesPreprocessing(List<Passenger> passengers) {
        System.out.println("constructBaseMatchesPreprocessing is called with drivers = " + (driverSize) + " and passengers = " + (passengerSize));
        startTime = System.currentTimeMillis();
        int passengerIndex;
        for (Passenger p: passengers) {
            passengerIndex = tripIDtoTravelDistanceIndex.get(p.getID());
            // from passenger's origin to passenger's destination
            travelDistance[passengerIndex][passengerIndex+passengerSize] = (long) p.getDistance();
        }
        endTime = System.currentTimeMillis();
        return (endTime - startTime);
    }
    
    private int roundDistanceForTip(double distanceInMeter) {
        long temp = Math.round(distanceInMeter/1000/1.609344);
        if (temp < 2)
            return 1;
        if (temp > 35)
            return 35;
        return (int)temp;
    }
    
    public void changeToNonNegativeWeight(Graph graph) {       
        int[] potential = BellmanFordSpecialized(graph);
        for (MatchEdge edge : graph.edgeSet) {
            edge.reducedCost = edge.cost + potential[edge.tail] - potential[edge.head];
            edge.cost = edge.cost + potential[edge.tail] - potential[edge.head];
        }
    }
    
    // Only three iterations are required, and detecting a negative-weight cycle is not needed.
    public int[] BellmanFordSpecialized(Graph graph) { 
        //System.out.println("BellmanFordSpecialized() is called....");
        int[] distanceFromRoot = new int[graph.outEdges.size()];
        for (Integer vertex: graph.outEdges.keySet())
            distanceFromRoot[vertex] = Integer.MAX_VALUE;
        distanceFromRoot[graph.sourceVertex] = 0;
        
        int alt;
        for (int i = 0; i < 3; i++) {
            for (MatchEdge edge : graph.edgeSet) {
                if (distanceFromRoot[edge.tail] != Integer.MAX_VALUE) {
                    alt = distanceFromRoot[edge.tail] + edge.cost;
                    if (alt < distanceFromRoot[edge.head])
                        distanceFromRoot[edge.head] = alt;
                }
            }
        }

        return distanceFromRoot;
    }
    
    public Pair<Integer[], List<MatchEdge>> DijkstraMinHeap(Graph graph) {
        //System.out.println("DijkstraMinHeap() is called....");
        Integer[] distanceFromRoot = new Integer[graph.outEdges.size()];
        MatchEdge[] predecessor = new MatchEdge[graph.outEdges.size()];
        boolean[] visitedVertex = new boolean[graph.outEdges.size()];
        PriorityQueue<Pair<Integer, Integer>> toBeVisited = new PriorityQueue<>(new DistComparator());
        
        for (Integer vertex : graph.outEdges.keySet()) {
            distanceFromRoot[vertex] = Integer.MAX_VALUE;
        }
        distanceFromRoot[graph.sourceVertex] = 0;
        toBeVisited.add(new Pair(graph.sourceVertex, 0));
        
        Pair<Integer, Integer> currentVertex;
        int alt;
        while (!toBeVisited.isEmpty()) {
            currentVertex = toBeVisited.poll();
            //toBeVisited.remove(currentVertex);
            visitedVertex[currentVertex.getP1()] = true;
            
            for (MatchEdge edge : graph.outEdges.get(currentVertex.getP1())) {
                //alt = distanceFromRoot[currentVertex.getP1()] + edge.reducedCost;
                alt = currentVertex.getP2() + edge.reducedCost;
                if (alt < distanceFromRoot[edge.head]) {
                    distanceFromRoot[edge.head] = alt;
                    predecessor[edge.head] = edge;
                    
                    if (!visitedVertex[edge.head])
                        toBeVisited.add(new Pair(edge.head, distanceFromRoot[edge.head]));
                }
            }
        }
        
        List<MatchEdge> st_Path = null;
        MatchEdge target = predecessor[graph.sinkVertex];
        if (target != null || Objects.equals(target, graph.sourceVertex)) {
            st_Path = new ArrayList<>();
            //st_Path.add(graph.sinkVertex);
            while (target != null) {
                //System.out.println("Current target vertex: " + target);
                st_Path.add(target);
                target = predecessor[target.tail];
                /*if (Objects.equals(target, graph.sourceVertex)) {
                    st_Path.add(graph.sourceVertex);
                    break;
                }*/
            }
        }
        
        return new Pair(distanceFromRoot, st_Path);
    }
    
    public Triple<Integer[], List<MatchEdge>, Set<Integer>> DijkstraFibonacciHeapEarlyStop(Graph graph) {
        //System.out.println("DijkstraMinHeap() is called....");
        Integer[] distanceFromRoot = new Integer[graph.outEdges.size()];
        MatchEdge[] predecessor = new MatchEdge[graph.outEdges.size()];
        boolean[] visitedVertex = new boolean[graph.outEdges.size()];
        Set<Integer> permanent = new HashSet<>();
		
        FibonacciHeap toBeVisited = new FibonacciHeap();
        // <data, key>
        toBeVisited.insert(0, graph.sourceVertex);
        
        for (Integer vertex : graph.outEdges.keySet()) {
            distanceFromRoot[vertex] = Integer.MAX_VALUE;
        }
        distanceFromRoot[graph.sourceVertex] = 0;
        
        Integer currentVertex;
        int alt;
        while (!toBeVisited.isEmpty()) {
            currentVertex = (Integer) toBeVisited.removeMin();
            visitedVertex[currentVertex] = true;
            
            for (MatchEdge edge : graph.outEdges.get(currentVertex)) {
                alt = distanceFromRoot[currentVertex] + edge.reducedCost;
                if (alt < distanceFromRoot[edge.head]) {
                    distanceFromRoot[edge.head] = alt;
                    predecessor[edge.head] = edge;
                    
                    if (!visitedVertex[edge.head])
                        toBeVisited.insert(edge.head, distanceFromRoot[edge.head]);
                }
            }
            permanent.add(currentVertex);
            if (Objects.equals(currentVertex, graph.sinkVertex))
                break;
        }
        
        List<MatchEdge> st_Path = null;
        MatchEdge target = predecessor[graph.sinkVertex];
        if (target != null || Objects.equals(target, graph.sourceVertex)) {
            st_Path = new ArrayList<>();
            //st_Path.add(graph.sinkVertex);
            while (target != null) {
                //System.out.println("Current target vertex: " + target);
                st_Path.add(target);
                target = predecessor[target.tail];
                /*if (Objects.equals(target, graph.sourceVertex)) {
                    st_Path.add(graph.sourceVertex);
                    break;
                }*/
            }
        }
        
        return new Triple(distanceFromRoot, st_Path, permanent);
    }
    
    public Pair<Integer[], List<MatchEdge>> DijkstraFibonacciHeap(Graph graph) {
        //System.out.println("DijkstraMinHeap() is called....");
        Integer[] distanceFromRoot = new Integer[graph.outEdges.size()];
        MatchEdge[] predecessor = new MatchEdge[graph.outEdges.size()];
        boolean[] visitedVertex = new boolean[graph.outEdges.size()];
        FibonacciHeap toBeVisited = new FibonacciHeap();
        // <data, key>
        toBeVisited.insert(0, graph.sourceVertex);
        
        for (Integer vertex : graph.outEdges.keySet()) {
            distanceFromRoot[vertex] = Integer.MAX_VALUE;
        }
        distanceFromRoot[graph.sourceVertex] = 0;
        
        //Node currentVertex;
        Integer currentVertex;
        int alt;
        while (!toBeVisited.isEmpty()) {
            currentVertex = (Integer) toBeVisited.removeMin();
            //toBeVisited.removeMin();
            visitedVertex[currentVertex] = true;
            
            for (MatchEdge edge : graph.outEdges.get(currentVertex)) {
                alt = distanceFromRoot[currentVertex] + edge.reducedCost;
                if (alt < distanceFromRoot[edge.head]) {
                    distanceFromRoot[edge.head] = alt;
                    predecessor[edge.head] = edge;
                    
                    if (!visitedVertex[edge.head])
                        toBeVisited.insert(edge.head, distanceFromRoot[edge.head]);
                }
            }
        }
        
        List<MatchEdge> st_Path = null;
        MatchEdge target = predecessor[graph.sinkVertex];
        if (target != null || Objects.equals(target, graph.sourceVertex)) {
            st_Path = new ArrayList<>();
            //st_Path.add(graph.sinkVertex);
            while (target != null) {
                //System.out.println("Current target vertex: " + target);
                st_Path.add(target);
                target = predecessor[target.tail];
                /*if (Objects.equals(target, graph.sourceVertex)) {
                    st_Path.add(graph.sourceVertex);
                    break;
                }*/
            }
        }
        
        return new Pair(distanceFromRoot, st_Path);
    }
    
    public class DistComparator implements Comparator<Pair<Integer,Integer>> {
        
        @Override
        public int compare(Pair<Integer, Integer> e1, Pair<Integer, Integer> e2) {
            if (e1.getP2() > e2.getP2())
                return 1;
            else if (e1.getP2() < e2.getP2())
                return -1;
            return 0;
        }
    }
    
    // Sort the drivers in descending order of the number of matches for each driver 
    public class DriverMatchesMaxComparator implements Comparator<Driver> {
        public DriverMatchesMaxComparator() {}

        @Override
        public int compare(Driver d1, Driver d2) {
            if (d1.getMatches().size() < d2.getMatches().size())
                return 1;
            else if (d1.getMatches().size() > d2.getMatches().size())
                return -1;
            return 0;
        }
    }
    
    // Sort the drivers in descending order of the capacity and matches for each driver
    public class DriverMatchesCapacityMaxComparator implements Comparator<Driver> {
        public DriverMatchesCapacityMaxComparator() {}

        @Override
        public int compare(Driver d1, Driver d2) {
            if (d1.getCapacity() > d2.getCapacity()) {
                if (d1.getMatches().size() < d2.getMatches().size()) {
                    if (d1.getMatches().isEmpty())
                        return 1;
                    else if (2*(d1.getCapacity() - d2.getCapacity()) >= (double) (d2.getMatches().size() / d1.getMatches().size()))
                        return -1;
                    else if (2*(d1.getCapacity() - d2.getCapacity()) < (double) (d2.getMatches().size() / d1.getMatches().size()))
                        return 1;
                }
                return -1;
            } else if (d1.getCapacity() < d2.getCapacity()) {
                if (d2.getMatches().size() < d1.getMatches().size()) {
                    if (d2.getMatches().isEmpty())
                        return -1;
                    else if (2*(d1.getCapacity() - d2.getCapacity()) <= (double) (d2.getMatches().size() / d1.getMatches().size()))
                        return 1;
                    else if (2*(d1.getCapacity() - d2.getCapacity()) > (double) (d2.getMatches().size() / d1.getMatches().size()))
                        return -1;
                }
                return 1;
            } else {
                if (d1.getMatches().size() < d2.getMatches().size())
                    return 1;
                else if (d1.getMatches().size() > d2.getMatches().size())
                    return -1;
                else
                    return 0;
            }
        }
    }
    
    // Sort the drivers in descending order of the capacity for each driver
    public class DriverCapacityMaxComparator implements Comparator<Driver> {
        public DriverCapacityMaxComparator() {}

        @Override
        public int compare(Driver d1, Driver d2) {
            if (d1.getCapacity() < d2.getCapacity())
                return 1;
            else if (d1.getCapacity() > d2.getCapacity())
                return -1;
            return 0; 
        }
    }
    
    // in ascending order of the number of matches each passenger belongs to
    public class PassengerMatchesMinComparator implements Comparator<Match> {
        public PassengerMatchesMinComparator() {}

        @Override
        public int compare(Match a1, Match a2) {
            Passenger p1 = a1.sfp.passengers.iterator().next();
            Passenger p2 = a2.sfp.passengers.iterator().next();
            if (p1.getNAssignments() > p2.getNAssignments())
                return 1;
            else if (p1.getNAssignments() < p2.getNAssignments())
                return -1;
            return 0;
        }
    }
}
