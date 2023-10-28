package simulation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
//import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Arrays;
//import java.util.List;
import java.util.Random;
import java.util.Set;
//import java.util.function.Function;
//import java.util.logging.Level;
//import java.util.logging.Logger;

public class Utility {
    public static String newline = System.getProperty("line.separator");	// use this when writing newline to file (print can just use \n)
    public static final int SEED = 7777;
    public static String pathToLogFile = null;
    public static Random random = new Random(SEED);
    public static boolean testing = false;;
    public static Path transmitTripDataTo = null;       // this should be null for now
    public static Path transmitTripDataFrom = null;     // this should be null for now
    public static double multiplier = 1.0;
    public static int skipToIteration = 0;
    public static boolean computeVirtualEdge = false;
    public static boolean LPR = false;
    public static boolean DEBUG = false;
    public static final double MileToKM = 1.609344;
    public static Writer logWriter = null;
    
    private Utility() {}
    
    /** 
     * This uses a fixed format for geojson.io, namely "lon, lat, marker-color, marker-size"
     * @param pathToFile complete path to file to be written, including file extension.
     * @param writeORappend write or append.
     * @param locationList a list of locations to be written to file.
     */
    @SafeVarargs
    public static void writePointsToFile(String pathToFile, char writeORappend, ArrayList<Location>... locationList) {	
        Writer writer= null;
        switch (writeORappend) {
            case 'w':
                writer = Utility.getWriter(pathToFile);
                break;
            case 'a':
                writer = Utility.getAppendWriter(pathToFile);
                break;
            default:
                System.out.println("Choose append or overwrite: 'w' or 'a'");
                break;
        }
        if (writer == null) {
                System.out.println("Data have not be written to file: " + pathToFile);
                return;
        }

        String defaultSize = "small";
        try {
                int count = 0;
                if (writeORappend == 'w')
                    writer.write("lon,lat,marker-color,marker-size"+newline);
                for (ArrayList<Location> locs : locationList) {
                    for (Location loc : locs) {
                        writer.write(loc.getLongitude()+","+loc.getLatitude()+",#b0b0b0,"+defaultSize+newline);
                        count++;
                    }
                }
                System.out.println(count + " number of records have written to file: " + pathToFile);
        } catch (IOException e) {
            System.out.println(e.toString());
        } finally {
            try {
                writer.close();
            } catch (IOException e) {
                System.out.println(e.toString());
            }
        }
    }
	
    public static Writer getWriter(String file) {
        try {
            File outFile = new File(file);
            outFile.createNewFile(); // if file already exists will do nothing
            FileOutputStream oFile = new FileOutputStream(outFile);
            return new BufferedWriter(new OutputStreamWriter(oFile, "utf-8"));
        } catch (IOException e) {
            System.out.println(e.toString());
            return null;
        }
    }
    
    public static Writer getWriter(File outFile) {
        try {
            outFile.createNewFile(); // if file already exists will do nothing
            FileOutputStream oFile = new FileOutputStream(outFile);
            return new BufferedWriter(new OutputStreamWriter(oFile, "utf-8"));
        } catch (IOException e) {
            System.out.println(e.toString());
            return null;
        }
    }
	
    public static Writer getAppendWriter(String file) {
        try {
            File outFile = new File(file);
            outFile.createNewFile();
            FileOutputStream oFile = new FileOutputStream(outFile, true);
            return new BufferedWriter(new OutputStreamWriter(oFile, "utf-8"));
        } catch (IOException e) {
            System.out.println(e.toString());
            return null;
        }
    }
	
    public static BufferedReader getReader(String file) {
        try {
            File inFile = new File(file);
            if (!inFile.isDirectory() && inFile.canRead())
                return new BufferedReader(new FileReader(inFile));
            else
                return null;
        } catch (FileNotFoundException e) {
            System.out.println(e.toString());
            return null;
        }
    }
	
    public static boolean isTwoSetsIntersect(HashSet<Integer> set1, HashSet<Integer> set2) {
        HashSet<Integer> aSet = set2;
        HashSet<Integer> small = set1;
        if (set1.size() > set2.size()) {
            aSet = set1;
            small = set2;
        }

        Iterator<Integer> itr=small.iterator();
        while(itr.hasNext()) {
            if (aSet.contains(itr.next()))
                return true;
        }
        return false;
    }
	
