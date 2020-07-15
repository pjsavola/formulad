package formulad;

import formulad.model.PlayerStats;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class ProfileMessage implements Serializable {
    private final UUID id;
    private final String name;
    private final int color1;
    private final int color2;
    private boolean local;
    private boolean ai;

    public static ProfileMessage pending = new ProfileMessage("...", false);
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
