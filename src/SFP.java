package simulation;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

public class SFP implements Serializable {
    private static final long serialVersionUID = 1L;
    public final Set<Passenger> passengers;
    public final List<Pair<Passenger, Boolean>> originOrDest; // <(Passenger), (false = origin, true = destination)>
    public final int[] travelDistanceIndex;
    public final int[] hourIndex;
    public final long departureTimeOfDriver;
    
    public SFP(Set<Passenger> passengers, List<Pair<Passenger, Boolean>> originOrDest, int[] travelDistanceIndex, int[] hourIndex, long departureTimeOfDriver) {
        this.passengers = passengers;
        this.originOrDest = originOrDest;
        this.travelDistanceIndex = travelDistanceIndex;
        this.hourIndex = hourIndex;
        this.departureTimeOfDriver = departureTimeOfDriver;
    }
}
