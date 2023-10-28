package simulation;

//import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.util.*;
//import static com.graphhopper.util.Parameters.Curbsides.CURBSIDE_ANY;
//import static com.graphhopper.util.Parameters.Curbsides.CURBSIDE_RIGHT;
//import com.graphhopper.util.PointList;

public class HopperOperation {
    private final GraphHopper hopper;

    public HopperOperation(GraphHopper hopper) {
            this.hopper = hopper;
    }
	
    public ResponsePath getPathBetweenTwoLocs(Location source, Location dest) throws Exception {
        // simple configuration of the request object
        GHRequest req = new GHRequest(source.getLatitude(),source.getLongitude(), dest.getLatitude(),dest.getLongitude()).
                // note that we have to specify which profile we are using even when there is only one like here
                        setProfile("car").
                // define the language for the turn instructions
                        setLocale(Locale.US);
        GHResponse rsp = hopper.route(req);
        // handle errors
        if (rsp.hasErrors())
            throw new RuntimeException(rsp.getErrors().toString());
        
        return rsp.getBest();
    }
    
    public ResponsePath getPathBetweenTwoLocs(double sourceLongitude, double sourceLatitude, double destLongitude, double destLatitude) throws Exception {
        // simple configuration of the request object
        GHRequest req = new GHRequest(sourceLatitude,sourceLongitude, destLatitude,destLongitude).
                // note that we have to specify which profile we are using even when there is only one like here
                        setProfile("car").
                // define the language for the turn instructions
                        setLocale(Locale.US);
        GHResponse rsp = hopper.route(req);
        // handle errors
        if (rsp.hasErrors())
            throw new RuntimeException(rsp.getErrors().toString());
        
        return rsp.getBest();
    }
    
    public void displayAllPathsBetweenTwoLocs(double sourceLongitude, double sourceLatitude, double destLongitude, double destLatitude) {
        GHRequest req = new GHRequest(sourceLatitude,sourceLongitude, destLatitude,destLongitude).setProfile("car").setLocale(Locale.US);
        GHResponse rsp = hopper.route(req);
        if (rsp.hasErrors())
            System.out.println(rsp.getErrors().toString());
        displayAllPaths(rsp);
    }
    
    public void displayAllPathsBetweenTwoLocs(double sourceLongitude, double sourceLatitude, double destLongitude, double destLatitude, boolean detail) {
        GHRequest req = new GHRequest(sourceLatitude,sourceLongitude, destLatitude,destLongitude).setProfile("car").setLocale(Locale.US);
        GHResponse rsp = hopper.route(req);
        if (rsp.hasErrors())
            System.out.println(rsp.getErrors().toString());
        
        if (detail)
            displayAllPathsDetail(rsp);
        else
            displayAllPaths(rsp);
    }
    
    private void displayAllPathsDetail(GHResponse rsp) {
        PointList pointList;
        double distance;
        long timeInMs;
        Translation tr;
        InstructionList il;
        for (ResponsePath path : rsp.getAll()) {
            // points, distance in meters and time in millis of the full path
            pointList = path.getPoints();
            distance = path.getDistance();
            timeInMs = path.getTime();

            tr = hopper.getTranslationMap().getWithFallBack(Locale.UK);
            il = path.getInstructions();
            // iterate over all turn instructions
            for (Instruction instruction : il) {
                System.out.println("distance " + instruction.getDistance() + " for instruction: " + instruction.getTurnDescription(tr));
            }
            for (int i = 0; i < pointList.size(); i++)
                System.out.println("("+pointList.get(i).lon+","+pointList.get(i).lat+")");
            System.out.println("Distance="+distance+", timeInMs="+timeInMs);
        }
    }
    
    public void displayAllPaths(GHResponse rsp) {
        double distance;
        long timeInMs;
        Translation tr;
        InstructionList il;
        for (ResponsePath path : rsp.getAll()) {
            // points, distance in meters and time in millis of the full path
            distance = path.getDistance();
            timeInMs = path.getTime();

            tr = hopper.getTranslationMap().getWithFallBack(Locale.UK);
            il = path.getInstructions();
            // iterate over all turn instructions
            for (Instruction instruction : il)
                System.out.println("distance " + instruction.getDistance() + " for instruction: " + instruction.getTurnDescription(tr));
            
            System.out.println("Distance="+distance+", timeInMs="+timeInMs);
        }
    }
    
