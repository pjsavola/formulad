package formulad.model;

import java.io.Serializable;

public class DamageNotifiaction implements Serializable {
    public String playerId;
    public int damage;

    public DamageNotifiaction playerId(String playerId) {
        this.playerId = playerId;
        return this;
    }

    public DamageNotifiaction damage(int damage) {
        this.damage = damage;
        return this;
    }

    public String getPlayerId() {
        return playerId;
    }

    public int getDamage() {
        return damage;
    }
}
