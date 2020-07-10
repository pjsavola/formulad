package formulad;

import javax.annotation.Nullable;
import javax.swing.*;
import java.util.Map;

public abstract class Screen extends JPanel {
    public abstract void highlightNodes(@Nullable Map<Integer, Integer> nodeToDamage);

    @Nullable
    public abstract Integer getNodeId(int x, int y);
}
