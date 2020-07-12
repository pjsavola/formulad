package formulad.model;

import formulad.Client;

import java.io.Serializable;

public class CurveStopNotification extends Notification implements Serializable {
    private final int curveStops;

    public CurveStopNotification(String playerId, int curveStops) {
        super(playerId);
        this.curveStops = curveStops;
    }

    public int getCurveStops() {
        return curveStops;
    }

    @Override
    public void notify(Client client) {
        client.notify(this);
    }
}
