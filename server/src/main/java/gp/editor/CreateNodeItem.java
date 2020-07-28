package gp.editor;

import gp.ai.Node;
import gp.ai.NodeType;

import java.awt.*;

public class CreateNodeItem implements UndoItem {
    private final int id;
    private final NodeType type;
    private final Point point;
    private final Node parent;
    private UndoStack stack;
    private Node node;

    public CreateNodeItem(int id, NodeType type, Point point, Node parent) {
        this.id = id;
        this.type = type;
        this.point = point;
        this.parent = parent;
    }

    @Override
    public boolean execute(UndoStack stack) {
        this.stack = stack;
        node = new Node(id, type);
        stack.nodes.add(node);
        stack.coordinates.put(node, point);
        if (parent != null) {
            parent.addChild(node);
        }
        return true;
    }

    @Override
    public void undo() {
        if (parent != null) {
            parent.removeChild(node);
        }
        stack.coordinates.remove(node);
        stack.nodes.remove(node);
    }
}
