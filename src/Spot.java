package simulation;

import java.io.Serializable;

public interface Spot extends Serializable {
    public Location randomLocation();
    public Location getSpotLocation();
    public void setSumOriginatedFromThis(double[] trafficValue);
    public int randomDestinationArea(int hour);
    public int getId();
    public void setId(int id);
}
