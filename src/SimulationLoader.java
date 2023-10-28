package simulation;

import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.storage.Graph;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
//import com.graphhopper.reader.osm;
//import com.graphhopper.routing.util.*;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringJoiner;

public class SimulationLoader {
    private final String DATAFOLDER = "data";
    private final String GH_FOLDER = "gh_folder";
    private final String TRIP_FOLDER = "trip_folder";
    private final String LOG_FOLDER = "log_folder";
    private final String CONFIG = "config.properties";
    private final String OSMFILENAME = "Chicago.osm.pbf";
    private final String SPEEDFILENAME = "avgSpeed.csv";
    private final String SURGEPRICING = "aSPF.csv";
    private final String TIPS = "TipsProbability.csv";
    private final String PASSENGERSINHOUR = "aTripsInHour.csv";
    private final String REGIONBOUNDARIES = "regionBoundaries.csv";
    private final String LOGFIlENAME = "SimulationLog.txt";
    private final boolean TurnCost = false;
    
    private boolean onlyGenerateTrips = false;          // trips are generated and matches are computed, and then written to file.
    private boolean loadGeneratedTrips = false;
    private final Location Airport_OHare = new Location(-87.9033949054243, 41.97863979251512);
    //private final Location Airport_Midway = new Location(-87.74175010393411, 41.78850894013623);
    
    private String GHWeight = "shortest";
    private boolean logResult = false;
    private String tripSubfolder = "";
    
    private final List<Region> regions = new ArrayList<>(25);
    String currentPath;
    private StringJoiner joiner;
    
    public SimulationLoader() {
    }
    
    public void setup() {
        Path currentRelativePath = Paths.get("");
        currentPath = currentRelativePath.toAbsolutePath().toString();
        System.out.println("Current relative path is: " + currentPath);
        
        ArrayList<Path> filePaths = new ArrayList<>();
        filePaths.add(Paths.get(currentPath, DATAFOLDER, REGIONBOUNDARIES));
        filePaths.add(Paths.get(currentPath, DATAFOLDER, SPEEDFILENAME));
        filePaths.add(Paths.get(currentPath, DATAFOLDER, SURGEPRICING));
        filePaths.add(Paths.get(currentPath, DATAFOLDER, TIPS));
        filePaths.add(Paths.get(currentPath, DATAFOLDER, PASSENGERSINHOUR));
        filePaths.add(Paths.get(currentPath, DATAFOLDER, OSMFILENAME));
        filePaths.add(Paths.get(currentPath, DATAFOLDER, GH_FOLDER));
        filePaths.add(Paths.get(currentPath, DATAFOLDER, CONFIG));
        
        //parameters = new SimulationParameters();
        
        if (Utility.areFilesAccessible(filePaths)) {
            try {
                readConfig(Paths.get(currentPath, DATAFOLDER, CONFIG).toString());
                
                if (loadGeneratedTrips) {
                    if (!verifyTripsLoadable()) {
                        System.out.println("Trip data not loaded. Exiting program....");
                        System.exit(0);
                    }
                }
                
                if (onlyGenerateTrips) {
                    if (!verifyTripsWritable()) {
                        System.out.println("Trip data folder trying to be loaded from does not exist. Exiting program....");
                        System.exit(0);
                    }
                }

                if (logResult) {
                    logResult = verifyLogFolderExist();
                    if (!logResult)
                        System.out.println("Log folder does not exist. No log will be written.");
                }
                
                if (!createRegions(Paths.get(currentPath, DATAFOLDER, REGIONBOUNDARIES).toFile())) {
                    System.out.println("Regions are not created ......Aborting......");
                    System.exit(0);
                }
                //testGenerateLocation();
                System.out.println("All regions are created and triangulated.");
                
                HopperOperation ho = createNetworkFromData(Paths.get(currentPath, DATAFOLDER, OSMFILENAME).toString(), Paths.get(currentPath, DATAFOLDER, GH_FOLDER).toString());
                if (ho == null) {
                    System.out.println("Road network is not created by GraphHopper ......Aborting......");
                    System.exit(0);
                } else {
                    int[][][] nPassengersGenerate = null;
                    double[][][] speed;
                    double[][][] surgePricing;
                    if (!loadGeneratedTrips) {
                        nPassengersGenerate = loadNumPasengersToGenerate(Paths.get(currentPath, DATAFOLDER, PASSENGERSINHOUR).toFile());
                        if (nPassengersGenerate == null) {
                            System.out.println("Number of passenger to be generated could not be loaded ......Aborting......");
                            System.exit(0);
                        }
                    }
                    speed = loadSpeedOrPricing(Paths.get(currentPath, DATAFOLDER, SPEEDFILENAME).toFile());
                    if (speed == null) {
                        System.out.println("Average speed could not be loaded ......Aborting......");
                        System.exit(0);
                    }
                    surgePricing = loadSpeedOrPricing(Paths.get(currentPath, DATAFOLDER, SURGEPRICING).toFile());
                    if (surgePricing == null) {
                        System.out.println("Surge pricing could not be loaded ......Aborting......");
                        System.exit(0);
                    }
                    HashMap<Integer,Double> averageTip = loadAverageTip(Paths.get(currentPath, DATAFOLDER, TIPS).toFile());
                    if (averageTip == null || averageTip.isEmpty()) {
                        System.out.println("Average tip amount could not be loaded ......Aborting......");
                        System.exit(0);
                    }
                    
                    TripGenerator tripGenerator = new TripGenerator(regions, ho, nPassengersGenerate, speed);
                    Algorithms alg = new Algorithms(ho, speed, surgePricing, averageTip);
                    SimulationOperator simOperator = new SimulationOperator(this, alg, tripGenerator, logResult);
                    simOperator.startSimulation();
                }
            } catch(IOException e) {
                System.out.println(e.toString());
                System.out.println("Region file was not loaded smoothly......Aborting......");
                System.exit(0);
            }
        } else {
            System.out.println("One or more files are not accessible. Check the paths to all files");
            System.exit(0);
        }
        
        
    }
    
