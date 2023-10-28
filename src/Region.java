package simulation;

import java.util.List;
import java.util.TreeMap;
import org.poly2tri.Poly2Tri;
import org.poly2tri.geometry.polygon.Polygon;
import org.poly2tri.geometry.polygon.PolygonPoint;
import org.poly2tri.triangulation.delaunay.DelaunayTriangle;

public class Region {
    private final int ID;
    private final List<Location> Boundaries;
    private final double PRECISION = 1000000d;
    private List<DelaunayTriangle> Triangles;
    private TreeMap<Double, Integer> probability;   // <probability, indexOfTriangles>
            
    public Region (int id, List<Location> boundaries) {
        ID = id;
        Boundaries = boundaries;
        triangulateRegion();
    }
    
    public int getID() {
        return ID;
    }

    public List<Location> getBoundaries() {
        return Boundaries;
    }
    
    public void displayRegion() {
        System.out.print("Region " + ID + ": ");
        for (Location loc : Boundaries)
            System.out.println(loc.toString());
    }
    
    private void triangulateRegion() {
        if (Boundaries.size() > 1) {
            PolygonPoint[] points = new PolygonPoint[Boundaries.size()-1];
            for (int i = 0; i < Boundaries.size()-1; i++)
                points[i] = new PolygonPoint(Boundaries.get(i).getLongitude(), Boundaries.get(i).getLatitude());
            Polygon polygon = new Polygon(points);
            Poly2Tri.triangulate(polygon);
            Triangles = polygon.getTriangles();

            double[] proportion = new double[Triangles.size()];
            double totalArea = Triangles.get(0).area();
            proportion[0] = Triangles.get(0).area();
            for (int i = 1; i < Triangles.size(); i++) {
                proportion[i] = Triangles.get(i).area() + proportion[i-1];
                totalArea += Triangles.get(i).area();
            }
            probability = new TreeMap<>();
            for (int i = 0; i < Triangles.size(); i++)
                probability.put(proportion[i]/ totalArea, i);
        }
    }
    
    double sign(Location p1, Location p2, Location p3)
    {
        return (p1.getX()- p3.getX()) * (p2.getY() - p3.getY()) - (p2.getX() - p3.getX()) * (p1.getY() - p3.getY());
    }

    boolean PointInTriangle(Location pt, Location v1, Location v2, Location v3)
    {
        double d1, d2, d3;
        boolean has_neg, has_pos;

        d1 = sign(pt, v1, v2);
        d2 = sign(pt, v2, v3);
        d3 = sign(pt, v3, v1);

        has_neg = (d1 < 0) || (d2 < 0) || (d3 < 0);
        has_pos = (d1 > 0) || (d2 > 0) || (d3 > 0);

        return !(has_neg && has_pos);
    }
    
    public Location generateRandomLocation() {
        if (Boundaries.size() == 1)
            return Boundaries.get(0);
        
        DelaunayTriangle triangle = Triangles.get(probability.higherEntry(Utility.random.nextDouble()).getValue());
        //System.out.println(triangle.points[0].getX()+","+triangle.points[0].getY());
        //System.out.println(triangle.points[1].getX()+","+triangle.points[1].getY());
        //System.out.println(triangle.points[2].getX()+","+triangle.points[2].getY());
        double r1 = Utility.random.nextDouble() * PRECISION / PRECISION;
        double r2 = Utility.random.nextDouble() * PRECISION / PRECISION;
        
        if (r1+r2 > 0) {
            r1 = 1 - r1;
            r2 = 1 - r2;
        }
        
        // vector AB, 0=A, 1=B
        double xAB = triangle.points[1].getX() - triangle.points[0].getX();
        double yAB = triangle.points[1].getY() - triangle.points[0].getY();
        // vector AC, 0+A, C=2
        double xAC = triangle.points[2].getX() - triangle.points[0].getX();
        double yAC = triangle.points[2].getY() - triangle.points[0].getY();
        
        // new point
        double randomPointX = r1*xAB + r2*xAC;
        double randomPointY = r1*yAB + r2*yAC;
        Location location = new Location(randomPointX+triangle.points[0].getX(), randomPointY+triangle.points[0].getY());
        
        if (PointInTriangle(location, new Location(triangle.points[0].getX(), triangle.points[0].getY()),
                new Location(triangle.points[1].getX(), triangle.points[1].getY()), new Location(triangle.points[2].getX(), triangle.points[2].getY())))
            return location;
        
        //System.out.println("Point ("+randomPointX+","+randomPointY+") not in Triangle, and project it.");
        // A + AB + AC - p
        randomPointX = triangle.points[0].getX() + xAB + xAC - randomPointX;
        randomPointY = triangle.points[0].getY() + yAB + yAC - randomPointY;
        return new Location(randomPointX, randomPointY);
    }
}
