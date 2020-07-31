package gp;

import gp.ai.AI;
import gp.ai.GreatAI;
import gp.ai.MagnificentAI;
import gp.ai.TrackData;

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
        switch (profile.getName()) {
            case "Kimi":
            case "Michael":
            case "Lewis":
            case "Sebastian":
            case "Fernando":
                profile.aiType = AI.Type.MAGNIFICENT;
                break;
            default:
                profile.aiType = AI.Type.GREAT;
                break;
        }
        return profile;
    }

    public static ProfileMessage readProfile(String[] line) {
        return new ProfileMessage(
                UUID.fromString(line[0]),
                line[1],
                Integer.parseInt(line[2]),
                Integer.parseInt(line[3]),
                AI.fromString(line[4])
        );
    }

    public String toLine() {
        return id + "," + name + "," + color1 + "," + color2 + "," + AI.toString(aiType);
    }

    private ProfileMessage(UUID id, String name, int color1, int color2, AI.Type aiType) {
        this.id = id;
        this.name = name;
        this.color1 = color1;
        this.color2 = color2;
        local = true;
        this.ai = aiType != AI.Type.MANUAL;
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

    public AI createAI(TrackData data) {
        if (aiType == null) return null;
        switch (aiType) {
            case GREAT: return new GreatAI(data);
            case MAGNIFICENT: return new MagnificentAI(data);
        }
        return null;
    }
}
