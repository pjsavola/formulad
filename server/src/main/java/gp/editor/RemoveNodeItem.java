package gp.editor;

import gp.ai.Node;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class RemoveNodeItem implements UndoItem {
    private final Node node;
    private UndoStack stack;
    private Point point;
    private Double attribute;
    private Double gridAngle;
    private List<Node> parents = new ArrayList<>();

    public RemoveNodeItem(Node node) {
        this.node = node;
    }

    @Override
    public boolean execute(UndoStack stack) {
        this.stack = stack;
        stack.nodes.forEach(n -> {
            if (n.hasChild(node)) {
                n.removeChild(node);
                parents.add(n);
            }
        });
        point = stack.coordinates.remove(node);
        attribute = stack.attributes.remove(node);
        gridAngle = stack.attributes.remove(node);
        stack.nodes.remove(node);
        return true;
    }

    @Override
    public void undo() {
        stack.nodes.add(node);
        if (gridAngle != null) stack.gridAngles.put(node, gridAngle);
        if (attribute != null) stack.attributes.put(node, attribute);
        stack.coordinates.put(node, point);
        parents.forEach(parent -> parent.addChild(node));
    }
}
