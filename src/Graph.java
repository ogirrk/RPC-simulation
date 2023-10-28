package simulation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/*
** Graph data structure specifically for the modified successive shortest path algorithm to solve the RPC1 problem variant.
** An extra set of edges is stored for fast access of the edges.
*/
public class Graph {
    public int numEdges;
    public HashMap<Integer, Set<MatchEdge>> outEdges;       // adjacency list of each vertex (represented by an integer)
    public Set<MatchEdge> edgeSet;                          // the set of all edges in the graph
    public Integer sourceVertex;
    public Integer sinkVertex;
    
    public Graph () {
        outEdges = new HashMap<>();
    }
    
    public Graph(int n, int m) {
        numEdges = m;
        outEdges = new HashMap<>(n);
        edgeSet = new HashSet<>(m);
    }

    public void addVertex(Integer tail) {
        outEdges.put(tail, new HashSet<>());
    }
    
    public void addEdge(MatchEdge edge) {
        outEdges.get(edge.tail).add(edge);
        edgeSet.add(edge);
    }
}
