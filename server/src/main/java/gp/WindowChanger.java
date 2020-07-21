package gp;

import javax.swing.*;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

public class WindowChanger extends WindowAdapter {
    private final JFrame frame;
    private final JPanel mainMenu;
    private JPanel panel;
    private Lobby lobby;
    private Game game;
    private String name;
    private boolean confirm;
    WindowChanger(JFrame frame, JPanel mainMenu) {
        this.frame = frame;
        this.mainMenu = mainMenu;
    }

    public void reset() {
        this.panel = null;
        this.lobby = null;
        this.game = null;
        this.name = null;
        this.confirm = false;
    }

    public void contentChanged(JPanel panel, Lobby lobby, Game game, String name, boolean confirm) {
        this.panel = panel;
        this.lobby = lobby;
        this.game = game;
        this.name = name;
        this.confirm = confirm;
    }

    @Override
    public void windowClosing(WindowEvent e) {
        super.windowClosing(e);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        final int confirmed = confirm ? JOptionPane.showConfirmDialog(panel,
                "Are you sure you want to terminate the " + name + "?", "Confirm",
                JOptionPane.YES_NO_OPTION) : JOptionPane.YES_OPTION;
        if (confirmed == JOptionPane.YES_OPTION) {
            for (KeyListener listener : frame.getKeyListeners()) {
                frame.removeKeyListener(listener);
            }
            if (lobby != null) {
                lobby.close();
            }
            if (game != null) {
                game.exit();
            } else if (panel != null) {
                frame.setMenuBar(new MainMenuBar(frame, mainMenu));
                frame.setContentPane(mainMenu);
                frame.pack();
                reset();
            } else {
                frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            }
        }
    }
}
