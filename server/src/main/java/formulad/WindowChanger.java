package formulad;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

public class WindowChanger extends WindowAdapter {
    private final JFrame frame;
    private final JPanel panel;
    private final JPanel oldPanel;
    private final Lobby lobby;
    private final Game game;
    private final String name;
    private final boolean confirm;
    private final WindowListener old;
    WindowChanger(JFrame frame, JPanel panel, JPanel oldPanel, Lobby lobby, Game game, String name, boolean confirm) {
        this.frame = frame;
        this.panel = panel;
        this.oldPanel = oldPanel;
        this.lobby = lobby;
        this.game = game;
        this.name = name;
        this.confirm = confirm;
        this.old = removeProfileSaver(frame);
    }

    private static WindowListener removeProfileSaver(JFrame frame) {
        final WindowListener[] listeners = frame.getWindowListeners();
        for (WindowListener l : listeners) {
            if (l instanceof FormulaD.ProfileSaver) {
                frame.removeWindowListener(l);
                return l;
            } else if (l instanceof WindowChanger) {
                frame.removeWindowListener(l);
                return ((WindowChanger) l).old;
            }
        }
        return null;
    }

    @Override
    public void windowClosing(WindowEvent e) {
        super.windowClosing(e);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        final int confirmed = confirm ? JOptionPane.showConfirmDialog(panel,
                "Are you sure you want to terminate the " + name + "?", "Confirm",
                JOptionPane.YES_NO_OPTION) : JOptionPane.YES_OPTION;
        if (confirmed == JOptionPane.YES_OPTION) {
            if (lobby != null) {
                lobby.close();
            }
            if (game != null) {
                game.exit();
            } else {
                frame.setContentPane(oldPanel);
                frame.pack();
            }
            frame.removeWindowListener(this);
            if (old != null) {
                frame.addWindowListener(old);
            }
        }
    }
}
