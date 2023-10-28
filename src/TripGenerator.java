package simulation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

public class TripGenerator {
    private final HopperOperation ho;
    private final List<Region> regions;
    private final int[][][] nPassengersGenerate;
    private final double[][][] Speed;
    private final int IntervalDurationInSec;        // IntervalDurationInSec duration in seconds
    private int currentInterval;
    private int currentHourIndex;
    private TimePeriod currentTimePeriod = null;
    private final int[] testOrigins = new int[]{4,7,19,24};
    private final double minDistInMeters = 800;
    
    private final double passengerArrivalTimeCoefficient = 3d;
    private final double passengerMaxTravelTimeCoefficient = 2d;
    private final double driverArrivalTimeCoefficient = 1.25;
    private final double driverDetourFactor = 1.4;
    private final double driverDetourFactorPeakHour = 1.2;
    private final int driverMaxDetourInSecond = 45*60;
    private final int driverMaxDetourInSecondPeakHour = 30*60;
    private final double smallSedanCostPerKM = 0.1251 / Utility.MileToKM;   // in dollars
    private final double mediumSedanCostPerKM = 0.1437 / Utility.MileToKM;
    private final double mediumSUVCostPerKM = 0.1889 / Utility.MileToKM;
    private int nDrivers = 0;
    
    private int ID = 0;
    private int capacity = 0;
    private Location startLoc;
    private Location endLoc;
    private long departureTime = 0L;
    private long arrivalTime = 0L;
    private double travelTimeInSecond = 0d;
    private int detourTime = 0;
    private long maxTravelDuration = 0L;
    private double sum = 0d;
    private double[] proportion;
    private TreeMap<Double, Integer> destRegionProbability;     // <probability, indexOfRegion>
    private int sumPassengersOrigin = 0;
    private double cost = 0d;
    private double distance = 0d;
    
    //private final Location Airport_OHare = new Location(-87.9033949054243, 41.97863979251512);
    private final Location Airport_Midway = new Location(-87.74175010393411,41.78850894013623);

    public TripGenerator(List<Region> regions, HopperOperation ho, int[][][] nPassengersGenerate,  double[][][] speed) {
        this.ho = ho;
        this.regions = regions;
        this.nPassengersGenerate = nPassengersGenerate;
        this.Speed = speed;
        IntervalDurationInSec = SimulationParameters.intervalInMinute*60;
    }
    
    public List<Passenger> generatePassengers(int currentTimeInSec) {
        List<Passenger> passengers = new ArrayList<>(nPassengersGenerate.length * nPassengersGenerate.length);
        final int numRetries = 12;
        int retry;
        boolean valid = true;
        for (int j = 0; j < nPassengersGenerate[currentInterval].length; j++) { // from region j
            for (int k = 0; k < nPassengersGenerate[currentInterval][j].length; k++) {  // to region k
                // Generate passenger trips
                for (int n = 0; n < nPassengersGenerate[currentInterval][j][k]; n++) {
                    if (regions.get(j).getID() == 20) {    // Midway airport region
                        if (Utility.random.nextDouble() < 0.5)
                            startLoc = regions.get(j).generateRandomLocation();
                        else
                            startLoc = Airport_Midway;      // Midway airport as origin then dest should not be the same airport
                    } else
                        startLoc = regions.get(j).generateRandomLocation();
                    
                    if (regions.get(k).getID() == 20) {    // Midway airport region
                        if (startLoc.equals(Airport_Midway))
                            endLoc = regions.get(k).generateRandomLocation();
                        else if (Utility.random.nextDouble() < 0.5)
                            endLoc = regions.get(k).generateRandomLocation();
                        else
                            endLoc = Airport_Midway;
                    } else
                        endLoc = regions.get(k).generateRandomLocation();
                    
                    distance = ho.getDistanceBetweenTwoLocs(startLoc, endLoc);
                    if (distance < minDistInMeters) {
                        valid = false;
                        for (retry = 0; retry < numRetries; retry++) {
                            startLoc = regions.get(j).generateRandomLocation();
                            endLoc = regions.get(k).generateRandomLocation();
                            distance = ho.getDistanceBetweenTwoLocs(startLoc, endLoc);
                            if (distance >= minDistInMeters) {
                                valid = true;
                                break;
                            }
                        }
                    }
                    if (!valid) {
                        valid = true;
                        continue;
                    }
                    if (distance == 0) {
                        System.out.println("Passenger - Origin region: " + (j+1) + " Destination region: " + (k+1));
                        continue;
                    }
                    
                    departureTime = (long) (Utility.random.nextInt(IntervalDurationInSec) + currentTimeInSec);
                    travelTimeInSecond = distance / Speed[currentHourIndex][j][k];
                    if (travelTimeInSecond >= 2700) {
                        arrivalTime = (long) (departureTime + (passengerArrivalTimeCoefficient-1) * travelTimeInSecond);
                        maxTravelDuration = (long) ((passengerMaxTravelTimeCoefficient-0.5)*travelTimeInSecond);
                    } else if (travelTimeInSecond >= 1800) {
                        arrivalTime = (long) (departureTime + (passengerArrivalTimeCoefficient-0.5) * travelTimeInSecond);
                        maxTravelDuration = (long) ((passengerMaxTravelTimeCoefficient-0.25)*travelTimeInSecond);
                    } else {
                        arrivalTime = (long) (departureTime + passengerArrivalTimeCoefficient * travelTimeInSecond);
                        maxTravelDuration = (long) (passengerMaxTravelTimeCoefficient*travelTimeInSecond);
                    }
                    
                    passengers.add(new Passenger(ID, startLoc.getLongitude(),startLoc.getLatitude(), endLoc.getLongitude(),endLoc.getLatitude(), j, k,
                                    departureTime, arrivalTime, maxTravelDuration, distance));
                    ID++;
                }
            }
        }
        return passengers;
    }
    
