package gp.editor;

import gp.ai.Node;

import java.util.ArrayList;
import java.util.List;

public class CreateEdgesItem implements UndoItem {
    private final List<CreateEdgeItem> items = new ArrayList<>();

    public void addEdge(CreateEdgeItem item) {
        items.add(item);
    }

    @Override
    public boolean execute(UndoStack stack) {
        for (CreateEdgeItem item : items) {
            item.execute(stack);
        }
        return true;
    }

    @Override
    public void undo() {
        for (CreateEdgeItem item : items) {
            item.undo();
        }
    }
}
