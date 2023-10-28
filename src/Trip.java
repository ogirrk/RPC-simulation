package simulation;
import java.io.Serializable;

public interface Trip extends Serializable {
    public int getID();
    public double getStartLongitude();
    public double getStartLatitude();
    public double getEndLongitude();
    public double getEndLatitude();
    public int getStartRegion();
    public int getEndRegion();
    public long getDepartureTime();
    public long getArrivalTime();
    public long getMaxTravelDuration();
}
