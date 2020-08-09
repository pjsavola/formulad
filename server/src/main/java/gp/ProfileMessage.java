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
    public transient Profile originalProfile;

    public static ProfileMessage pending = new ProfileMessage("...", false);

    private static Random random = new Random();
    private static String[] drivers = new String[] { "Hamilton", "Bottas", "Verstappen", "Albon", "Vettel", "Leclerc", "Perez", "Stroll", "Norris", "Sainz", "Ricciardo", "Ocon", "Gasly", "Kvyat", "Räikkönen", "Giovinazzi", "Magnussen", "Grosjean", "Russell", "XX" };
    private static String[] randomNames = new String[] { "Mika", "Keke", "Kimi", "Heikki", "Leo", "Valtteri", "Nico", "Michael", "Lewis", "Sebastian", "Max", "Fernando" };
    private static final Color[] defaultColors = {
            Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, Color.PINK,
            Color.CYAN, Color.ORANGE, Color.WHITE, Color.MAGENTA, Color.GRAY };
    private static final Color[] defaultBorderColors = {
            new Color(0x770000), new Color(0x000077), new Color(0x007700), new Color(0x777700), new Color(0x773333),
            new Color(0x007777), new Color(0x993300), Color.GRAY, new Color(0x770077), Color.BLACK };

    public static ProfileMessage createRandomAIProfile(Set<String> usedNames) {
        final List<String> validNames = Arrays.stream(randomNames).filter(n -> !usedNames.contains(n)).collect(Collectors.toList());
        final ProfileMessage profile = new ProfileMessage(validNames.get(random.nextInt(validNames.size())), true);
        profile.color1 = random.nextInt(0xFFFFFF + 1);
        profile.color2 = random.nextInt(0xFFFFFF + 1);
        profile.aiType = AI.Type.AMATEUR;
        return profile;
    }

    public static ProfileMessage readProfile(String[] line) {
        final ProfileMessage profile = new ProfileMessage(
                UUID.fromString(line[0]),
                line[1],
                Integer.parseInt(line[2]),
                Integer.parseInt(line[3]),
                Boolean.parseBoolean(line[4]),
                AI.Type.valueOf(line[5])
        );
        return profile;
    }

    public String toLine() {
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

    public ProfileMessage(Profile profile) {
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

    public int getColor1() {
        return color1;
    }

    public int getColor2() {
        return color2;
    }

    public void setLocal(boolean local) {
        this.local = local;
    }

    public boolean isLocal() {
        return local;
    }

    public void setAi(boolean ai) {
        this.ai = ai;
    }

    public boolean isAi() {
        return ai;
    }

    public void setAIType(AI.Type type) {
        aiType = type;
    }

    public AI createAI(TrackData data) {
        if (aiType == null) return null;
        switch (aiType) {
            case BEGINNER: return new BeginnerAI(data);
            case AMATEUR: return new AmateurAI(data);
            case PRO: return new ProAI(data);
        }
        return null;
    }
}
