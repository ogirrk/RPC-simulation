package simulation;

public class Passenger implements Trip {
    private static final long serialVersionUID = 1L;
    private final int ID;
    private final double startLongitude;
    private final double startLatitude;
    private final double endLongitude;
    private final double endLatitude;
    private final int startRegion;          // this is the index in the region/speed array, not the region id
    private final int endRegion;            // this is the index in the region/speed array, not the region id
    private long departureTime;
    private final long arrivalTime;
    private final long maxTravelDuration;       // maximum travel duration in second
    private final double distance;
    private TripState state = TripState.before_assignment;
    private volatile int nAssignments = 0;

    public Passenger(int id, double Longitude, double Latitude, double endLongitude, double endLatitude, int startRegion, int endRegion, 
                        long departureTime, long arrivalTime, long maxTravelDuration, double distance) {
        ID = id;
        startLatitude = Latitude;
        startLongitude = Longitude;
        this.endLongitude = endLongitude;
        this.endLatitude = endLatitude;
        this.startRegion = startRegion;
        this.endRegion = endRegion;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.maxTravelDuration = maxTravelDuration;
        this.distance = distance;
    }
		
    @Override
    public int getID() {
        return ID;
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
    
    @Override
    public long getDepartureTime() {
        return departureTime;
    }
    
    @Override
    public long getMaxTravelDuration() {
        return maxTravelDuration;
    }

    public double getDistance() {
        return distance;
    }

    public void setDepartureTime(long departureTime) {
        this.departureTime = departureTime;
    }
    
    @Override
    public long getArrivalTime() {
        return arrivalTime;
    }

    public TripState getState() {
        return state;
    }

    public void setState(TripState state) {
        this.state = state;
    }

    public int getNAssignments() {
        return nAssignments;
    }

    public void incrementNAssignments() {
        nAssignments++;
    }

    public void decrementNAssignments() {
        nAssignments--;
    }
    
    public void resetVariablesRollingHorizon() {
        nAssignments = 0;
    }
	
    @Override
    public boolean equals(Object other) {
        if (other == null)
            return false;
        if (this == other)
            return true;
        if (other instanceof Passenger) {
            Passenger p = (Passenger) other;
            return (this.ID == p.ID);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 37 * hash + this.ID;
        return hash;
    }
        
    @Override
    public String toString() {
        return String.valueOf(ID);
    }

    public String toStringAll() {
        return String.format("Passenger ID: (%d) - Start location: (%.6f, %.6f) - Destination: (%.6f, %.6f) " + 
                                "- Departure region: (%d) - Arrival region: (%d) - Departure time: (%d) - Arrival time: (%d) - Max Travel Duration: (%d)",
                                ID, startLongitude, startLatitude, endLongitude, endLatitude, startRegion, endRegion, departureTime, arrivalTime, maxTravelDuration);
    }
}