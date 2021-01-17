package gp;

import gp.ai.*;

import java.awt.*;
import java.io.Serializable;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class ProfileMessage implements Serializable {
    private final UUID id;
    private final String name;
    private int[] colors;
    private boolean local;
    private boolean ai;
    private transient AI.Type aiType;
    transient Profile originalProfile;

    static ProfileMessage pending = new ProfileMessage("...", false);

    private static Random random = new Random();
    private static String[] randomNames = new String[] { "Mika", "Keke", "Kimi", "Heikki", "Leo", "Valtteri", "Nico", "Michael", "Lewis", "Sebastian", "Max", "Fernando" };
    private static String[] backupRandomNames = new String[] { "Lando", "Carlos", "Alexander", "George", "Daniel", "Esteban", "Pierre", "Daniil", "Romain", "Kevin", "Nicholas", "Robert", "Charles", "Antonio" };

    static ProfileMessage createRandomAIProfile(Set<String> usedNames) {
        List<String> validNames = Arrays.stream(randomNames).filter(n -> !usedNames.contains(n)).collect(Collectors.toList());
        if (validNames.isEmpty()) {
            validNames = Arrays.stream(backupRandomNames).filter(n -> !usedNames.contains(n)).collect(Collectors.toList());
        }
        final String name;
        if (validNames.isEmpty()) {
            int i = 1;
            while (usedNames.contains("Player " + i)) {
                ++i;
            }
            name = "Player " + i;
        } else {
            name = validNames.get(random.nextInt(validNames.size()));
        }
        final ProfileMessage profile = new ProfileMessage(name, true);
        profile.colors[0] = random.nextInt(0xFFFFFF + 1);
        profile.colors[1] = random.nextInt(0xFFFFFF + 1);
        profile.colors[2] = profile.colors[0];
        profile.colors[3] = 0x000000;
        profile.aiType = AI.Type.AMATEUR;
        return profile;
    }

    static ProfileMessage readProfile(String[] line) {
        if (line.length == 6) {
            // Old format
            final int[] colors = new int[4];
            colors[0] = Integer.parseInt(line[2]);
            colors[1] = Integer.parseInt(line[3]);
            colors[2] = colors[0];
            colors[3] = 0x000000;
            return new ProfileMessage(UUID.fromString(line[0]), line[1], colors, Boolean.parseBoolean(line[4]), AI.Type.valueOf(line[5]));
        } else if (line.length == 5) {
            // New format
            final int[] colors = Arrays.stream(line[2].split(";")).mapToInt(Integer::parseInt).toArray();
            return new ProfileMessage(UUID.fromString(line[0]), line[1], colors, Boolean.parseBoolean(line[3]), AI.Type.valueOf(line[4]));
        } else {
            return null;
        }
    }

    String toLine() {
        final String colorString = Arrays.stream(colors).mapToObj(Integer::toString).collect(Collectors.joining(";"));
        return id + "," + name + "," + colorString + "," + ai + "," + aiType;
    }

    private ProfileMessage(UUID id, String name, int[] colors, boolean ai, AI.Type aiType) {
        this.id = id;
        this.name = name;
        this.colors = colors;
        local = true;
        this.ai = ai;
        this.aiType = aiType;
    }

    private ProfileMessage(String name, boolean ai) {
        id = UUID.randomUUID();
        this.name = name;
        colors = new int[4];
        local = true;
        this.ai = ai;
    }

    ProfileMessage(Profile profile) {
        id = profile.getId();
        name = profile.getName();
        colors = new int[4];
        for (int i = 0; i < 4; ++i) {
            colors[i] = profile.getColor(i);
        }
        originalProfile = profile;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    int getColor(int index) {
        return colors[index];
    }

    int[] getColors() {
        return colors;
    }

    void setLocal() {
        this.local = true;
    }

    boolean isLocal() {
        return local;
    }

    public void setAi(boolean ai) {
        this.ai = ai;
    }

    public boolean isAi() {
        return ai;
    }

    void setAIType(AI.Type type) {
        aiType = type;
    }

    AI createAI(TrackData data) {
        if (aiType == null) return null;
        switch (aiType) {
            case BEGINNER: return new BeginnerAI(data);
            case AMATEUR: return new AmateurAI(data);
            case PRO: return new ProAI(data);
        }
        return null;
    }
}
