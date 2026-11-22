package forge.gamemodes.net.event;

/**
 * Server tells a reconnecting client that its seat is gone — either the
 * disconnect window expired, or the match was never one this user was in.
 */
public final class SeatLostEvent implements NetEvent {
    private static final long serialVersionUID = 1L;

    @Override
    public String toString() {
        return "SeatLost";
    }
}
