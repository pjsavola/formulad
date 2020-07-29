package gp.editor;

import gp.ai.Node;
import gp.ai.NodeType;

import java.awt.*;
import java.util.List;
import java.util.ArrayList;

public class CreateNodesItem implements UndoItem {
    private final int firstId;
    private final NodeType type;
    private final Point start;
    private final Node parent;
    private final int dx;
    private final int dy;
    private final int count;
    private UndoStack stack;
    private List<Node> nodes = new ArrayList<>();

    public CreateNodesItem(int firstId, NodeType type, Point start, Node parent, int dx, int dy, int count) {
        this.firstId = firstId;
        this.type = type;
        this.start = start;
        this.parent = parent;
        this.dx = dx;
        this.dy = dy;
        this.count = count;
    }

    @Override
    public boolean execute(UndoStack stack) {
        if (count < 1) {
            return false;
        }
        this.stack = stack;
        for (int i = 0; i < count; ++i) {
            final Node newNode = new Node(firstId + i, type);
            stack.nodes.add(newNode);
            final Point p = new Point(start.x + (i + 1) * dx / count, start.y + (i + 1) * dy / count);
            newNode.setLocation(p);
            if (!nodes.isEmpty()) {
                nodes.get(nodes.size() - 1).addChild(newNode);
            }
            nodes.add(newNode);
        }
        parent.addChild(nodes.get(0));
        return true;
    }

    @Override
    public void undo() {
        parent.removeChild(nodes.get(0));
        stack.nodes.removeAll(nodes);
    }

    public Node getLastItem() {
        return nodes.get(nodes.size() - 1);
    }
}
