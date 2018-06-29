package formulad;

import java.util.List;

import formulad.ai.Node;

public class DamageAndPath {
    private int damage;
    private List<Node> path;

    public DamageAndPath(int damage, List<Node> head) {
        this.damage = damage;
        path = head;
    }

    public DamageAndPath addPrefix(int damage, List<Node> prefix) {
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
