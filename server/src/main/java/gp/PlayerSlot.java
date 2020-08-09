package gp;

import gp.ai.AI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlayerSlot extends JButton {
    private static AI.Type selectedType;
    private class CarIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            if (profile != null && profile != ProfileMessage.pending) {
                Player.draw((Graphics2D) g, x + 7, y + 4, 0, new Color(profile.getColor1()), new Color(profile.getColor2()), 1.0);
            }
        }
        @Override
        public int getIconWidth() {
            return 16;
        }
        @Override
        public int getIconHeight() {
            return 9;
        }
    }

    ProfileMessage profile;
    private final int gridPosition;

    private static Set<String> getUsedAINames(PlayerSlot[] slots) {
        final Set<String> usedNames = new HashSet<>();
        for (PlayerSlot slot : slots) {
            if (slot.profile != null) {
                if (slot.profile.isAi()) {
                    usedNames.add(slot.profile.getName());
                }
            }
        }
        return usedNames;
    }

    PlayerSlot(ProfileMessage profile, int gridPosition) {
        this.profile = profile;
        this.gridPosition = gridPosition;
    }

    PlayerSlot(JFrame frame, List<ProfileMessage> localProfiles, Lobby lobby, PlayerSlot[] slots, int gridPosition) {
        this.gridPosition = gridPosition;
        setIcon(new CarIcon());
        addActionListener(e -> {
            if (profile == null) {
                /*
                if (localProfiles.isEmpty()) {
                    setProfile(ProfileMessage.createRandomAIProfile(getUsedAINames(slots)));
                    return;
                }*/
                profile = ProfileMessage.pending;
                final JDialog dialog = new JDialog(frame);

                final DefaultListModel<String> model = new DefaultListModel<>();
                localProfiles.forEach(profile -> model.addElement(profile.getName()));

                final JList<String> list = new JList<>(model);
                final JButton selectButton = new JButton("Select");
                final JButton addAiButton = new JButton("Add AI");
                final JRadioButton difficultyButton1 = new JRadioButton("Beginner");
                final JRadioButton difficultyButton2 = new JRadioButton("Amateur");
                final JRadioButton difficultyButton3 = new JRadioButton("Pro");
                final ButtonGroup group = new ButtonGroup();
                group.add(difficultyButton1);
                group.add(difficultyButton2);
                group.add(difficultyButton3);


                selectButton.addActionListener(e12 -> {
                    final int index = list.getSelectedIndex();
                    if (index == -1) {
                        return;
                    }
                    final ProfileMessage selectedProfile = localProfiles.remove(index);
                    final AI.Type type = difficultyButton1.isSelected() ? AI.Type.BEGINNER : (difficultyButton2.isSelected() ? AI.Type.AMATEUR : AI.Type.PRO);
                    selectedType = type;
                    selectedProfile.setAIType(type);
                    selectedProfile.setLocal(true);
                    setProfile(selectedProfile);
                    dialog.setVisible(false);
                });
                addAiButton.addActionListener(e13 -> {
                    final int index = list.getSelectedIndex();
                    if (index == -1) {
                        return;
                    }
                    final ProfileMessage aiProfile = ProfileMessage.createRandomAIProfile(getUsedAINames(slots));
                    final AI.Type type = difficultyButton1.isSelected() ? AI.Type.BEGINNER : (difficultyButton2.isSelected() ? AI.Type.AMATEUR : AI.Type.PRO);
                    selectedType = type;
                    aiProfile.setAIType(type);
                    setProfile(aiProfile);
                    dialog.setVisible(false);
                });

                list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                list.setSelectedIndex(0);
                list.addListSelectionListener(e14 -> {
                    if (!e14.getValueIsAdjusting()) {
                        if (list.getSelectedIndex() == -1) {
                            selectButton.setEnabled(false);
                        } else {
                            selectButton.setEnabled(true);
                        }
                    }
                });

                list.setVisibleRowCount(5);
                final JScrollPane listScrollPane = new JScrollPane(list);

                final JPanel buttonPane = new JPanel();
                buttonPane.setLayout(new BoxLayout(buttonPane, BoxLayout.Y_AXIS));
                buttonPane.add(selectButton);
                buttonPane.add(addAiButton);
                buttonPane.add(difficultyButton1);
                buttonPane.add(difficultyButton2);
                buttonPane.add(difficultyButton3);
                buttonPane.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));

                final JPanel contents = new JPanel(new GridLayout(0, 2));
                contents.add(listScrollPane, BorderLayout.CENTER);
                contents.add(buttonPane, BorderLayout.PAGE_END);
                dialog.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        super.windowClosed(e);
                        profile = null;
                    }
                });
                if (selectedType == AI.Type.BEGINNER) {
                    difficultyButton1.setSelected(true);
                } else if (selectedType == AI.Type.AMATEUR) {
                    difficultyButton2.setSelected(true);
                } else {
                    difficultyButton3.setSelected(true);
                }
                dialog.setTitle("Select player");
                dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                dialog.setContentPane(contents);
                dialog.pack();
                dialog.setModal(true);
                dialog.setLocationRelativeTo(frame);
                dialog.setVisible(true);
            } else {
                final boolean incomingPlayer = profile == ProfileMessage.pending;
                if (profile.isLocal() && !incomingPlayer) {
                    if (!profile.isAi()) {
                        localProfiles.add(profile);
                    }
                    profile = null;
                    setEnabled(true);
                } else if (lobby != null) {
                    int result = JOptionPane.showConfirmDialog(getParent(), "Are you sure you want to kick player " + profile.getName(), "Confirm", JOptionPane.YES_NO_OPTION);
                    if (result == JOptionPane.YES_OPTION) {
                        lobby.dropClient(profile.getId());
                        profile = null;
                        setEnabled(true);
                    }
                }
            }
        });
    }

    void setProfile(Profile profile) {
        this.profile = new ProfileMessage(profile);
        this.profile.setLocal(true);
    }

    void setProfile(ProfileMessage message) {
        this.profile = message;
    }

    boolean isFree() {
        return profile == null;
    }

    @Override
    public String getText() {
        return gridPosition + ". " + (profile == null ? "Free" : profile.getName());
    }

    public ProfileMessage getProfile() {
        return profile;
    }
}