    public void displayPathBetweenTwoLocs(Location source, Location dest) {
        try {
            ResponsePath path = getPathBetweenTwoLocs(source, dest);
            // points, distance in meters and time in millis of the full path
            PointList pointList = path.getPoints();
            double distance = path.getDistance();
            long timeInMs = path.getTime();

            Translation tr = hopper.getTranslationMap().getWithFallBack(Locale.UK);
            InstructionList il = path.getInstructions();
            // iterate over all turn instructions
            for (Instruction instruction : il) {
                System.out.println("distance " + instruction.getDistance() + " for instruction: " + instruction.getTurnDescription(tr));
            }
            for (int i = 0; i < pointList.size(); i++)
                System.out.println("("+pointList.get(i).lon+","+pointList.get(i).lat+")");
            System.out.println("Distance="+distance+", timeInMs="+timeInMs);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println(Arrays.toString(e.getStackTrace()));
        }
    }
    
    public double getDistanceBetweenTwoLocsDefault(Location source, Location dest) {
        GHRequest req = new GHRequest(source.getLatitude(),source.getLongitude(), dest.getLatitude(),dest.getLongitude()).setProfile("car").setLocale(Locale.US);
        return route(req);
    }
    
    public double getDistanceBetweenTwoLocsDefault(double sourceLongitude, double sourceLatitude, double destLongitude, double destLatitude) {
        GHRequest req = new GHRequest(sourceLatitude,sourceLongitude, destLatitude,destLongitude).setProfile("car").setLocale(Locale.US);
        return route(req);
    }
    
    // By car, in meters
    public double getDistanceBetweenTwoLocs(Location source, Location dest) {
        GHRequest req = new GHRequest(source.getLatitude(),source.getLongitude(), dest.getLatitude(),dest.getLongitude()).
                setProfile("car").setLocale(Locale.US).setAlgorithm(Parameters.Algorithms.DIJKSTRA_BI);
        return route(req);
    }

    public double getDistanceBetweenTwoLocs(double sourceLongitude, double sourceLatitude, double destLongitude, double destLatitude) {
        GHRequest req = new GHRequest(sourceLatitude,sourceLongitude, destLatitude,destLongitude).
                    setProfile("car").setLocale(Locale.US).setAlgorithm(Parameters.Algorithms.DIJKSTRA_BI);
        return route(req);
    }
    
    // By car, in meters
    public double getDistanceBetweenTwoLocsAStarCH(Location source, Location dest) {
        GHRequest req = new GHRequest(source.getLatitude(),source.getLongitude(), dest.getLatitude(),dest.getLongitude()).
                setProfile("car").setLocale(Locale.US).setAlgorithm(Parameters.Algorithms.ASTAR_BI);
        return route(req);
    }

    public double getDistanceBetweenTwoLocsAStarCH(double sourceLongitude, double sourceLatitude, double destLongitude, double destLatitude) {
        GHRequest req = new GHRequest(sourceLatitude,sourceLongitude, destLatitude,destLongitude).
                    setProfile("car").setLocale(Locale.US).setAlgorithm(Parameters.Algorithms.ASTAR_BI);
        return route(req);
    }
    
    private double route(GHRequest req) {
        GHResponse res = hopper.route(req);
        // handle errors
        if (res.hasErrors()) {
            System.out.println(res.getErrors().toString());
            return 0;
        }
        return res.getBest().getDistance();
    }
    
    public void routeWithTurnCostsAndOtherUTurnCosts(Location source, Location dest) {
        GHRequest req = new GHRequest(source.getLatitude(),source.getLongitude(), dest.getLatitude(),dest.getLongitude())
                .setCurbsides(Arrays.asList(Parameters.Curbsides.CURBSIDE_ANY, Parameters.Curbsides.CURBSIDE_RIGHT))
                // to change u-turn costs per request we have to disable CH. otherwise the u-turn costs we set per request
                // will be ignored and those set for our profile will be used.
                .putHint(Parameters.CH.DISABLE, true)
                .setProfile("car").setLocale(Locale.US);
        
        //route(req.putHint(Parameters.Routing.U_TURN_COSTS, 10));
        req = req.putHint(Parameters.Routing.U_TURN_COSTS, 15);
        GHResponse rsp = hopper.route(req);
        if (rsp.hasErrors())
            throw new RuntimeException(rsp.getErrors().toString());
        System.out.println("Distance="+rsp.getBest().getDistance()+", timeInMs="+rsp.getBest().getTime());
    }
}
