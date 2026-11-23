package forge.game.event;

public record GameEventRollDie(int sides, int result) implements GameEvent {

    public GameEventRollDie() {
        this(0, 0);
    }

    @Override
    public <T> T visit(IGameEventVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
