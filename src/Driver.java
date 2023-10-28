package simulation;

import java.util.ArrayList;

public class Driver implements Trip {
    private static final long serialVersionUID = 1L;
    private final int ID;
    private int capacity = 0;
    private final double startLongitude;
    private final double startLatitude;
    private final double endLongitude;
    private final double endLatitude;
    private final int startRegion;          // this is the index in the region/speed array, not the region id
    private final int endRegion;            // this is the index in the region/speed array, not the region id
    private double distance;
    private long departureTime;
    private long arrivalTime;
    private double costPerMeter;
    private long maxTravelDuration;         // maximum travel duration in second
    private TripState state = TripState.before_assignment;
    private ArrayList<Match> matches = null;
    private ArrayList<Integer> indexLevel = null;               // records the indices when the size of a match increases by 1 compared to the next match.

    public Driver(int id, int capacity, double Longitude, double Latitude, double endLongitude, double endLatitude,  int startRegion, int endRegion) {
        ID = id;
        this.capacity = capacity;
        startLatitude = Latitude;
        startLongitude = Longitude;
        this.endLongitude = endLongitude;
        this.endLatitude = endLatitude;
        this.startRegion = startRegion;
        this.endRegion = endRegion;
    }

    // all parameters (11)
    public Driver(int id, int capacity, double Longitude, double Latitude, double endLongitude, double endLatitude, int startRegion, int endRegion,
                    long departureTime, long arrivalTime, double maxTravelDuration, double cost, double dist) {
        ID = id;
        this.capacity = capacity;
        startLatitude = Latitude;
        startLongitude = Longitude;
        this.endLongitude = endLongitude;
        this.endLatitude = endLatitude;
        this.startRegion = startRegion;
        this.endRegion = endRegion;
        this.maxTravelDuration = (long) maxTravelDuration;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        costPerMeter = cost;
        this.distance = dist;
        matches = new ArrayList<>();
        indexLevel = new ArrayList<>(capacity);
    }

    @Override
    public long getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(long departureTime) {
        this.departureTime = departureTime;
    }

    @Override
    public long getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(long arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public double getCostPerMeter() {
        return costPerMeter;
    }

    public void setCostPerMeter(double costPerMeter) {
        this.costPerMeter = costPerMeter;
    }

    @Override
    public int getID() {
            return ID;
    }

    public int getCapacity() {
            return capacity;
    }

    public void setCapacity(int capacity) {
            this.capacity = capacity;		
    }

    @Override
    public double getStartLongitude() {
            return startLongitude;
    }

    @Override
    public double getStartLatitude() {
            return startLatitude;
    }

    @Override
    public double getEndLongitude() {
            return endLongitude;
    }

    @Override
    public double getEndLatitude() {
            return endLatitude;
    }
    
    @Override
    public int getStartRegion() {
        return startRegion;
    }
    
    @Override
    public int getEndRegion() {
        return endRegion;
    }
    
    public double getDistance() {
        return distance;
    }

    @Override
    public long getMaxTravelDuration() {
        return maxTravelDuration;
    }
    
    public void setMaxTravelDuration(long duration) {
        maxTravelDuration = duration;
    }

    public TripState getState() {
        return state;
    }

    public void setState(TripState state) {
        this.state = state;
    }

    public ArrayList<Match> getMatches() {
        return matches;
    }

    public void setMatches(ArrayList<Match> Matches) {
        this.matches = Matches;
    }

    public void addMatch(Match match) {
            matches.add(match);
    }

    public void resetAssignments() {
        matches = new ArrayList<>();
    }

    public void addIndexLevel(int index) {
        indexLevel.add(index);
    }

    public void setIndexLevel(int which, int toWhat) {
        indexLevel.set(which, toWhat);
    }
    
    public ArrayList<Integer> getIndexLevel() {
        return indexLevel;
    }
    
    public void resetVariablesRollingHorizon() {
        if (matches != null)
            matches.clear();
        if (indexLevel != null)
            indexLevel.clear();
    }

    @Override
    public String toString() {
        return String.valueOf(ID);
    }
    
    public String toStringCore() {
        return String.format("Driver ID: (%d) - Start location: (%.6f, %.6f) - Destination: (%.6f, %.6f)" + 
                                            " - Departure region: (%d) - Arrival region: (%d)",
                             ID, startLongitude, startLatitude, endLongitude, endLatitude, startRegion, endRegion);
    }

    public String toStringAll() {
        return String.format("Driver ID: (%d) - Start Location: (%.6f, %.6f) - Destination: (%.6f, %.6f) - Capacity: (%d) - Departure region: (%d) - Arrival region: (%d)" +
                                        " - Departure time: (%d) - Arrival time: (%d) - Max Travel Duration: (%d) - costPerMeter: (%.8f)",
                             ID, startLongitude, startLatitude, endLongitude, endLatitude, capacity, startRegion, endRegion,
                             departureTime, arrivalTime, maxTravelDuration, costPerMeter);
    }
}
