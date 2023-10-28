package simulation;

import ilog.concert.*;
import ilog.cplex.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExactSolver {
    private int numTrues = 0;
    
    public ExactSolver() {}
    
    public Pair<Solution, Long> exact(List<Driver> drivers, List<Passenger> passengers, double profitTarget, int interval, int numMatches) {       
        numMatches = shouldConstructFormulation(drivers, numMatches);
        if (numMatches >= 0 && numMatches <= Integer.MAX_VALUE)
            System.out.println("*********** Building the ILP model for maximum cardinality matching for profit target "+profitTarget+"********");
        else
            return null;
        
        long startTime = System.currentTimeMillis();
        long endTime;
        HashMap<Driver, Match> matching = null;
        int index = 0;
        HashMap<Integer,Integer> matchIDToEdgeIndex = new HashMap<>(drivers.size()+passengers.size());
        HashMap<Match,Driver> matchToDriver = new HashMap<>(numMatches);
        HashMap<Integer, Match> edgeIndexToMatch = new HashMap<>(numMatches);     // for quick access, given edge id
        for (Driver driver : drivers) {
            for (Match match : driver.getMatches()) {
                matchToDriver.put(match, driver);
                matchIDToEdgeIndex.put(match.id, index);
                edgeIndexToMatch.put(index, match);
                index++;
            }
        }
        
        boolean solved;
        int weight = Integer.MIN_VALUE;
        // each hyperedge e of H(D,R,E) contains an match, each e should be represented by match binary variable x_e
        try {
            int[] objvals = new int[numMatches];
            // E_j for each j in drivers+passengers
            int runningSum = 0;
            List<Set<Integer>> E_j = new ArrayList<>(drivers.size()+passengers.size());
            
            // construct E_j for j in D
            Set<Integer> temp;
            for (Driver driver : drivers) {
                temp = new HashSet<>(driver.getMatches().size());
                for (Match match : driver.getMatches()) {
                    objvals[matchIDToEdgeIndex.get(match.id)] = match.sfp.passengers.size();
                    temp.add(matchIDToEdgeIndex.get(match.id));        // get edge id given match id, that contains this driver
                }
                if (!temp.isEmpty()) {
                    runningSum+= temp.size();
                    E_j.add(temp);
                }
            }
            
            // construct E_j for j in R
            for (Passenger passenger : passengers) {
                temp = new HashSet<>();
                // found out which edge/match contains this passenger
                for (Driver driver : drivers) {
                    for (Match match : driver.getMatches()) {
                        for (Passenger p : match.sfp.passengers) {
                            if (passenger.getID() == p.getID()) {
                                temp.add(matchIDToEdgeIndex.get(match.id));
                                break;
                            }
                        }
                    }
                }
                if (!temp.isEmpty()) {
                    runningSum+= temp.size();
                    E_j.add(temp);
                }
            }
            
            // create model and solve it
            IloCplex cplex = new IloCplex();
            //IloCplex.Param.MIP.Display;
            cplex.setParam(IloCplex.Param.MIP.Display, 0);
            
            IloNumVar[] x = cplex.boolVarArray(numMatches);
            //System.out.println("x varible type: " + x[0].getType().toString());
                        
            // objective function
            cplex.addMaximize(cplex.scalProd(x, objvals));
            
            // for all edgeIndexToMatch contain match trip (driver and passenger), create match constraint for it.
            IloLinearNumExpr expr;
            for (Set<Integer> set : E_j) {
                expr = cplex.linearNumExpr();
                if (!set.isEmpty()) {
                    for (Integer edgeIndex : set)
                        expr.addTerm(1.0, x[edgeIndex]);
                    cplex.addLe(expr, 1);
                }
            }
            
            // profit target constraint
            expr = cplex.linearNumExpr();
            for (Driver driver : drivers) {
                for (Match match : driver.getMatches()) {
                    expr.addTerm(match.profit, x[matchIDToEdgeIndex.get(match.id)]);
                }
            }
            cplex.addGe(expr, profitTarget);
            
            System.out.println("Number of Constraints: " + (runningSum+1));
            
            solved = cplex.solve();
            if (solved) {
                double[] val = cplex.getValues(x);
                matching = constructSolution(edgeIndexToMatch, matchToDriver, val, 0, 0, "maxWeight()", interval);
                endTime = System.currentTimeMillis();
                verifySolution(matching, false);
                numTrues = 0;
                for (int i = 0; i < val.length; i++) {
                    if (val[i] == 1)
                        numTrues++;
                }
                cplex.output().println("Solution status = " + cplex.getStatus());
                cplex.output().println("Solution value = " + cplex.getObjValue());
                double tempObj = cplex.getObjValue();
                if (tempObj != (int)tempObj) {
                    String[] logs = new String[2];
                    logs[0] = "maxWeight() interval: " + interval;
                    logs[1] = "Obejctive value is not an integer: " + tempObj;
                    Utility.simulationLogToFile(logs, false);
                    System.out.println("Obejctive value is not an integer.");
                }
                weight = (int)tempObj;
                System.out.println("Weight of the final solution = " + weight + " ----- with " + numTrues + " matches.");
                //int ncols = cplex.getNcols();
                //for (int j = 0; j < ncols; ++j)
                    //cplex.output().println("Column: " + j + " Value = " + val[j]);
            } else {
                endTime = System.currentTimeMillis();
            }
            cplex.end();
        } catch (IloException e) {
            System.err.println("Concert exception caught: " + e.getMessage());
            return null;
        }
        
        return new Pair(new Solution(matching, weight), endTime-startTime);
    }
    
    public Pair<Solution, Long> maxWeightILP(List<Driver> drivers, List<Passenger> passengers, int interval, int numMatches) {
        numMatches = shouldConstructFormulation(drivers, numMatches);
        if (numMatches >= 0 && numMatches <= Integer.MAX_VALUE)
            System.out.println("\n.........Building the ILP model for maximum weight matching.......");
        else
            return null;
        
        long startTime = System.currentTimeMillis();
        long endTime;
        HashMap<Driver, Match> matching = null;
        int index = 0;
        HashMap<Integer,Integer> matchIDToEdgeIndex = new HashMap<>(numMatches);
        HashMap<Match,Driver> matchToDriver = new HashMap<>(numMatches);
        HashMap<Integer, Match> edgeIndexToMatch = new HashMap<>(numMatches);     // for quick access, given edge id
        for (Driver driver : drivers) {
            for (Match match : driver.getMatches()) {
                matchToDriver.put(match, driver);
                matchIDToEdgeIndex.put(match.id, index);
                edgeIndexToMatch.put(index, match);
                index++;
            }
        }
        
        boolean solved;
        int weight = Integer.MIN_VALUE;
        try {
            int[] objvals = new int[numMatches];
            // E_j for each j in drivers+passengers
            List<Set<Integer>> E_j = new ArrayList<>(drivers.size()+passengers.size());
            
            // construct E_j for j in D
            Set<Integer> temp;
            for (Driver driver : drivers) {
                temp = new HashSet<>(driver.getMatches().size());
                for (Match match : driver.getMatches()) {
                    objvals[matchIDToEdgeIndex.get(match.id)] = match.profit;
                    temp.add(matchIDToEdgeIndex.get(match.id));        // get edge id given match id, that contains this driver
                }
                if (!temp.isEmpty()) {
                    E_j.add(temp);
                }
            }
            
            // construct E_j for j in R
            for (Passenger passenger : passengers) {
                temp = new HashSet<>();
                // found out which edge/match contains this passenger
                for (Driver driver : drivers) {
                    for (Match match : driver.getMatches()) {
                        for (Passenger p : match.sfp.passengers) {
                            if (passenger.getID() == p.getID()) {
                                temp.add(matchIDToEdgeIndex.get(match.id));
                                break;
                            }
                        }
                    }
                }
                if (!temp.isEmpty()) {
                    E_j.add(temp);
                }
            }
            //System.out.println("Constraints size: " + runningSum);
            
            // create model and solve it
            IloCplex cplex = new IloCplex();
            //IloCplex.Param.MIP.Display;
            cplex.setParam(IloCplex.Param.MIP.Display, 0);
            //cplex.setParam(IloCplex.Param.MIP.Interval, -4095);
            
            IloNumVar[] x = cplex.boolVarArray(numMatches);
            //System.out.println("x varible type: " + x[0].getType().toString());
            //IloNumVar[] x = cplex.numVarArray(numMatches, 0, 1);
            
            // objective function
            cplex.addMaximize(cplex.scalProd(x, objvals));
            
            // for all edgeIndexToMatch contain match trip (driver and passenger), create match constraint for it.
            IloLinearNumExpr expr;
            for (Set<Integer> set : E_j) {
                expr = cplex.linearNumExpr();
                if (!set.isEmpty()) {
                    for (Integer edgeIndex : set)
                        expr.addTerm(1.0, x[edgeIndex]);
                    cplex.addLe(expr, 1);
                }
            }
            
            solved = cplex.solve();
            if (solved) {
                matching = constructSolution(edgeIndexToMatch, matchToDriver, cplex.getValues(x), 0, 0, "maxWeight()", interval);
                endTime = System.currentTimeMillis();
                cplex.output().println("Solution status = " + cplex.getStatus());
                cplex.output().println("Solution value = " + cplex.getObjValue());
                double tempObj = cplex.getObjValue();
                if (tempObj != (int)tempObj) {
                    String[] logs = new String[2];
                    logs[0] = "maxWeightILP() interval: " + interval;
                    logs[1] = "Obejctive value is not an integer: " + tempObj;
                    Utility.simulationLogToFile(logs, false);
                    System.out.println("Obejctive value is not an integer.");
                }
                weight = (int)tempObj;
                verifySolution(matching, false);
            } else {
                endTime = System.currentTimeMillis();
            }
            cplex.end();
        } catch (IloException e) {
            System.err.println("Concert exception caught: " + e);
            return null;
        }
        System.out.println(".......... Running time for finding maximum weight: " + (endTime-startTime) + " milliseconds.\n");
        return new Pair(new Solution(matching, weight), endTime-startTime);
    }
    
    public Pair<Solution, Long> maxWeight(List<Driver> drivers, List<Passenger> passengers, int interval, int numMatches) {
        numMatches = shouldConstructFormulation(drivers, numMatches);
        if (numMatches >= 0 && numMatches <= Integer.MAX_VALUE)
            System.out.println("\n.........Building the LP model for maximum weight matching.......");
        else
            return null;
        
        long startTime = System.currentTimeMillis();
        long endTime;
        HashMap<Driver, Match> matching = null;
        int index = 0;
        HashMap<Integer,Integer> matchIDToEdgeIndex = new HashMap<>(numMatches);
        HashMap<Match,Driver> matchToDriver = new HashMap<>(numMatches);
        HashMap<Integer, Match> edgeIndexToMatch = new HashMap<>(numMatches);     // for quick access, given edge id
        for (Driver driver : drivers) {
            for (Match match : driver.getMatches()) {
                matchToDriver.put(match, driver);
                matchIDToEdgeIndex.put(match.id, index);
                edgeIndexToMatch.put(index, match);
                index++;
            }
        }
        
        boolean solved;
        int weight = Integer.MIN_VALUE;
        try {
            int[] objvals = new int[numMatches];
            // E_j for each j in drivers+passengers
            List<Set<Integer>> E_j = new ArrayList<>(drivers.size()+passengers.size());
            
            // construct E_j for j in D
            Set<Integer> temp;
            for (Driver driver : drivers) {
                temp = new HashSet<>(driver.getMatches().size());
                for (Match match : driver.getMatches()) {
                    objvals[matchIDToEdgeIndex.get(match.id)] = match.profit;
                    temp.add(matchIDToEdgeIndex.get(match.id));        // get edge id given match id, that contains this driver
                }
                if (!temp.isEmpty()) {
                    E_j.add(temp);
                }
            }
            
            // construct E_j for j in R
            for (Passenger passenger : passengers) {
                temp = new HashSet<>();
                // found out which edge/match contains this passenger
                for (Driver driver : drivers) {
                    for (Match match : driver.getMatches()) {
                        for (Passenger p : match.sfp.passengers) {
                            if (passenger.getID() == p.getID()) {
                                temp.add(matchIDToEdgeIndex.get(match.id));
                                break;
                            }
                        }
                    }
                }
                if (!temp.isEmpty()) {
                    E_j.add(temp);
                }
            }
            //System.out.println("Constraints size: " + runningSum);
            
            // create model and solve it
            IloCplex cplex = new IloCplex();
            //IloCplex.Param.MIP.Display;
            cplex.setParam(IloCplex.Param.MIP.Display, 0);
            //cplex.setParam(IloCplex.Param.MIP.Interval, -4095);
            
            //IloNumVar[] x = cplex.boolVarArray(numMatches);
            //System.out.println("x varible type: " + x[0].getType().toString());
            IloNumVar[] x = cplex.numVarArray(numMatches, 0, 1);
            
            // objective function
            cplex.addMaximize(cplex.scalProd(x, objvals));
            
            // for all edgeIndexToMatch contain match trip (driver and passenger), create match constraint for it.
            IloLinearNumExpr expr;
            for (Set<Integer> set : E_j) {
                expr = cplex.linearNumExpr();
                if (!set.isEmpty()) {
                    for (Integer edgeIndex : set)
                        expr.addTerm(1.0, x[edgeIndex]);
                    cplex.addLe(expr, 1);
                }
            }

            solved = cplex.solve();
            if (solved) {
                matching = constructSolution(edgeIndexToMatch, matchToDriver, cplex.getValues(x), 0, 0, "maxWeight()", interval);
                endTime = System.currentTimeMillis();
                cplex.output().println("Solution status = " + cplex.getStatus());
                cplex.output().println("Solution value = " + cplex.getObjValue());
                double tempObj = cplex.getObjValue();
                if (tempObj != (int)tempObj) {
                    String[] logs = new String[2];
                    logs[0] = "maxWeight() interval: " + interval;
                    logs[1] = "Obejctive value is not an integer: " + tempObj;
                    Utility.simulationLogToFile(logs, false);
                    System.out.println("Obejctive value is not an integer.");
                }
                weight = (int)tempObj;
                verifySolution(matching, false);
            } else {
                endTime = System.currentTimeMillis();
            }
            cplex.end();
        } catch (IloException e) {
            System.err.println("Concert exception caught: " + e);
            return null;
        }
        System.out.println(".......... Running time for finding maximum weight: " + (endTime-startTime) + " milliseconds.\n");
        return new Pair(new Solution(matching, weight), endTime-startTime);
    }
    
    public Pair<Solution, Long> maxMatchingILP(List<Driver> drivers, List<Passenger> passengers, int interval, int numMatches) {
        numMatches = shouldConstructFormulation(drivers, numMatches);
        if (numMatches >= 0 && numMatches <= Integer.MAX_VALUE)
            System.out.println("..........Building the ILP model for maximum cardinality matching.......");
        else
            return null;
        
        long startTime = System.currentTimeMillis();
        long endTime;
        int index = 0;
        HashMap<Driver, Match> maxCardinalityMatching = null;
        HashMap<Integer,Integer> matchIDToEdgeIndex = new HashMap<>(numMatches);
        HashMap<Match,Driver> matchToDriver = new HashMap<>(numMatches);
        HashMap<Integer, Match> edgeIndexToMatch = new HashMap<>(numMatches);     // for quick access, given edge id
        for (Driver driver : drivers) {
            for (Match match : driver.getMatches()) {
                matchToDriver.put(match, driver);
                matchIDToEdgeIndex.put(match.id, index);
                edgeIndexToMatch.put(index, match);
                index++;
            }
        }
        
        boolean solved;
        int weight = Integer.MIN_VALUE;
        try {
            int[] objvals = new int[numMatches];
            // E_j for each j in drivers+passengers
            int runningSum = 0;
            List<Set<Integer>> E_j = new ArrayList<>(drivers.size()+passengers.size());

            // construct E_j for j in D
            Set<Integer> temp;
            for (Driver driver : drivers) {
                temp = new HashSet<>(driver.getMatches().size());
                for (Match match : driver.getMatches()) {
                    objvals[matchIDToEdgeIndex.get(match.id)] = 1;
                    temp.add(matchIDToEdgeIndex.get(match.id));        // get edge id given match id, that contains this driver
                }
                if (!temp.isEmpty()) {
                    runningSum+= temp.size();
                    E_j.add(temp);
                }
            }
            
            // construct E_j for j in R
            for (Passenger passenger : passengers) {
                temp = new HashSet<>();
                // found out which edge/match contains this passenger
                for (Driver driver : drivers) {
                    for (Match match : driver.getMatches()) {
                        for (Passenger p : match.sfp.passengers) {
                            if (passenger.getID() == p.getID()) {
                                temp.add(matchIDToEdgeIndex.get(match.id));
                                break;
                            }
                        }
                    }
                }
                if (!temp.isEmpty()) {
                    runningSum+= temp.size();
                    E_j.add(temp);
                }
            }
            System.out.println("Constraints size: " + runningSum);
            
            // create model and solve it
            IloCplex cplex = new IloCplex();
            //IloCplex.Param.MIP.Display;
            cplex.setParam(IloCplex.Param.MIP.Display, 0);
            
            IloNumVar[] x = cplex.boolVarArray(numMatches);
            //IloNumVar[] x = cplex.numVarArray(numMatches,0,1);
            
            //System.out.println("x varible type: " + x[0].getType().toString());
            //IloNumVar[] x = cplex.numVarArray(numMatches, 0, 1);
            
            // objective function
            cplex.addMaximize(cplex.scalProd(x, objvals));
            
            // for all edgeIndexToMatch contain match trip (driver and passenger), create match constraint for it.
            IloLinearNumExpr expr;
            for (Set<Integer> set : E_j) {
                expr = cplex.linearNumExpr();
                if (!set.isEmpty()) {
                    for (Integer edgeIndex : set)
                        expr.addTerm(1.0, x[edgeIndex]);
                    cplex.addLe(expr, 1);
                }
            }

            solved = cplex.solve();
            endTime = System.currentTimeMillis();
            if (solved) {
                cplex.output().println("Solution status = " + cplex.getStatus());
                double tempObj = cplex.getObjValue();
                weight = (int)tempObj;
                if (tempObj != weight) {
                    String[] logs = new String[2];
                    logs[0] = "maxMatchingILP() interval: " + interval;
                    logs[1] = "Obejctive value is not an integer: " + tempObj;
                    Utility.simulationLogToFile(logs, false);
                    System.out.println("Obejctive value is not an integer.");
                }
                cplex.output().println("Solution value = " + cplex.getObjValue());
                
                //List<Integer> finalSolution = new ArrayList<>();
                double[] val = cplex.getValues(x);
                maxCardinalityMatching = constructSolution(edgeIndexToMatch, matchToDriver, val, 0, 0, "maxMatching()", interval);
                verifySolution(maxCardinalityMatching, false);
            }
            cplex.end();
        } catch (IloException e) {
            System.err.println("Concert exception caught: " + e);
            return null;
        }
        System.out.println(".......... Running time for finding maximum cardinality matching: " + (endTime-startTime) + " milliseconds.\n");
        return new Pair(new Solution(maxCardinalityMatching, weight), endTime-startTime);
    }
    
    public Pair<Solution, Long> maxMatching(List<Driver> drivers, List<Passenger> passengers, int interval, int numMatches) {
        numMatches = shouldConstructFormulation(drivers, numMatches);
        if (numMatches >= 0 && numMatches <= Integer.MAX_VALUE)
            System.out.println("..........Building the LP model for maximum cardinality matching.......");
        else
            return null;
        
        long startTime = System.currentTimeMillis();
        long endTime;
        int index = 0;
        HashMap<Driver, Match> maxCardinalityMatching = null;
        HashMap<Integer,Integer> matchIDToEdgeIndex = new HashMap<>(numMatches);
        HashMap<Match,Driver> matchToDriver = new HashMap<>(numMatches);
        HashMap<Integer, Match> edgeIndexToMatch = new HashMap<>(numMatches);     // for quick access, given edge id
        for (Driver driver : drivers) {
            for (Match match : driver.getMatches()) {
                matchToDriver.put(match, driver);
                matchIDToEdgeIndex.put(match.id, index);
                edgeIndexToMatch.put(index, match);
                index++;
            }
        }
        
        boolean solved;
        int weight = Integer.MIN_VALUE;
        try {
            int[] objvals = new int[numMatches];
            // E_j for each j in drivers+passengers
            int runningSum = 0;
            List<Set<Integer>> E_j = new ArrayList<>(drivers.size()+passengers.size());

            // construct E_j for j in D
            Set<Integer> temp;
            for (Driver driver : drivers) {
                temp = new HashSet<>(driver.getMatches().size());
                for (Match match : driver.getMatches()) {
                    objvals[matchIDToEdgeIndex.get(match.id)] = 1;
                    temp.add(matchIDToEdgeIndex.get(match.id));        // get edge id given match id, that contains this driver
                    //matches.put(matchIDToEdgeIndex.get(match.id), match);
                }
                if (!temp.isEmpty()) {
                    runningSum+= temp.size();
                    E_j.add(temp);
                }
            }
            
            // construct E_j for j in R
            for (Passenger passenger : passengers) {
                temp = new HashSet<>();
                // found out which edge/match contains this passenger
                for (Driver driver : drivers) {
                    for (Match match : driver.getMatches()) {
                        for (Passenger p : match.sfp.passengers) {
                            if (passenger.getID() == p.getID()) {
                                temp.add(matchIDToEdgeIndex.get(match.id));
                                break;
                            }
                        }
                    }
                }
                if (!temp.isEmpty()) {
                    runningSum+= temp.size();
                    E_j.add(temp);
                }
            }
            System.out.println("Constraints size: " + runningSum);
            
            // create model and solve it
            IloCplex cplex = new IloCplex();
            //IloCplex.Param.MIP.Display;
            cplex.setParam(IloCplex.Param.MIP.Display, 0);
            
            //IloNumVar[] x = cplex.boolVarArray(numMatches);
            IloNumVar[] x = cplex.numVarArray(numMatches,0,1);
            
            //System.out.println("x varible type: " + x[0].getType().toString());
            //IloNumVar[] x = cplex.numVarArray(numMatches, 0, 1);
            
            // objective function
            cplex.addMaximize(cplex.scalProd(x, objvals));
            
            // for all edgeIndexToMatch contain match trip (driver and passenger), create match constraint for it.
            IloLinearNumExpr expr;
            for (Set<Integer> set : E_j) {
                expr = cplex.linearNumExpr();
                if (!set.isEmpty()) {
                    for (Integer edgeIndex : set)
                        expr.addTerm(1.0, x[edgeIndex]);
                    cplex.addLe(expr, 1);
                }
            }

            solved = cplex.solve();
            endTime = System.currentTimeMillis();
            if (solved) {
                cplex.output().println("Solution status = " + cplex.getStatus());
                double tempObj = cplex.getObjValue();
                weight = (int)tempObj;
                if (tempObj != weight) {
                    String[] logs = new String[2];
                    logs[0] = "maxMatching() interval: " + interval;
                    logs[1] = "Obejctive value is not an integer: " + tempObj;
                    Utility.simulationLogToFile(logs, false);
                    System.out.println("Obejctive value is not an integer.");
                }
                cplex.output().println("Solution value = " + cplex.getObjValue());
                
                //List<Integer> finalSolution = new ArrayList<>();
                double[] val = cplex.getValues(x);
                maxCardinalityMatching = constructSolution(edgeIndexToMatch, matchToDriver, val, 0, 0, "maxMatching()", interval);
                verifySolution(maxCardinalityMatching, false);
            }
            cplex.end();
        } catch (IloException e) {
            System.err.println("Concert exception caught: " + e);
            return null;
        }
        System.out.println(".......... Running time for finding maximum cardinality matching: " + (endTime-startTime) + " milliseconds.\n");
        return new Pair(new Solution(maxCardinalityMatching, weight), endTime-startTime);
    }
    
    public Pair<Solution, Long> minCostMaxFlow(List<Driver> drivers, List<Passenger> passengers, double profitTarget, int FlowValue, int interval, int numMatches, int driverWithMatches, boolean LogResult) {
        numMatches = shouldConstructFormulation(drivers, numMatches);
        if (numMatches >= 0 && numMatches <= Integer.MAX_VALUE)
            System.out.println("Building the ILP model for min-cost max-flow for RPC1 with profitTarget "+ profitTarget + " and starting flow value "+ FlowValue);
        else
            return null;
        
        String[] logs;
        long startTime = System.currentTimeMillis();
        long endTime = 0;
        int index = 0;
        int passengersWithMatch = 0;
        for (Passenger passenger : passengers) {
            if (passenger.getNAssignments() > 0)
                passengersWithMatch++;
        }
        HashMap<Driver, Match> matching = null;
        int numberOfEdges = numMatches + driverWithMatches + passengersWithMatch + 1;
        HashMap<Match,Driver> matchToDriver = new HashMap<>(numMatches);
        HashMap<Integer, Match> edgeIndexToMatch = new HashMap<>(numMatches);     // for quick access, given edge id
        
        int[] cost = new int[numberOfEdges];
        
        HashMap<Integer,Set<Integer>> driverIDToInEdge = new HashMap<>(driverWithMatches);
        Set<Integer> edge;
        for (Driver driver : drivers) {
            if (!driver.getMatches().isEmpty()) {
                edge = new HashSet<>(1);
                edge.add(index);
                index++;
                driverIDToInEdge.put(driver.getID(), edge);
            }
        }
        
        HashMap<Integer,Set<Integer>> driverIDToOutEdge = new HashMap<>(driverWithMatches);
        HashMap<Integer,Set<Integer>> passengerIDToInEdge = new HashMap<>(passengersWithMatch);
        int id;
        for (Driver driver : drivers) {
            for (Match match : driver.getMatches()) {
                edgeIndexToMatch.put(index, match);
                id = driver.getID();
                if (driverIDToOutEdge.containsKey(id)) {
                    driverIDToOutEdge.get(id).add(index);
                } else {
                    edge = new HashSet<>(1);
                    edge.add(index);
                    driverIDToOutEdge.put(id, edge);
                }
                
                for (Passenger p : match.sfp.passengers) {
                    id = p.getID();
                    if (passengerIDToInEdge.containsKey(id)) {
                        passengerIDToInEdge.get(id).add(index);
                    } else {
                        edge = new HashSet<>(1);
                        edge.add(index);
                        passengerIDToInEdge.put(id, edge);
                    }
                }
                
                matchToDriver.put(match, driver);
                cost[index] = -match.profit;
                index++;
            }
        }

        HashMap<Integer,Set<Integer>> passegnerIDToOutEdge = new HashMap<>(passengersWithMatch);
        for (Passenger passenger : passengers) {
            if (passenger.getNAssignments() > 0) {
                edge = new HashSet<>(1);
                edge.add(index);
                index++;
                passegnerIDToOutEdge.put(passenger.getID(), edge);
            }
        }        
        //cost[numberOfEdges-1] = 0;
        
        int y;
        boolean solved = false;
        int weight = 0;
        try {            
            IloCplex cplex = new IloCplex();
            cplex.setParam(IloCplex.Param.MIP.Display, 0);
            //IloNumVar[] f = cplex.numVarArray(numberOfEdges, lower, upper);
            //IloNumVar[] f = cplex.numVarArray(numberOfEdges, 0, 1, IloNumVarType.Int);
            IloNumVar[] f = cplex.numVarArray(numberOfEdges,0,1);
            
            // objective function
            cplex.addMinimize(cplex.scalProd(f, cost));
            
            IloLinearNumExpr expr;
            IloLinearNumExpr exprRHS;
            for (Driver driver : drivers) {
                if (!driver.getMatches().isEmpty()) {
                    expr = cplex.linearNumExpr();
                    for (Integer edgeIndex : driverIDToInEdge.get(driver.getID()))
                        expr.addTerm(1.0, f[edgeIndex]);
                    exprRHS = cplex.linearNumExpr();
                    for (Integer edgeIndex : driverIDToOutEdge.get(driver.getID()))
                        exprRHS.addTerm(1.0, f[edgeIndex]);
                    cplex.addEq(expr, exprRHS);
                }
            }
            for (Passenger passenger : passengers) {
                if (passenger.getNAssignments() > 0) {
                    expr = cplex.linearNumExpr();
                    for (Integer edgeIndex : passengerIDToInEdge.get(passenger.getID()))
                        expr.addTerm(1.0, f[edgeIndex]);
                    exprRHS = cplex.linearNumExpr();
                    for (Integer edgeIndex : passegnerIDToOutEdge.get(passenger.getID()))
                        exprRHS.addTerm(1.0, f[edgeIndex]);
                    cplex.addEq(expr, exprRHS);
                }
            }
            
            expr = cplex.linearNumExpr();
            for (Passenger passenger : passengers) {
                if (passenger.getNAssignments() > 0) {
                    for (Integer edgeIndex : passengerIDToInEdge.get(passenger.getID()))
                        expr.addTerm(1.0, f[edgeIndex]);
                }
            }
            IloConstraint lastConstraint;
            
            for (y = FlowValue; y > 0; y--) {
                f[numberOfEdges-1].setUB(y);
                f[numberOfEdges-1].setLB(y);
                lastConstraint = cplex.eq(expr, f[numberOfEdges-1]);
                cplex.add(lastConstraint);
                
                solved = cplex.solve();
                if (solved) {
                    double tempObj = cplex.getObjValue();
                    if (-tempObj >= profitTarget) {
                        endTime = System.currentTimeMillis();
                        cplex.output().println("Solution status = " + cplex.getStatus());
                        cplex.output().println("Solution value = " + tempObj);
                        if (tempObj != (int)tempObj) {
                            logs = new String[2];
                            logs[0] = "minCostMaxFlow() interval: " + interval;
                            logs[1] = "Obejctive value is not an integer: " + tempObj;
                            Utility.simulationLogToFile(logs, false);
                            System.out.println("Obejctive value is not an integer.");
                        }
                        weight = (int)tempObj;
                        //verifySolution(edgeIndexToMatch, matchToDriver, cplex.getValues(f), driverWithMatches, passengersWithMatch+1);
                        matching = constructSolution(edgeIndexToMatch, matchToDriver, cplex.getValues(f), driverWithMatches, 
                                                     passengersWithMatch+1, "minCostMaxFlow()", interval);
                        cplex.end();
                        verifySolution(matching, false);
                        System.out.println("Number of times min-cost flow was solved: " + (FlowValue - y + 1));
                            System.out.println("Average running time for solving one min-cost flow is: " + (endTime-startTime) / (FlowValue - y + 1.0));
                        if (LogResult) {
                            logs = new String[2];
                            logs[0] = "Number of times min-cost flow was solved: " + (FlowValue - y + 1);
                            logs[1] = "Average running time for solving one min-cost flow is: " + (endTime-startTime) / (FlowValue - y + 1.0);
                            Utility.simulationLogToFile(logs, false);
                        }
                        break;
                    }
                }
                cplex.delete(lastConstraint);
            }
            
            if (!solved) {
                endTime = System.currentTimeMillis();
                System.out.println("****** Running time for finding optimal solution for RPC1: " + (endTime-startTime) + " milliseconds.\n");
                cplex.end();
                return new Pair(new Solution(new HashMap<>(),0), endTime-startTime);
            }
        } catch (IloException e) {
            System.err.println("Concert exception caught: " + e);
            return null;
        }
        System.out.println("****** Running time for finding optimal solution for RPC1: " + (endTime-startTime) + " milliseconds.\n");
        return new Pair(new Solution(matching, weight), endTime-startTime);
    }
    
    private int shouldConstructFormulation(List<Driver> drivers, int numMatches) {
        if (numMatches == 0) {
            for (Driver driver : drivers)
                numMatches += driver.getMatches().size();
            if (numMatches == 0) {
                System.out.println("There is no feasible match in this interval, so skipping the ILP model....");
                return 0;
            }
        }        
        
        if (numMatches > Integer.MAX_VALUE) {
            System.out.format("However, there are too many matches (%d > %d)", numMatches, Integer.MAX_VALUE);
            System.out.println("Skipping the ILP model....");
            return Integer.MAX_VALUE;
        }
        return numMatches;
    }
    
    // the average travel time for passengers in the computed ridesharing solution vs every severed passenger in a single-passenger match
    public void calculateAverageTimeDifference() {
        
    }
    
    // the average cost for passengers in the computed ridesharing solution vs every severed passenger in a single-passenger match
    public void calculateAverageCostDifferecne() {
        
    }
    
    // the number of passengers served for maximum profit vs target profit
    public void calculateTotalPassengerDifference() {
        
    }
    
    /*private void verifySolution(HashMap<Integer, Match> edgeIndexToMatch, HashMap<Match,Driver> matchToDriver, double[] variableValue) {
        List<Match> solution = new ArrayList<>(); 
        for (int i = 0; i < variableValue.length; i++) {
            if (variableValue[i] > 0)
                solution.add(edgeIndexToMatch.get(i));
        }
        Set<Driver> driversInSln = new HashSet<>();
        Set<Passenger> passengersInSln = new HashSet<>();
        Set<Passenger> temp;
        for (Match match : solution) {
            if (driversInSln.contains(matchToDriver.get(match))) {
                System.out.println("Incorrect solution- multiple drivers.");
            } else {
                driversInSln.add(matchToDriver.get(match));
            }
            temp = new HashSet<>();
            temp.addAll(Arrays.asList(match.sfp.passengers));
            for (Passenger p : temp) {
                if (passengersInSln.contains(p)) {
                    System.out.println("Incorrect solution- multiple passengers.");
                } else {
                    passengersInSln.add(p);
                }
            }
        }
    }*/
    
    private HashMap<Driver, Match>constructSolution(HashMap<Integer, Match> edgeIndexToMatch, HashMap<Match,Driver> matchToDriver, 
                                                    double[] variableValue, int startOffset, int endOffset, String funcName, int interval) {
        int nPositive = 0;
        String[] logs = new String[2];
        for (int i = startOffset; i < variableValue.length - endOffset; i++) {
            if (variableValue[i] > 0) {
                if (variableValue[i] <= 0.000001)
                    variableValue[i] = 0;
                else if (variableValue[i] >= 0.999999 || variableValue[i] <= 1.000001) {
                    variableValue[i] = 1;
                    nPositive++;
                } else {
                    logs[0] = funcName + " interval: " + interval;
                    logs[1] = "Non-integral variable: "+ variableValue[i] + " at index "+ i;
                    Utility.simulationLogToFile(logs, false);
                    System.out.println("Non-integral variable: "+ variableValue[i] + " at index "+ i);
                }
            }
        }
        HashMap<Driver, Match> solution = new HashMap<>(nPositive);
        Driver d;
        for (int i = startOffset; i < variableValue.length - endOffset; i++) {
            if (variableValue[i] > 0) {
                d = matchToDriver.get(edgeIndexToMatch.get(i));
                if (solution.containsKey(d))
                    System.out.println("Incorrect solution- multiple drivers: " + d.toStringAll());
                else
                    solution.put(matchToDriver.get(edgeIndexToMatch.get(i)), edgeIndexToMatch.get(i));
            }
        }
        return solution;
    }
    
    private void verifySolution(HashMap<Driver, Match> solution, boolean display) {
        Set<Driver> driversInSln = new HashSet<>();
        Set<Passenger> passengersInSln = new HashSet<>();
        System.out.println("solution size = " + solution.size());
        for (Map.Entry<Driver, Match> entry : solution.entrySet()) {
            for (Passenger p : entry.getValue().sfp.passengers) {
                if (passengersInSln.contains(p))
                    System.out.println("Incorrect solution- multiple passengers: " + p.toStringAll());
                else
                    passengersInSln.add(p);
            }
        }
        
        if (display) {
            System.out.println("Drivers: " + driversInSln.toString());
            System.out.println("Passengers: " + passengersInSln.toString());
        }
    }
}
