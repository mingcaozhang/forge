package forge.gamemodes.net.event;

import forge.util.LogSafe;

public class LoginEvent implements NetEvent {
    private static final long serialVersionUID = -8865183377417377938L;

    private final String username;
    private final int sleeveIndex;
    private final String version;
    private final boolean libgdx;
    public LoginEvent(final String username, final int sleeveIndex, final String version, final boolean libgdx) {
        this.username = username;
        this.sleeveIndex = sleeveIndex;
        this.version = version;
        this.libgdx = libgdx;
    }

    public String getUsername() {
        return username;
    }

    public int getSleeveIndex() {
        return sleeveIndex;
    }

    public String getVersion() {
        return version;
    }

    public boolean isLibgdx() {
        return libgdx;
    }

    @Override
    public String toString() {
        return String.format("Login: %s, version %s%s", LogSafe.forLog(username), LogSafe.forLog(version), libgdx ? ", mobile" : "");
    }
}
