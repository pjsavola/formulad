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
    private int color1;
    private int color2;
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
        profile.color1 = random.nextInt(0xFFFFFF + 1);
        profile.color2 = random.nextInt(0xFFFFFF + 1);
        profile.aiType = AI.Type.AMATEUR;
        return profile;
    }

    static ProfileMessage readProfile(String[] line) {
        return new ProfileMessage(
                UUID.fromString(line[0]),
                line[1],
                Integer.parseInt(line[2]),
                Integer.parseInt(line[3]),
                Boolean.parseBoolean(line[4]),
                AI.Type.valueOf(line[5])
        );
    }

    String toLine() {
        return id + "," + name + "," + color1 + "," + color2 + "," + ai + "," + aiType;
    }

    private ProfileMessage(UUID id, String name, int color1, int color2, boolean ai, AI.Type aiType) {
        this.id = id;
        this.name = name;
        this.color1 = color1;
        this.color2 = color2;
        local = true;
        this.ai = ai;
        this.aiType = aiType;
    }

    private ProfileMessage(String name, boolean ai) {
        id = UUID.randomUUID();
        this.name = name;
        color1 = 0;
        color2 = 0;
        local = true;
        this.ai = ai;
    }

    ProfileMessage(Profile profile) {
        id = profile.getId();
        name = profile.getName();
        color1 = profile.getColor1();
        color2 = profile.getColor2();
        originalProfile = profile;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    int getColor1() {
        return color1;
    }

    int getColor2() {
        return color2;
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
