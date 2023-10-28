# RPC-simulation
This repo contains the complete implementation of the extensive empirical study (simulation and algorithms) in [this paper](https://doi.org/10.48550/arXiv.2310.04933), to be appeared in COCOA2023.
All algorithms are implemented in Java.

# Prerequisites
- Java 11 (higher may work, but not tested)
- IBM ILOG CPLEX v12.xx (other versions of CPLEX may work, but not tested)

# Mandatory and Dependency
There must exist two folders call "lib" and "data" (without the quotes) in the same directory where the executable is.
1. In the data folder, it needs to contain all the files needed for the simulation (total of 7 files and 3 sub-folders).
All the needed files and folders are included in the executable folder, except one file.
   - The file named "Chicago.osm.pbf" is a place holder file. It originally contains the geological data of the city of Chicago, but since it has been compiled into a compact format (stored in the sub-folder "gh_folder"), Chicago.osm.pbf is now a place holder.
2. In the lib folder, it should contain all the following libraries using Maven.
```
<dependency>
    <groupId>com.graphhopper</groupId>
    <artifactId>graphhopper-core</artifactId>
    <version>7.0-pre1</version>
</dependency>
<dependency>
   <groupId>com.graphhopper</groupId>
   <artifactId>graphhopper-map-matching-core</artifactId>
   <version>1.0</version>
</dependency>
<dependency>
    <groupId>org.slf4j</groupId>
	  <artifactId>slf4j-simple</artifactId>
	  <version>1.7.30</version>
</dependency>
<dependency>
   <groupId>ilog.cplex</groupId>
   <artifactId>cplex</artifactId>
   <version>12.10</version>
</dependency>
<dependency>
   <groupId>org.orbisgis</groupId>
   <artifactId>poly2tri</artifactId>   
   <version>0.5.0-SNAPSHOT</version>
</dependency>
```

The following repository should be included in your Java application as well.
```
<repository>
    <id>orbisgis-snapshot</id>
    <name>OrbisGIS sonatype snapshot repository</name>
    <url>https://oss.sonatype.org/content/repositories/snapshots/</url>
</repository>
```

# Usage
To run the simulation, execute the jar file: RPCSimulation.jar in the executable folder.
You may have to include the lib in the system PATH for the program to locate the libraries.
You may also use the following command. Once you cd to the executable folder, enter
```
java -cp "RPCSimulation.jar;lib/*" simulation.RPCSimulation
```
If you run into memory problem, adjust the JVM memory settings with "-Xmx" and "-Xms".
The default setting only runs the simulation for the RPC+ problam variant for one iteration.
To run all 72 iterations, change the line
```
NumberOfIntervals=1 to NumberOfIntervals=72 in the "config.properties" file in the data folder.
```
