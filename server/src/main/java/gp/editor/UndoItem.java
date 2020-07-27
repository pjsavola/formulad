package gp.editor;

public interface UndoItem {
    boolean execute(UndoStack stack);
    void undo();
}
