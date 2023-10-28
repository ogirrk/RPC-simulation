
package simulation;

import java.io.Serializable;

public class Triple<T1, T2, T3> implements Serializable {
    private static final long serialVersionUID = 1L;
    private T1 p1;
    private T2 p2;
    private T3 p3;
    
    public Triple(T1 P1, T2 P2, T3 P3) {
        p1 = P1;
        p2 = P2;
        p3 = P3;
    }

    public T1 getP1() {
        return p1;
    }

    public void setP1(T1 p1) {
        this.p1 = p1;
    }

    public T2 getP2() {
        return p2;
    }

    public void setP2(T2 p2) {
        this.p2 = p2;
    }

    public T3 getP3() {
        return p3;
    }

    public void setP3(T3 p3) {
        this.p3 = p3;
    }
}
