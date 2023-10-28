package simulation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RPCPlus {
    private final int[] passengerCoveredLS2Plus;	// passengerCovered for each interval for LS2Plus algorithm
    private final double[] occupancyRateLS2Plus;
    private final double[] vacancyRateLS2Plus;
    private final long[] runningTimeLS2Plus;
    private final int[] profitLS2Plus;
    private final int[] numMatchesLS2Plus;
    
    private final int[] passengerCoveredLS2;	// passengerCovered for each interval for LS2 algorithm
    private final double[] occupancyRateLS2;
    private final double[] vacancyRateLS2;
    private final long[] runningTimeLS2;
    private final int[] profitLS2;
    private final int[] numMatchesLS2;
    
    private final int[] passengerCoveredGreedy;	// passengerCoveredGreedy for each interval for Greedy algorithm
    private final double[] occupancyRateGreedy;
    private final double[] vacancyRateGreedy;
    private final long[] runningTimeGreedy;
    private final int[] profitGreedy;
    private final int[] numMatchesGreedy;
    
    private final int[] passengerCoveredExact;	// passengerCoveredExact for each interval for exact algorithm
    private final double[] occupancyRateExact;
    private final double[] vacancyRateExact;
    private final long[] runningTimeExact;
    private final int[] profitExact;
    private final int[] numMatchesExact;
    
    private final Algorithms Alg;
    private final ExactSolver cplex;
    private final boolean ApproximatedDistanceUsed;
    private double profitTarget = 0d;
    private HashMap<Match, Driver> matchToDriver = null;
    private Set<Passenger> passengersInSolution = null;
    private HashMap<Match, Neighborhood> simplifiedGraph = null;  // a specific simplified version of the hypergraph H representing the neighborhood of each edge/match
    private int numOfedges = 0;
    private double solutionProfit = 0;
    
    public RPCPlus(int N, Algorithms Alg, ExactSolver cplex, boolean approximatedDistanceUsed) {
        passengerCoveredLS2Plus = new int[N];
        occupancyRateLS2Plus = new double[N];
        vacancyRateLS2Plus = new double[N];
        runningTimeLS2Plus = new long[N];
        profitLS2Plus = new int[N];
        numMatchesLS2Plus = new int[N];
        
        passengerCoveredLS2 = new int[N];
        occupancyRateLS2 = new double[N];
        vacancyRateLS2 = new double[N];
        runningTimeLS2 = new long[N];
        profitLS2 = new int[N];
        numMatchesLS2 = new int[N];
        
        passengerCoveredGreedy = new int[N];
        occupancyRateGreedy = new double[N];
        vacancyRateGreedy = new double[N];
        runningTimeGreedy = new long[N];
        profitGreedy = new int[N];
        numMatchesGreedy = new int[N];
        
        passengerCoveredExact = new int[N];
        occupancyRateExact = new double[N];
        vacancyRateExact = new double[N];
        runningTimeExact = new long[N];
        profitExact = new int[N];
        numMatchesExact = new int[N];
        
        this.Alg = Alg;
        this.cplex = cplex;
        ApproximatedDistanceUsed = approximatedDistanceUsed;
    }
    
    public HashMap<Driver, Match> Greedy(List<Driver> drivers, List<Passenger> passengers, int numMatches, int currentInterval, int method) {
        if (numMatches == 0) {
            for (Driver driver : drivers)
                numMatches += driver.getMatches().size();
            if (numMatches == 0) {
                System.out.println("There is no feasible match in this interval ("+currentInterval+"), so Greedy is not executed....");
                return null;
            }
        }        
        
        if (numMatches > Integer.MAX_VALUE) {
            System.out.format("However, there are too many matches (%d > %d)", numMatches, Integer.MAX_VALUE);
            System.out.println("Greedy is not executed for this interval " + currentInterval);
            return null;
        }
        
        profitTarget = 0;
        solutionProfit = 0;
        switch (method) {
            case 0:
                if (numMatches > 2000)
                    return GreedySorted(drivers, passengers, numMatches, currentInterval);
                else
                    return GreedySimpleRemoval(drivers, passengers, numMatches, currentInterval);
            case 1:
                return GreedySorted(drivers, passengers, numMatches, currentInterval);
            default:
                return GreedySimpleRemoval(drivers, passengers, numMatches, currentInterval);
        }
    }
    
    public HashMap<Driver, Match> GreedySorted(List<Driver> drivers, List<Passenger> passengers, int numMatches, int currentInterval) {
        long endTime;
        long startTime = System.currentTimeMillis();
        
        // need to create a list of matches/edges and a hashmap for each match to the driver owns it
        List<Match> matches = new ArrayList<>(numMatches);
        matchToDriver = new HashMap<>(numMatches);
        for (Driver driver : drivers) {
            for (Match m : driver.getMatches()) {
                if (m.profit >= 0) {
                    matches.add(m);
                    matchToDriver.put(m, driver);
                }
            }
        }
        matches.sort(new MatchProfitMaxComparator());
        
        HashMap<Driver, Match> solution = new HashMap<>(drivers.size());
        passengersInSolution = new HashSet<>(solution.size()*2);
        Driver tempDriver;
        Match tempMatch;
        int index = 0;
        
        tempMatch = matches.get(index);
        tempDriver = matchToDriver.get(tempMatch);
        //driversInSolution.add(tempDriver);
        passengersInSolution.addAll(tempMatch.sfp.passengers);
        solution.put(tempDriver, tempMatch);
        profitTarget = profitTarget + tempMatch.profit;
        
        outerFor:
        for (index = 1; index < matches.size(); index++) {
            if (solution.containsKey(matchToDriver.get(matches.get(index))))
                continue;
            for (Passenger p : matches.get(index).sfp.passengers) {
                if (passengersInSolution.contains(p))
                    continue outerFor;
            }
            
            tempMatch = matches.get(index);
            tempDriver = matchToDriver.get(tempMatch);
            solution.put(tempDriver, tempMatch);
            profitTarget = profitTarget + tempMatch.profit;
            //driversInSolution.add(tempDriver);
            passengersInSolution.addAll(tempMatch.sfp.passengers);
            if (solution.size() == drivers.size() || passengersInSolution.size() == passengers.size())
                break;
        }
        
        if (ApproximatedDistanceUsed) {
            int solutionSize = solution.size();
            Alg.validateSolutionMD(solution);
            if (solutionSize < solution.size()) { // update passengersInSolution
                passengersInSolution.clear();
                for (Map.Entry<Driver, Match> entry : solution.entrySet()) {
                    for (Passenger p : entry.getValue().sfp.passengers)
                        passengersInSolution.add(p);
                }
            }
        }
        
        passengerCoveredGreedy[currentInterval] = passengersInSolution.size();
        occupancyRateGreedy[currentInterval] = (double) (passengerCoveredGreedy[currentInterval]) / drivers.size() + 1;
        numMatchesGreedy[currentInterval] = solution.size();
        vacancyRateGreedy[currentInterval] = 1.0 - (double) (solution.size()) / drivers.size();
        profitGreedy[currentInterval] = Alg.profitOfSolution(solution);
        endTime = System.currentTimeMillis();
        runningTimeGreedy[currentInterval] = endTime - startTime;
        
        Alg.verifySolution(solution);
        System.out.println("Number of matches: "+solution.size() + ", Number of passegners: " + passengersInSolution.size() + ", Profit: " + profitGreedy[currentInterval]);
        System.out.println("****** Running time for Greedy (RPC+): " + (endTime-startTime) + " milliseconds.\n");
        
        return solution;
    }
    
    public HashMap<Driver, Match> GreedySimpleRemoval(List<Driver> drivers, List<Passenger> passengers, int numMatches, int currentInterval) {
        long endTime;
        long startTime = System.currentTimeMillis();
        
        // need to create a list of matches/edges and a hashmap for each match to the driver owns it
        List<Match> matches = new ArrayList<>(numMatches);
        matchToDriver = new HashMap<>(numMatches);
        for (Driver driver : drivers) {
            for (Match m : driver.getMatches()) {
                if (m.profit >= 0) {
                    matches.add(m);
                    matchToDriver.put(m, driver);
                }
            }
        }
        
        int maxProfit;
        Driver maxDriver = null;
        Match maxMatch;
        HashMap<Driver, Match> solution = new HashMap<>(drivers.size());
        passengersInSolution = new HashSet<>(solution.size()*2);
        Iterator<Match> matchIter;
        Match tempMatch;
        
        do {
            // find the match with max profit
            maxProfit = 0;
            maxMatch = null;
            for (Match match : matches) {
                if (match.profit >= maxProfit) {
                    maxProfit = match.profit;
                    maxMatch = match;
                    maxDriver = matchToDriver.get(match);
                }
            }
            
            // add it to the solution
            if (maxMatch != null) {
                solution.put(maxDriver, maxMatch);
                profitTarget = profitTarget + maxMatch.profit;
                passengersInSolution.addAll(maxMatch.sfp.passengers);
                //driversInSolution.add(maxDriver);
                if (solution.size() == drivers.size() || passengersInSolution.size() == passengers.size())
                    break;
            }
            
            // remove all incident edges/matches
            matchIter = matches.iterator();
            while(matchIter.hasNext()) {
                tempMatch = matchIter.next();
                if (solution.containsKey(matchToDriver.get(tempMatch))) {
                    matchIter.remove();
                    continue;
                }
                for (Passenger p : tempMatch.sfp.passengers) {
                    if (passengersInSolution.contains(p)) {
                        matchIter.remove();
                        break;
                    }
                }
            }
        } while (!matches.isEmpty());
        
        if (ApproximatedDistanceUsed) {
            int solutionSize = solution.size();
            Alg.validateSolutionMD(solution);
            if (solutionSize < solution.size()) { // update passengersInSolution
                passengersInSolution.clear();
                for (Map.Entry<Driver, Match> entry : solution.entrySet()) {
                    for (Passenger p : entry.getValue().sfp.passengers)
                        passengersInSolution.add(p);
                }
            }
        }
        
        passengerCoveredGreedy[currentInterval] = passengersInSolution.size();
        occupancyRateGreedy[currentInterval] = (double) (passengerCoveredGreedy[currentInterval]) / drivers.size() + 1;
        numMatchesGreedy[currentInterval] = solution.size();
        vacancyRateGreedy[currentInterval] = 1.0 - (double) (solution.size()) / drivers.size();
        profitGreedy[currentInterval] = Alg.profitOfSolution(solution);
        endTime = System.currentTimeMillis();
        runningTimeGreedy[currentInterval] = endTime - startTime;
        
        Alg.verifySolution(solution);
        System.out.println("Number of matches: "+solution.size() + ", Number of passegners: " + passengersInSolution.size() + ", Profit: " + profitGreedy[currentInterval]);
        System.out.println("****** Running time for GreedySimpleRemoval (RPC+): " + (endTime-startTime) + " milliseconds.\n");
        
        return solution;
    }
    
    public boolean Exact(List<Driver> drivers, List<Passenger> passengers, int numMatches, int currentInterval, double profitTargetMultiplier, double lowerBoundProfitTarget) {
        HashMap<Driver, Match> solution = Greedy(drivers, passengers, numMatches, currentInterval, 0);
        if (solution == null) {
            System.out.println("A profit target is not determined. Exact is skipped for this interval " + currentInterval);
            return false ;
        }
        
        if (solution.isEmpty()) {
            System.out.println("The initial solution contains no match. Exact is skipped for this interval " + currentInterval);
            return false;
        }
        
        long endTime;
        long startTime = System.currentTimeMillis();
        
        System.out.println("******* Using IP formulation Exact to find an exact solution for RPC+.....");
        
        List<Match> A = new ArrayList<>(solution.size()/10);
        for (Map.Entry<Driver, Match> entry : solution.entrySet()) {
            if (entry.getValue().sfp.passengers.size() == 1)
                A.add(entry.getValue());
        }
        if (!A.isEmpty()) {
            if (profitTargetMultiplier < 1)
                changeProfitTarget(A, profitTargetMultiplier, lowerBoundProfitTarget);
        }
        System.out.println("Profit Target: " + profitTarget);
        
        Pair<Solution, Long> pair = cplex.exact(drivers, passengers, profitTarget, currentInterval, numMatches);
        if (pair == null) {
            System.out.println("No matching found");
            return false;
        }
        
        HashMap<Driver, Match> matches = pair.getP1().matches;
        if (matches.isEmpty()) {
            System.out.println("Solution contains zero match.");
            runningTimeExact[currentInterval] = pair.getP2();
            return true;
        }
        
        if (ApproximatedDistanceUsed) {
            Alg.validateSolutionMD(solution);
        }
        Set<Passenger> passengersCovered = new HashSet<>(matches.size()*2);
        for (Map.Entry<Driver, Match> entry : matches.entrySet()) {
            for (Passenger p : entry.getValue().sfp.passengers)
                passengersCovered.add(p);
        }

        passengerCoveredExact[currentInterval] = passengersCovered.size();
        occupancyRateExact[currentInterval] = (double) (passengerCoveredExact[currentInterval]) / drivers.size() + 1.0;
        numMatchesExact[currentInterval] = matches.size();
        vacancyRateExact[currentInterval] = 1.0 - (double) (numMatchesExact[currentInterval]) / drivers.size();
        profitExact[currentInterval] = Alg.profitOfSolution(matches);
        endTime = System.currentTimeMillis();
        runningTimeExact[currentInterval] = endTime - startTime + pair.getP2();
        
        //Alg.verifySolution(matches);
        System.out.println("Number of matches: "+ numMatchesExact[currentInterval] + ", Number of passegners: " + passengerCoveredExact[currentInterval] + ", Profit: " + profitExact[currentInterval]);
        System.out.println("****** Running time for Exact (RPC+): " + runningTimeExact[currentInterval] + " milliseconds.\n");
        
        return true;
    }
    
    public boolean LS2(List<Driver> drivers, List<Passenger> passengers, int numMatches, int currentInterval, boolean clearGraph, double profitMultiplier, double lowerBoundProfitTarget) {
        long endTime;
        long startTime = System.currentTimeMillis();
        HashMap<Driver, Match> solution = Greedy(drivers, passengers, numMatches, currentInterval, 0);
        if (solution == null) {
            System.out.println("A feasible solution was not created. LS2 is skipped for this interval " + currentInterval);
            return false ;
        }
        
        if (solution.isEmpty()) {
            System.out.println("The solution contains no match. LS2 is skipped for this interval " + currentInterval);
            return false;
        }
        
        //System.out.println("passengersInSolution: " + passengersInSolution.toString());
        //System.out.println("driversInSolution: " + solution.keySet().toString());
        
        List<Match> A = new ArrayList<>(solution.size()/10);
        for (Map.Entry<Driver, Match> entry : solution.entrySet()) {
            if (entry.getValue().sfp.passengers.size() == 1)
                A.add(entry.getValue());
        }
        
        if (A.isEmpty()) {
            System.out.println("Every match in the current solution contain more than one passenger.");
            return true;
        } else {
            solutionProfit = profitTarget;
            System.out.println("Number of matches with one passenger in greedy (initial) solution: " + A.size());
            changeProfitTarget(A, profitMultiplier, lowerBoundProfitTarget);
            System.out.println("New Profit Target: " + profitTarget);
        }
        A.sort(new MatchProfitMinComparator());
        
        // Need to create the simplified hypergraph H
        if (simplifiedGraph == null)
            createSimplifiedHypergraph(drivers, numMatches);
        System.out.println("Number of edges: " + numOfedges);
        
        // largest match size is two
        Set<Match> improvement;
        for (Match match : A) {
            passengersInSolution.removeAll(match.sfp.passengers);
            solution.remove(matchToDriver.get(match));
            improvement = findImprovementLS2(solution, match);
            if (!improvement.isEmpty()) {   // in this case, size() must be two.
                for (Match m : improvement) {    // the driver of match is in the improvement
                    passengersInSolution.addAll(m.sfp.passengers);
                    solution.put(matchToDriver.get(m), m);
                }
            } else {
                solution.put(matchToDriver.get(match), match);
                passengersInSolution.addAll(match.sfp.passengers);
            }
        }
        
        if (ApproximatedDistanceUsed) {
            int solutionSize = solution.size();
            Alg.validateSolutionMD(solution);
            if (solutionSize < solution.size()) { // update passengersInSolution
                passengersInSolution.clear();
                for (Map.Entry<Driver, Match> entry : solution.entrySet()) {
                    for (Passenger p : entry.getValue().sfp.passengers)
                        passengersInSolution.add(p);
                }
            }
        }
        
        passengerCoveredLS2[currentInterval] = passengersInSolution.size();
        occupancyRateLS2[currentInterval] = (double) (passengerCoveredLS2[currentInterval]) / drivers.size() + 1.0;
        numMatchesLS2[currentInterval] = solution.size();
        vacancyRateLS2[currentInterval] = 1.0 - (double) (numMatchesLS2[currentInterval]) / drivers.size();
        profitLS2[currentInterval] = Alg.profitOfSolution(solution);
        
        endTime = System.currentTimeMillis();
        runningTimeLS2[currentInterval] = endTime - startTime;
        Alg.verifySolution(solution);
        System.out.println("Number of matches: "+ numMatchesLS2[currentInterval] + ", Number of passegners: " + passengerCoveredLS2[currentInterval] + ", Profit: " + profitLS2[currentInterval]);
        System.out.println("****** Running time for LS2 (RPC+): " + runningTimeLS2[currentInterval] + " milliseconds.\n");
        
        if (clearGraph) {
            simplifiedGraph.clear();
            simplifiedGraph = null;
            numOfedges = 0;
        }
        matchToDriver = null;
        passengersInSolution = null;
        
        return true;
    }
    
    public boolean LS2Plus(List<Driver> drivers, List<Passenger> passengers, int numMatches, int currentInterval, boolean clearGraph, double profitMultiplier, double lowerBoundProfitTarget) {
        long endTime;
        long startTime = System.currentTimeMillis();
        HashMap<Driver, Match> solution = Greedy(drivers, passengers, numMatches, currentInterval, 0);
        if (solution == null) {
            System.out.println("A feasible solution was not created. LS2 is skipped for this interval " + currentInterval);
            return false;
        }
        
        if (solution.isEmpty()) {
            System.out.println("The solution contains no match. LS2 is skipped for this interval " + currentInterval);
            return false;
        }
        
        //System.out.println("passengersInSolution: " + passengersInSolution.toString());
        //System.out.println("driversInSolution: " + solution.keySet().toString());
        
        List<Match> A = new ArrayList<>(solution.size()/10);
        for (Map.Entry<Driver, Match> entry : solution.entrySet()) {
            if (entry.getValue().sfp.passengers.size() == 1)
                A.add(entry.getValue());
        }
        if (A.isEmpty()) {
            System.out.println("Every match in the current solution contain more than one passenger.");
            return true;
        } else {
            solutionProfit = profitTarget;
            System.out.println("Number of matches with one passenger in greedy (initial) solution: " + A.size());
            changeProfitTarget(A, profitMultiplier, lowerBoundProfitTarget);
            System.out.println("New Profit Target: " + profitTarget);
        }
        A.sort(new MatchProfitMinComparator());
        
        // Need to create the simplified hypergraph H
        if (simplifiedGraph == null)
            createSimplifiedHypergraph(drivers, numMatches);
        System.out.println("Number of edges: " + numOfedges);
        
        // largest match size is higher than two
        Set<Match> improvement;
        Match m;
        for (int i = 0; i < A.size(); i++) {
            passengersInSolution.removeAll(A.get(i).sfp.passengers);
            solution.remove(matchToDriver.get(A.get(i)));
            improvement = findImprovement(solution, A.get(i));
            if (improvement.size() == 1) {
                m = improvement.iterator().next();
                passengersInSolution.addAll(m.sfp.passengers);
                solution.put(matchToDriver.get(m), m);
                solutionProfit = solutionProfit + m.profit - A.get(i).profit;
            } else if (improvement.size() > 1) {    // the driver of match A.get(i) is in the improvement
                for (Match match : improvement) {
                    passengersInSolution.addAll(match.sfp.passengers);
                    solution.put(matchToDriver.get(match), match);
                    solutionProfit = solutionProfit + match.profit;
                }
                solutionProfit = solutionProfit - A.get(i).profit;
            }
            else {
                solution.put(matchToDriver.get(A.get(i)), A.get(i));
                passengersInSolution.addAll(A.get(i).sfp.passengers);
            }
        }
        
        if (ApproximatedDistanceUsed) {
            int solutionSize = solution.size();
            Alg.validateSolutionMD(solution);
            if (solutionSize < solution.size()) { // update passengersInSolution
                passengersInSolution.clear();
                for (Map.Entry<Driver, Match> entry : solution.entrySet()) {
                    for (Passenger p : entry.getValue().sfp.passengers)
                        passengersInSolution.add(p);
                }
            }
        }
        
        passengerCoveredLS2Plus[currentInterval] = passengersInSolution.size();
        occupancyRateLS2Plus[currentInterval] = (double) (passengerCoveredLS2Plus[currentInterval]) / drivers.size() + 1.0;
        numMatchesLS2Plus[currentInterval] = solution.size();
        vacancyRateLS2Plus[currentInterval] = 1.0 - (double) (numMatchesLS2Plus[currentInterval]) / drivers.size();
        profitLS2Plus[currentInterval] = Alg.profitOfSolution(solution);
        endTime = System.currentTimeMillis();
        runningTimeLS2Plus[currentInterval] = endTime - startTime;
        
        Alg.verifySolution(solution);
        System.out.println("Number of matches: "+ numMatchesLS2Plus[currentInterval] + ", Number of passegners: " + passengerCoveredLS2Plus[currentInterval] + ", Profit: " + profitLS2Plus[currentInterval]);
        System.out.println("****** Running time for LS2Plus (RPC+): " + runningTimeLS2Plus[currentInterval] + " milliseconds.\n");
        
        if (clearGraph) {
            simplifiedGraph.clear();
            simplifiedGraph = null;
            numOfedges = 0;
        }
        matchToDriver = null;
        passengersInSolution = null;
        
        return true;
    }
    
    private Set<Match> findImprovement(HashMap<Driver, Match> solution, Match match) {
        int maxCoveredPassengers = 0;
        Set<Match> improvement = new HashSet<>(2);
        outerFor:
        for (Match match1 : simplifiedGraph.get(match).adjacentNeighborhood) {
            if (solution.containsKey(matchToDriver.get(match1)))
                continue;
            for (Passenger p : match1.sfp.passengers)
                if (passengersInSolution.contains(p))
                    continue outerFor;
            
            if (match1.sfp.passengers.size() > 1) {
                System.out.println("Test if match1 ("+ match1.sfp.passengers.toString()+ ") is an improvement over testing match (" + match.sfp.passengers.toString()+")");
                System.out.format("%.5f + %d - %d >= %.5f%n", solutionProfit, match1.profit, match.profit, profitTarget);
                if (solutionProfit + match1.profit - match.profit >= profitTarget) { // match1 is an improvment
                    if (match1.sfp.passengers.size() > maxCoveredPassengers) {
                        maxCoveredPassengers = match1.sfp.passengers.size();
                        improvement.clear();
                        improvement.add(match1);
                    }
                }
            }
                
            innerFor:
            for (Match match2 : simplifiedGraph.get(match).sameDriverNeighborhood) {
                for (Passenger p : match2.sfp.passengers)
                    if (passengersInSolution.contains(p))
                        continue innerFor;
                // if match1 and match2 can make an improvement
                System.out.println("Test if match1 and match2 intersect?: " + match1.sfp.passengers.toString() + " x " + match2.sfp.passengers.toString());
                if (!simplifiedGraph.get(match1).adjacentNeighborhood.contains(match2)) {   // match 1 and match 2 do not intersect
                    if (solutionProfit + match1.profit + match2.profit - match.profit >= profitTarget) {
                        if (match1.sfp.passengers.size() + match2.sfp.passengers.size() > maxCoveredPassengers) {
                            maxCoveredPassengers = match1.sfp.passengers.size() + match2.sfp.passengers.size();
                            improvement.clear();
                            improvement.add(match1);
                            improvement.add(match2);
                        }
                    }
                }
            }
        }
        return improvement;
    }
    
    private Set<Match> findImprovementLS2(HashMap<Driver, Match> solution, Match match) {
        Set<Match> improvement = new HashSet<>(2);
        outerFor:
        for (Match match1 : simplifiedGraph.get(match).adjacentNeighborhood) {
            if (solution.containsKey(matchToDriver.get(match1)))
                continue;
            for (Passenger p : match1.sfp.passengers)
                if (passengersInSolution.contains(p))
                    continue outerFor;
                
            innerFor:
            for (Match match2 : simplifiedGraph.get(match).sameDriverNeighborhood) {
                for (Passenger p : match2.sfp.passengers)
                    if (passengersInSolution.contains(p))
                        continue innerFor;
                // if match1 and match2 can make an improvement
                System.out.println("Test if match1 and match2 intersect?: " + match1.sfp.passengers.toString() + " x " + match2.sfp.passengers.toString());
                if (!simplifiedGraph.get(match1).adjacentNeighborhood.contains(match2)) {   // match 1 and match 2 do not intersect
                    if (match1.sfp.passengers.size() + match2.sfp.passengers.size() == 4) {
                        if (solutionProfit + match1.profit + match2.profit - match.profit >= profitTarget) {
                            solutionProfit = solutionProfit + match1.profit + match2.profit - match.profit;
                            improvement.add(match1);
                            improvement.add(match2);
                            return improvement;
                        }
                    }
                }
            }
        }
        return improvement;
    }
    
    private void createSimplifiedHypergraph(List<Driver> drivers, int numMatches) {
        long startTime = System.currentTimeMillis();
        long endTime;
        simplifiedGraph = new HashMap<>(numMatches);
        int size;
        for (Driver driver : drivers) {
            size = driver.getMatches().size();
            for (Match m : driver.getMatches()) {
                if (m.profit >= 0) {
                    simplifiedGraph.put(m, new Neighborhood());
                    simplifiedGraph.get(m).sameDriverNeighborhood = new HashSet<>(size-1); // this neighbor set have the same driver
                    simplifiedGraph.get(m).adjacentNeighborhood = new HashSet<>(drivers.size()); // this is the neighbor set not having the same driver
                }
            }
             
            for (int i = 0; i < size; i++) {
                if (driver.getMatches().get(i).profit < 0)
                    continue;
                for (int j = i + 1; j < size; j++) {
                    if (driver.getMatches().get(j).profit < 0)
                        continue;
                    simplifiedGraph.get(driver.getMatches().get(i)).sameDriverNeighborhood.add(driver.getMatches().get(j));
                    simplifiedGraph.get(driver.getMatches().get(j)).sameDriverNeighborhood.add(driver.getMatches().get(i));
                    numOfedges++;
                }
            }
        }
        
        size = drivers.size();
        for (int i = 0; i < size; i++) {
            for (Match match1 : drivers.get(i).getMatches()) {   
                if (match1.profit < 0)
                    continue;
                for (int j = i + 1; j < size; j++) {
                    for (Match match2 : drivers.get(j).getMatches()) {
                        if (match2.profit < 0)
                            continue;
                        // check if the passengers of the two matches intersect
                        for (Passenger p : match2.sfp.passengers) {
                            if (match1.sfp.passengers.contains(p)) {
                                simplifiedGraph.get(match1).adjacentNeighborhood.add(match2);
                                simplifiedGraph.get(match2).adjacentNeighborhood.add(match1);
                                numOfedges++;
                                break;
                            }
                        }
                    }
                }
            }
        }
        endTime = System.currentTimeMillis();
        System.out.println("Simplified hypergraph instance created. It took " + (endTime - startTime) + " milliseconds.");
    }
    
    public boolean LS2WithoutGraph(List<Driver> drivers, List<Passenger> passengers, int numMatches, int currentInterval, double profitTargetMultiplier, double lowerBoundProfitTarget) {
        long endTime;
        long startTime = System.currentTimeMillis();
        HashMap<Driver, Match> solution = Greedy(drivers, passengers, numMatches, currentInterval, 0);
        if (solution == null) {
            System.out.println("A feasible solution was not created. LS2 is skipped for this interval " + currentInterval);
            return false;
        }
        
        if (solution.isEmpty()) {
            System.out.println("The solution contains no matche. LS2 is skipped for this interval " + currentInterval);
            return false;
        }
        
        //System.out.println("passengersInSolution: " + passengersInSolution.toString());
        //System.out.println("driversInSolution: " + solution.keySet().toString());
        
        List<Match> A = new ArrayList<>(solution.size()/10);
        for (Map.Entry<Driver, Match> entry : solution.entrySet()) {
            if (entry.getValue().sfp.passengers.size() == 1)
                A.add(entry.getValue());
        }
        if (A.isEmpty()) {
            System.out.println("Every match in the current solution contain more than one passenger.");
            return true;
        } else {
            solutionProfit = profitTarget;
            System.out.println("Number of matches with one passenger in greedy (initial) solution: " + A.size());
            if (profitTargetMultiplier < 1)
                changeProfitTarget(A, profitTargetMultiplier, lowerBoundProfitTarget);
            System.out.println("Profit Target: " + profitTarget);
        }
        A.sort(new MatchProfitMinComparator());
        
        HashMap<Passenger, Set<Match>> passengerInNonnegativeMatches = new HashMap<>(A.size());
        for (Match m : A) {
            for (Passenger p : m.sfp.passengers)
                passengerInNonnegativeMatches.put(p, new HashSet<>(p.getNAssignments()));
        }
        for (Driver d : drivers) {
            for (Match match : d.getMatches()) {
                if (match.profit >= 0) {
                    for (Passenger p : match.sfp.passengers) {
                        if (passengerInNonnegativeMatches.containsKey(p))
                            passengerInNonnegativeMatches.get(p).add(match);
                    }
                }
            }
        }
        
        Set<Match> improvement;
        Match m;
        for (int i = 0; i < A.size(); i++) {
            passengersInSolution.removeAll(A.get(i).sfp.passengers);
            solution.remove(matchToDriver.get(A.get(i)));
            improvement = findImprovementWithoutGraph(solution, A.get(i), passengerInNonnegativeMatches);
            if (improvement.size() == 1) {
                m = improvement.iterator().next();
                System.out.println("An improvement is found: ("+ matchToDriver.get(m).getID()+ ", " + m.sfp.passengers.toString() + ")");
                passengersInSolution.addAll(m.sfp.passengers);
                solution.put(matchToDriver.get(m), m);
                solutionProfit = solutionProfit + m.profit - A.get(i).profit;
            } else if (improvement.size() > 1) {    // the driver of match A.get(i) is in the improvement
                System.out.println("An improvement consists of two matches is found:");
                for (Match match : improvement) {
                    System.out.println("(" + matchToDriver.get(match).getID()+ ", " + match.sfp.passengers.toString() + ")");
                    passengersInSolution.addAll(match.sfp.passengers);
                    solution.put(matchToDriver.get(match), match);
                    solutionProfit = solutionProfit + match.profit;
                }
                solutionProfit = solutionProfit - A.get(i).profit;
            } else {
                solution.put(matchToDriver.get(A.get(i)), A.get(i));
                passengersInSolution.addAll(A.get(i).sfp.passengers);
            }
        }
        
        if (ApproximatedDistanceUsed) {
            int solutionSize = solution.size();
            Alg.validateSolutionMD(solution);
            if (solutionSize < solution.size()) { // update passengersInSolution
                passengersInSolution.clear();
                for (Map.Entry<Driver, Match> entry : solution.entrySet()) {
                    for (Passenger p : entry.getValue().sfp.passengers)
                        passengersInSolution.add(p);
                }
            }
        }
        
        passengerCoveredLS2Plus[currentInterval] = passengersInSolution.size();
        occupancyRateLS2Plus[currentInterval] = (double) (passengerCoveredLS2Plus[currentInterval]) / drivers.size() + 1.0;
        numMatchesLS2Plus[currentInterval] = solution.size();
        vacancyRateLS2Plus[currentInterval] = 1.0 - (double) (numMatchesLS2Plus[currentInterval]) / drivers.size();
        profitLS2Plus[currentInterval] = Alg.profitOfSolution(solution);
        
        endTime = System.currentTimeMillis();
        runningTimeLS2Plus[currentInterval] = endTime - startTime;
        Alg.verifySolution(solution);
        System.out.println("Number of matches: "+ numMatchesLS2Plus[currentInterval] + ", Number of passegners: " + passengerCoveredLS2Plus[currentInterval] + ", Profit: " + profitLS2Plus[currentInterval]);
        System.out.println("****** Running time for LS2WithoutGraph (RPC+): " + runningTimeLS2Plus[currentInterval] + " milliseconds.\n");
        
        matchToDriver = null;
        passengersInSolution = null;
        return true;
    }
    
    private Set<Match> findImprovementWithoutGraph(HashMap<Driver, Match> solution, Match match, HashMap<Passenger, Set<Match>> passengerInMatches) {
        int maxCoveredPassengers = 0;
        Set<Match> improvement = new HashSet<>(2);
        Passenger passengerInChecking = match.sfp.passengers.iterator().next();
        Driver driverInMatch = matchToDriver.get(match);
        Set<Passenger> union;
        int profitDiff = Integer.MIN_VALUE;
        
        outerFor:
        for (Match match1 : passengerInMatches.get(passengerInChecking)) {
            if (matchToDriver.get(match1).getID() == driverInMatch.getID())
                continue;

            if (solution.containsKey(matchToDriver.get(match1)))
                continue;
            
            for (Passenger p : match1.sfp.passengers)
                if (passengersInSolution.contains(p))
                    continue outerFor;
            
            if (match1.sfp.passengers.size() > 1) {
                /*System.out.println("Test if match1 ("+ matchToDriver.get(match1).getID()+ ", " + match1.sfp.passengers.toString() + ") is an improvement over the testing match ("
                            + matchToDriver.get(match).getID()+ ", " + match.sfp.passengers.toString() + ")");
                System.out.format("%.3f + %d - %d >= %.3f%n", solutionProfit, match1.profit, match.profit, profitTarget);*/
                if (solutionProfit + match1.profit - match.profit >= profitTarget) { // match1 is an improvment
                    if (match1.sfp.passengers.size() >= maxCoveredPassengers) {
                        if (match1.profit - match.profit > profitDiff) {
                            maxCoveredPassengers = match1.sfp.passengers.size();
                            profitDiff = match1.profit - match.profit;
                            improvement.clear();
                            improvement.add(match1);
                        }
                    }
                }
            }
        }
        
        forloop:
        for (Match m : driverInMatch.getMatches()) {
            if (m.profit < 0)
                continue;
            
            for (Passenger p : m.sfp.passengers)
                if (passengersInSolution.contains(p))
                    continue forloop;
            
            if (m.sfp.passengers.size() > 1) {
                /*System.out.println("Test if match m ("+ matchToDriver.get(m).getID()+ ", " + m.sfp.passengers.toString() + ") is an improvement over the testing match ("
                            + matchToDriver.get(match).getID()+ ", " + match.sfp.passengers.toString() + ")");
                System.out.format("%.3f + %d - %d >= %.3f%n", solutionProfit, m.profit, match.profit, profitTarget);*/
                if (solutionProfit + m.profit - match.profit >= profitTarget) { // match1 is an improvment
                    if (m.sfp.passengers.size() > maxCoveredPassengers) {
                        maxCoveredPassengers = m.sfp.passengers.size();
                        improvement.clear();
                        improvement.add(m);
                    }
                }
            }
            
            innerFor:
            for (Match match1 : passengerInMatches.get(passengerInChecking)) {
                if (matchToDriver.get(match1).getID() == driverInMatch.getID())
                    continue;
                    
                if (solution.containsKey(matchToDriver.get(match1)))
                    continue;
                
                for (Passenger p : match1.sfp.passengers)
                    if (passengersInSolution.contains(p))
                        continue innerFor;
                
                // do matches m and match1 intersect without the passenger in the testing match?
                //System.out.println("Test if m and match1 intersect?: ("+ matchToDriver.get(m).getID()+ ", " + m.sfp.passengers.toString() + ") x ("
                  //          + matchToDriver.get(match1).getID()+ ", " + match1.sfp.passengers.toString() + ")");
                // the intersection of matches m and match2 minus passengerInChecking is empty?
                for (Passenger p : match1.sfp.passengers)
                    if (m.sfp.passengers.contains(p))
                        continue innerFor;
                //System.out.format("%.3f + %d + %d - %d >= %.3f%n", solutionProfit, m.profit, match1.profit, match.profit, profitTarget);
                if (solutionProfit + m.profit + match1.profit - match.profit >= profitTarget) {
                    union = new HashSet<>(m.sfp.passengers);
                    union.addAll(match1.sfp.passengers);
                    if (union.size() > maxCoveredPassengers) {
                        maxCoveredPassengers = union.size();
                        improvement.clear();
                        improvement.add(m);
                        improvement.add(match1);
                    }
                }
            }
        }

        return improvement;
    }
    
    private void changeProfitTarget(List<Match> A, double profitTargetMultiplier, double lowerBoundProfitTarget) {
        double profit = 0d;
        for (Match m : A)
            profit = profit + m.profit;
        
        double LB = profitTarget - profit + 2 * profit / (Alg.getLargestMatchSize()+1);
        LB = Math.min(LB, lowerBoundProfitTarget*profitTarget);
        
        // normalize the range
        profitTarget = (profitTarget - LB) * profitTargetMultiplier + LB;
    }
    
    public double getProfitTarget() {
        return profitTarget;
    }

    public int[] getPassengerCoveredLS2Plus() {
        return passengerCoveredLS2Plus;
    }

    public double[] getOccupancyRateLS2Plus() {
        return occupancyRateLS2Plus;
    }

    public double[] getVacancyRateLS2Plus() {
        return vacancyRateLS2Plus;
    }

    public long[] getRunningTimeLS2Plus() {
        return runningTimeLS2Plus;
    }

    public int[] getProfitLS2Plus() {
        return profitLS2Plus;
    }

    public int[] getNumMatchesLS2Plus() {
        return numMatchesLS2Plus;
    }

    public int[] getPassengerCoveredLS2() {
        return passengerCoveredLS2;
    }

    public double[] getOccupancyRateLS2() {
        return occupancyRateLS2;
    }

    public double[] getVacancyRateLS2() {
        return vacancyRateLS2;
    }

    public long[] getRunningTimeLS2() {
        return runningTimeLS2;
    }

    public int[] getProfitLS2() {
        return profitLS2;
    }

    public int[] getNumMatchesLS2() {
        return numMatchesLS2;
    }

    public int[] getPassengerCoveredGreedy() {
        return passengerCoveredGreedy;
    }

    public double[] getOccupancyRateGreedy() {
        return occupancyRateGreedy;
    }

    public double[] getVacancyRateGreedy() {
        return vacancyRateGreedy;
    }

    public long[] getRunningTimeGreedy() {
        return runningTimeGreedy;
    }

    public int[] getProfitGreedy() {
        return profitGreedy;
    }

    public int[] getNumMatchesGreedy() {
        return numMatchesGreedy;
    }

    public int[] getPassengerCoveredExact() {
        return passengerCoveredExact;
    }

    public double[] getOccupancyRateExact() {
        return occupancyRateExact;
    }

    public double[] getVacancyRateExact() {
        return vacancyRateExact;
    }

    public long[] getRunningTimeExact() {
        return runningTimeExact;
    }

    public int[] getProfitExact() {
        return profitExact;
    }

    public int[] getNumMatchesExact() {
        return numMatchesExact;
    }

    public int getNumOfedges() {
        return numOfedges;
    }
    
    
    // in descending order of the profit of the matches
    public class MatchProfitMaxComparator implements Comparator<Match> {
        public MatchProfitMaxComparator() {}

        @Override
        public int compare(Match a1, Match a2) {
            if (a1.profit > a2.profit)
                return -1;
            else if (a1.profit < a2.profit)
                return 1;
            return 0;
        }
    }
    
    // in ascending order of the profit of the matches
    public class MatchProfitMinComparator implements Comparator<Match> {
        public MatchProfitMinComparator() {}

        @Override
        public int compare(Match a1, Match a2) {
            if (a1.profit > a2.profit)
                return 1;
            else if (a1.profit < a2.profit)
                return -1;
            return 0;
        }
    }
}
