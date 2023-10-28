package simulation;
import java.io.Serializable;

public enum TripState implements Serializable {
    before_assignment,
    assigned,
    active,
    served      // completed
}
