package simulation;

public class MatchEdge {
    public final int edgeId;  // unique id for O(1) access
    public Integer tail; 
    public Integer head;
    public final Match match;
    public final int profit;
    public int cost;
    public int reducedCost;
    public boolean reversedEdge;
    
    public MatchEdge (int id, Integer tail, Integer head, Match match, int cost) {
        this.edgeId = id;
        this.tail = tail;
        this.head = head;
        this.match = match;
        this.cost = cost;
        if (match != null)
            this.profit = match.profit;
        else
            this.profit = 0;
    }
    
    @Override
    public int hashCode() {
        return this.edgeId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final MatchEdge other = (MatchEdge) obj;
        return this.edgeId == other.edgeId;
    }
}