    public void readConfig(String configFileName) throws FileNotFoundException, IOException {
        Properties prop = new Properties();        
        try (FileInputStream fis = new FileInputStream(configFileName)) {
            prop.load(fis);
            
            if (Utility.isInt(prop.getProperty("Variant"))) {
                SimulationParameters.problemVariant = Integer.parseInt(prop.getProperty("Variant"));
            }
            if (SimulationParameters.problemVariant != 1 && SimulationParameters.problemVariant != 2) {
                System.out.println("Please select the correct Problem Variant. Exiting program....");
                System.exit(0);
            }
            if (Utility.isInt(prop.getProperty("StartHour"))) {
                SimulationParameters.startHour = Integer.parseInt(prop.getProperty("StartHour"));
            }
            if (SimulationParameters.startHour < 6 || SimulationParameters.startHour > 23) {
                System.out.println("Please select the correct start hour for simulation. Exiting program....");
                System.exit(0);
            }
            if (Utility.isInt(prop.getProperty("NumberOfIntervals"))) {
                SimulationParameters.numberOfIntervals = Integer.parseInt(prop.getProperty("NumberOfIntervals"));
            }
            if (SimulationParameters.numberOfIntervals < 1 || SimulationParameters.numberOfIntervals > 71) {
                System.out.println("Please select the correct number of intervals for simulation. Exiting program....");
                System.exit(0);
            }
            if (Utility.isInt(prop.getProperty("IntervalInMinutes"))) {
                SimulationParameters.intervalInMinute = Integer.parseInt(prop.getProperty("IntervalInMinutes"));
            }
            if (SimulationParameters.intervalInMinute != 15) {
                System.out.println("Currently, the number of minutes in an interval must be 15.");
                SimulationParameters.intervalInMinute = 15;
            }
            
            
            if (Utility.isDouble(prop.getProperty("DistanceRadius"))) {
                SimulationParameters.distanceRadius = Double.parseDouble(prop.getProperty("DistanceRadius"));
            }
            if (Utility.isInt(prop.getProperty("CandidateTest"))) {
                SimulationParameters.candidateTest = Integer.parseInt(prop.getProperty("CandidateTest"));
            }
            
            
            if (Utility.isInt(prop.getProperty("ComputeMatchMethod"))) {
                SimulationParameters.computeMatchMethod = Integer.parseInt(prop.getProperty("ComputeMatchMethod"));
            }
            if (Utility.isInt(prop.getProperty("ComputeDistanceMethod"))) {
                SimulationParameters.computeDistanceMethod = Integer.parseInt(prop.getProperty("ComputeDistanceMethod"));
            }
            if (Utility.isBoolean(prop.getProperty("MultiThreading"))) {
                SimulationParameters.useMultiThread = Boolean.parseBoolean(prop.getProperty("MultiThreading"));
            }
            if (prop.getProperty("NumThreads") != null) {
                if (Utility.isInt(prop.getProperty("NumThreads")))
                    SimulationParameters.nThreads = Integer.parseInt(prop.getProperty("NumThreads"));
                if (SimulationParameters.nThreads == 0)
                    SimulationParameters.nThreads = Runtime.getRuntime().availableProcessors();
            }
            
            
            if (Utility.isInt(prop.getProperty("MaxBaseMatchesForDriver"))) {
                SimulationParameters.maxNumBaseMatchesPerDriver = Integer.parseInt(prop.getProperty("MaxBaseMatchesForDriver"));
            }
            if (Utility.isInt(prop.getProperty("MaxMatchesForDriver"))) {
                SimulationParameters.maxNumMatchesPerDriver = Integer.parseInt(prop.getProperty("MaxMatchesForDriver"));
            }
            if (Utility.isInt(prop.getProperty("MaxBaseMatchesForPassenger"))) {
                SimulationParameters.thresholdMatchesForEachPassenger = Integer.parseInt(prop.getProperty("MaxBaseMatchesForPassenger"));
            }
            if (Utility.isInt(prop.getProperty("MinNumBaseMatchesPerDriver"))) {
                SimulationParameters.minNumBaseMatchesPerDriver = Integer.parseInt(prop.getProperty("MinNumBaseMatchesPerDriver"));
            }
            
            
            if (Utility.isBoolean(prop.getProperty("AlgorithmBaseMatches"))) {
                SimulationParameters.algorithmBaseMatches = Boolean.parseBoolean(prop.getProperty("AlgorithmBaseMatches"));
            }
            if (Utility.isBoolean(prop.getProperty("AlgorithmReduceBaseMatches"))) {
                SimulationParameters.algorithmReduceBaseMatches = Boolean.parseBoolean(prop.getProperty("AlgorithmReduceBaseMatches"));
            }
            if (Utility.isBoolean(prop.getProperty("AlgorithmAllMatches"))) {
                SimulationParameters.algorithmAllMatches = Boolean.parseBoolean(prop.getProperty("AlgorithmAllMatches"));
            }
            if (Utility.isBoolean(prop.getProperty("AlgorithmComputeSolutions"))) {
                SimulationParameters.algorithmComputeSolutions = Boolean.parseBoolean(prop.getProperty("AlgorithmComputeSolutions"));
            }
            if (Utility.isBoolean(prop.getProperty("AlgorithmRP"))) {
                SimulationParameters.algorithmRP = Boolean.parseBoolean(prop.getProperty("AlgorithmRP"));
            }

            
            if (Utility.isDouble(prop.getProperty("CostMultiplier"))) {
                SimulationParameters.costMultiplier = Double.parseDouble(prop.getProperty("CostMultiplier"));
            }
            if (Utility.isDouble(prop.getProperty("ExtraCost"))) {
                SimulationParameters.extraCost = Double.parseDouble(prop.getProperty("ExtraCost"));
            }
            if (Utility.isDouble(prop.getProperty("ChanceForExtraCost"))) {
                SimulationParameters.chanceForExtraCost = Double.parseDouble(prop.getProperty("ChanceForExtraCost"));
            }
            if (Utility.isInt(prop.getProperty("OperatingCostType"))) {
                SimulationParameters.operatingCostType = Integer.parseInt(prop.getProperty("OperatingCostType"));
            }
            if (Utility.isDouble(prop.getProperty("RevenueReduction"))) {
                SimulationParameters.revenueReduction = Double.parseDouble(prop.getProperty("RevenueReduction"));
            }
            if (Utility.isDouble(prop.getProperty("ProfitTargetMultiplier"))) {
                SimulationParameters.profitTargetMultiplier = Double.parseDouble(prop.getProperty("ProfitTargetMultiplier"));
            }
            if (Utility.isDouble(prop.getProperty("LowerBoundProfitTarget"))) {
                SimulationParameters.lowerBoundProfitTarget = Double.parseDouble(prop.getProperty("LowerBoundProfitTarget"));
            }
            
            System.out.println(SimulationParameters.allParameters());
            if (SimulationParameters.useMultiThread)
                System.out.println("Number of threads: " + SimulationParameters.nThreads);
            else
                System.out.println("Single-threaded");
            
            if (prop.getProperty("GHWeight") != null) {
                if (prop.getProperty("GHWeight").equals("shortest") || prop.getProperty("GHWeight").equals("fastest"))
                    GHWeight = prop.getProperty("GHWeight");
            }

            if (Utility.isBoolean(prop.getProperty("OnlyGenerateTrips"))) {
                onlyGenerateTrips = Boolean.parseBoolean(prop.getProperty("OnlyGenerateTrips"));
            }
            if (Utility.isBoolean(prop.getProperty("LoadGeneratedTrips"))) {
                loadGeneratedTrips = Boolean.parseBoolean(prop.getProperty("LoadGeneratedTrips"));
            }
            
            if (Utility.isBoolean(prop.getProperty("LogResult"))) {
                logResult = Boolean.parseBoolean(prop.getProperty("LogResult"));
            }
            
            if (prop.getProperty("TripSubfolder") != null) {
                tripSubfolder = prop.getProperty("TripSubfolder");
                tripSubfolder = Paths.get(TRIP_FOLDER, tripSubfolder).toString();
                System.out.println("tripSubfolder: " + tripSubfolder);
            } else {
                tripSubfolder = TRIP_FOLDER;
            }
            
            if (prop.getProperty("SkipToIteration") != null) {
                if (Utility.isInt(prop.getProperty("SkipToIteration")))
                Utility.skipToIteration = Integer.parseInt(prop.getProperty("SkipToIteration"));
            }
            
            if (Utility.isBoolean(prop.getProperty("Test"))) {
                Utility.testing = Boolean.parseBoolean(prop.getProperty("Test"));
            }
            if (Utility.testing)
                System.out.println("Testing.....");
        }
    }
    
