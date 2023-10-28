package simulation;

public final class Location {
    //private static final long serialVersionUID = 1L;
    private final double longitude;
    private final double latitude;

    public Location(double longitude, double latitude) {
        this.longitude = longitude;
        this.latitude = latitude;
    }

    public Location(double longitude, double latitude, String color) {
        this.longitude = longitude;
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getLatitude() {
        return latitude;
    }
    
    public double getX() {
        return longitude;
    }

    public double getY() {
        return latitude;
    }

    public double distanceTo(Location loc) {
        return Math.sqrt( (latitude - loc.getLatitude())*(latitude - loc.getLatitude()) +
                            (longitude - loc.getLongitude())*(longitude - loc.getLongitude()) );
    }
    
    // this method returns this Location for now (but can be a random location near this location later)
    public Location randomLocation() {
        return this;
    }
    public Location getLocation() {
        return this;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        long temp;
        temp = Double.doubleToLongBits(latitude);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(longitude);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null)
                return false;
        if (this == other)
                return true;
        if (other instanceof Location) {
                Location loc = (Location) other;
                return (this.longitude == loc.longitude && this.latitude == loc.latitude);
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("Location (%.12f, %.12f)",longitude,latitude);
    }
    
    public String toSimpleString() {
        return String.format("%.12f, %.12f",longitude,latitude);
    }
}
