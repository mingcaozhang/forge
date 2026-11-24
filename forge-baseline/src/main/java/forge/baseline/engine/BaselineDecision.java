package forge.baseline.engine;

import forge.baseline.action.BaselineAction;
import forge.baseline.state.BaselinePrompt;
import forge.baseline.state.BaselineSnapshot;

import java.io.Serializable;

/**
 * One pass-priority decision of the scripted player, as observed data: the
 * prompt snapshot the engine was blocked on (the full state plus the
 * engine-computed legal candidates) and the fed {@link BaselineAction} that
 * answered it. This is the unit a trajectory consumer (the Phase 32.1
 * JSONL shard writer) records per decision.
 *
 * <p>UI-agnostic: it only references the baseline state/action contract types.
 */
public record BaselineDecision(BaselineSnapshot snapshot, BaselineAction action) implements Serializable {
    private static final long serialVersionUID = 1L;

    /** The prompt the engine was blocked on; the legal candidates live here. */
    public BaselinePrompt prompt() {
        return snapshot.getPrompt();
    }

    /** The sequence number of the state the decision was made on. */
    public long sequenceNumber() {
        return snapshot.getSequenceNumber();
    }
}
