package gp.editor;

import gp.ai.Node;

public class CreateEdgeItem implements UndoItem {
    private final Node from;
    private final Node to;
    private boolean reverse;

    public CreateEdgeItem(Node from, Node to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public boolean execute(UndoStack stack) {
        if (from.hasChild(to)) {
            return false;
        }
        reverse = to.hasChild(from);
        from.addChild(to);
        return true;
    }

    @Override
    public void undo() {
        if (reverse) {
            to.addChild(from);
        } else {
            from.removeChild(to);
        }
    }
}