    public int[][][] loadNumPasengersToGenerate(File avgTripsFile) throws FileNotFoundException, IOException {
        boolean valid = true;
        int[][][] nPassengersGenerate = new int[SimulationParameters.numberOfIntervals][regions.size()][regions.size()];
        int totalPassengers = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(avgTripsFile))) {
            br.readLine();      // skip the header
            String[] entries;
            int hourIntervalIndex;            
            for (String line; (line = br.readLine()) != null;) {
                entries = line.split(",");
                if (entries.length == 5) {
                    if (Utility.isInt(entries[0]) && Utility.isInt(entries[1])) {
                        hourIntervalIndex = Integer.parseInt(entries[0]);
                        if (hourIntervalIndex >= SimulationParameters.startHour) {
                            hourIntervalIndex = (hourIntervalIndex-SimulationParameters.startHour)*4 + Integer.parseInt(entries[1]);
                            if (hourIntervalIndex >= SimulationParameters.numberOfIntervals)
                                break;
                            //hourIntervalIndex = (hourIntervalIndex-startHour)*4 + Integer.parseInt(entries[1]);
                            if (Utility.isInt(entries[2]) && Utility.isInt(entries[3]) && Utility.isInt(entries[4])) {
                                nPassengersGenerate[hourIntervalIndex][Integer.parseInt(entries[2])-1][Integer.parseInt(entries[3])-1] = Integer.parseInt(entries[4]);
                                totalPassengers += Integer.parseInt(entries[4]);
                            } else {
                                valid = false;
                                System.out.println("Incorrect format: " + line);
                            }
                        }
                    } else {
                        valid = false;
                        System.out.println("Incorrect format: " + line);
                    }
                } else {
                    valid = false;
                    System.out.println("Incorrect format: " + line);
                }
            }
        }
        
        System.out.println("Total Number of Passengers = " + totalPassengers);
        if (valid)
            return nPassengersGenerate;
        return null;
    }
    
    public double[][][] loadSpeedOrPricing(File file) throws FileNotFoundException, IOException {
        boolean valid = true;
        double[][][] data = new double[23-SimulationParameters.startHour+1][regions.size()][regions.size()];
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            br.readLine();      // skip the header
            String[] entries;
            int hourIndex;
            for (String line; (line = br.readLine()) != null;) {
                entries = line.split(",");
                if (entries.length == 4) {
                    if (Utility.isInt(entries[0])) {
                        hourIndex = Integer.parseInt(entries[0]);
                        if (hourIndex >= SimulationParameters.startHour) {
                            hourIndex = (hourIndex-SimulationParameters.startHour);
                            if (Utility.isInt(entries[1]) && Utility.isInt(entries[2]) && Utility.isDouble(entries[3])) {
                                data[hourIndex][Integer.parseInt(entries[1])-1][Integer.parseInt(entries[2])-1] = Double.parseDouble(entries[3]);
                            } else {
                                valid = false;
                                System.out.println("Incorrect format: " + line);
                            }
                        }
                    } else {
                        valid = false;
                        System.out.println("Incorrect format: " + line);
                    }
                } else {
                    valid = false;
                    System.out.println("Incorrect format: " + line);
                }
            }
        }
        
        if (valid)
            return data;
        return null;
    }
    
    public HashMap<Integer,Double> loadAverageTip(File file) throws FileNotFoundException, IOException {
        boolean valid = true;
        HashMap<Integer,Double> data = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            br.readLine();      // skip the header
            String[] entries;
            int distance;
            for (String line; (line = br.readLine()) != null;) {
                entries = line.split(",");
                if (entries.length == 4) {
                    if (Utility.isInt(entries[0])) {
                        distance = Integer.parseInt(entries[0]);
                        if (Utility.isDouble(entries[2]) && Utility.isDouble(entries[3])) {
                            data.put(distance, Double.parseDouble(entries[2])*Double.parseDouble(entries[3]));
                        } else {
                            valid = false;
                            System.out.println("Incorrect format: " + line);
                        }
                    }
                } else {
                    valid = false;
                    System.out.println("Incorrect format: " + line);
                }
            }
        }
        
        data.put(1,0d); // anything 1 mile or less, no tip
        if (valid)
            return data;
        return null;
    }
    
    public boolean createRegions(File regionFile) throws FileNotFoundException, IOException {
        boolean valid = true;
        int numRegions = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(regionFile))) {
            br.readLine();      // skip the header
            int currentRegion = 0;
            int temp;
            List<Location> locations = new ArrayList<>();
            String[] entries;
            for (String line; (line = br.readLine()) != null;) {
                entries = line.split(",");
                if (entries.length == 3) {
                    if (Utility.isInt(entries[0])) {
                        temp = Integer.parseInt(entries[0]);
                        //System.out.println(temp + " and currentRegion = " + currentRegion);
                        if (temp != currentRegion) {
                            numRegions++;
                            if (currentRegion > 0 && locations.size() > 3) {
                                regions.add(new Region(currentRegion, locations));
                                //regions.get(regions.size()-1).displayRegion();
                            }
                            currentRegion = temp;
                            locations = new ArrayList<>();
                        }
                    } else {
                        valid = false;
                        System.out.println("Incorrect format: " + line);
                        continue;
                    }
                    if (Utility.isDouble(entries[1]) && Utility.isDouble(entries[2])) {
                        locations.add(new Location(Double.parseDouble(entries[1]), Double.parseDouble(entries[2])));
                    } else {
                        valid = false;
                        System.out.println("Incorrect format: " + line);
                    }
                } else {
                    valid = false;
                    System.out.println("Incorrect format: " + line);
                }
            }
        }
        if (numRegions == 25)
            regions.add(new Region(25, Arrays.asList(Airport_OHare)));
            
        System.out.println("Number of regions data read: " + numRegions);
        return valid;
    }
    
    /*
    * This creates the logical road-map network from OSM data
    */
    public HopperOperation createNetworkFromData(String osm, String graphFolder) {
        GraphHopper hopper;
        HopperOperation ho = null;
        try {
            GraphHopperConfig graphHopperConfig = new GraphHopperConfig();
            graphHopperConfig.putObject("datareader.file", osm);
            graphHopperConfig.putObject("graph.location", graphFolder);
            
            Profile profile;
            if (TurnCost) {
                graphHopperConfig.putObject("graph.graph.vehicles", "|turn_costs=true");
                profile = new Profile("car").setVehicle("car").setWeighting(GHWeight).setTurnCosts(true).putHint("u_turn_costs", 40);
            } else {
                profile = new Profile("car").setVehicle("car").setWeighting(GHWeight);
            }
            
            CHProfile chProfile = new CHProfile(profile.getName());
            graphHopperConfig.setProfiles(Collections.singletonList(profile));
            graphHopperConfig.setCHProfiles(Collections.singletonList(chProfile));
            hopper = new GraphHopper().init(graphHopperConfig);
            
            //hopper.setProfiles(profile);
            //hopper.setOSMFile(osm);
            //hopper.setGraphHopperLocation(graphFolder);
            
            // this enables speed mode for the profile we called car, comment it out for non-speed mode
            //hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));            
            //hopper.getCHPreparationHandler().setCHProfiles(new CHProfile(profile.getName()));
            
            hopper.importOrLoad();
            Graph network = hopper.getBaseGraph();
            System.out.println("number of edges: " + network.getEdges() + " --- number of nodes: " + network.getNodes());
            
            ho = new HopperOperation(hopper);
        } catch(Exception e) {
            System.out.println(e.getMessage());
        }
        return ho;
    }
    
    private boolean verifyLogFolderExist() {
        try {
            Files.createDirectories(Paths.get(currentPath, DATAFOLDER, LOG_FOLDER));
            Utility.setPathToLogFile(Paths.get(currentPath, DATAFOLDER, LOG_FOLDER, LOGFIlENAME));
            System.out.println("Log folder exists.");
            return true;
        } catch (IOException e) {
            System.out.println(e.toString());
        }
        return false;
    }
    
    private boolean verifyTripsWritable() {
        try {
            Files.createDirectories(Paths.get(currentPath, DATAFOLDER, tripSubfolder));
        } catch (IOException e) {
            System.out.println(e.toString());
            onlyGenerateTrips = false;
            return false;
        }
        return true;
    }
    
    private boolean verifyTripsLoadable() {
        boolean tripFolderExists = true;
        try {
            Files.createDirectories(Paths.get(currentPath, DATAFOLDER, tripSubfolder));
        } catch (IOException e) {
            System.out.println(e.toString());
            loadGeneratedTrips = false;
            tripFolderExists = false;
        }
        
        if (tripFolderExists && Files.isReadable(Paths.get(currentPath, DATAFOLDER, tripSubfolder))) {            
            if (SimulationParameters.algorithmBaseMatches) {
                System.out.println("inconsistent options: both generating base matches and loading previously generated trips are selected.");
                return false;
            }
            
            if (SimulationParameters.problemVariant == 1) {
                if (SimulationParameters.algorithmComputeSolutions || SimulationParameters.algorithmRP) {
                    return verifyTripsLoadable("base");
                } else {
                    System.out.println("The option to compute solutions for RP and RPCS is not selected for ProblemVariant 1.");
                    return false;
                }
            } else {
                if (SimulationParameters.algorithmAllMatches) {
                    return verifyTripsLoadable("base");
                } else if (SimulationParameters.algorithmComputeSolutions || SimulationParameters.algorithmRP) {
                    return verifyTripsLoadable("total");
                } else {
                    System.out.println("The option to compute all matches or to compute solutions for RP and RPCS is not selected for ProblemVariant 2.");
                    return false;
                }
            }
        } else {
            System.out.println("Attempt to load previously generated trips data, but (" + Paths.get(currentPath, DATAFOLDER, tripSubfolder) + ") is not readable/accessable.");
            return false;
        }
    }
    
    private boolean verifyTripsLoadable(String BaseOrTotal) {
        Path fileNamePath;
        String slnParamters;
        for (int i = 0; i < SimulationParameters.numberOfIntervals; i++) {
            joiner = new StringJoiner("_");
            if (BaseOrTotal.equals("total")) {
                joiner.add("drivers").add("total").add(Integer.toString(SimulationParameters.problemVariant)).add(Integer.toString(SimulationParameters.startHour)).
                        add(Integer.toString(i)).add(Integer.toString(SimulationParameters.candidateTest)).add(Double.toString(SimulationParameters.distanceRadius)).
                        add(Integer.toString(SimulationParameters.minNumBaseMatchesPerDriver)).
                        add(Integer.toString(SimulationParameters.maxNumBaseMatchesPerDriver)).
                        add(Integer.toString(SimulationParameters.thresholdMatchesForEachPassenger)).
                        add(Integer.toString(SimulationParameters.maxNumMatchesPerDriver));
            } else {
                joiner.add("drivers").add("base").add(Integer.toString(SimulationParameters.problemVariant)).add(Integer.toString(SimulationParameters.startHour)).
                        add(Integer.toString(i)).add(Integer.toString(SimulationParameters.candidateTest)).add(Double.toString(SimulationParameters.distanceRadius)).
                        add(Integer.toString(SimulationParameters.minNumBaseMatchesPerDriver)).
                        add(Integer.toString(SimulationParameters.maxNumBaseMatchesPerDriver)).
                        add(Integer.toString(SimulationParameters.thresholdMatchesForEachPassenger));
            }
            
            slnParamters = joiner.toString();
            fileNamePath = Paths.get(currentPath, DATAFOLDER, tripSubfolder, slnParamters);
            if (!Files.isReadable(fileNamePath)) {
                System.out.println("Previously generated drivers data cannot be loaded, either not readable or do not exist.");
                System.out.println("Check file name: " + fileNamePath.toString());
                return false;
            }
            slnParamters = slnParamters.replace("drivers", "passengers");
            fileNamePath = Paths.get(currentPath, DATAFOLDER, tripSubfolder, slnParamters);
            if (!Files.isReadable(fileNamePath)) {
                System.out.println("Previously generated passengers data cannot be loaded, either not readable or do not exist.");
                System.out.println("Check file name: " + fileNamePath.toString());
                return false;
            }
            
            if (SimulationParameters.problemVariant == 2) {
                if (SimulationParameters.algorithmAllMatches && BaseOrTotal.equals("total")) {
                    slnParamters = slnParamters.replace("passengers", "IndexIDMapping");
                    fileNamePath = Paths.get(currentPath, DATAFOLDER, tripSubfolder, slnParamters);
                    if (!Files.isReadable(fileNamePath)) {
                        System.out.println("Previously generated IndexIDMapping data cannot be loaded, either not readable or do not exist.");
                        System.out.println("Check file name: " + fileNamePath.toString());
                        return false;
                    }

                    slnParamters = slnParamters.replace("IndexIDMapping", "travelDistance");
                    fileNamePath = Paths.get(currentPath, DATAFOLDER, tripSubfolder, slnParamters);
                    if (!Files.isReadable(fileNamePath)) {
                        System.out.println("Previously generated travelDistance data cannot be loaded, either not readable or do not exist.");
                        System.out.println("Check file name: " + fileNamePath.toString());
                        return false;
                    }
                }
            }
        }
        return true;
    }
    
    @SuppressWarnings("unchecked")
    public Pair<List<Driver>, List<Passenger>> loadTripsFromFile(int currentInterval, Algorithms alg) {
        System.out.format("Loading previously generated trip data from file for interval (%d) ..........%n", currentInterval);        
        joiner = new StringJoiner("_");

        if (SimulationParameters.problemVariant == 2) {
            if (SimulationParameters.algorithmAllMatches) {
                joiner.add("drivers").add("base").add("2").add(Integer.toString(SimulationParameters.startHour)).add(Integer.toString(currentInterval)).
                        add(Integer.toString(SimulationParameters.candidateTest)).add(Double.toString(SimulationParameters.distanceRadius)).
                        add(Integer.toString(SimulationParameters.minNumBaseMatchesPerDriver)).
                        add(Integer.toString(SimulationParameters.maxNumBaseMatchesPerDriver)).
                        add(Integer.toString(SimulationParameters.thresholdMatchesForEachPassenger));
            } else if (SimulationParameters.algorithmComputeSolutions || SimulationParameters.algorithmRP) {
                joiner.add("drivers").add("total").add("2").add(Integer.toString(SimulationParameters.startHour)).add(Integer.toString(currentInterval)).
                        add(Integer.toString(SimulationParameters.candidateTest)).add(Double.toString(SimulationParameters.distanceRadius)).
                        add(Integer.toString(SimulationParameters.minNumBaseMatchesPerDriver)).
                        add(Integer.toString(SimulationParameters.maxNumBaseMatchesPerDriver)).
                        add(Integer.toString(SimulationParameters.thresholdMatchesForEachPassenger)).
                        add(Integer.toString(SimulationParameters.maxNumMatchesPerDriver));
            } else {
                System.out.println("Select a correct loading option for ProblemVariant 2 (compute all matches or compute solutions).");
                return null;
            }
        } else if (SimulationParameters.algorithmComputeSolutions || SimulationParameters.algorithmRP) {
            joiner.add("drivers").add("base").add("1").add(Integer.toString(SimulationParameters.startHour)).add(Integer.toString(currentInterval)).
                    add(Integer.toString(SimulationParameters.candidateTest)).add(Double.toString(SimulationParameters.distanceRadius)).
                    add(Integer.toString(SimulationParameters.minNumBaseMatchesPerDriver)).
                    add(Integer.toString(SimulationParameters.maxNumBaseMatchesPerDriver)).
                    add(Integer.toString(SimulationParameters.thresholdMatchesForEachPassenger));
        } else {
            System.out.println("Select a correct loading option for ProblemVariant 1 (compute solutions).");
            return null;
        }
        
        String slnParamters = joiner.toString();
        ArrayList<Driver> currentDrivers = null;
        ArrayList<Passenger> currentPassengers = null;
        Path fileNamePath = Paths.get(currentPath, DATAFOLDER, tripSubfolder, slnParamters);
        
        try(
                FileInputStream streamIn = new FileInputStream(fileNamePath.toFile());
                ObjectInputStream ois = new ObjectInputStream(streamIn);
        ) {
                Object o = ois.readObject();
                if (o instanceof ArrayList) {
                    ArrayList<?> driversList = (ArrayList<?>) o;
                    if (!driversList.isEmpty()) {
                        if ((driversList).get(0) instanceof Driver) {
                            currentDrivers = (ArrayList<Driver>) driversList;
                        } else {
                            System.out.println("Unable to load previously generated driver data.......");
                        }
                    }
                }
        } catch (Exception e) {
            System.out.println(e.toString() + "while loading " + fileNamePath);
            return null;
        }

        if (currentDrivers == null)
            return null;
        
        //paramters = "passengers_"+nHours+"_"+startHour+"_"+currentInterval;
        slnParamters = slnParamters.replace("drivers", "passengers");
        fileNamePath = Paths.get(currentPath, DATAFOLDER, tripSubfolder, slnParamters);
        
        try(
                FileInputStream streamIn = new FileInputStream(fileNamePath.toFile());
                ObjectInputStream ois = new ObjectInputStream(streamIn);
        ) {
                Object o = ois.readObject();
                if (o instanceof ArrayList) {
                    ArrayList<?> passengersList = (ArrayList<?>) o;
                    if (!passengersList.isEmpty()) {
                        if ((passengersList).get(0) instanceof Passenger) {
                            currentPassengers = (ArrayList<Passenger>) passengersList;
                        } else {
                            System.out.println("Unable to load previously generated passenger data.......");
                        }
                    }
                }
        } catch (Exception e) {
            System.out.println(e.toString() + "while loading " + fileNamePath);
            return null;
        }
        
        if (currentPassengers == null)
            return null;
        
        // need to load IndexIDMapping and travelDistanceMatrix
        if (SimulationParameters.problemVariant == 2) {
            if (SimulationParameters.algorithmAllMatches) {
                slnParamters = slnParamters.replace("passengers", "IndexIDMapping");
                fileNamePath = Paths.get(currentPath, DATAFOLDER, tripSubfolder, slnParamters);
                try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(fileNamePath.toFile())));
                ) {
                   int size = in.readInt();
                   alg.tripIDtoTravelDistanceIndex = new HashMap<>(size);
                   for (int i = 0; i < size; i++)
                        alg.tripIDtoTravelDistanceIndex.put(in.readInt(), in.readInt());
                } catch (EOFException ex) {
                    System.out.println(ex.toString());
                    return null;
                } catch (Exception e) {
                    System.out.println(e.toString());
                    return null;
                }
                
                // load travel time matrix
                slnParamters = slnParamters.replace("IndexIDMapping", "travelDistance");
                fileNamePath = Paths.get(currentPath, DATAFOLDER, tripSubfolder, slnParamters);
                try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(fileNamePath.toFile())));
                ) {
                    alg.travelDistance = new long[currentDrivers.size() + currentPassengers.size()*2][currentDrivers.size() + currentPassengers.size()*2];
                    for (int i = 0; i < currentDrivers.size() + currentPassengers.size()*2; i++) {
                        for (int j = 0; j < currentDrivers.size() + currentPassengers.size()*2; j++)
                            alg.travelDistance[i][j] = in.readLong();
                    }
                } catch (EOFException ex) {
                    System.out.println(ex.toString());
                    return null;
                } catch (Exception e) {
                    System.out.println(e.toString());
                    return null;
                }
            }
        }
        
        return new Pair<>(currentDrivers,currentPassengers);
    }
    
    @SuppressWarnings("unchecked")
    public boolean writeTripsToFile(List<Driver> currentDrivers, List<Passenger> currentPassengers, int currentInterval, Algorithms alg) {
        System.out.format("Writing generated trips to file for interval (%d) ..........%n", currentInterval);
        joiner = new StringJoiner("_");

        if (SimulationParameters.problemVariant == 2) {
            if (SimulationParameters.algorithmAllMatches)
                joiner.add("drivers").add("total").add("2").add(Integer.toString(SimulationParameters.startHour)).add(Integer.toString(currentInterval)).
                        add(Integer.toString(SimulationParameters.candidateTest)).add(Double.toString(SimulationParameters.distanceRadius)).
                        add(Integer.toString(SimulationParameters.minNumBaseMatchesPerDriver)).
                        add(Integer.toString(SimulationParameters.maxNumBaseMatchesPerDriver)).
                        add(Integer.toString(SimulationParameters.thresholdMatchesForEachPassenger)).
                        add(Integer.toString(SimulationParameters.maxNumMatchesPerDriver));
            else if (SimulationParameters.algorithmBaseMatches)
                joiner.add("drivers").add("base").add("2").add(Integer.toString(SimulationParameters.startHour)).add(Integer.toString(currentInterval)).
                        add(Integer.toString(SimulationParameters.candidateTest)).add(Double.toString(SimulationParameters.distanceRadius)).
                        add(Integer.toString(SimulationParameters.minNumBaseMatchesPerDriver)).
                        add(Integer.toString(SimulationParameters.maxNumBaseMatchesPerDriver)).
                        add(Integer.toString(SimulationParameters.thresholdMatchesForEachPassenger));
            else {
                System.out.println("Select a correct option. Trips and matches have to be generated first before saving to file for ProblemVariant 2.");
                return false;
            }
        } else if (SimulationParameters.algorithmBaseMatches) {
            joiner.add("drivers").add("base").add("1").add(Integer.toString(SimulationParameters.startHour)).add(Integer.toString(currentInterval)).
                    add(Integer.toString(SimulationParameters.candidateTest)).add(Double.toString(SimulationParameters.distanceRadius)).
                    add(Integer.toString(SimulationParameters.minNumBaseMatchesPerDriver)).
                    add(Integer.toString(SimulationParameters.maxNumBaseMatchesPerDriver)).
                    add(Integer.toString(SimulationParameters.thresholdMatchesForEachPassenger));
        } else {
            System.out.println("Select a correct option. Trips and base matches have to be generated first before saving to file for ProblemVariant 1.");
            return false;
        }
        
        String slnParamters = joiner.toString();
        Path fileNamePath = Paths.get(currentPath, DATAFOLDER, tripSubfolder, slnParamters);
        try(
                FileOutputStream fout = new FileOutputStream(fileNamePath.toFile(), false);
                ObjectOutputStream oos = new ObjectOutputStream(fout);
        ) {
                oos.writeObject(currentDrivers);
        } catch (Exception e) {
            System.out.println(e.toString());
            return false;
        }
        
        if (Utility.transmitTripDataTo != null)
            if (!Utility.transmitTripData(fileNamePath, slnParamters))
                return false;
        
        slnParamters = slnParamters.replace("drivers", "passengers");
        fileNamePath = Paths.get(currentPath, DATAFOLDER, tripSubfolder, slnParamters);
        try(
                FileOutputStream fout = new FileOutputStream(fileNamePath.toFile(), false);
                ObjectOutputStream oos = new ObjectOutputStream(fout);
        ) {
                oos.writeObject(currentPassengers);
        } catch (Exception e) {
            System.out.println(e.toString());
            return false;
        }
        
        if (Utility.transmitTripDataTo != null)
            if (!Utility.transmitTripData(fileNamePath, slnParamters))
                return false;
        if (SimulationParameters.problemVariant == 2 && SimulationParameters.algorithmBaseMatches && && !SimulationParameters.algorithmAllMatches) {
            slnParamters = slnParamters.replace("passengers", "IndexIDMapping");
            fileNamePath = Paths.get(currentPath, DATAFOLDER, tripSubfolder, slnParamters);
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(fileNamePath.toFile(), false)));
            ) {
               out.writeInt(alg.tripIDtoTravelDistanceIndex.size());
               System.out.format("size of tripIDtoTravelDistanceIndex = %d%n", alg.tripIDtoTravelDistanceIndex.size());
               for (Map.Entry<Integer, Integer> entry : alg.tripIDtoTravelDistanceIndex.entrySet()) {
                   out.writeInt(entry.getKey());
                   out.writeInt(entry.getValue());
               }
            } catch (Exception e) {
                System.out.println(e.toString());
                return false;
            }

            // save travel time matrix
            slnParamters = slnParamters.replace("IndexIDMapping", "travelDistance");
            fileNamePath = Paths.get(currentPath, DATAFOLDER, tripSubfolder, slnParamters);
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(fileNamePath.toFile(), false)));
            ) {
                for (long[] travelDistance : alg.travelDistance) {
                    for (int j = 0; j < travelDistance.length; j++) {
                        out.writeLong(travelDistance[j]);
                    }
                }
            } catch (Exception e) {
                System.out.println(e.toString());
                return false;
            }
        }
        
        return true;
    }
    
    public boolean isOnlyGenerateTrips() {
        return onlyGenerateTrips;
    }

    public boolean isLoadGeneratedTrips() {
        return loadGeneratedTrips;
    }
}
