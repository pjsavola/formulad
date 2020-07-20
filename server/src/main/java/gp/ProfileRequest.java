package gp;

import java.io.Serializable;

public class ProfileRequest implements Serializable {
    private final String trackId;

    public ProfileRequest(String trackId) {
        this.trackId = trackId;
    }

    public String getTrackId() {
        return trackId;
    }
}
