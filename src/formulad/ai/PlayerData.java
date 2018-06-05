package formulad.ai;

public class PlayerData {

    private final int id;
    private Node node;
    private int hitpoints;
    private int gear;
    private int curveStops;

    public PlayerData(int id, Node node, int hitpoints, int gear, int curveStops) {
        this.id = id;
        this.node = node;
        this.hitpoints = hitpoints;
        this.gear = gear;
        this.curveStops = curveStops;
    }

    public void update(Node node, int hitpoints, int gear, int stops) {
        this.node = node;
        this.hitpoints = hitpoints;
        this.gear = gear;
        this.curveStops = curveStops;
    }

    public int getId() {
        return id;
    }

    public Node getNode() {
        return node;
    }

    public int getHitpoints() {
        return hitpoints;
    }

    public int getGear() {
        return gear;
    }

    public int getCurveStops() {
        return curveStops;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other instanceof PlayerData) return id == ((PlayerData) other).id;
        return false;
    }
}