    public List<Driver> generateDrivers(int currentTimeInSec, Runnable toRun) {
        List<Driver> drivers = new ArrayList<>(nPassengersGenerate.length);
        int destRegionIndex;
        final int numRetries = 12;
        int retry;
        boolean valid = true;
        for (int j = 0; j < nPassengersGenerate[currentInterval].length; j++) {
            setupDestRegionProbability(j);
            calculateNumbDriversGenerate(j);
            for (int n = 0; n < nDrivers; n++) {
                if (regions.get(j).getID() == 20) {    // Midway airport, as origin
                    if (Utility.random.nextDouble() < 0.5)
                        startLoc = regions.get(j).generateRandomLocation();
                    else
                        startLoc = Airport_Midway;
                } else
                    startLoc = regions.get(j).generateRandomLocation();
                destRegionIndex = destRegionProbability.higherEntry(Utility.random.nextDouble()).getValue();
                if (regions.get(destRegionIndex).getID() == 20) {    // Midway airport, as dest
                    if (startLoc.equals(Airport_Midway))
                        endLoc = regions.get(destRegionIndex).generateRandomLocation();
                    else if (Utility.random.nextDouble() < 0.5) 
                        endLoc = regions.get(destRegionIndex).generateRandomLocation();
                    else
                        endLoc = Airport_Midway;
                } else
                    endLoc = regions.get(destRegionIndex).generateRandomLocation();

                distance = ho.getDistanceBetweenTwoLocs(startLoc, endLoc);
                if (distance < minDistInMeters) {
                        valid = false;
                        for (retry = 0; retry < numRetries; retry++) {
                            startLoc = regions.get(j).generateRandomLocation();
                            endLoc = regions.get(destRegionIndex).generateRandomLocation();
                            distance = ho.getDistanceBetweenTwoLocs(startLoc, endLoc);
                            if (distance >= minDistInMeters) {
                                valid = true;
                                break;
                            }
                        }
                    }
                    if (!valid) {
                        valid = true;
                        continue;
                    }
                if (distance == 0) {
                    System.out.println("Driver - Origin region: " + (j+1) + " Destination region: " + (destRegionIndex+1));
                    continue;
                }
                
                travelTimeInSecond = distance / Speed[currentHourIndex][j][destRegionIndex];
                departureTime = (long) Math.floor(Utility.random.nextInt(IntervalDurationInSec) + currentTimeInSec);
                toRun.run();    // determine capacity, cost, detourTime
                
                if (travelTimeInSecond+detourTime >= 3600)
                    arrivalTime = (long) ((driverArrivalTimeCoefficient-0.25) *(travelTimeInSecond + detourTime)) + departureTime;
                else if (travelTimeInSecond+detourTime >= 2700)
                    arrivalTime = (long) ((driverArrivalTimeCoefficient-0.125) *(travelTimeInSecond + detourTime)) + departureTime;
                else
                    arrivalTime = (long) (driverArrivalTimeCoefficient *(travelTimeInSecond + detourTime)) + departureTime;
                
                drivers.add(new Driver(ID, capacity, startLoc.getLongitude(),startLoc.getLatitude(), endLoc.getLongitude(),endLoc.getLatitude(), j, destRegionIndex,
                                departureTime, arrivalTime, travelTimeInSecond+detourTime, cost / 1000.0, distance));
                ID++;
            }
        }
        
        return drivers;
    }
    
