package simulation;

import java.util.HashMap;

public class Solution {
    public final HashMap<Driver, Match> matches;
    public final int weight;
    
    public Solution(HashMap<Driver, Match> matches, int profit) {
        this.matches = matches;
        this.weight = profit;
    }
}
