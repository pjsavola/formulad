package formulad;

import java.util.List;
import java.util.Map;

import com.sun.istack.internal.Nullable;

public interface AI {
    void sendPlayerData(List<Player> playerList);
    int decideGear();
    NodeOrAdjustment decideTarget(@Nullable Map<Node, DamageAndPath> targets);

    class NodeOrAdjustment {
        @Nullable
        public final Node node;
        public final int adjust;

        public NodeOrAdjustment(@Nullable Node node, int adjust) {
            this.node = node;
            this.adjust = adjust;
        }
    }
}
