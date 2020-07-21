package gp;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;

public class MainMenuBar extends MenuBar {
    public MainMenuBar(JFrame frame, JPanel panel) {
        final Menu helpMenu = new Menu("Info");
        add(helpMenu);
        final MenuItem aboutItem = new MenuItem("About");
        final MenuItem rulesItem = new MenuItem("Rules");
        helpMenu.add(aboutItem);
        helpMenu.add(rulesItem);
        aboutItem.addActionListener(e -> {
            JOptionPane.showConfirmDialog(panel, "GP Online version 1.0", "About", JOptionPane.DEFAULT_OPTION);
        });
        rulesItem.addActionListener(e -> {
            try (InputStream is = Main.class.getResourceAsStream("/rules.txt"); InputStreamReader in = new InputStreamReader(is)) {
                final JTextArea textArea = new JTextArea();
                final JScrollPane scrollPane = new JScrollPane(textArea);
                scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
                scrollPane.setPreferredSize(new Dimension(250, 250));
                textArea.setColumns(20);
                textArea.setLineWrap(true);
                textArea.setRows(10);
                textArea.setWrapStyleWord(true);
                textArea.setEditable(false);
                textArea.read(in, null);
                System.out.println(textArea.getText());
                final JPanel textPanel = new JPanel(new BorderLayout());
                textPanel.add(scrollPane);
                JDialog dialog = new JDialog(frame);
                dialog.setTitle("Game Rules");
                dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                dialog.setContentPane(textPanel);
                dialog.pack();
                dialog.setModal(true);
                dialog.setLocationRelativeTo(frame);
                dialog.setVisible(true);
            } catch (IOException | NullPointerException ex) {
                Main.log.log(Level.WARNING, "rules.txt not found", ex);
            }
        });
    }
}