    public static boolean isTwoSetsIntersect(HashSet<Integer> set1, int[] set2) {		
        for (int value : set2) {
            if (set1.contains(value))
                return true;
        }
        return false;
    }
	
    // special size of intersection
    public static int sizeOfIntersection(HashSet<Integer> set1, HashSet<Integer> set2) {
        HashSet<Integer> aSet = set2;
        HashSet<Integer> small = set1;
        if (set1.size() > set2.size()) {
            aSet = set1;
            small = set2;
        }

        int size = 0;
        for (int element : small) {
            if (aSet.contains(element))
                size++;
        }
        if (size == 0)
            return -1;
        return size == small.size() ? 0 : size;
    }
	
    // Is set1 a subset of set2
    public static boolean isSubset(HashSet<Integer> set1, HashSet<Integer> set2) {
        if (set1.size() > set2.size())
            return false;

        for (int element : set1) {
            if (!set2.contains(element))
                return false;
        }
        return true;
    }

    // this is a special equal check: |set1|=|set2| is true
    public static boolean isTwoSetsEqual(Set<Integer> set1, int[] set2) {
        for (int element : set2) {
            if (!set1.contains(element))
               return false;
        }
        return true;
    }
	
    public static boolean isContained(int[] list, int elmenet) {
        for (int e: list)
                if (e == elmenet)
                        return true;
        return false;
    }

    public static boolean areFilesAccessible(ArrayList<Path> filePaths) {
        for (Path p : filePaths) {
            if (!isFileAccessible(p)) {
                System.out.println("Not able to access/load: " + p);
                return false;
            }
        }
        return true;
    }

    public static boolean isFileAccessible(Path pathToFile) {
        if (pathToFile.isAbsolute()) {
            if (pathToFile.toFile().isDirectory())
                return true;
            else
                return (Files.isWritable(pathToFile) && Files.isReadable(pathToFile));
        }
        return false;
    }
	
	public static boolean isInt(char ch) {
        try {
            return Character.isDigit(ch);
        } catch (Exception e) {
            System.out.println(ch + " is not an integer.");
            return false;
        }
    }
	
    public static boolean isInt(String value) {
        try {
            Integer.valueOf(value);
            return true;
        } catch (NumberFormatException e) {
            System.out.println(value + " is not an Integer.");
            return false;
        }
    }
    
    public static boolean isFloat(String value) {
        try {
            Float.valueOf(value);
            return true;
        } catch (NumberFormatException e) {
            System.out.println(value + " is not a Float.");
            return false;
        }
    }

    public static boolean isDouble(String value) {
        try {
            Double.valueOf(value);
            return true;
        } catch (NumberFormatException e) {
            System.out.println(value + " is not a Double.");
            return false;
        }
    }

    public static boolean isBoolean(String value) {
        try {
            Boolean.valueOf(value);
            return true;
        } catch (Exception e) {
            System.out.println(value + " is not a Boolean.");
            return false;
        }
    }
    
    public static boolean isLong(String value) {
        try {
            Long.valueOf(value);
            return true;
        } catch (NumberFormatException e) {
            System.out.println(value + " is not a Long.");
            return false;
        }
    }
		
    public static Writer startSimulationLogToFile(String log) {
        if (pathToLogFile == null) {
            System.out.println("Setup path to log file first");
            return null;
        }

        // append to file
        logWriter = Utility.getAppendWriter(pathToLogFile);
        if (logWriter == null) {
            System.out.println("Data have not be written to file: " + pathToLogFile);
            return null;
        }

        boolean oper = false;
        try {
            LocalDateTime localDateTime = LocalDateTime.now();
            logWriter.write(newline+"---------------------------------------------------------------------------"+newline);
            logWriter.write("--------------- Begin Simulation + Simulation Start Time: "+ localDateTime + " ---------------"+newline);
            logWriter.write(log + newline+"---------------------------------------------------------------------------"+newline);
            logWriter.flush();
            oper = true;
        } catch (IOException e) {
            System.out.println(e.toString());
            try {
                logWriter.close();
            } catch (IOException e1) {
                System.out.println(e1.toString());
            }
        }
        if (oper)
            return logWriter;
        return null;
    }
    
    public static void simulationLogToFile(String[] logs, boolean flush) {
        simulationLogToFile(logWriter, logs, flush);
    }
    
