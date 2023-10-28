package simulation;

public class SimulationParameters {
    public static int problemVariant = 1;                       // Variant 2: PRCplus, Variant 1: RPC1
    public static int startHour = 6;                            // the time the simulation starts: default = 6
    public static int numberOfIntervals = 1;
    public static int intervalInMinute = 15;
    public static int candidateTest = 1;
    public static double distanceRadius = 1.0;
    
    public static int computeMatchMethod = 1;
    public static int computeDistanceMethod = 0;
    public static boolean useMultiThread = false;
    public static int nThreads = 4;
    
    public static int minNumBaseMatchesPerDriver = 25;
    public static int maxNumBaseMatchesPerDriver = 100;
    public static int maxNumMatchesPerDriver = 20000;
    public static int thresholdMatchesForEachPassenger = 12;
    
    public static boolean algorithmBaseMatches = false;
    public static boolean algorithmReduceBaseMatches = true;
    public static boolean algorithmAllMatches = false;
    public static boolean algorithmComputeSolutions = true;
    public static boolean algorithmRP = false;                  // compute solutions for the RP problem
    
    public static double costMultiplier = 1.0;
    public static double chanceForExtraCost = 0d;
    public static double extraCost = 0d;
    public static int operatingCostType = 0;
    public static double revenueReduction = 1.0;
    public static double profitTargetMultiplier = 1.0;
    public static double lowerBoundProfitTarget = 0.6;
    
    private SimulationParameters() {}
    
    public static String allParameters() {
        return "Parameters:" + Utility.newline
                +"{problemVariant="+problemVariant + ", startHour="+startHour + ", numberOfIntervals="+numberOfIntervals + ", intervalInMinute="+intervalInMinute +"}"+ Utility.newline
                +"{candidateTest="+candidateTest +", distanceRadius="+distanceRadius +"}"+ Utility.newline
                +"{computeMatchMethod="+computeMatchMethod +", computeDistanceMethod="+computeDistanceMethod +", useMultiThread="+useMultiThread +", nThreads="+nThreads +"}"+ Utility.newline
                +"{minBaseMatchesPerDriver="+minNumBaseMatchesPerDriver +", maxBaseMatchesPerDriver="+maxNumBaseMatchesPerDriver +", maxMatchesPerDriver="+maxNumMatchesPerDriver +", maxBaseMatchesPerPassenger="+thresholdMatchesForEachPassenger +"}"+ Utility.newline
                +"{algorithmBaseMatches="+algorithmBaseMatches + ", algorithmReduceBaseMatches="+algorithmReduceBaseMatches + ", algorithmAllMatches="+algorithmAllMatches+ ", algorithmComputeSolutions="+algorithmComputeSolutions + ", algorithmRP="+algorithmRP +"}"+ Utility.newline
                +"{costMultiplier="+costMultiplier + ", chanceForExtraCost="+chanceForExtraCost + ", extraCost="+extraCost + ", operatingCostType="+operatingCostType + ", revenueReduction="+revenueReduction + ", profitTargetMultiplier="+profitTargetMultiplier + ", lowerBoundProfitTarget="+lowerBoundProfitTarget +"}";
    }
}
