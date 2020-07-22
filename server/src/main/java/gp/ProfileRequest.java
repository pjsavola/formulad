package gp;

import java.io.Serializable;

public class ProfileRequest implements Serializable {
    private final String trackId;
    private final boolean external;

    public ProfileRequest(String trackId, boolean external) {
        this.trackId = trackId;
        this.external = external;
    }

    public String getTrackId() {
        return trackId;
    }

    public boolean isTrackExternal() {
        return external;
    }
}