    public List<Driver> generateDrivers(int currentTimeInSec, int nPassengers) {
        List<Driver> drivers = new ArrayList<>(nPassengers);
        int destRegionIndex;
        final int numRetries = 12;
        int retry;
        boolean valid = true;
        for (int j = 0; j < nPassengersGenerate[currentInterval].length; j++) {
            setupDestRegionProbability(j);
            calculateNumbDriversGenerate(j);
            for (int n = 0; n < nDrivers; n++) {
                if (regions.get(j).getID() == 20) {    // Midway airport region
                    if (Utility.random.nextDouble() < 0.5)
                        startLoc = regions.get(j).generateRandomLocation();
                    else
                        startLoc = Airport_Midway;  // Midway airport as origin then dest should not be the same airport
                } else
                    startLoc = regions.get(j).generateRandomLocation();
                
                destRegionIndex = destRegionProbability.higherEntry(Utility.random.nextDouble()).getValue();
                if (regions.get(destRegionIndex).getID() == 20) {    // Midway airport
                    if (startLoc.equals(Airport_Midway))
                        endLoc = regions.get(destRegionIndex).generateRandomLocation();
                    else if (Utility.random.nextDouble() < 0.5) 
                        endLoc = regions.get(destRegionIndex).generateRandomLocation();
                    else
                        endLoc = Airport_Midway;
                } else
                    endLoc = regions.get(destRegionIndex).generateRandomLocation();

                distance = ho.getDistanceBetweenTwoLocs(startLoc, endLoc);
                if (distance < minDistInMeters) {
                        valid = false;
                        for (retry = 0; retry < numRetries; retry++) {
                            startLoc = regions.get(j).generateRandomLocation();
                            endLoc = regions.get(destRegionIndex).generateRandomLocation();
                            distance = ho.getDistanceBetweenTwoLocs(startLoc, endLoc);
                            if (distance >= minDistInMeters) {
                                valid = true;
                                break;
                            }
                        }
                    }
                    if (!valid) {
                        valid = true;
                        continue;
                    }
                if (distance == 0) {
                    System.out.println("Driver - Origin region: " + (j+1) + " Destination region: " + (destRegionIndex+1));
                    continue;
                }
                
                travelTimeInSecond = distance / Speed[currentHourIndex][j][destRegionIndex];
                departureTime = (long) Math.floor(Utility.random.nextInt(IntervalDurationInSec) + currentTimeInSec);
                //toRun.run();    // determine capacity, cost, detourTime
                determineCapCostDetour();
                
                if (travelTimeInSecond+detourTime >= 3600)
                    arrivalTime = (long) ((driverArrivalTimeCoefficient-0.25) *(travelTimeInSecond + detourTime)) + departureTime;
                else if (travelTimeInSecond+detourTime >= 2700)
                    arrivalTime = (long) ((driverArrivalTimeCoefficient-0.125) *(travelTimeInSecond + detourTime)) + departureTime;
                else
                    arrivalTime = (long) (driverArrivalTimeCoefficient *(travelTimeInSecond + detourTime)) + departureTime;
                
                //if (ID == 2084 || ID == 3967)
                  //  System.out.println("arrivalTime:" + arrivalTime +" --- travelTimeInSecond:"+travelTimeInSecond+" --- detourTime:"+detourTime+
                    //                        " --- departureTime:"+departureTime + " --- distance:"+distance+ " --- Speed:"+Speed[currentHourIndex][j][destRegionIndex]);
                
                drivers.add(new Driver(ID, capacity, startLoc.getLongitude(),startLoc.getLatitude(), endLoc.getLongitude(),endLoc.getLatitude(), j, destRegionIndex,
                                departureTime, arrivalTime, travelTimeInSecond+detourTime, cost / 1000.0, distance));
                ID++;
            }
        }
        
        return drivers;
    }
    
