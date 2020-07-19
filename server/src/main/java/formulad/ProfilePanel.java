package formulad;


import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.List;

public class ProfilePanel extends JPanel {
    private class CarPreview extends JPanel {
        @Override
        public void paintComponent(Graphics g) {
            final int x = getWidth() / 2;
            final int y = getHeight() / 2;
            Player.draw((Graphics2D) g, x, y, 0, new Color(activeProfile.getColor1()), new Color(activeProfile.getColor2()), 2.5);
        }
    }

    private class ColorChangeListener implements MouseListener {
        private final JPanel panel;
        private final boolean mainColor;
        private ColorChangeListener(JPanel panel, boolean mainColor) {
            this.panel = panel;
            this.mainColor = mainColor;
        }
        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON1) return;
            final int oldColor = mainColor ? activeProfile.getColor1() : activeProfile.getColor2();
            final String result = (String) JOptionPane.showInputDialog(ProfilePanel.this, "Set color RGB value", "Set color", JOptionPane.PLAIN_MESSAGE, null, null, Integer.toHexString(oldColor));
            if (result == null) return;
            try {
                final int color = Color.decode(result).getRGB() & 0x00FFFFFF;
                if (mainColor) activeProfile.setColor1(color);
                else activeProfile.setColor2(color);
                panel.repaint();
            } catch (NumberFormatException ex) {
                JOptionPane.showConfirmDialog(ProfilePanel.this, "Please provide valid RGB value between #000000 and #FFFFFF", "Error", JOptionPane.DEFAULT_OPTION);
            }
        }
        @Override
        public void mousePressed(MouseEvent e) {
        }
        @Override
        public void mouseReleased(MouseEvent e) {
        }
        @Override
        public void mouseEntered(MouseEvent e) {
        }
        @Override
        public void mouseExited(MouseEvent e) {
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

        add(profileTitle);
        add(profileName);
        add(carPreview);

        final JSlider sliderColor1 = new JSlider(JSlider.HORIZONTAL, 0x000000, 0xFFFFFF, activeProfile.getColor1());
        final JSlider sliderColor2 = new JSlider(JSlider.HORIZONTAL, 0x000000, 0xFFFFFF, activeProfile.getColor2());

        sliderColor1.addChangeListener(e -> {
            final JSlider slider = (JSlider) e.getSource();
            activeProfile.setColor1(slider.getValue());
            carPreview.repaint();
        });
        sliderColor2.addChangeListener(e -> {
            final JSlider slider = (JSlider) e.getSource();
            activeProfile.setColor2(slider.getValue());
            carPreview.repaint();
        });
        sliderColor1.addMouseListener(new ColorChangeListener(carPreview, true));
        sliderColor2.addMouseListener(new ColorChangeListener(carPreview, false));

        add(sliderColor1);
        add(sliderColor2);

        profileTitle.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                JFrame frame = null;
                Container parent = ProfilePanel.this;
                while (parent != null) {
                    parent = parent.getParent();
                    if (parent instanceof JFrame) {
                        frame = (JFrame) parent;
                        break;
                    }
                }
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
                            final Profile newProfile = new Profile(result);
                            newProfile.setActive(true);
                            profiles.add(newProfile);
                            activeProfile.setActive(false);
                            activeProfile = newProfile;
                            profileName.setText(result);
                            dialog.setVisible(false);
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
            public void mousePressed(MouseEvent e) {
            }
            @Override
            public void mouseReleased(MouseEvent e) {
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

        profileName.addMouseListener(new MouseListener() {
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
            public void mousePressed(MouseEvent e) {
            }
            @Override
            public void mouseReleased(MouseEvent e) {
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
    }

    public Profile getActiveProfile() {
        return activeProfile;
    }
}
