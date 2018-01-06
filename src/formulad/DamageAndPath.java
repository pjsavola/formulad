package formulad;

import java.util.List;

public class DamageAndPath {
    private int damage;
    private List<Node> path;

    public DamageAndPath(final int damage,
                         final List<Node> head) {
        this.damage = damage;
        path = head;
    }

    public DamageAndPath addPrefix(final int damage, final List<Node> prefix) {
        this.damage += damage;
        path.addAll(0, prefix);
        return this;
    }

    public int getDamage() {
        return damage;
    }

    public List<Node> getPath() {
        return path;
    }
}