    public Pair<List<Driver>, List<Passenger>> genearteTrips(int currentTimeInSec, boolean testing) {
        List<Passenger> passengers;
        List<Driver> drivers;
        if (testing) {
            passengers = generatePassengersTest(currentTimeInSec);
            drivers = generateDriversTest(currentTimeInSec, passengers.size());
        } else {
            passengers = generatePassengers(currentTimeInSec);
            drivers = generateDrivers(currentTimeInSec, passengers.size());
        }
        return new Pair<>(drivers,passengers);
    }
    
    public Pair<List<Driver>, List<Passenger>> genearteDebugTrips(int currentTimeInSec) {
        List<Passenger> passengers = new ArrayList<>(4);
        List<Driver> drivers = new ArrayList<>(2);
        
        int i;
        for (i = 0; i < 2; i++) {
            startLoc = new Location(-87.709937, 41.958431);
            endLoc = Airport_Midway;
            distance = ho.getDistanceBetweenTwoLocs(startLoc, endLoc);
            travelTimeInSecond = distance / Speed[currentHourIndex][4][19];
            departureTime = (long) currentTimeInSec;
            
            if (i == 0) {
                capacity = 2;
                detourTime = (int) (driverDetourFactor*travelTimeInSecond);
            } else {
                capacity = 5;
                detourTime = (int) (4*driverDetourFactor*travelTimeInSecond);
            }

            if (travelTimeInSecond+detourTime >= 3600)
                arrivalTime = (long) ((driverArrivalTimeCoefficient-0.25) *(travelTimeInSecond + detourTime)) + departureTime;
            else if (travelTimeInSecond+detourTime >= 2700)
                arrivalTime = (long) ((driverArrivalTimeCoefficient-0.125) *(travelTimeInSecond + detourTime)) + departureTime;
            else
                arrivalTime = (long) (driverArrivalTimeCoefficient *(travelTimeInSecond + detourTime)) + departureTime;

            drivers.add(new Driver(i, capacity, startLoc.getLongitude(),startLoc.getLatitude(), endLoc.getLongitude(),endLoc.getLatitude(), 4, 19,
                                    departureTime, arrivalTime, travelTimeInSecond+detourTime, mediumSUVCostPerKM / 1000.0, distance));
            System.out.println("Driver ID: "+i+ ", travel time of OD = " + (long)travelTimeInSecond);
        }
        
        int[] passengerRegions = new int[]{4,15,8,14,7,4,16,19};
        for (int j = 0; j < passengerRegions.length/2; j++) {            
            switch (j) {
                case 0:
                    startLoc = new Location(-87.709937, 41.948431);
                    endLoc = regions.get(passengerRegions[2*j+1]).generateRandomLocation();
                    departureTime = (long) currentTimeInSec;
                    distance = ho.getDistanceBetweenTwoLocs(startLoc, endLoc);
                    travelTimeInSecond = distance / Speed[currentHourIndex][passengerRegions[2*j]][passengerRegions[2*j+1]];
                    maxTravelDuration = (long) (7 * travelTimeInSecond);
                    break;
                case 1:
                    startLoc = new Location(-87.700578,41.921853);
                    endLoc = regions.get(passengerRegions[2*j+1]).generateRandomLocation();
                    departureTime = (long) currentTimeInSec+IntervalDurationInSec-1;
                    distance = ho.getDistanceBetweenTwoLocs(startLoc, endLoc);
                    travelTimeInSecond = distance / Speed[currentHourIndex][passengerRegions[2*j]][passengerRegions[2*j+1]];
                    maxTravelDuration = (long) (8 * travelTimeInSecond);
                    break;
                case 2:
                    startLoc = regions.get(passengerRegions[2*j]).generateRandomLocation();
                    endLoc = regions.get(passengerRegions[2*j+1]).generateRandomLocation();
                    departureTime = (long) currentTimeInSec + IntervalDurationInSec-1;
                    distance = ho.getDistanceBetweenTwoLocs(startLoc, endLoc);
                    travelTimeInSecond = distance / Speed[currentHourIndex][passengerRegions[2*j]][passengerRegions[2*j+1]];
                    maxTravelDuration = (long) (9 * travelTimeInSecond);
                    break;
                default:
                    startLoc = regions.get(passengerRegions[2*j]).generateRandomLocation();
                    endLoc = regions.get(passengerRegions[2*j+1]).generateRandomLocation();
                    departureTime = (long) currentTimeInSec + IntervalDurationInSec-1;
                    distance = ho.getDistanceBetweenTwoLocs(startLoc, endLoc);
                    travelTimeInSecond = distance / Speed[currentHourIndex][passengerRegions[2*j]][passengerRegions[2*j+1]];
                    maxTravelDuration = (long) (10 * travelTimeInSecond);
                    break;
            }
            arrivalTime = (long) (departureTime + maxTravelDuration);

            System.out.println("Passenger ID: "+i+ ", travel time of OD = " + (long)travelTimeInSecond);
            passengers.add(new Passenger(i, startLoc.getLongitude(),startLoc.getLatitude(), endLoc.getLongitude(),endLoc.getLatitude(), 
                            passengerRegions[2*j], passengerRegions[2*j+1], departureTime, arrivalTime, maxTravelDuration, distance));
            i++;
        }
        
        return new Pair<>(drivers,passengers);
    }
    
