package gp.model;

import java.io.Serializable;

public class Kick implements Serializable {
    private String reason;

    public Kick(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
