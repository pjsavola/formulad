package gp;


import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Random;

class ProfilePanel extends JPanel {
    private class CarPreview extends JPanel {
        final int width = 100;
        final int height = 50;
        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            final int x = width / 2;
            final int y = height / 2;
            Player.draw((Graphics2D) g, x, y, 0, activeProfile.getColors(), 2.5);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(width, height);
        }
    }

    private class ColorChangeListener extends MouseAdapter {
        private class TextFieldListener implements DocumentListener {
            private final JTextField field;
            private final int index;
            TextFieldListener(JTextField field, int index) {
                this.field = field;
                this.index = index;
            }
            @Override
            public void insertUpdate(DocumentEvent e) {
                refresh();
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
                refresh();
            }
            @Override
            public void changedUpdate(DocumentEvent e) {
                refresh();
            }
            void refresh() {
                try {
                    final int color = Color.decode(toCode(field.getText())).getRGB() & 0x00FFFFFF;
                    activeProfile.setColor(index, color);
                    carPreview.repaint();
                } catch (Exception ex) {
                    // No repaint
                }
            }
        }
        private class ButtonListener implements ActionListener {
            private final JTextField field;
            private final int index;
            ButtonListener(JTextField field, int index) {
                this.field = field;
                this.index = index;
            }
            @Override
            public void actionPerformed(ActionEvent e) {
                final int color = Main.random.nextInt(0xFFFFFF + 1);
                activeProfile.setColor(index, color);
                carPreview.repaint();
                field.setText(Integer.toHexString(color));
            }
        }
        private final JPanel panel;
        private final CarPreview carPreview = new CarPreview();
        private ColorChangeListener(JPanel panel) {
            this.panel = panel;
        }
        @Override
        public void mouseClicked(MouseEvent e) {
            final int orig0 = activeProfile.getColor(0);
            final int orig1 = activeProfile.getColor(1);
            final int orig2 = activeProfile.getColor(2);
            final int orig3 = activeProfile.getColor(3);
            JTextField color1 = new JTextField(Integer.toHexString(orig0), 10);
            JTextField color2 = new JTextField(Integer.toHexString(orig1), 10);
            JTextField color3 = new JTextField(Integer.toHexString(orig2), 10);
            JTextField color4 = new JTextField(Integer.toHexString(orig3), 10);
            color1.getDocument().addDocumentListener(new TextFieldListener(color1, 0));
            color2.getDocument().addDocumentListener(new TextFieldListener(color2, 1));
            color3.getDocument().addDocumentListener(new TextFieldListener(color3, 2));
            color4.getDocument().addDocumentListener(new TextFieldListener(color4, 3));

            final JButton button0 = new JButton("Randomize");
            final JButton button1 = new JButton("Randomize");
            final JButton button2 = new JButton("Randomize");
            final JButton button3 = new JButton("Randomize");
            button0.addActionListener(new ButtonListener(color1, 0));
            button1.addActionListener(new ButtonListener(color2, 1));
            button2.addActionListener(new ButtonListener(color3, 2));
            button3.addActionListener(new ButtonListener(color4, 3));

            JPanel myPanel = new JPanel(new GridLayout(0, 3));
            myPanel.add(new JLabel("Main color:"));
            myPanel.add(color1);
            myPanel.add(button0);
            myPanel.add(new JLabel("Wing Color:"));
            myPanel.add(color2);
            myPanel.add(button1);
            myPanel.add(new JLabel("Side Color:"));
            myPanel.add(color3);
            myPanel.add(button2);
            myPanel.add(new JLabel("Helmet Color:"));
            myPanel.add(color4);
            myPanel.add(button3);
            myPanel.add(new JPanel());
            myPanel.add(carPreview);

            int result = JOptionPane.showConfirmDialog(null, myPanel, "Adjust color RGB values", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result == JOptionPane.OK_OPTION) {
                try {
                    activeProfile.setColor(0, Color.decode(toCode(color1.getText())).getRGB() & 0x00FFFFFF);
                    activeProfile.setColor(1, Color.decode(toCode(color2.getText())).getRGB() & 0x00FFFFFF);
                    activeProfile.setColor(2, Color.decode(toCode(color3.getText())).getRGB() & 0x00FFFFFF);
                    activeProfile.setColor(3, Color.decode(toCode(color4.getText())).getRGB() & 0x00FFFFFF);
                    panel.repaint();
                    return;
                } catch (Exception ex) {
                    JOptionPane.showConfirmDialog(ProfilePanel.this, "Please provide valid RGB value between 000000 and FFFFFF", "Error", JOptionPane.DEFAULT_OPTION);
                }
            }
            activeProfile.setColor(0, orig0);
            activeProfile.setColor(1, orig1);
            activeProfile.setColor(2, orig2);
            activeProfile.setColor(3, orig3);
        }

        private String toCode(String str) {
            if (str.startsWith("0x")) {
                return "#" + str.substring(2);
            } else if (str.startsWith("#")) {
                return str;
            } else {
                return "#" + str;
            }
        }
    }

    private Profile activeProfile;

    ProfilePanel(List<Profile> profiles) {
        activeProfile = profiles.stream().filter(Profile::isActive).findFirst().orElse(profiles.get(0));
        setBorder(new EmptyBorder(20, 50, 10, 10));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        final JLabel profileTitle = new JLabel("Active profile");
        profileTitle.setFont(new Font("Arial", Font.BOLD, 16));
        final JLabel profileName = new JLabel(activeProfile.getName());
        profileName.setFont(new Font("Arial", Font.PLAIN, 16));
        final CarPreview carPreview = new CarPreview();
        final JLabel profileStats = new JLabel("- Stats");
        profileStats.setFont(new Font("Arial", Font.PLAIN, 14));
        add(profileTitle);
        add(profileName);
        add(profileStats);
        add(carPreview);

        carPreview.addMouseListener(new ColorChangeListener(carPreview));

        profileTitle.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                final JFrame frame = (JFrame) getTopLevelAncestor();
                final JDialog dialog = new JDialog(frame);

                final DefaultListModel<String> model = new DefaultListModel<>();
                profiles.forEach(profile -> model.addElement(profile.getName()));

                final JList<String> list = new JList<>(model);

                final JButton newButton = new JButton("New");
                final JButton selectButton = new JButton("Select");
                final JButton deleteButton = new JButton("Delete");
                newButton.addActionListener(e1 -> {
                    String result = (String) JOptionPane.showInputDialog(dialog, "New profile name", "New profile", JOptionPane.PLAIN_MESSAGE, null, null, null);
                    if (result != null && !result.isEmpty()) {
                        if (profiles.stream().noneMatch(p -> p.getName().equals(result))) {
                            final Profile newProfile = new Profile(activeProfile.getManager(), result);
                            newProfile.setActive(true);
                            newProfile.setColor(0, Main.random.nextInt(0xFFFFFF));
                            newProfile.setColor(1, Main.random.nextInt(0xFFFFFF));
                            newProfile.setColor(2, newProfile.getColor(0));
                            profiles.add(newProfile);
                            activeProfile.setActive(false);
                            activeProfile = newProfile;
                            profileName.setText(result);
                            dialog.setVisible(false);
                            dialog.dispose();
                            carPreview.repaint();
                        } else {
                            JOptionPane.showConfirmDialog(dialog, "Profile with name " + result + " already exists", "Error", JOptionPane.DEFAULT_OPTION);
                        }
                    }
                });
                selectButton.addActionListener(e12 -> {
                    final int index = list.getSelectedIndex();
                    if (index == -1) {
                        return;
                    }
                    final Profile selectedProfile = profiles.get(index);
                    if (selectedProfile != activeProfile) {
                        activeProfile.setActive(false);
                        selectedProfile.setActive(true);
                        activeProfile = selectedProfile;
                        profileName.setText(selectedProfile.getName());
                        carPreview.repaint();
                    }
                    dialog.setVisible(false);
                    dialog.dispose();
                });
                deleteButton.addActionListener(e13 -> {
                    final int index = list.getSelectedIndex();
                    if (index == -1) {
                        return;
                    }
                    final String name = profiles.get(index).getName();
                    int result = JOptionPane.showConfirmDialog(dialog, "Are you sure you want to delete profile " + name, "Confirm", JOptionPane.YES_NO_OPTION);
                    if (result == JOptionPane.YES_OPTION) {
                        final Profile removedProfile = profiles.remove(index);
                        model.remove(index);
                        list.setSelectedIndex(-1);
                        removedProfile.delete();
                    }
                });
                deleteButton.setEnabled(false);

                list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                list.setSelectedIndex(profiles.indexOf(activeProfile));
                list.addListSelectionListener(e14 -> {
                    if (!e14.getValueIsAdjusting()) {
                        if (list.getSelectedIndex() == -1) {
                            deleteButton.setEnabled(false);
                            selectButton.setEnabled(false);
                        } else {
                            deleteButton.setEnabled(!profiles.get(list.getSelectedIndex()).isActive());
                            selectButton.setEnabled(true);
                        }
                    }
                });
                list.setVisibleRowCount(5);
                final JScrollPane listScrollPane = new JScrollPane(list);

                final JPanel buttonPane = new JPanel();
                buttonPane.setLayout(new BoxLayout(buttonPane, BoxLayout.Y_AXIS));
                buttonPane.add(newButton);
                buttonPane.add(selectButton);
                buttonPane.add(deleteButton);
                buttonPane.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));

                final JPanel contents = new JPanel(new GridLayout(0, 2));
                contents.add(listScrollPane, BorderLayout.CENTER);
                contents.add(buttonPane, BorderLayout.PAGE_END);
                dialog.setTitle("Manage profiles");
                dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                dialog.setContentPane(contents);
                dialog.pack();
                dialog.setModal(true);
                dialog.setLocationRelativeTo(frame);
                dialog.setVisible(true);
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                profileTitle.setForeground(Color.RED);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                profileTitle.setForeground(Color.BLACK);
            }
        });

        profileName.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String result = (String) JOptionPane.showInputDialog(ProfilePanel.this, "Change name", "Change name", JOptionPane.PLAIN_MESSAGE,  null, null, activeProfile.getName());
                if (result != null && !result.isEmpty() && !result.equals(activeProfile.getName())) {
                    if (profiles.stream().noneMatch(p -> p.getName().equals(result))) {
                        activeProfile.setName(result);
                        profileName.setText(result);
                    } else {
                        JOptionPane.showConfirmDialog(ProfilePanel.this, "Profile with name " + result + " already exists", "Error", JOptionPane.DEFAULT_OPTION);
                    }
                }
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                profileName.setForeground(Color.RED);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                profileName.setForeground(Color.BLACK);
            }
        });
        profileStats.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                final JFrame frame = (JFrame) getTopLevelAncestor();
                new ProfileStats(frame, activeProfile);
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                profileStats.setForeground(Color.RED);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                profileStats.setForeground(Color.BLACK);
            }
        });
    }

    Profile getActiveProfile() {
        return activeProfile;
    }
}