    private void calculateNumbDriversGenerate(int j) {
        sumPassengersOrigin = 0;
        for (int k = 0; k < nPassengersGenerate[currentInterval][j].length; k++)
                sumPassengersOrigin += nPassengersGenerate[currentInterval][j][k];
        
        switch(currentTimePeriod) {
            case MorningPeak:
            case AfternoonPeak:
                if (SimulationParameters.problemVariant == 2)
                    nDrivers = (int) Math.ceil(sumPassengersOrigin/4.0);
                else
                    nDrivers = (int) Math.ceil(0.9*sumPassengersOrigin);
                break;
            case MorningNoon:
                if (SimulationParameters.problemVariant == 2)
                    nDrivers = (int) Math.ceil(sumPassengersOrigin/2.0);
                else
                    nDrivers = (int) Math.ceil(1.2*sumPassengersOrigin);
                break;
            default:
                if (SimulationParameters.problemVariant == 2)
                    nDrivers = (int) Math.ceil(sumPassengersOrigin/3.0);
                else
                    nDrivers = (int) Math.ceil(1.0*sumPassengersOrigin);
                break;
        }
    }
    
    private void setupDestRegionProbability(int j) {
        proportion = new double[nPassengersGenerate[currentInterval][j].length];
        int start;
        for (start = 0; start < nPassengersGenerate[currentInterval][j].length; start++) {
            if (nPassengersGenerate[currentInterval][j][start] > 0) {
                proportion[start] = nPassengersGenerate[currentInterval][j][start];
                break;
            }
        }
        
        destRegionProbability = new TreeMap<>();
        int previousNonZeroIndex = start;
        for (int k = start+1; k < nPassengersGenerate[currentInterval][j].length; k++) {
            if (nPassengersGenerate[currentInterval][j][k] > 0) {
                proportion[k] = nPassengersGenerate[currentInterval][j][k] + proportion[previousNonZeroIndex];
                previousNonZeroIndex = k;
            }
        }
        sum = proportion[proportion.length-1];
        for (int k = 0; k < proportion.length; k++) {
            if (proportion[k] > 0)
                destRegionProbability.put(proportion[k]/sum, k);
        }
    }
    
    public void setCurrentTimeParameters(int currentInterval, int currentHourIndex, TimePeriod currentTimePeriod) {
        this.currentInterval = currentInterval;
        this.currentHourIndex = currentHourIndex;
        this.currentTimePeriod = currentTimePeriod;
    }
    
