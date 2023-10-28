package simulation;

import java.util.Set;

public class Neighborhood {     // neighborhood of an edge e (match)
    public Set<Match> sameDriverNeighborhood;   // the set of edges incident to e such that each edge in the set has the same driver
    public Set<Match> adjacentNeighborhood;     // // the set of edges incident to e such that driver of each edge in the set is different from the driver of e
    /* The union of the two (sameDriverNeighborhood and adjacentNeighborhood) is the whole neighborhood of the match */
}
