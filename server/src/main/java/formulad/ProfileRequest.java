package formulad;

import java.io.Serializable;
import java.util.UUID;

public class ProfileRequest implements Serializable {
    private final String trackId;

    public ProfileRequest(String trackId) {
        this.trackId = trackId;
    }

    public String getTrackId() {
        return trackId;
    }
}
