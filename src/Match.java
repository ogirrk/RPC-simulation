package simulation;

import java.io.Serializable;

public class Match implements Serializable {
    private static final long serialVersionUID = 1L;
    public int id;            // just indicates the id for this assignment, which is unique for the duration of an iteration
    public double revenue;          // in dollar
    public double cost;             // in dollar
    public int profit;              // profit stored in cent.
    public final SFP sfp;
    
    public Match(SFP sfp) {
        this.sfp = sfp;
    }
    
    public Match(int id, SFP sfp) {
        this.id = id;
        this.sfp = sfp;
    }

    public void setRevenue(double revenue) {
        this.revenue = revenue;
    }

    public void setCost(double cost) {
        this.cost = cost;
    }

    public void setProfit(int profit) {
        this.profit = profit;
    }
    
    @Override
    public int hashCode() {
        return id;
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
        Match asgn = (Match) obj;
        return asgn.id == this.id;
    }

    @Override
    public String toString() {
        return String.valueOf(id);
    }
}