    public static void simulationLogToFile(Writer writer, String[] logs, boolean flush) {
        if (writer == null) {
            System.out.println("Simulation Logfile handler has not be set.");
            return;
        }
            
        try {
            for (String log : logs) {
                if (log != null){
                    if (log.length() > 0){
                        writer.write(log);
                        writer.write(newline);
                    }
                }
            }
            if (flush)
                writer.flush();
        } catch (IOException e) {
            System.out.println(e.toString());
            try {
                writer.close();
            } catch (IOException e1) {
                System.out.println(e1.toString());
            }
        }
    }
    
    public static void finalSimulationLog(String... logs) {
        finalSimulationLog(logWriter, logs);
    }
    
    public static void finalSimulationLog(Writer writer, String... logs) {
        if (writer == null) {
            System.out.println("Simulation Logfile handler has not be set.");
            return;
        }
        
        try {
            writer.write("---------------------- End Simulation ----------------------"+newline);
            writer.flush();
        } catch (IOException e) {
            System.out.println(e.toString());
        } finally {
            try {
                writer.close();
            } catch (IOException e1) {
                System.out.println(e1.toString());
            }
        }
    }

    public static boolean transmitTripData(Path source, String paramters) {
        try {
            Path target = transmitTripDataTo.resolve(paramters);
            System.out.println("Transmiting newly generated trip data to " + target);
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } catch (Exception e2) {
            System.out.println(e2.getMessage());
        } 
        return false;
    }
    
    public static boolean copyTripDataFrom(Path dest, String paramters) {
        try {
            Path source = transmitTripDataFrom.resolve(paramters);
            System.out.println("Transmiting previously generated trip data from " + source);
            Files.copy(source, dest, StandardCopyOption.COPY_ATTRIBUTES);
            return true;
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } catch (Exception e2) {
            System.out.println(e2.getMessage());
        }
        return false;
    }
    
    public static void deleteFile(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            //Logger.getLogger(Utility.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println("Cannot delete file: " + e.getMessage());
        }
    }

    public static boolean compareTwoMatrices(double[][] M1, double[][] M2) {
        if (M1.length != M2.length)
            return false;
        for (int i = 0; i < M1.length; i++) {
            if (M1[i].length != M2[i].length)
                return false;
            for (int j = 0; j < M1[i].length; j++)
                if (M1[i][j] != M2[i][j])
                    return false;
        }
        return true;
    }
    
    public static boolean compareTwoMatrices(long[][] M1, long[][] M2) {
        if (M1.length != M2.length) {
            System.out.format("M1.length != M2.length: (%d) == (%d)", M1.length, M2.length);
            return false;
        }
        for (int i = 0; i < M1.length; i++) {
            if (M1[i].length != M2[i].length) {
                System.out.format("M1[i].length != M2[i].length: (%d) == (%d)", M1[i].length, M2[i].length);
                return false;
            }
            for (int j = 0; j < M1[i].length; j++)
                if (M1[i][j] != M2[i][j])
                    return false;
        }
        return true;
    }

    public static boolean isBoolean(char ch) {
        if (isInt(ch)) {
            int value = ch - '0';
            return value == 0 || value == 1;
        }
        return false;
    }

    public static boolean convertToBoolean(char ch) {
        int value = ch - '0';
        return value != 0;
    }

    public static void setPathToLogFile(Path fullPath) {
        pathToLogFile = fullPath.toString();
    }

    public static double[][] copyMatrixLoop(double[][] matrix) {
        double[][] temp = null;
        if (matrix != null) {
            temp = new double[matrix.length][matrix.length];
            for (int i = 0; i < matrix.length; i++)
                System.arraycopy(matrix[i], 0, temp[i], 0, matrix[i].length);
        }
        return temp;
    }

    public static double[][] copyMatrix(double[][] matrix) {
        if (matrix == null)
            return null;
        return Arrays.stream(matrix).map(double[]::clone).toArray(double[][]::new);
    }
    
    public static long[][] copyMatrix(long[][] matrix) {
        if (matrix == null)
            return null;
        return Arrays.stream(matrix).map(long[]::clone).toArray(long[][]::new);
    }

    public static int generateHashCode(int[] arr, int id) {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
        result = prime * result + Arrays.hashCode(arr);
        return result;
    }

    public static String flatIntArray(int[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int num : arr)
            sb.append(num);
        return sb.toString();
    }
}
