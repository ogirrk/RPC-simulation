package simulation;

//import java.util.ArrayList;
//import java.util.Arrays;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SimulationOperator {
    private int currentInterval;
    private int currentHour = 0;		// simulated timer, related to N and IntervalInMinute
    
    private final SimulationLoader simLoader;
    private final TripGenerator tripGenerator;
    private final Algorithms alg;
    private final ExactSolver cplex;
    private final RPCOne RpcOne;
    private final RPCPlus RpcPlus;
    
    private List<Driver> currentDrivers = null;
    private List<Passenger> currentPassengers = null;
    private int[] numTripsGenerated;
    private int[] numDriversGenerated;
    private int[] numPassengersGenerated;
    private int[] numMatchesRemoved;
    private int[] numMatches;
    private int[] numNegativeMatches;
    private int[] passengerCoveredRP;
    private double[] occupancyRateRP;
    private double[] vacancyRateRP;
    private int[] maxWeightRP;
    private long[] runningTimeRP;
    
    private final HashMap<Integer, TimePeriod> ProfileHours = new HashMap<>(10);
    private long[] computeTimeAlg1;     // time it takes to find all base matches
    private long[] computeTimeAlg2;     // time it takes to find all feasible matches
    private int numDriversWithMatch = 0;
    private double profitTarget = 0d;
    private double[] profitTargets;
    private boolean[] validIntervalResults;
    private final boolean LogResult;
    private Writer writer = null;
    
    public SimulationOperator(SimulationLoader loader, Algorithms alg, TripGenerator tripGenerator, boolean logResult) {
        simLoader = loader;
        this.alg = alg;
        this.tripGenerator = tripGenerator;
        this.LogResult = logResult;
        this.cplex = new ExactSolver();
        if (SimulationParameters.computeDistanceMethod == 1) {
            this.RpcOne = new RPCOne(SimulationParameters.numberOfIntervals, cplex, alg, true);
            this.RpcPlus = new RPCPlus(SimulationParameters.numberOfIntervals, alg, cplex, true);
        } else {
            this.RpcOne = new RPCOne(SimulationParameters.numberOfIntervals, cplex, alg, false);
            this.RpcPlus = new RPCPlus(SimulationParameters.numberOfIntervals, alg, cplex, false);
        }
        initialize();
    }
    
    public void startSimulation() {
        numTripsGenerated = new int[SimulationParameters.numberOfIntervals];
        numDriversGenerated = new int[SimulationParameters.numberOfIntervals];
        numPassengersGenerated = new int[SimulationParameters.numberOfIntervals];
        numMatchesRemoved = new int[SimulationParameters.numberOfIntervals];
        numMatches = new int[SimulationParameters.numberOfIntervals];
        numNegativeMatches = new int[SimulationParameters.numberOfIntervals];
        passengerCoveredRP = new int[SimulationParameters.numberOfIntervals];
        occupancyRateRP = new double[SimulationParameters.numberOfIntervals];
        vacancyRateRP = new double[SimulationParameters.numberOfIntervals];
        maxWeightRP = new int[SimulationParameters.numberOfIntervals];
        runningTimeRP = new long[SimulationParameters.numberOfIntervals];
        
        validIntervalResults = new boolean[SimulationParameters.numberOfIntervals];
        computeTimeAlg1 = new long[SimulationParameters.numberOfIntervals];
        computeTimeAlg2 = new long[SimulationParameters.numberOfIntervals];
        profitTargets = new double[SimulationParameters.numberOfIntervals];
        
        if (LogResult) {
            writer = Utility.startSimulationLogToFile(SimulationParameters.allParameters());
        }
        System.out.println("+++++++++++++++ Total number of simulated intervals is " + SimulationParameters.numberOfIntervals + " +++++++++++++++");
        try {
            long startTime;
            long endTime;
            long runTime;
            long accumlativeTime = 0;
            Pair<List<Driver>, List<Passenger>> driversAndPassengers;

            for (currentInterval = 0; currentInterval < SimulationParameters.numberOfIntervals; currentInterval++) {
                startTime = System.currentTimeMillis();
                currentHour = (int) Math.floor(currentInterval * SimulationParameters.intervalInMinute / 60D) + SimulationParameters.startHour;
                System.out.format("==================== Iteration #%d -- Current Hour: %d:%02d ====================%n",
                                    (currentInterval+1), currentHour, (currentInterval % (60/SimulationParameters.intervalInMinute)) * SimulationParameters.intervalInMinute);

                if (currentInterval < Utility.skipToIteration) {
                    validIntervalResults[currentInterval] = false;
                    continue;
                }

                if (simLoader.isLoadGeneratedTrips()) {
                    driversAndPassengers = simLoader.loadTripsFromFile(currentInterval, alg);
                    if (driversAndPassengers == null)
                        continue;
                    currentDrivers = driversAndPassengers.getP1();
                    currentPassengers = driversAndPassengers.getP2();
                    numDriversGenerated[currentInterval] = currentDrivers.size();
                    numPassengersGenerated[currentInterval] = currentPassengers.size();
                    numTripsGenerated[currentInterval] = currentDrivers.size()+currentPassengers.size();
                    System.out.println("Number of drivers loaded in this interval: " + currentDrivers.size());
                    System.out.println("Number of passengers loaded in this interval: " + currentPassengers.size());
                    System.out.println("Number of trips loaded in this interval: " + numTripsGenerated[currentInterval]);
                    alg.matchLoadedSetup(currentDrivers, currentPassengers, currentHour-SimulationParameters.startHour);
                    Pair<Integer,Integer> pair = alg.countTotalNumMatches(currentDrivers);
                    numMatches[currentInterval] = pair.getP1();
                    numDriversWithMatch = pair.getP2();
                    System.out.println("Number of matches: "+ numMatches[currentInterval]);
                    System.out.println("Number of drivers with at least one match: "+ numDriversWithMatch);
                    //alg.displayData();
                } else {
                    if (SimulationParameters.algorithmBaseMatches) {
                        if (Utility.DEBUG) {
                            runTime = debugTrips();
                        } else {
                            runTime = generateTrips();
                        }
                        System.out.println("Time it took to generate trips: " + (runTime)/1000 + " seconds.");
                        numDriversGenerated[currentInterval] = currentDrivers.size();
                        numPassengersGenerated[currentInterval] = currentPassengers.size();
                        numTripsGenerated[currentInterval] = currentDrivers.size()+currentPassengers.size();
                        System.out.println("Number of trips generated in this interval: " + numTripsGenerated[currentInterval]);
                        
                        if (!createBaseMatches()) {
                            System.out.println("Base matches were not computed correctly. This interval ("+(currentInterval+1)+") is skipped");
                            continue;
                        }
                        
                        if ((SimulationParameters.problemVariant == 2) || (SimulationParameters.problemVariant == 1 && SimulationParameters.algorithmReduceBaseMatches))
                            reduceBaseMatches();
                        else
                            System.out.println("- Base matches are not reduced.");
                    } else {
                        System.out.println("Select a correct algorithm to execute. Since trips are not loaded, base matches should be computed (AlgorithmBaseMatches=True required).");
                        break;
                    }
                }
                
                switch(SimulationParameters.problemVariant) {
                    case 1:
                        if (simLoader.isOnlyGenerateTrips()) {
                            numNegativeMatches[currentInterval] = alg.getNumNegativeMatches(currentDrivers);
                            if (simLoader.writeTripsToFile(currentDrivers, currentPassengers, currentInterval, alg)) {
                                validIntervalResults[currentInterval] = true;
                                endTime = System.currentTimeMillis();
                                System.out.println("The simulation duration for this iteration #" + (currentInterval+1) + " is " + (endTime - startTime)/1000 + " seconds.\n");
                                accumlativeTime = accumlativeTime + (endTime - startTime);
                                continue;
                            } else {
                                System.out.println("Trips data could not be written to file. Aborting program.....");
                                System.exit(0);
                            }
                        }

                        if (SimulationParameters.algorithmRP || SimulationParameters.algorithmComputeSolutions) {
                            if (SimulationParameters.algorithmRP) {
                                if (!solveRP(false)) {
                                    endTime = System.currentTimeMillis();
                                    System.out.println("Incorrect computation while solving RP.");
                                    System.out.println("The simulation duration for this iteration #" + (currentInterval+1) + " is " + (endTime - startTime)/1000 + " seconds.\n");
                                    accumlativeTime = accumlativeTime + (endTime - startTime);
                                    continue;
                                }
                            } else {
                               if (!performVariantOneAlgorithms()) {
                                    endTime = System.currentTimeMillis();
                                    System.out.println("Incorrect computation while solving RPC1.");
                                    System.out.println("The simulation duration for this iteration #" + (currentInterval+1) + " is " + (endTime - startTime)/1000 + " seconds.\n");
                                    accumlativeTime = accumlativeTime + (endTime - startTime);
                                    continue;
                                }
                            }
                        } else {
                            System.out.println("Select a correct algorithm to execute: AlgorithmRP or AlgorithmComputeSolutions.");
                            continue;
                        }
                        break;
                    case 2:
                        if (SimulationParameters.algorithmAllMatches) {
                            if (!computeAllMatches()) {
                                System.out.println("ProblemVariant 2: Matches were not computed correctly. This interval ("+(currentInterval+1)+") is skipped");
                                endTime = System.currentTimeMillis();
                                accumlativeTime = accumlativeTime + (endTime - startTime);
                                continue;
                            }
                        }

                        if (simLoader.isOnlyGenerateTrips()) {
                            if (simLoader.writeTripsToFile(currentDrivers, currentPassengers, currentInterval, alg)) {
                                validIntervalResults[currentInterval] = true;
                                endTime = System.currentTimeMillis();
                                System.out.println("The simulation duration for this iteration #" + (currentInterval+1) + " is " + (endTime - startTime)/1000 + " seconds.\n");
                                accumlativeTime = accumlativeTime + (endTime - startTime);
                                continue;
                            } else {
                                System.out.println("Trips data could not be written to file. Aborting program.....");
                                System.exit(0);
                            }
                        }
						
                        if (SimulationParameters.algorithmRP || SimulationParameters.algorithmComputeSolutions) {
                            if (SimulationParameters.algorithmRP) {
                                if (!solveRP(false)) {
                                    endTime = System.currentTimeMillis();
                                    System.out.println("Incorrect computation while solving RP.");
                                    System.out.println("The simulation duration for this iteration #" + (currentInterval+1) + " is " + (endTime - startTime)/1000 + " seconds.\n");
                                    accumlativeTime = accumlativeTime + (endTime - startTime);
                                    continue;
                                }
                            } else {
                               if (!RpcPlus.LS2WithoutGraph(currentDrivers, currentPassengers, numMatches[currentInterval], currentInterval, 
                                       SimulationParameters.profitTargetMultiplier, SimulationParameters.lowerBoundProfitTarget)) {
                                    endTime = System.currentTimeMillis();
                                    accumlativeTime = accumlativeTime + (endTime - startTime);
                                    continue;
                                }
                                if (!RpcPlus.Exact(currentDrivers, currentPassengers, numMatches[currentInterval], currentInterval, 
                                        SimulationParameters.profitTargetMultiplier, SimulationParameters.lowerBoundProfitTarget)) {
                                    endTime = System.currentTimeMillis();
                                    accumlativeTime = accumlativeTime + (endTime - startTime);
                                    continue;
                                }
                                /* alternative implementations of LS2
                                if (!RpcPlus.LS2(currentDrivers, currentPassengers, numMatches[currentInterval], currentInterval, false)) {
                                    endTime = System.currentTimeMillis();
                                    accumlativeTime = accumlativeTime + (endTime - startTime);
                                    continue;
                                }
                                if (!RpcPlus.LS2Plus(currentDrivers, currentPassengers, numMatches[currentInterval], currentInterval, true)) {
                                    endTime = System.currentTimeMillis();
                                    accumlativeTime = accumlativeTime + (endTime - startTime);
                                    continue;
                                }
                                */
                                profitTargets[currentInterval] = RpcPlus.getProfitTarget();
                            }
                        } else {
                            System.out.println("Select a correct algorithm to execute: AlgorithmRP or AlgorithmComputeSolutions.");
                            continue;
                        }
                        break;
                        
                    default:
                        System.out.println("Select a correct Problem Variant.");
                        continue;
                }
                
                //displayNumMatchesPassengersIn();
                validIntervalResults[currentInterval] = true;
                endTime = System.currentTimeMillis();
                System.out.println("The simulation duration for this iteration #" + (currentInterval+1) + " is " + (endTime - startTime)/1000 + " seconds.\n");
                accumlativeTime = accumlativeTime + (endTime - startTime);
            }
            System.out.println("+++++++++++++++++++++ The whole simulation took " + (accumlativeTime/1000) + " seconds. +++++++++++++++++++++");
            simulationResults();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println(Arrays.toString(e.getStackTrace()));
        }
        
        if (LogResult)
            Utility.finalSimulationLog(writer);
    }
    
    private void problemVariantOneResults(boolean write) {
        if (write) {
            
        } else {
            int validResults = 0;
            for (int i = 0; i < SimulationParameters.numberOfIntervals; i++) {
                if (validIntervalResults[i])
                    validResults++;
            }
            if (validResults != SimulationParameters.numberOfIntervals)
                System.out.println("Number of intervals with valid results: " + validResults);
            
            if (validResults > 0) {
                commonTripsInfo(false, validResults);
                if (simLoader.isOnlyGenerateTrips())
                    return;
                
                System.out.print(Utility.newline);
                System.out.println("=============================================================================================");
                
                if (SimulationParameters.algorithmRP) {
                    RPresults(validResults);
                    System.out.println("\n=============================================================================================");
                }
                
                if (SimulationParameters.algorithmComputeSolutions) {
                    System.out.print(Utility.newline);
                    System.out.println("/////////////// Display (RPC1) results ////////////////");
                    System.out.println("Profit target for each interval:\n" + Arrays.toString(profitTargets));
                    System.out.format("Total profit targets: %.2f%n", Arrays.stream(profitTargets).sum());
                    System.out.println("=============================================================================================");
                    System.out.println("Number of passengers served by Greedy in each interval:\n" + Arrays.toString(RpcOne.getPassengerCoveredGreedy()));
                    double[] results = new double[SimulationParameters.numberOfIntervals];
                    for (int i = 0; i < SimulationParameters.numberOfIntervals; i++)
                        results[i] = (double) (RpcOne.getPassengerCoveredGreedy()[i]) / numPassengersGenerated[i];
                    printResult(results, "% of passengers served by Greedy in each interval: ","5f");

                    long sum = Arrays.stream(RpcOne.getPassengerCoveredGreedy()).sum();
                    double avg = (double) (sum) / validResults;
                    System.out.format("Total number of passengers served: %d -- Average number of passengers served per interval: %.3f%n", sum, avg);
                    
                    System.out.println("Number of negative matches by Greedy in each interval:\n" + Arrays.toString(RpcOne.getNegativeMatchesGreedy()));
                    sum = Arrays.stream(RpcOne.getNegativeMatchesGreedy()).sum();
                    avg = (double) (sum) / validResults;
                    System.out.format("Total number of negative matches: %d -- Average number of negative matches per interval: %.3f%n", sum, avg);
                    
                    System.out.println("Profit of the solution found by Greedy in each interval:\n" + Arrays.toString(RpcOne.getProfitGreedy()));
                    sum = Arrays.stream(RpcOne.getProfitGreedy()).sum();
                    avg = (double) (sum) / validResults;
                    System.out.format("Total Profit of all solutions: %d -- Average profit of a solution found per interval: %.3f%n", sum, avg);

                    printResult(RpcOne.getOccupancyRateGreedy(), "The mean occupancy rate by Greedy in each interval:", "4f");
                    avg = Arrays.stream(RpcOne.getOccupancyRateGreedy()).sum() / validResults;
                    System.out.format("The average occupancy rate per interval: %.4f%n", avg);
                    printResult(RpcOne.getVacancyRateGreedy(), "The mean vacancy rate by Greedy in each interval:","4f");
                    avg = Arrays.stream(RpcOne.getVacancyRateGreedy()).sum() / validResults;
                    System.out.format("The average vacancy rate per interval: %.4f%n", avg);

                    System.out.println("Computational Time in (ms) of Greedy: " + Arrays.toString(RpcOne.getRunningTimeGreedy()));
                    avg = (double) Arrays.stream(RpcOne.getRunningTimeGreedy()).sum() / validResults;
                    System.out.format("The Computational time per interval: %.4f%n", avg);

                    System.out.println("\n=============================================================================================");
                    System.out.println("Number of passengers served by Exact-NetworkFlow in each interval:\n" + Arrays.toString(RpcOne.getPassengerCoveredExactNF()));
                    results = new double[SimulationParameters.numberOfIntervals];
                    for (int i = 0; i < SimulationParameters.numberOfIntervals; i++)
                        results[i] = (double) (RpcOne.getPassengerCoveredExactNF()[i]) / numPassengersGenerated[i];
                    printResult(results, "% of passengers served by Exact-NetworkFlow in each interval: ","5f");

                    sum = Arrays.stream(RpcOne.getPassengerCoveredExactNF()).sum();
                    avg = (double) (sum) / validResults;
                    System.out.format("Total number of passengers served: %d -- Average number of passengers served per interval: %.3f%n", sum, avg);
                    
                    System.out.println("Number of negative matches by Exact-NetworkFlow in each interval:\n" + Arrays.toString(RpcOne.getNegativeMatchesExactNF()));
                    sum = Arrays.stream(RpcOne.getNegativeMatchesExactNF()).sum();
                    avg = (double) (sum) / validResults;
                    System.out.format("Total number of negative matches: %d -- Average number of negative matches per interval: %.3f%n", sum, avg);
                    
                    System.out.println("Profit of the solution found by Exact-NetworkFlow in each interval:\n" + Arrays.toString(RpcOne.getProfitExactNF()));
                    sum = Arrays.stream(RpcOne.getProfitExactNF()).sum();
                    avg = (double) (sum) / validResults;
                    System.out.format("Total Profit of all solutions: %d -- Average profit of a solution found per interval: %.3f%n", sum, avg);

                    printResult(RpcOne.getOccupancyRateExactNF(), "The mean occupancy rate by Exact-NetworkFlow in each interval:", "4f");
                    avg = Arrays.stream(RpcOne.getOccupancyRateExactNF()).sum() / validResults;
                    System.out.format("The average occupancy rate per interval: %.4f%n", avg);
                    printResult(RpcOne.getVacancyRateExactNF(), "The mean vacancy rate by Exact-NetworkFlow in each interval:","4f");
                    avg = Arrays.stream(RpcOne.getVacancyRateExactNF()).sum() / validResults;
                    System.out.format("The average vacancy rate per interval: %.4f%n", avg);

                    System.out.println("Computational Time in (ms) of Exact-NetworkFlow: " + Arrays.toString(RpcOne.getRunningTimeExactNF()));
                    avg = (double) Arrays.stream(RpcOne.getRunningTimeExactNF()).sum() / validResults;
                    System.out.format("The Computational time per interval: %.4f%n", avg);

                    System.out.println("\n=============================================================================================");
                    System.out.println("Number of passengers served by Exact-Formulation in each interval:\n" + Arrays.toString(RpcOne.getPassengerCoveredExact()));
                    results = new double[SimulationParameters.numberOfIntervals];
                    for (int i = 0; i < SimulationParameters.numberOfIntervals; i++)
                        results[i] = (double) (RpcOne.getPassengerCoveredExact()[i]) / numPassengersGenerated[i];
                    printResult(results, "% of passengers served by Exact-Formulation in each interval: ","5f");

                    sum = Arrays.stream(RpcOne.getPassengerCoveredExact()).sum();
                    avg = (double) (sum) / validResults;
                    System.out.format("Total number of passengers served: %d -- Average number of passengers served per interval: %.3f%n", sum, avg);
                    
                    System.out.println("Number of negative matches by Exact-Formulation in each interval:\n" + Arrays.toString(RpcOne.getNegativeMatchesExact()));
                    sum = Arrays.stream(RpcOne.getNegativeMatchesExact()).sum();
                    avg = (double) (sum) / validResults;
                    System.out.format("Total number of negative matches: %d -- Average number of negative matches per interval: %.3f%n", sum, avg);
                    
                    System.out.println("Profit of the solution found by Exact-Formulation in each interval:\n" + Arrays.toString(RpcOne.getProfitExact()));
                    sum = Arrays.stream(RpcOne.getProfitExact()).sum();
                    avg = (double) (sum) / validResults;
                    System.out.format("Total Profit of all solutions: %d -- Average profit of a solution found per interval: %.3f%n", sum, avg);

                    printResult(RpcOne.getOccupancyRateExact(), "The mean occupancy rate by Exact-Formulation in each interval:", "4f");
                    avg = Arrays.stream(RpcOne.getOccupancyRateExact()).sum() / validResults;
                    System.out.format("The average occupancy rate per interval: %.4f%n", avg);
                    printResult(RpcOne.getVacancyRateExact(), "The mean vacancy rate by Exact-Formulation in each interval:","4f");
                    avg = Arrays.stream(RpcOne.getVacancyRateExact()).sum() / validResults;
                    System.out.format("The average vacancy rate per interval: %.4f%n", avg);

                    System.out.println("Computational Time in (ms) of Exact-Formulation: " + Arrays.toString(RpcOne.getRunningTimeExact()));
                    avg = (double) Arrays.stream(RpcOne.getRunningTimeExact()).sum() / validResults;
                    System.out.format("The Computational time per interval: %.4f%n", avg);
                    
                    System.out.println("\n=============================================================================================");
                    System.out.println("Number of passengers served by ExactNF (modified SSP) in each interval:\n" + Arrays.toString(RpcOne.getPassengerCoveredNewExactNF()));
                    results = new double[SimulationParameters.numberOfIntervals];
                    for (int i = 0; i < SimulationParameters.numberOfIntervals; i++)
                        results[i] = (double) (RpcOne.getPassengerCoveredNewExactNF()[i]) / numPassengersGenerated[i];
                    printResult(results, "% of passengers served by NewExactNF in each interval: ","5f");

                    sum = Arrays.stream(RpcOne.getPassengerCoveredNewExactNF()).sum();
                    avg = (double) (sum) / validResults;
                    System.out.format("Total number of passengers served: %d -- Average number of passengers served per interval: %.3f%n", sum, avg);
                    
                    System.out.println("Number of negative matches by NewExactNF in each interval:\n" + Arrays.toString(RpcOne.getNegativeMatchesNewExactNF()));
                    sum = Arrays.stream(RpcOne.getNegativeMatchesNewExactNF()).sum();
                    avg = (double) (sum) / validResults;
                    System.out.format("Total number of negative matches: %d -- Average number of negative matches per interval: %.3f%n", sum, avg);
                    
                    System.out.println("Profit of the solution found by NewExactNF in each interval:\n" + Arrays.toString(RpcOne.getProfitNewExactNF()));
                    sum = Arrays.stream(RpcOne.getProfitNewExactNF()).sum();
                    avg = (double) (sum) / validResults;
                    System.out.format("Total Profit of all solutions: %d -- Average profit of a solution found per interval: %.3f%n", sum, avg);

                    printResult(RpcOne.getOccupancyRateNewExactNF(), "The mean occupancy rate by NewExactNF in each interval:", "4f");
                    avg = Arrays.stream(RpcOne.getOccupancyRateNewExactNF()).sum() / validResults;
                    System.out.format("The average occupancy rate per interval: %.4f%n", avg);
                    printResult(RpcOne.getVacancyRateNewExactNF(), "The mean vacancy rate by NewExactNF in each interval:","4f");
                    avg = Arrays.stream(RpcOne.getVacancyRateNewExactNF()).sum() / validResults;
                    System.out.format("The average vacancy rate per interval: %.4f%n", avg);

                    System.out.println("Computational Time in (ms) of NewExactNF: " + Arrays.toString(RpcOne.getRunningTimeNewExactNF()));
                    avg = (double) Arrays.stream(RpcOne.getRunningTimeNewExactNF()).sum() / validResults;
                    System.out.format("The Computational time per interval: %.4f%n", avg);
                }
            }
        }
    }
    
    private void problemVariantTwoResults(boolean write) {
        if (write) {
            
        } else {
            int validResults = 0;
            for (int i = 0; i < SimulationParameters.numberOfIntervals; i++) {
                if (validIntervalResults[i])
                    validResults++;
            }
            if (validResults != SimulationParameters.numberOfIntervals)
                System.out.println("Number of intervals with valid results: " + validResults);
            
            if (validResults > 0) {
                commonTripsInfo(false, validResults);
                if (simLoader.isOnlyGenerateTrips())
                    return;
                
                System.out.print(Utility.newline);
                System.out.println("=============================================================================================");
                    
                if (SimulationParameters.algorithmRP) {
                    RPresults(validResults);
                    System.out.println("\n=============================================================================================");
                }
                          
                if (SimulationParameters.algorithmComputeSolutions) {
                    System.out.print(Utility.newline);
                    System.out.println("/////////////// Display (RPC+) results ////////////////");
                    System.out.println("Profit target for each interval:\n" + Arrays.toString(profitTargets));
                    System.out.format("Total profit targets: %.2f%n", Arrays.stream(profitTargets).sum());
                    System.out.println("=============================================================================================");
                    System.out.println("Number of passengers served by Greedy in each interval:\n" + Arrays.toString(RpcPlus.getPassengerCoveredGreedy()));
                    double[] results = new double[SimulationParameters.numberOfIntervals];
                    for (int i = 0; i < SimulationParameters.numberOfIntervals; i++)
                        results[i] = (double) (RpcPlus.getPassengerCoveredGreedy()[i]) / numPassengersGenerated[i];
                    printResult(results, "% of passengers served by Greedy in each interval: ","5f");

                    long sum = Arrays.stream(RpcPlus.getPassengerCoveredGreedy()).sum();
                    double avg = (double) (sum) / validResults;
                    System.out.format("Total number of passengers served: %d -- Average number of passengers served per interval: %.3f%n", sum, avg);
                    
                    System.out.println("Number of matches by Greedy in each interval:\n" + Arrays.toString(RpcPlus.getNumMatchesGreedy()));
                    sum = Arrays.stream(RpcPlus.getNumMatchesGreedy()).sum();
                    avg = (double) (sum) / validResults;
                    System.out.format("Total number of matches: %d -- Average number of matches per interval: %.3f%n", sum, avg);
                    
                    System.out.println("Profit of the solution found by Greedy in each interval:\n" + Arrays.toString(RpcPlus.getProfitGreedy()));
                    sum = Arrays.stream(RpcPlus.getProfitGreedy()).sum();
                    avg = (double) (sum) / validResults;
                    System.out.format("Total Profit of all solutions: %d -- Average profit of a solution found per interval: %.3f%n", sum, avg);

                    printResult(RpcPlus.getOccupancyRateGreedy(), "The mean occupancy rate by Greedy in each interval:", "4f");
                    avg = Arrays.stream(RpcPlus.getOccupancyRateGreedy()).sum() / validResults;
                    System.out.format("The average occupancy rate per interval: %.4f%n", avg);
                    printResult(RpcPlus.getVacancyRateGreedy(), "The mean vacancy rate by Greedy in each interval:","4f");
                    avg = Arrays.stream(RpcPlus.getVacancyRateGreedy()).sum() / validResults;
                    System.out.format("The average vacancy rate per interval: %.4f%n", avg);

                    System.out.println("Computational Time in (ms) of Greedy: " + Arrays.toString(RpcPlus.getRunningTimeGreedy()));
                    avg = (double) Arrays.stream(RpcPlus.getRunningTimeGreedy()).sum() / validResults;
                    System.out.format("The Computational time per interval: %.4f%n", avg);

                    System.out.println("\n=============================================================================================");
                    System.out.println("Number of passengers served by LS2 in each interval:\n" + Arrays.toString(RpcPlus.getPassengerCoveredLS2Plus()));
                    results = new double[SimulationParameters.numberOfIntervals];
                    for (int i = 0; i < SimulationParameters.numberOfIntervals; i++)
                        results[i] = (double) (RpcPlus.getPassengerCoveredLS2Plus()[i]) / numPassengersGenerated[i];
                    printResult(results, "% of passengers served by LS2 in each interval: ","5f");

                    sum = Arrays.stream(RpcPlus.getPassengerCoveredLS2Plus()).sum();
                    avg = (double) (sum) / validResults;
                    System.out.format("Total number of passengers served: %d -- Average number of passengers served per interval: %.3f%n", sum, avg);
                    
                    System.out.println("Number of matches by LS2 in each interval:\n" + Arrays.toString(RpcPlus.getNumMatchesLS2Plus()));
                    sum = Arrays.stream(RpcPlus.getNumMatchesLS2Plus()).sum();
                    avg = (double) (sum) / validResults;
                    System.out.format("Total number of matches: %d -- Average number of matches per interval: %.3f%n", sum, avg);
                    
                    System.out.println("Profit of the solution found by LS2 in each interval:\n" + Arrays.toString(RpcPlus.getProfitLS2Plus()));
                    sum = Arrays.stream(RpcPlus.getProfitLS2Plus()).sum();
                    avg = (double) (sum) / validResults;
                    System.out.format("Total Profit of all solutions: %d -- Average profit of a solution found per interval: %.3f%n", sum, avg);

                    printResult(RpcPlus.getOccupancyRateLS2Plus(), "The mean occupancy rate by LS2 in each interval:", "4f");
                    avg = Arrays.stream(RpcPlus.getOccupancyRateLS2Plus()).sum() / validResults;
                    System.out.format("The average occupancy rate per interval: %.4f%n", avg);
                    printResult(RpcPlus.getVacancyRateLS2Plus(), "The mean vacancy rate by LS2 in each interval:","4f");
                    avg = Arrays.stream(RpcPlus.getVacancyRateLS2Plus()).sum() / validResults;
                    System.out.format("The average vacancy rate per interval: %.4f%n", avg);

                    System.out.println("Computational Time in (ms) of LS2: " + Arrays.toString(RpcPlus.getRunningTimeLS2Plus()));
                    avg = (double) Arrays.stream(RpcPlus.getRunningTimeLS2Plus()).sum() / validResults;
                    System.out.format("The Computational time per interval: %.4f%n", avg);

                    System.out.println("\n=============================================================================================");
                    System.out.println("Number of passengers served by Exact-Formulation in each interval:\n" + Arrays.toString(RpcPlus.getPassengerCoveredExact()));
                    results = new double[SimulationParameters.numberOfIntervals];
                    for (int i = 0; i < SimulationParameters.numberOfIntervals; i++)
                        results[i] = (double) (RpcPlus.getPassengerCoveredExact()[i]) / numPassengersGenerated[i];
                    printResult(results, "% of passengers served by Exact-Formulation in each interval: ","5f");

                    sum = Arrays.stream(RpcPlus.getPassengerCoveredExact()).sum();
                    avg = (double) (sum) / validResults;
                    System.out.format("Total number of passengers served: %d -- Average number of passengers served per interval: %.3f%n", sum, avg);
                    
                    System.out.println("Number of matches by Exact-Formulation in each interval:\n" + Arrays.toString(RpcPlus.getNumMatchesExact()));
                    sum = Arrays.stream(RpcPlus.getNumMatchesExact()).sum();
                    avg = (double) (sum) / validResults;
                    System.out.format("Total number of matches: %d -- Average number of matches per interval: %.3f%n", sum, avg);
                    
                    System.out.println("Profit of the solution found by Exact-Formulation in each interval:\n" + Arrays.toString(RpcPlus.getProfitExact()));
                    sum = Arrays.stream(RpcPlus.getProfitExact()).sum();
                    avg = (double) (sum) / validResults;
                    System.out.format("Total Profit of all solutions: %d -- Average profit of a solution found per interval: %.3f%n", sum, avg);

                    printResult(RpcPlus.getOccupancyRateExact(), "The mean occupancy rate by Exact-Formulation in each interval:", "4f");
                    avg = Arrays.stream(RpcPlus.getOccupancyRateExact()).sum() / validResults;
                    System.out.format("The average occupancy rate per interval: %.4f%n", avg);
                    printResult(RpcPlus.getVacancyRateExact(), "The mean vacancy rate by Exact-Formulation in each interval:","4f");
                    avg = Arrays.stream(RpcPlus.getVacancyRateExact()).sum() / validResults;
                    System.out.format("The average vacancy rate per interval: %.4f%n", avg);

                    System.out.println("Computational Time in (ms) of Exact-Formulation: " + Arrays.toString(RpcPlus.getRunningTimeExact()));
                    avg = (double) Arrays.stream(RpcPlus.getRunningTimeExact()).sum() / validResults;
                    System.out.format("The Computational time per interval: %.4f%n", avg);
                }
            }
        }
    }
    
    private void RPresults(int validResults) {
        System.out.println("Number of passengers served for RP in each interval:\n" + Arrays.toString(passengerCoveredRP));
        double[] results = new double[SimulationParameters.numberOfIntervals];
        for (int i = 0; i < SimulationParameters.numberOfIntervals; i++)
            results[i] = (double) (passengerCoveredRP[i]) / numPassengersGenerated[i];
        printResult(results, "% of passengers served for RP in each interval: ","5f");

        long sum = Arrays.stream(passengerCoveredRP).sum();
        double avg = (double) (sum) / validResults;
        System.out.format("Total number of passengers served: %d -- Average number of passengers served per interval: %.3f%n", sum, avg);

        System.out.println("Optimal solution of RP (max weight matching) in each interval:\n" + Arrays.toString(maxWeightRP));
        sum = Arrays.stream(maxWeightRP).sum();
        avg = (double) (sum) / validResults;
        System.out.format("Total Profit (weight) of all solutions for RP: %d -- Average profit of a solution found per interval: %.3f%n", sum, avg);
        
        printResult(occupancyRateRP, "The mean occupancy rate for RP in each interval:", "4f");
        avg = Arrays.stream(occupancyRateRP).sum() / validResults;
        System.out.format("The average occupancy rate per interval: %.4f%n", avg);
        printResult(vacancyRateRP, "The mean vacancy rate for RP in each interval:","4f");
        avg = Arrays.stream(vacancyRateRP).sum() / validResults;
        System.out.format("The average vacancy rate per interval: %.4f%n", avg);
        
        System.out.println("Computational Time in (ms) of RP-Exact: " + Arrays.toString(runningTimeRP));
        avg = (double) Arrays.stream(runningTimeRP).sum() / validResults;
        System.out.format("The Computational time per interval: %.4f%n", avg);
    }
    
    private void commonTripsInfo(boolean write, int validResults) {
        if (write) {
            
        } else {
            System.out.println(SimulationParameters.allParameters());
            System.out.format("\nTotal trips generated: %d, total drivers: %d, total passengers: %d%n",
                            Arrays.stream(numTripsGenerated).sum(), Arrays.stream(numDriversGenerated).sum(), Arrays.stream(numPassengersGenerated).sum());
            System.out.println("Trips generated each interval: " + Arrays.toString(numTripsGenerated));
            System.out.println("Number of drivers in each interval: " + Arrays.toString(numDriversGenerated));
            System.out.println("Number of passengers in each interval: " + Arrays.toString(numPassengersGenerated));
            
            if (!simLoader.isLoadGeneratedTrips()) {
                System.out.println("Number of matches removed: " + Arrays.toString(numMatchesRemoved));
                System.out.format("Total number of num matches removed: %d -- Average number of matches removed per interval: %.4f%n",
                                    Arrays.stream(numMatchesRemoved).sum(), (double)(Arrays.stream(numMatchesRemoved).sum())/validResults);
                System.out.println("Computational Time (in ms) of Algorithm 1: " + Arrays.toString(computeTimeAlg1));
                System.out.format("Average running time (in ms) per interval for Algorithm 1: %.4f%n", (double) (Arrays.stream(computeTimeAlg1).sum())/validResults);
            }
            if (SimulationParameters.algorithmAllMatches) {
                if (SimulationParameters.problemVariant == 2) {
                    System.out.println("Computational Time (in ms) of Algorithm 2: " + Arrays.toString(computeTimeAlg2));
                    System.out.format("Average running time (in ms) per interval for Algorithm 2: %.4f%n", (double) (Arrays.stream(computeTimeAlg2).sum()/validResults));
                }
            }
            System.out.println("Number of matches: " + Arrays.toString(numMatches));
            System.out.println("Number of negative matches: " + Arrays.toString(numNegativeMatches));
        }
    }
    
    private void simulationResults(boolean write) {
        switch (SimulationParameters.problemVariant) {
            case 1:
                problemVariantOneResults(write);
                break;
            case 2:
                problemVariantTwoResults(write);
                break;
        }
    }
    
    private void simulationResults() {
        simulationResults(false);
    }
    
    private boolean performVariantOneAlgorithms() {
        if (SimulationParameters.costMultiplier >= 1) {
            if (SimulationParameters.extraCost > 0 && SimulationParameters.chanceForExtraCost > 0) {
                alg.decreaseProfitByIncreasingCost(currentDrivers, currentPassengers, costMultiplierDecider(), 
                                                SimulationParameters.chanceForExtraCost, SimulationParameters.extraCost, SimulationParameters.operatingCostType);
            } else if (SimulationParameters.costMultiplier > 1) {
                alg.decreaseProfitByIncreasingCost(currentDrivers, currentPassengers, costMultiplierDecider(), SimulationParameters.operatingCostType);
            }
        }
        if (SimulationParameters.revenueReduction < 1)
            alg.decreaseProfitByReducingRevenue(currentDrivers, SimulationParameters.revenueReduction);
        numNegativeMatches[currentInterval] = alg.getNumNegativeMatches(currentDrivers);
        System.out.println("Number of negative matches: " + numNegativeMatches[currentInterval]);
        
        if (!setProfitTarget()) {
            System.out.println("Maximum profit target not set, skip this interval: "+ (currentInterval+1));
            return false;
        }

        // exact solved by ILP formulation
        if (!RpcOne.Exact(profitTarget, currentDrivers, currentPassengers, currentInterval, numMatches[currentInterval])) {
            System.out.println("!!!! Invalid RpcOne.Exact computation, skip this interval: "+ (currentInterval+1));
            return false;
        }

        // greedy
        if (!RpcOne.Greedy(profitTarget, currentDrivers, currentPassengers, currentInterval, numMatches[currentInterval], alg.getNegativeMatches(currentDrivers))) {
            System.out.println("!!!! Invalid RpcOne.Greedy computation, skip this interval: "+ (currentInterval+1));
            return false;
        }

        // exact solving multiple min-cost flow
        if (!RpcOne.ExactNetworkFlow(profitTarget, currentDrivers, currentPassengers, currentInterval, numMatches[currentInterval], numDriversWithMatch, LogResult)) {
            System.out.println("!!!! Invalid RpcOne.ExactNetworkFlow computation, skip this interval: "+ (currentInterval+1));
            return false;
        }
        
        // exact by modified succesive shortest path algorithm
        if (!RpcOne.ExactNF(profitTarget, currentDrivers, currentPassengers, currentInterval, numMatches[currentInterval], numDriversWithMatch, numNegativeMatches[currentInterval])) {
            System.out.println("!!!! Invalid RpcOne.ExactNF computation, skip this interval: "+ (currentInterval+1));
            return false;
        }
        
        return true;
    }
    
    private boolean computeAllMatches() {
        switch (SimulationParameters.computeMatchMethod) {
            case 1:
                computeTimeAlg2[currentInterval] = alg.computeAllMatches(currentDrivers, currentPassengers);
                break;
            case 2:
                if (SimulationParameters.computeDistanceMethod == 1)
                    computeTimeAlg2[currentInterval] = alg.computeAllMatchesDP(currentDrivers, currentPassengers, true);
                else
                    computeTimeAlg2[currentInterval] = alg.computeAllMatchesDP(currentDrivers, currentPassengers, false);
                break;
            default:
                System.out.println("Select a correct compute method for constructing all matches.");
                return false;
        }
        numMatches[currentInterval] = alg.countCurrentMatches(currentDrivers);
        System.out.println("Total number of matches: " + numMatches[currentInterval]);
        System.out.println("Largest match size (number of passengers): " + alg.getLargestMatchSize());
        numNegativeMatches[currentInterval] = alg.getNumNegativeMatches(currentDrivers);
        alg.setPassengerInNumMatches(currentDrivers);
        return alg.verifyMatches(currentDrivers, true);
    }
    
    private boolean createBaseMatches() {
        alg.BaseMatchSetup(currentDrivers, currentPassengers, ProfileHours.get(currentHour), currentHour-SimulationParameters.startHour);
        if (SimulationParameters.candidateTest == 1) {
            alg.potentialPairsDriverDetour(currentDrivers, currentPassengers);
            if (SimulationParameters.computeDistanceMethod == 1)
                computeTimeAlg1[currentInterval] = alg.constructBaseMatchesMD(currentDrivers, currentPassengers, alg::testCandidate);
            else
                computeTimeAlg1[currentInterval] = alg.constructBaseMatches(currentDrivers, currentPassengers, alg::testCandidate);
        } else {
            alg.potentialPairsFixedRadius(currentDrivers, currentPassengers);
            if (SimulationParameters.computeDistanceMethod == 1)
                computeTimeAlg1[currentInterval] = alg.constructBaseMatchesMD(currentDrivers, currentPassengers, alg::testCandidateFixedRadius);
            else
                computeTimeAlg1[currentInterval] = alg.constructBaseMatches(currentDrivers, currentPassengers, alg::testCandidateFixedRadius);
        }
        Pair<Integer,Integer> pair = alg.countTotalNumMatches(currentDrivers);
        numMatches[currentInterval] = pair.getP1();
        numDriversWithMatch = pair.getP2();
        System.out.println("Number of matches: "+ numMatches[currentInterval]);
        System.out.println("Number of drivers with at least one match: "+ numDriversWithMatch);
        
        return alg.verifyMatches(currentDrivers, true);
    }
    
    private void reduceBaseMatches() {
        alg.sortDriversBasedOnMatches(currentDrivers, 0);
        //displayDriversInOrder();
        numMatchesRemoved[currentInterval] = alg.reduceFeasibleMatches(currentDrivers, currentPassengers, 0);
        System.out.println("Number of matches removed: " + numMatchesRemoved[currentInterval]);
        //System.out.println("Number of drivers with at least one match: "+ alg.numDriversWithMatch(currentDrivers));
        alg.sortDriversBasedOnMatches(currentDrivers, 0);
        alg.sortDriversBasedOnMatches(currentDrivers, 1);
        Pair<Integer,Integer> pair = alg.countTotalNumMatches(currentDrivers);
        //alg.sortDriversBasedOnMatches(currentDrivers, 2);
        //displayDriversInOrder();
        //displayNumMatchesPassengersIn();
        numMatches[currentInterval] = pair.getP1();
        numDriversWithMatch = pair.getP2();
        System.out.println("Number of matches: "+ numMatches[currentInterval]);
        System.out.println("Number of drivers with at least one match: "+ numDriversWithMatch);
    }
    
    private long generateTrips() throws Exception {
        long startTime = System.currentTimeMillis();
        tripGenerator.setCurrentTimeParameters(currentInterval, currentHour-SimulationParameters.startHour, ProfileHours.get(currentHour));
        Pair<List<Driver>, List<Passenger>> pair = tripGenerator.genearteTrips(currentTimeInSecond(), Utility.testing);
        
        if (pair == null)
            throw new Exception("Did not generate any trip, aborting....");
                
        long endTime = System.currentTimeMillis();
        currentDrivers = pair.getP1();
        currentPassengers = pair.getP2();
        tripGenerator.verifyGeneratedTrips(currentDrivers, currentPassengers);
                    
        return endTime - startTime;
    }
    
    private long debugTrips() throws Exception {
        long startTime = System.currentTimeMillis();
        tripGenerator.setCurrentTimeParameters(currentInterval, currentHour-SimulationParameters.startHour, ProfileHours.get(currentHour));
        Pair<List<Driver>, List<Passenger>> DP = tripGenerator.genearteDebugTrips(currentTimeInSecond());
        
        if (DP == null)
            throw new Exception("Did not generate any trip, aborting....");
        
        long endTime = System.currentTimeMillis();
        currentDrivers = DP.getP1();
        currentPassengers = DP.getP2();
        for (Driver d : currentDrivers)
            System.out.println(d.toStringAll());
        for (Passenger p : currentPassengers)
            System.out.println(p.toStringAll());

        tripGenerator.verifyGeneratedTrips(currentDrivers, currentPassengers);
        
        return endTime - startTime;
    }
    
    private boolean solveRP(boolean ILPorLP) {    // the RP problem, ILP (i)-(iii), is solved by finding a max weight matching
        long startTime = System.currentTimeMillis();
        Pair<Solution, Long> matching;
        if (ILPorLP)
            matching = cplex.maxWeightILP(currentDrivers, currentPassengers, currentInterval, numMatches[currentInterval]);
        else
            matching = cplex.maxWeight(currentDrivers, currentPassengers, currentInterval, numMatches[currentInterval]);
        if (matching == null) {
            System.out.println("Matching is not found.");
            return false;
        }
        
        HashMap<Driver, Match> solution = matching.getP1().matches;
        if (SimulationParameters.computeDistanceMethod == 1)
            alg.validateSolutionMD(solution);
        
        if (SimulationParameters.problemVariant == 1) {
            passengerCoveredRP[currentInterval] = solution.size();
            occupancyRateRP[currentInterval] = (double) (passengerCoveredRP[currentInterval]) / currentDrivers.size() + 1.0;
            vacancyRateRP[currentInterval] = 1.0 - (double) (passengerCoveredRP[currentInterval]) / currentDrivers.size();
        } else {
            Set<Passenger> passengersInSolution = new HashSet<>(solution.size()*2);
            for (Map.Entry<Driver, Match> entry : solution.entrySet()) {
                for (Passenger p : entry.getValue().sfp.passengers)
                    passengersInSolution.add(p);
            }
            passengerCoveredRP[currentInterval] = passengersInSolution.size();
            occupancyRateRP[currentInterval] = (double) (passengerCoveredRP[currentInterval]) / currentDrivers.size() + 1.0;
            vacancyRateRP[currentInterval] = 1.0 - (double) (solution.size()) / currentDrivers.size();
        }

        maxWeightRP[currentInterval] = alg.profitOfSolution(solution);
        runningTimeRP[currentInterval] = System.currentTimeMillis() - startTime;
        
        if (SimulationParameters.costMultiplier >= 1) {
            if (SimulationParameters.extraCost > 0 && SimulationParameters.chanceForExtraCost > 0) {
                alg.decreaseProfitByIncreasingCost(currentDrivers, currentPassengers, costMultiplierDecider(), 
                                            SimulationParameters.chanceForExtraCost, SimulationParameters.extraCost, SimulationParameters.operatingCostType);
            } else if (SimulationParameters.costMultiplier > 1) {
                alg.decreaseProfitByIncreasingCost(currentDrivers, currentPassengers, costMultiplierDecider(), SimulationParameters.operatingCostType);
            }
        }
        if (SimulationParameters.revenueReduction < 1)
            alg.decreaseProfitByReducingRevenue(currentDrivers, SimulationParameters.revenueReduction);
        numNegativeMatches[currentInterval] = alg.getNumNegativeMatches(currentDrivers);
        
        return true;
    }
    
    private boolean setProfitTarget() {
        Pair<Solution, Long> matching = cplex.maxWeight(currentDrivers, currentPassengers, currentInterval, numMatches[currentInterval]);
        if (matching == null) {
            System.out.println("Matching is not found.");
            return false;
        }
        
        profitTarget = matching.getP1().weight * SimulationParameters.profitTargetMultiplier;
        System.out.println("Maximum profit = " + matching.getP1().weight);
        profitTargets[currentInterval] = profitTarget;
        //System.out.println("Profit Target = " + profitTarget);
        
        // validate the solution (matching) for recording purpose
        HashMap<Driver, Match> solution = matching.getP1().matches;
        long startTime = System.currentTimeMillis();
        if (SimulationParameters.computeDistanceMethod == 1)
            alg.validateSolutionMD(solution);
        
        passengerCoveredRP[currentInterval] = solution.size();
        occupancyRateRP[currentInterval] = (double) (passengerCoveredRP[currentInterval]) / currentDrivers.size() + 1.0;
        vacancyRateRP[currentInterval] = 1.0 - (double) (passengerCoveredRP[currentInterval]) / currentDrivers.size();
        maxWeightRP[currentInterval] = alg.profitOfSolution(solution);
        runningTimeRP[currentInterval] = System.currentTimeMillis() - startTime + matching.getP2();
        
        return true;
    }
    
    public void displayMatchMinMaxProfit() {
        double min;
        double max;
        Match minMatch;
        Match maxMatch;
        for (Driver driver : currentDrivers) {
            System.out.println("Driver ID: " + driver.getID());
            min = Double.MAX_VALUE;
            max = Double.MIN_VALUE;
            minMatch = null;
            maxMatch = null;
            for (Match match : driver.getMatches()) {
                if (match.profit > max) {
                    max = match.profit;
                    maxMatch = match;
                } else if (match.profit < min) {
                    min = match.profit;
                    minMatch = match;
                }
            }
            if (maxMatch != null)
                System.out.println("Match ID: "+maxMatch.id+" Revenue: (" + maxMatch.revenue + ") Cost: ("+ maxMatch.cost+") MAX Profit: "+ maxMatch.profit);
            if (minMatch != null)
                System.out.println("Match ID: "+minMatch.id+" Revenue: (" + minMatch.revenue + ") Cost: ("+ minMatch.cost+") MIN Profit: "+ minMatch.profit);
        }
    }
    
    public void matchWithNegativeProfit() {
        for (Driver driver : currentDrivers) {
            for (Match match : driver.getMatches()) {
                if (match.profit < 0)
                    System.out.println("Match ID: "+match.id+", Revenue: (" + match.revenue + ") Cost: ("+ match.cost+") Profit: "+ match.profit);
            }
        }
    }
    
    public void matchesWithNegativeProfit() {
        for (Driver driver : currentDrivers) {
            for (Match match : driver.getMatches()) {
                if (match.profit < 0) {
                    System.out.println("Match ID: "+match.id+", Revenue: (" + match.revenue + ") Cost: ("+ match.cost+") Profit: "+ match.profit);
                    System.out.println("Driver ID: "+driver.getID()+ ", Passenger ID: "+ match.sfp.passengers.toString());
                }
            }
        }
    }
    
    public void displayMatchProfitDistribution(int denominator) {
        HashMap<Integer, Integer> profitDistribution = new HashMap<>();    // <profit, numOfMatchesWithThis weight>
        int profit;
        for (Driver driver : currentDrivers) {
            for (Match match : driver.getMatches()) {
                profit = match.profit / denominator;
                if (profitDistribution.containsKey(profit))
                    profitDistribution.put(profit, profitDistribution.get(profit)+1);
                else
                    profitDistribution.put(profit, 1);
            }
        }
        System.out.println(profitDistribution.toString());
    }
    
    public void displayMatchesProfit(int limit) {
        int count;
        for (Driver driver : currentDrivers) {
            count = 0;
            System.out.println("Driver ID: " + driver.getID());
            for (Match match : driver.getMatches()) {
                count++;
                System.out.println("Match ID: "+match.id+ " -- Passengers: "+ match.sfp.passengers.toString() + 
                        " Revenue: (" + match.revenue + ") Cost: ("+ match.cost+") Profit: "+ match.profit);
                if (count >= limit)
                    break;
            }
        }
    }
    
    public void displayMatchProfitOfDriver(Driver driver) {
        System.out.println("Driver ID: " + driver.getID());
        for (Match match : driver.getMatches()) {
            System.out.println("Match ID: "+match.id+ " -- Passengers: "+ match.sfp.passengers.toString() + 
                    " Revenue: (" + match.revenue + ") Cost: ("+ match.cost+") Profit: "+ match.profit);
        }
    }
    
    public void displayMatchProfitOfDriver(int driverID) {
        for (Driver driver : currentDrivers) {
            if (driver.getID() == driverID) {
                System.out.println("Driver ID: " + driver.getID());
                for (Match match : driver.getMatches()) {
                    System.out.println("Match ID: "+match.id+ " -- Passengers: "+ match.sfp.passengers.toString() + 
                            " Revenue: (" + match.revenue + ") Cost: ("+ match.cost+") Profit: "+ match.profit);
                }
                break;
            }
        }
    }
    
    public void displayAllMatchesProfit() {
        for (Driver driver : currentDrivers) {
            System.out.println("Driver ID: " + driver.getID());
            for (Match match : driver.getMatches()) {
                System.out.println("Match ID: "+match.id+ " -- Passengers: "+ match.sfp.passengers.toString() + 
                        " Revenue: (" + match.revenue + ") Cost: ("+ match.cost+") Profit: "+ match.profit);
            }
        }
    }
    
    public void displayMatchInfo(int matchId) {
        for (Driver driver : currentDrivers) {
            for (Match match : driver.getMatches()) {
                if (match.id == matchId) {
                    System.out.println("Driver: " + driver.toStringAll() + ", Match ID: "+match.id);
                    int ind;
                    System.out.print("[[ ");
                    for (ind = 0; ind < match.sfp.originOrDest.size()-1; ind++)
                        System.out.print("Passenger "+ match.sfp.originOrDest.get(ind).getP1().getID() + "("+match.sfp.originOrDest.get(ind).getP2()+") -- ");
                    System.out.println("Passenger "+ match.sfp.originOrDest.get(ind).getP1().getID() + "("+match.sfp.originOrDest.get(ind).getP2()+") ]]");
                    for (Passenger p : match.sfp.passengers)
                        System.out.println(p.toStringAll());
                    break;
                }
            }
        }
    }
    
    public void displayDriversInOrder() {
        for (Driver driver : currentDrivers)
            System.out.println("Driver ("+driver.getID()+"): has " + driver.getMatches().size()+ " matches and capacity "+ driver.getCapacity());
    }
    
    public void displayNumMatchesPassengersIn() {
        for (Passenger passenger : currentPassengers)
            System.out.println("Passenger ("+passenger.getID()+") is in number of matches: "+ passenger.getNAssignments());
    }
    
    private int currentTimeInSecond() {
        return (currentInterval * SimulationParameters.intervalInMinute + SimulationParameters.startHour*60) * 60;
    }
    
    private double costMultiplierDecider() {
        switch(ProfileHours.get(currentHour)) {
            case MorningPeak:
            case AfternoonPeak:
                return SimulationParameters.costMultiplier+0.2;
            case MorningNoon:
            case Night:
                return SimulationParameters.costMultiplier;
            default:
                return SimulationParameters.costMultiplier+0.1;
        }
    }
    
    private void initialize() {
        int i = 6;
        for (; i <= 6; i++)
            ProfileHours.put(i, TimePeriod.Initial);
        for (; i <= 9; i++) { // 7:00-9:59
            ProfileHours.put(i, TimePeriod.MorningPeak);
        }
        for (; i <= 11; i++) {// 10:00-11:59
            ProfileHours.put(i, TimePeriod.MorningNoon);
        }
        for (;i <= 15; i++) // 12:00-15:59
            ProfileHours.put(i, TimePeriod.NoonAfternoon);
        
        for (; i <= 19; i++) // 16:00 - 19:59
            ProfileHours.put(i, TimePeriod.AfternoonPeak);

        for (; i <= 21; i++) // 20:00 - 21:59
            ProfileHours.put(i, TimePeriod.Evening);
        for (; i < 24; i++)  // 22:00 - 23:59
            ProfileHours.put(i, TimePeriod.Night);
    }
    
    public void printResult(double[] arr, String msg, String decimals) {
        System.out.println(msg);
        System.out.print("[");
        for (int i = 0; i < arr.length; i++) {
            if (i < arr.length - 1)
                System.out.format("%." + decimals + ", ", arr[i]);
            else
                System.out.format("%." + decimals, arr[i]);
        }
        System.out.println("]");
    }
}