package simulation;

import java.io.Serializable;

public class Pair<T1, T2> implements Serializable {
    private static final long serialVersionUID = 1L;
    private T1 p1;
    private T2 p2;

    public Pair() {}
    
    public Pair(T1 P1, T2 P2) {
        p1 = P1;
        p2 = P2;
    }

    public void set(T1 P1, T2 P2) {
        p1 = P1;
        p2 = P2;
    }

     public void setP1(T1 P1) {
        p1 = P1;
    }

     public void setP2(T2 P2) {
        p2 = P2;
    }

    public T1 getP1() {
        return p1; 
    }

    public T2 getP2() {
        return p2;
    }
}