    private List<Passenger> generatePassengersTest(int currentTimeInSec) {
        List<Passenger> passengers = new ArrayList<>(nPassengersGenerate.length * nPassengersGenerate.length);
        final int numRetries = 12;
        int retry;
        boolean valid = true;
        for (int j = 0; j < testOrigins.length; j++) {  // from region j
            for (int k = 0; k < 25; k++) {
                // Generate passenger trips
                for (int n = 0; n < nPassengersGenerate[currentInterval][testOrigins[j]][k]; n++) {
                    if (regions.get(testOrigins[j]).getID() == 20) {    // Midway airport, as origin
                        if (Utility.random.nextDouble() < 0.5)
                            startLoc = regions.get(testOrigins[j]).generateRandomLocation();
                        else
                            startLoc = Airport_Midway;
                    } else
                        startLoc = regions.get(testOrigins[j]).generateRandomLocation();
                    
                    if (regions.get(k).getID() == 20) {    // Midway airport, as dest
                        if (Utility.random.nextDouble() < 0.5)
                            endLoc = regions.get(k).generateRandomLocation();
                        else
                            endLoc = Airport_Midway;
                    } else
                        endLoc = regions.get(k).generateRandomLocation();
                    
                    distance = ho.getDistanceBetweenTwoLocs(startLoc, endLoc);
                    if (distance < minDistInMeters) {
                        valid = false;
                        for (retry = 0; retry < numRetries; retry++) {
                            startLoc = regions.get(testOrigins[j]).generateRandomLocation();
                            endLoc = regions.get(k).generateRandomLocation();
                            distance = ho.getDistanceBetweenTwoLocs(startLoc, endLoc);
                            if (distance >= minDistInMeters) {
                                valid = true;
                                break;
                            }
                        }
                    }
                    if (!valid) {
                        valid = true;
                        continue;
                    }
                    if (distance == 0) {
                        System.out.println("Passenger - Origin region: " + (testOrigins[j]+1) + " Destination region: " + (k+1));
                        continue;
                    }
                    
                    departureTime = (long) (Utility.random.nextInt(IntervalDurationInSec) + currentTimeInSec);
                    travelTimeInSecond = distance / Speed[currentHourIndex][testOrigins[j]][k];
                    
                    if (travelTimeInSecond >= 2700) {
                        arrivalTime = (long) (departureTime + (passengerArrivalTimeCoefficient-1) * travelTimeInSecond);
                        maxTravelDuration = (long) ((passengerMaxTravelTimeCoefficient-0.5)*travelTimeInSecond);
                    } else if (travelTimeInSecond >= 1800) {
                        arrivalTime = (long) (departureTime + (passengerArrivalTimeCoefficient-0.5) * travelTimeInSecond);
                        maxTravelDuration = (long) ((passengerMaxTravelTimeCoefficient-0.25)*travelTimeInSecond);
                    } else {
                        arrivalTime = (long) (departureTime + passengerArrivalTimeCoefficient * travelTimeInSecond);
                        maxTravelDuration = (long) (passengerMaxTravelTimeCoefficient*travelTimeInSecond);
                    }
                    
                    passengers.add(new Passenger(ID, startLoc.getLongitude(),startLoc.getLatitude(), endLoc.getLongitude(),endLoc.getLatitude(), testOrigins[j], k,
                                    departureTime, arrivalTime, maxTravelDuration, distance));
                    /*if (ID == 8 || ID == 30) { // debug
                        System.out.println("PassengerID:"+ID+", distance="+distance+", speed="+Speed[currentHourIndex][testOrigins[j]][k]+ " -- "+
                                startLoc.getLongitude()+","+startLoc.getLatitude()+","+endLoc.getLongitude()+","+endLoc.getLatitude());
                        ho.displayAllPathsBetweenTwoLocs(startLoc.getLongitude(),startLoc.getLatitude(), endLoc.getLongitude(),endLoc.getLatitude());
                    }*/
                    ID++;
                }
            }
        }
        return passengers;
    }
    
