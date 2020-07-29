package gp.editor;

import gp.ai.Node;

import java.awt.*;
import java.util.*;
import java.util.List;

public class UndoStack {
    private final Deque<UndoItem> items = new ArrayDeque<>();
    private MenuItem menuItem;
    final List<Node> nodes;
    final Map<Node, Double> attributes;

    public UndoStack(List<Node> nodes, Map<Node, Double> attributes) {
        this.nodes = nodes;
        this.attributes = attributes;
    }

    public void setMenuItem(MenuItem menuItem) {
        this.menuItem = menuItem;
        menuItem.setEnabled(canUndo());
    }

    public void execute(UndoItem item) {
        if (item.execute(this)) {
            items.push(item);
        }
        menuItem.setEnabled(canUndo());
    }

    public boolean canUndo() {
        return !items.isEmpty();
    }

    public void undo() {
        if (canUndo()) {
            final UndoItem item = items.pop();
            item.undo();
        }
        menuItem.setEnabled(canUndo());
    }

    public void clear() {
        items.clear();
    }
}
