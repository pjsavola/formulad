package gp.model;

import gp.Client;

import java.io.Serializable;

public class RollNotification extends Notification implements Serializable {
    private final int gear;
    private final int roll;

    public RollNotification(String playerId, int gear, int roll) {
        super(playerId);
        this.gear = gear;
        this.roll = roll;
    }

    public int getGear() {
        return gear;
    }

    public int getRoll() {
        return roll;
    }

    @Override
    public void notify(Client client) {
        client.notify(this);
    }
}
