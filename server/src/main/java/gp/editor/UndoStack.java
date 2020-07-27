package gp.editor;

import gp.ai.Node;

import java.awt.*;
import java.util.*;
import java.util.List;

public class UndoStack {
    private final Deque<UndoItem> items = new ArrayDeque<>();
    private MenuItem menuItem;
    final List<Node> nodes;
    final Map<Node, Point> coordinates;
    final Map<Node, Double> attributes;
    final Map<Node, Double> gridAngles;

    public UndoStack(List<Node> nodes, Map<Node, Point> coordinates, Map<Node, Double> attributes, Map<Node, Double> gridAngles) {
        this.nodes = nodes;
        this.coordinates = coordinates;
        this.attributes = attributes;
        this.gridAngles = gridAngles;
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
}
