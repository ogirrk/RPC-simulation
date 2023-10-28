package simulation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RPCOne {
    private final int[] passengerCoveredExact;	// passengerCoveredExact for each interval for exact algorithm
    private final double[] occupancyRateExact;
    private final double[] vacancyRateExact;
    private final long[] runningTimeExact;
    private final int[] profitExact;
    private final int[] negativeMatchesExact;
    
    private final int[] passengerCoveredExactNF;  // passengerCoveredExactFlow for each interval for the network flow exact algorithm
    private final double[] occupancyRateExactNF;
    private final double[] vacancyRateExactNF;
    private final long[] runningTimeExactNF;
    private final int[] profitExactNF;
    private final int[] negativeMatchesExactNF;
    
    private final int[] passengerCoveredGreedy;	// passengerCoveredGreedy for each interval for Greedy algorithm
    private final double[] occupancyRateGreedy;
    private final double[] vacancyRateGreedy;
    private final long[] runningTimeGreedy;
    private final int[] profitGreedy;
    private final int[] negativeMatchesGreedy;
    
    private final int[] passengerCoveredNewExactNF;  // passengerCoveredNewExactNF for each interval for the modified SSP
    private final double[] occupancyRateNewExactNF;
    private final double[] vacancyRateNewExactNF;
    private final long[] runningTimeNewExactNF;
    private final int[] profitNewExactNF;
    private final int[] negativeMatchesNewExactNF;
    
    private final ExactSolver cplex;
    private final Algorithms Alg;
    private final boolean ApproximatedDistanceUsed;
    
    public RPCOne(int N, ExactSolver cplex, Algorithms Alg, boolean approximatedDistanceUsed) {
        passengerCoveredExact = new int[N];
        occupancyRateExact = new double[N];
        vacancyRateExact = new double[N];
        runningTimeExact = new long[N];
        profitExact = new int[N];
        negativeMatchesExact = new int[N];
                
        passengerCoveredExactNF = new int[N];
        occupancyRateExactNF = new double[N];
        vacancyRateExactNF = new double[N];
        runningTimeExactNF = new long[N];
        profitExactNF = new int[N];
        negativeMatchesExactNF = new int[N];
        
        passengerCoveredGreedy = new int[N];
        occupancyRateGreedy = new double[N];
        vacancyRateGreedy = new double[N];
        runningTimeGreedy = new long[N];
        profitGreedy = new int[N];
        negativeMatchesGreedy = new int[N];
        
        passengerCoveredNewExactNF = new int[N];
        occupancyRateNewExactNF = new double[N];
        vacancyRateNewExactNF = new double[N];
        runningTimeNewExactNF = new long[N];
        profitNewExactNF = new int[N];
        negativeMatchesNewExactNF = new int[N];
        
        this.cplex = cplex;
        this.Alg = Alg;
        ApproximatedDistanceUsed = approximatedDistanceUsed;
    }
    
    public boolean Exact(double profitTarget, List<Driver> drivers, List<Passenger> passengers, int currentInterval, int numMatches) {
        System.out.println("******* Using IP formulation Exact to find an exact solution for RPC1.....");
        Pair<Solution, Long> pair = cplex.exact(drivers, passengers, profitTarget, currentInterval, numMatches);
        if (pair == null) {
            System.out.println("No matching found");
            return false;
        }
        
        HashMap<Driver, Match> solution = pair.getP1().matches;
        if (solution.isEmpty()) {
            System.out.println("Solution contains zero match.");
            runningTimeExact[currentInterval] = pair.getP2();
            return true;
        }
        
        long startTime = System.currentTimeMillis();
        long endTime;
        
        passengerCoveredExact[currentInterval] = solution.size();
        profitExact[currentInterval] = Alg.profitOfSolution(solution);
        endTime = System.currentTimeMillis();
        
        if (ApproximatedDistanceUsed) {
            Alg.validateSolutionMD(solution);
        }

        occupancyRateExact[currentInterval] = (double) (passengerCoveredExact[currentInterval]) / drivers.size() + 1.0;
        vacancyRateExact[currentInterval] = 1.0 - (double) (solution.size()) / drivers.size();
        runningTimeExact[currentInterval] = endTime - startTime + pair.getP2();
        
        for (Map.Entry<Driver, Match> entry : solution.entrySet()) {
            if (entry.getValue().profit < 0)
                negativeMatchesExact[currentInterval]++;
        }
        System.out.println("Number of matches: "+ solution.size() + 
                            ", Number of negative edges: " + negativeMatchesExact[currentInterval] + ", Profit: " + profitExact[currentInterval]);
        System.out.println("****** Running time for Exact IP formulation (RPC1): " + runningTimeExact[currentInterval] + " milliseconds.\n");
        
        return true;
    }
    
    public boolean ExactNetworkFlow(double profitTarget, List<Driver> drivers, List<Passenger> passengers, int currentInterval, int numMatches, int numDriverWithMatches, boolean LogResult) {
        System.out.println("******* Using NetworkFlow Exact to find an exact solution for RPC1.....");        
        Pair<Solution, Long> pair = cplex.maxMatching(drivers, passengers, currentInterval, numMatches);
        if (pair == null) {
            System.out.println("No matching found");
            return false;
        }
        
        Solution solution = pair.getP1();
        if (solution.matches.isEmpty()) {
            System.out.println("Solution contains zero match.");
            runningTimeExactNF[currentInterval] = pair.getP2();
            return true;
        }
        if (solution.weight != pair.getP1().weight) {
            System.out.println("Solution contains " + solution.matches.size()+ " matches, but the weight (cardinality) of the matching is " + solution.weight);
            return false;
        }
        
        runningTimeExactNF[currentInterval] = pair.getP2();
        pair = cplex.minCostMaxFlow(drivers, passengers, profitTarget, solution.weight, currentInterval, numMatches, numDriverWithMatches, LogResult);
        if (pair == null) {
            System.out.println("No matching found");
            return false;
        }
        solution = pair.getP1();
        if (solution.matches.isEmpty()) {
            System.out.println("No solution is found for profit target " + profitTarget);
            runningTimeExactNF[currentInterval] = runningTimeExactNF[currentInterval] + pair.getP2();
            return true;
        }
        if (solution.weight != pair.getP1().weight) {
            System.out.println("Solution contains " + solution.matches.size()+ " matches, but the cardinality of the matching is " + solution.weight);
            return false;
        }
        
        HashMap<Driver, Match> matches = pair.getP1().matches;
        
        long startTime = System.currentTimeMillis();
        long endTime;
        
        passengerCoveredExactNF[currentInterval] = matches.size();
        profitExactNF[currentInterval] = Alg.profitOfSolution(matches);
        endTime = System.currentTimeMillis();
        
        if (ApproximatedDistanceUsed) {
            Alg.validateSolutionMD(matches);
        }
        occupancyRateExactNF[currentInterval] = (double) (passengerCoveredExactNF[currentInterval]) / drivers.size() + 1.0;
        vacancyRateExactNF[currentInterval] = 1.0 - (double) (matches.size()) / drivers.size();
        runningTimeExactNF[currentInterval] = runningTimeExactNF[currentInterval] + endTime - startTime + pair.getP2();
        
        for (Map.Entry<Driver, Match> entry : matches.entrySet()) {
            if (entry.getValue().profit < 0)
                negativeMatchesExactNF[currentInterval]++;
        }
        
        System.out.println("Number of matches: "+ matches.size() + 
                            ", Number of passegners: " + negativeMatchesExactNF[currentInterval] + ", Profit: " + profitExactNF[currentInterval]);
        System.out.println("****** Running time for ExactNetworkFlow (RPC1): " + runningTimeExactNF[currentInterval] + " milliseconds.\n");
        
        return true;
    }
    
    public boolean Greedy(double profitTarget, List<Driver> drivers, List<Passenger> passengers, int currentInterval, int numMatches, HashMap<Driver,List<Match>> negativeMatches) {
        System.out.println("******* Using Greedy to find an approximation for RPC1 by starting to find a max weight matching.....");
        Pair<Solution, Long> pair = cplex.maxWeight(drivers, passengers, currentInterval, numMatches);
        if (pair == null) {
            System.out.println("No solution found");
            return false;
        }
            
        HashMap<Driver, Match> solution = pair.getP1().matches;
        if (solution.isEmpty()) {
            System.out.println("Solution contains zero match.");
            runningTimeGreedy[currentInterval] = pair.getP2();
            return true;
        }
        
        long endTime;
        long startTime = System.currentTimeMillis();
        Set<Passenger> passengersInSolution = new HashSet<>(solution.size());
        if (negativeMatches == null || negativeMatches.isEmpty()) {
            for (Map.Entry<Driver, Match> entry : solution.entrySet()) {
                for (Passenger p : entry.getValue().sfp.passengers)
                    passengersInSolution.add(p);
            }
            passengerCoveredGreedy[currentInterval] = passengersInSolution.size();
            profitGreedy[currentInterval] = Alg.profitOfSolution(solution);
            endTime = System.currentTimeMillis();
            occupancyRateGreedy[currentInterval] = (double) (passengerCoveredGreedy[currentInterval]) / drivers.size() + 1.0;
            vacancyRateGreedy[currentInterval] = 1.0 - (double) (solution.size()) / drivers.size();
            runningTimeGreedy[currentInterval] = endTime - startTime + pair.getP2();
            
            for (Map.Entry<Driver, Match> entry : solution.entrySet()) {
                if (entry.getValue().profit < 0)
                    negativeMatchesGreedy[currentInterval]++;
            }
            System.out.println("Number of matches: "+ solution.size()+ ", Number of passegners: " + passengerCoveredGreedy[currentInterval] + 
                                ", Number of negative edges: " + negativeMatchesGreedy[currentInterval] + ", Profit: " + profitGreedy[currentInterval]);
            System.out.println("****** Running time for Greedy (RPC1): " + runningTimeGreedy[currentInterval] + " milliseconds.\n");
            return true;
        }
        
        Iterator<Map.Entry<Driver, List<Match>>> iter;
        Map.Entry<Driver,List<Match>> iterEntry;
        Iterator<Match> matchIter;
        Match tempMatch;
        
        for (Map.Entry<Driver, Match> entry : solution.entrySet()) {
            //driversInSolution.add(match.getP1());
            if (negativeMatches.containsKey(entry.getKey()))
                negativeMatches.remove(entry.getKey());
            for (Passenger p : entry.getValue().sfp.passengers) {
                passengersInSolution.add(p);
                iter = negativeMatches.entrySet().iterator();
                while (iter.hasNext()) {
                    iterEntry = iter.next();
                    matchIter = iterEntry.getValue().iterator();
                    while (matchIter.hasNext()) {
                        tempMatch = matchIter.next();
                        if (tempMatch.sfp.passengers.contains(p)) {
                            matchIter.remove();
                            break;
                        }
                    }
                    if (negativeMatches.get(iterEntry.getKey()).isEmpty())
                        iter.remove();
                }
            }
        }
        
        int count = 0;
        for (var entry : negativeMatches.entrySet())
            count = count + entry.getValue().size();
        System.out.println("Number of negative matches not in the max weight matching solution: " + count);
        
        int currentProfit = pair.getP1().weight;
        int maxProfit;
        Driver maxDriver;
        Match maxMatch;
        while (!negativeMatches.isEmpty()) {
            // find max weight match
            maxProfit = Integer.MIN_VALUE;
            maxDriver = null;
            maxMatch = null;
            for (var entry : negativeMatches.entrySet()) {
                for (Match m : entry.getValue()) {
                    if (m.profit > maxProfit) {
                        maxProfit = m.profit;
                        maxDriver = entry.getKey();
                        maxMatch = m;
                    }
                }
            }
            // add it to the current solution
            if (maxMatch != null) {
                if (currentProfit + maxProfit >= profitTarget) {
                    currentProfit = currentProfit + maxProfit;
                    solution.put(maxDriver, maxMatch);
                    passengersInSolution.addAll(maxMatch.sfp.passengers);
                    negativeMatches.remove(maxDriver);
                } else {
                    break;
                }
            }
            
            iter = negativeMatches.entrySet().iterator();
            while (iter.hasNext()) {
                iterEntry = iter.next();
                matchIter = iterEntry.getValue().iterator();
                while (matchIter.hasNext()) {
                    tempMatch = matchIter.next();
                    for (Passenger p : tempMatch.sfp.passengers)
                        if (passengersInSolution.contains(p)) {
                            matchIter.remove();
                            break;
                        }
                }
                if (negativeMatches.get(iterEntry.getKey()).isEmpty())
                    iter.remove();
            }
        }

        passengerCoveredGreedy[currentInterval] = solution.size();
        profitGreedy[currentInterval] = Alg.profitOfSolution(solution);
        endTime = System.currentTimeMillis();
        
        if (ApproximatedDistanceUsed) {
            Alg.validateSolutionMD(solution);
        }
        occupancyRateGreedy[currentInterval] = (double) (passengerCoveredGreedy[currentInterval]) / drivers.size() + 1;
        vacancyRateGreedy[currentInterval] = 1.0 - (double) (solution.size()) / drivers.size();
        runningTimeGreedy[currentInterval] = endTime - startTime + pair.getP2();
        
        Alg.verifySolution(solution);
        for (Map.Entry<Driver, Match> entry : solution.entrySet()) {
            if (entry.getValue().profit < 0)
                negativeMatchesGreedy[currentInterval]++;
        }
        System.out.println("Number of matches: "+ solution.size() + 
                              ", Number of negative edges: " + negativeMatchesGreedy[currentInterval] + ", Profit: " + profitGreedy[currentInterval]);
        System.out.println("****** Running time for Greedy (RPC1): " + runningTimeGreedy[currentInterval] + " milliseconds.\n");
        return true;
    }
    
    public boolean ExactNF(double profitTarget, List<Driver> drivers, List<Passenger> passengers, int currentInterval, int numMatches, int numDriverWithMatches, int numNegativeMatches) {
        System.out.println("******* Using ExactNF (modified SSP) to find an exact solution for RPC1.....");
        System.out.println("profitTarget: " + profitTarget);
        
        long endTime;
        long startTime = System.currentTimeMillis();
        // construct N(V,E): E is the set of matches, and V is the set of drivers + the set of passengers.
        Graph N = new Graph(numDriverWithMatches + passengers.size(), numMatches);
        
        HashMap<Integer,Driver> vertexToDriver = new HashMap<>(numDriverWithMatches);
        HashMap<Trip,Integer> TripToVertex = new HashMap<>(numDriverWithMatches + passengers.size());
        int vertexId = 0;
        int edgeId = 0;
        
        // vertices and edges
        N.addVertex(vertexId);
        N.sourceVertex = vertexId;
        vertexId++;
        
        N.addVertex(vertexId);
        N.sinkVertex = vertexId;
        vertexId++;
        
        Passenger passenger;
        for (Driver driver : drivers) {
            if (!driver.getMatches().isEmpty()) {
                vertexToDriver.put(vertexId, driver);
                TripToVertex.put(driver, vertexId);
                N.addVertex(vertexId);
                N.addEdge(new MatchEdge(edgeId, 0, vertexId, null, 0));
                vertexId++;
                edgeId++;
                for (Match match : driver.getMatches()) {
                    passenger = match.sfp.passengers.iterator().next();
                    if (!TripToVertex.containsKey(passenger)) {
                        TripToVertex.put(passenger, vertexId);
                        N.addVertex(vertexId);
                        N.addEdge(new MatchEdge(edgeId, vertexId, 1, null, 0));
                        //passengerVertices++;
                        vertexId++;
                        edgeId++;
                    }
                    
                    N.addEdge(new MatchEdge(edgeId, TripToVertex.get(driver), TripToVertex.get(passenger), match, -match.profit));
                    edgeId++;
                }
                
            }
        }
         
        //System.out.println("Number of vertices = " + N.outEdges.size() + " and number of edges = " + N.edgeSet.size());   
        if (numNegativeMatches != numMatches)
            Alg.changeToNonNegativeWeight(N);
        
        // successive shortest path algorithm
        Set<MatchEdge> temporarySolution = new HashSet<>();
        HashMap<Driver, Match> solution = null;
        //Set<MatchEdge> forwardEdges = new HashSet<>(N.edgeSet);
        MatchEdge edge;
        int totalProfit = 0;
        int previousTotalProfit = Integer.MIN_VALUE;
        Integer temp;
        /*for (MatchEdge e : N.edgeSet) {
            if (e.cost > 0)
                forwardEdges.add(e);
        }*/
            
        Integer[] nodePotential = new Integer[N.outEdges.size()];
        for (Integer u : N.outEdges.keySet())
            nodePotential[u] = 0;
        // reduced costs are changed in-place
        
        //System.out.println("Time elapsed before executing main loop: " + (System.currentTimeMillis()-startTime));
        int iteration = 0;
        // main loop
        while (true) {
            iteration++;
            //System.out.println("This is iteration: " + iteration);
            //Pair<Integer[], List<MatchEdge>> pair = Alg.DijkstraMinHeap(N);
            //Pair<Integer[], List<MatchEdge>> pair = Alg.DijkstraFibonacciHeap(N);
            Triple<Integer[], List<MatchEdge>, Set<Integer>> pair = Alg.DijkstraFibonacciHeapEarlyStop(N);
            Integer[] distanceFromRoot = pair.getP1();
            List<MatchEdge> st_Path = pair.getP2();
            Set<Integer> permanent = pair.getP3();
            if (st_Path == null) {    // s-t path does not exist
                if (totalProfit >= profitTarget) {
                    solution = new HashMap<>(temporarySolution.size());
                    for (MatchEdge e : temporarySolution)
                        solution.put(vertexToDriver.get(e.head), e.match);
                }
                //System.out.println("SSP terminated by max-flow reached.");
                break;
            }
            
            // update node potential and reduced cost
            //for (Integer u : N.outEdges.keySet())
                //nodePotential[u] = nodePotential[u] - distanceFromRoot[u];
                       
            // augment flow along the shortest path
            // do a total profit check first
            for (int i = st_Path.size()-1; i >= 0; i--) {
                edge = st_Path.get(i);
                if (edge.reversedEdge)
                    totalProfit -= edge.profit;
                else
                    totalProfit += edge.profit;
            }
            
            if (totalProfit >= profitTarget) {
                previousTotalProfit = totalProfit;
            } else if (previousTotalProfit >= profitTarget) {
                solution = new HashMap<>(temporarySolution.size());
                for (MatchEdge e : temporarySolution)
                    solution.put(vertexToDriver.get(e.head), e.match);
                break;
            }
            
            for (Integer u : N.outEdges.keySet()) {
                if (permanent.contains(u))
                    nodePotential[u] = nodePotential[u] - distanceFromRoot[u] + distanceFromRoot[N.sinkVertex];
            }
            
            for (MatchEdge e : N.edgeSet) { // edge in the original graph \hat{N}
                if (!e.reversedEdge)
                    e.reducedCost = e.cost - nodePotential[e.tail] + nodePotential[e.head];
                // check if something went wrong, reducedCost should not be negative
                if (e.reducedCost < 0) { 
                    System.out.println("c("+e.tail+", " + e.head +") = " + e.reducedCost);
                    System.out.format("e.cost (%d) - nodePotential[e.tail] (%d) + nodePotential[e.head] (%d)%n", e.cost, nodePotential[e.tail], nodePotential[e.head]);
                    System.out.println("Iteration: " + iteration);
                    System.out.println("totalProfit: " + totalProfit);
                    System.out.println("previousTotalProfit: " + previousTotalProfit);
                    System.out.println("temporarySolution.size() = " + temporarySolution.size());
                }
            }
            
            //System.out.print("\ns-t path:");
            for (int i = st_Path.size()-1; i >= 0; i--) {
                edge = st_Path.get(i);
                //System.out.print("("+edge.tail+","+edge.head+")");
                if (edge.reversedEdge) { // the edge will be reversed, put the according match to the solution
                    edge.reversedEdge = false;
                    if (edge.match != null)
                        temporarySolution.remove(edge);
                } else {
                    if (edge.match != null)
                        temporarySolution.add(edge);
                    edge.reversedEdge = true;
                }
                // does not need to negative the weight since it is zero
                //edge.edgeForward ^= true;
                N.outEdges.get(edge.head).add(edge);
                N.outEdges.get(edge.tail).remove(edge);
                temp = edge.head;
                edge.head = edge.tail;
                edge.tail = temp;
            }
            
            //System.out.println("totalProfit: " + totalProfit);
            //System.out.println("previousTotalProfit: " + previousTotalProfit);
            //System.out.println("temporarySolution.size() = " + temporarySolution.size());
        }
        endTime = System.currentTimeMillis();
        System.out.println("Profit: " + Alg.profitOfSolution(solution));
        
        if (solution != null) {
            passengerCoveredNewExactNF[currentInterval] = solution.size();
            occupancyRateNewExactNF[currentInterval] = (double) (passengerCoveredNewExactNF[currentInterval]) / drivers.size() + 1;
            vacancyRateNewExactNF[currentInterval] = 1.0 - (double) (solution.size()) / drivers.size();
            profitNewExactNF[currentInterval] = previousTotalProfit;
            runningTimeNewExactNF[currentInterval] = endTime - startTime;
            Alg.verifySolution(solution);
            for (Map.Entry<Driver, Match> entry : solution.entrySet()) {
                if (entry.getValue().profit < 0)
                    negativeMatchesNewExactNF[currentInterval]++;
            }
            System.out.println("Number of matches: "+ solution.size() + 
                           ", Number of negative edges: " + negativeMatchesNewExactNF[currentInterval] + ", Profit: " + profitNewExactNF[currentInterval]);
        } else {
            System.out.println("No solution is found");
        }
        System.out.println("****** Running time for ExactNF-modified SSP (RPC1): " + runningTimeNewExactNF[currentInterval] + " milliseconds.\n");
        
        return true;
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

    public int[] getPassengerCoveredGreedy() {
        return passengerCoveredGreedy;
    }

    public double[] getOccupancyRateGreedy() {
        return occupancyRateGreedy;
    }

    public double[] getVacancyRateGreedy() {
        return vacancyRateGreedy;
    }

    public long[] getRunningTimeExact() {
        return runningTimeExact;
    }

    public long[] getRunningTimeGreedy() {
        return runningTimeGreedy;
    }

    public int[] getPassengerCoveredExactNF() {
        return passengerCoveredExactNF;
    }

    public double[] getOccupancyRateExactNF() {
        return occupancyRateExactNF;
    }

    public double[] getVacancyRateExactNF() {
        return vacancyRateExactNF;
    }

    public long[] getRunningTimeExactNF() {
        return runningTimeExactNF;
    }

    public int[] getProfitExact() {
        return profitExact;
    }

    public int[] getProfitExactNF() {
        return profitExactNF;
    }

    public int[] getProfitGreedy() {
        return profitGreedy;
    }

    public int[] getNegativeMatchesExact() {
        return negativeMatchesExact;
    }

    public int[] getNegativeMatchesExactNF() {
        return negativeMatchesExactNF;
    }

    public int[] getNegativeMatchesGreedy() {
        return negativeMatchesGreedy;
    }

    public int[] getPassengerCoveredNewExactNF() {
        return passengerCoveredNewExactNF;
    }

    public double[] getOccupancyRateNewExactNF() {
        return occupancyRateNewExactNF;
    }

    public double[] getVacancyRateNewExactNF() {
        return vacancyRateNewExactNF;
    }

    public long[] getRunningTimeNewExactNF() {
        return runningTimeNewExactNF;
    }

    public int[] getProfitNewExactNF() {
        return profitNewExactNF;
    }

    public int[] getNegativeMatchesNewExactNF() {
        return negativeMatchesNewExactNF;
    }
}
