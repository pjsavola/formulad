package formulad;

import formulad.model.PlayerStats;

import java.io.Serializable;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class ProfileMessage implements Serializable {
    private final UUID id;
    private final String name;
    private int color1;
    private int color2;
    private boolean local;
    private boolean ai;
    public transient Profile originalProfile;

    public static ProfileMessage pending = new ProfileMessage("...", false);

    private static Random random = new Random();
    private static String[] randomNames = new String[] { "Mika", "Keke", "Kimi", "Heikki", "Leo", "Valtteri", "Nico", "Michael", "Lewis", "Sebastian", "Max", "Fernando" };

    public static ProfileMessage createRandomAIProfile(Set<String> usedNames) {
        final List<String> validNames = Arrays.stream(randomNames).filter(n -> !usedNames.contains(n)).collect(Collectors.toList());
        final ProfileMessage profile = new ProfileMessage(validNames.get(random.nextInt(validNames.size())), true);
        profile.color1 = random.nextInt(0xFFFFFF + 1);
        profile.color2 = random.nextInt(0xFFFFFF + 1);
        return profile;
    }
    public static ProfileMessage aiProfile = new ProfileMessage("AI", true);

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
}