    public List<Driver> generateDriversTest(int currentTimeInSec, int nPassengers) {
        List<Driver> drivers = new ArrayList<>(nPassengers);
        int destRegionIndex;
        final int numRetries = 12;
        int retry;
        boolean valid = true;
        for (int j = 0; j < testOrigins.length; j++) {
            setupDestRegionProbability(testOrigins[j]);
            calculateNumbDriversGenerate(testOrigins[j]);
            for (int n = 0; n < nDrivers; n++) {
                if (regions.get(testOrigins[j]).getID() == 20) {    // Midway airport, as origin
                    if (Utility.random.nextDouble() < 0.5)
                        startLoc = regions.get(testOrigins[j]).generateRandomLocation();
                    else
                        startLoc = Airport_Midway;
                } else
                    startLoc = regions.get(testOrigins[j]).generateRandomLocation();
                destRegionIndex = destRegionProbability.higherEntry(Utility.random.nextDouble()).getValue();
                if (regions.get(destRegionIndex).getID() == 20) {    // Midway airport, as dest
                    if (Utility.random.nextDouble() < 0.5) 
                        endLoc = regions.get(destRegionIndex).generateRandomLocation();
                    else
                        endLoc = Airport_Midway;
                } else
                    endLoc = regions.get(destRegionIndex).generateRandomLocation();

                distance = ho.getDistanceBetweenTwoLocs(startLoc, endLoc);
                if (distance < minDistInMeters) {
                    valid = false;
                    for (retry = 0; retry < numRetries; retry++) {
                        startLoc = regions.get(testOrigins[j]).generateRandomLocation();
                        endLoc = regions.get(destRegionIndex).generateRandomLocation();
                        distance = ho.getDistanceBetweenTwoLocs(startLoc, endLoc);
                        if (distance >= minDistInMeters) {
                            valid = true;
                            break;
                        }
                    }
                }
                if (!valid) {
                    valid = true;
                    continue;
                }
                if (distance == 0) {
                    System.out.println("Driver - Origin region: " + (testOrigins[j]+1) + " Destination region: " + (destRegionIndex+1));
                    continue;
                }
                
                travelTimeInSecond = distance / Speed[currentHourIndex][testOrigins[j]][destRegionIndex];
                departureTime = (long) Math.floor(Utility.random.nextInt(IntervalDurationInSec) + currentTimeInSec);
                //toRun.run();    // determine capacity and cost
                determineCapCostDetour();
                
                if (travelTimeInSecond+detourTime >= 3600)
                    arrivalTime = (long) ((driverArrivalTimeCoefficient-0.25) *(travelTimeInSecond + detourTime)) + departureTime;
                else if (travelTimeInSecond+detourTime >= 2700)
                    arrivalTime = (long) ((driverArrivalTimeCoefficient-0.125) *(travelTimeInSecond + detourTime)) + departureTime;
                else
                    arrivalTime = (long) (driverArrivalTimeCoefficient *(travelTimeInSecond + detourTime)) + departureTime;
                
                drivers.add(new Driver(ID, capacity, startLoc.getLongitude(),startLoc.getLatitude(), endLoc.getLongitude(),endLoc.getLatitude(), testOrigins[j], destRegionIndex,
                                departureTime, arrivalTime, travelTimeInSecond+detourTime, cost/1000.0, distance));
                ID++;
            }
        }
        
        return drivers;
    }
    
    private void determineCapCostDetour() {
        switch(currentTimePeriod) {
        case MorningPeak:
        case AfternoonPeak:
            if (SimulationParameters.problemVariant == 1) {
                capacity = 1;
                if (Utility.random.nextDouble() < 0.5)
                    cost = smallSedanCostPerKM;
                else
                    cost = mediumSedanCostPerKM;
            } else if (Utility.random.nextDouble() < 0.95) { // 95-5% split (1,2,3)-(1,2,3,4,5) for peak hours.
                capacity = Utility.random.nextInt(3) + 1;
                if (Utility.random.nextDouble() < 0.5)
                    cost = smallSedanCostPerKM;
                else
                    cost = mediumSedanCostPerKM;
            } else {
                capacity = Utility.random.nextInt(5) + 1;
                cost = mediumSUVCostPerKM;
            }
            //detourTime = (int) Math.max(IntervalDurationInSec, driverDetourFactorPeakHour*travelTimeInSecond);
            detourTime = (int) (driverDetourFactorPeakHour*travelTimeInSecond);
            if (detourTime > driverMaxDetourInSecondPeakHour)
                detourTime = Utility.random.nextInt(detourTime-driverMaxDetourInSecondPeakHour+1) + driverMaxDetourInSecondPeakHour;
            else if (detourTime > IntervalDurationInSec)
                detourTime = Utility.random.nextInt(driverMaxDetourInSecondPeakHour-detourTime+1) + detourTime;
            else
                detourTime = Utility.random.nextInt(Math.min(driverMaxDetourInSecondPeakHour-IntervalDurationInSec, IntervalDurationInSec)+1)+IntervalDurationInSec;
                                            
            break;
        default:
            if (SimulationParameters.problemVariant == 1) {
                capacity = 1;
                if (Utility.random.nextDouble() < 0.5)
                    cost = smallSedanCostPerKM;
                else
                    cost = mediumSedanCostPerKM;
            } else if (Utility.random.nextDouble() < 0.90) { // 90-10% split (1,2,3)-(1,2,3,4,5) for non-peak hours.
                capacity = Utility.random.nextInt(3) + 1;
                if (Utility.random.nextDouble() < 0.5)
                    cost = smallSedanCostPerKM;
                else
                    cost = mediumSedanCostPerKM;
            } else {
                capacity = Utility.random.nextInt(5) + 1;
                cost = mediumSUVCostPerKM;
            }
            //detourTime = (int) Math.max(IntervalDurationInSec, driverDetourFactor*travelTimeInSecond);
            detourTime = (int) (driverDetourFactor*travelTimeInSecond);
            if (detourTime > driverMaxDetourInSecond)
                detourTime = Utility.random.nextInt(detourTime-driverMaxDetourInSecond+1) + driverMaxDetourInSecond;
            else if (detourTime > IntervalDurationInSec)
                detourTime = Utility.random.nextInt(driverMaxDetourInSecond-detourTime+1) + detourTime;
            else
                detourTime = Utility.random.nextInt(Math.min(driverMaxDetourInSecond-IntervalDurationInSec, IntervalDurationInSec)+1)+IntervalDurationInSec;
            break;
        }
    }
    
