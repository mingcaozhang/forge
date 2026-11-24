package forge.baseline.trajectory;

import forge.baseline.action.BaselineAction;
import forge.baseline.state.BaselineSnapshot;

/**
 * The non-human policy that drives the scripted side of a batch match: maps
 * the pending prompt snapshot (the state plus the engine-computed legal
 * candidates) to the {@link BaselineAction} that answers it.
 *
 * <p>A batch match is AI-vs-AI in the sense that neither side is a human: the
 * scripted side is answered by this policy, the other side is a
 * {@code LobbyPlayerAi}. The policy side's decisions are exactly the
 * (state, legal candidates, chosen action) triples the trajectory shards
 * export.
 *
 * <p>The policy runs on the batch runner's match thread, not on the game
 * thread, and must be deterministic for a fixed prompt snapshot — the shard
 * output is only comparable across runs if the policy is. It must not return
 * {@code null} (the engine rejects null actions); an action outside the legal
 * candidates is rejected by the engine and treated as a pass, which the
 * chosen-action field of the shard still records as fed.
 */
@FunctionalInterface
public interface ActionPolicy {
    BaselineAction choose(BaselineSnapshot promptSnapshot);
}
