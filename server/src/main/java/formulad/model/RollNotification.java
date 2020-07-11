package formulad.model;

import java.io.Serializable;

public class RollNotification implements Serializable {
    private String playerId;
    private int gear;
    private int roll;

    public RollNotification playerId(String playerId) {
        this.playerId = playerId;
        return this;
    }

    public RollNotification gear(int gear) {
        this.gear = gear;
        return this;
    }

    public RollNotification roll(int roll) {
        this.roll = roll;
        return this;
    }

    public String getPlayerId() {
        return playerId;
    }

    public int getGear() {
        return gear;
    }

    public int getRoll() {
        return roll;
    }
}