    public void verifyGeneratedTrips(List<Driver> drivers, List<Passenger> passengers) {
        System.out.println("Number of passengers: " + passengers.size());
        long depart;
        long arrive;
        int[] passengersTimeDistribution = new int[60];
        int[] passengersTravelTimesDistribution = new int[60];
        int temp;
        double temp2;
        
        for (Passenger p : passengers) {
            depart = p.getDepartureTime();
            arrive = p.getArrivalTime();
            if (arrive <= 0 || depart <= 0)
                System.out.println("!! Passenger ("+p.getID()+") incorret time: ("+ depart+","+arrive+")");
            if (depart > arrive) {
                System.out.println(p.toStringAll());
            } else {
                temp = (int) Math.floor((arrive - depart)/120.0);
                if (temp >= 60)
                    temp = 59;
                passengersTimeDistribution[temp]++;
            }
            temp2 = p.getMaxTravelDuration();
            if (arrive - depart < temp2) {
                System.out.println("!! Passenger ("+p.getID()+") incorret travel duration: ("+ depart+","+arrive+","+p.getMaxTravelDuration()+")");
            }
            temp = (int) (temp2/120.0);
            if (temp >= 60)
                temp = 59;
            passengersTravelTimesDistribution[temp]++;
        }
        System.out.print("passengerTimeWindowLengthDistribution:");
        System.out.println(Arrays.toString(passengersTimeDistribution));
        System.out.print("passengerMaxTravelDurationDistribution:");
        System.out.println(Arrays.toString(passengersTravelTimesDistribution));
        
        int[] driversTimeDistribution = new int[60];
        int[] driverstTravelTimeDistribution = new int[60];
        System.out.println("Number of drivers: " + drivers.size());
        for (Driver d : drivers) {
            if (d.getCapacity() <= 0)
                System.out.println("!! Driver ("+d.getID()+") has Capacity: "+ d.getCapacity());
            depart = d.getDepartureTime();
            arrive = d.getArrivalTime();
            if (arrive <= 0 || depart <= 0)
                System.out.println("!! Driver ("+d.getID()+") incorret time: ("+ depart+","+arrive+")");
            if (depart > arrive) {
                System.out.println(d.toStringAll());
            } else {
                temp = (int) Math.floor((arrive - depart)/120.0);
                if (temp >= 60)
                    temp = 59;
                driversTimeDistribution[temp]++;
            }
            temp2 = d.getMaxTravelDuration();
            if (arrive - depart < temp2)
                System.out.println("!! Driver ("+d.getID()+") incorret travel duration: ("+ depart+","+arrive+","+d.getMaxTravelDuration()+")");
            
            temp = (int) (temp2/120.0);
            if (temp >= 60)
                temp = 59;
            driverstTravelTimeDistribution[temp]++;
        }
        System.out.print("driverTimeWindowLengthDistribution:");
        System.out.println(Arrays.toString(driversTimeDistribution));
        System.out.print("driverMaxTravelDurationDistribution:");
        System.out.println(Arrays.toString(driverstTravelTimeDistribution));
    }
}
