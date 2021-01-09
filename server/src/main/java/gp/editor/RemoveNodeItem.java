package gp.editor;

import gp.ai.Node;

import java.util.ArrayList;
import java.util.List;

public class RemoveNodeItem implements UndoItem {
    private final Node node;
    private UndoStack stack;
    private Double attribute;
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
        attribute = stack.attributes.remove(node);
        stack.nodes.remove(node);
        return true;
    }

    @Override
    public void undo() {
        stack.nodes.add(node);
        if (attribute != null) stack.attributes.put(node, attribute);
        parents.forEach(parent -> parent.addChild(node));
    }
}
