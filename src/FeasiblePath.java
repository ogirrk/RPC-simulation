package simulation;

import java.util.ArrayList;
import java.util.List;

public class FeasiblePath {
    public List<Pair<Passenger, Boolean>> originOrDest;
    
    public FeasiblePath() {
        originOrDest = new ArrayList<>(2);
    }
    
    public FeasiblePath(int size) {
        originOrDest = new ArrayList<>(size);
    }
    
    public FeasiblePath(List<Pair<Passenger, Boolean>> originOrDest) {
        this.originOrDest = originOrDest;
    }
    
    public void addLocation(Pair<Passenger, Boolean> loc) {
        originOrDest.add(loc);
    }
    
    public int size() {
        return originOrDest.size();
    }
}
