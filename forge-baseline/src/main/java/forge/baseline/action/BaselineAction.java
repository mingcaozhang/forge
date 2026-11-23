package forge.baseline.action;

import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbilityView;
import forge.game.spellability.StackItemView;
import forge.gamemodes.match.DrawOfferMessage;
import forge.gamemodes.match.YieldUpdate;

import java.io.Serializable;
import java.util.List;

/**
 * The canonical action model of the baseline contract: the UI-agnostic 1:1
 * counterpart of the {@code IGameController} client-action surface (and of the
 * {@code CLIENT} entries of {@code ProtocolMethod}, which name the same actions
 * on the wire). A consumer that drives a game issues these actions; the Phase 28
 * adapter feeds them to the engine in place of the UI.
 *
 * <p>Every variant maps to exactly one {@code IGameController} method and one
 * {@code ProtocolMethod} name:
 *
 * <table class="plain">
 * <caption>BaselineAction &rarr; IGameController &rarr; ProtocolMethod</caption>
 * <tr><th>{@link SelectCard}</th><td>{@code selectCard(CardView, List, ITriggerEvent)}</td><td>{@code selectCard}</td></tr>
 * <tr><th>{@link SelectAbility}</th><td>{@code selectAbility(SpellAbilityView)}</td><td>{@code selectAbility}</td></tr>
 * <tr><th>{@link SelectPlayer}</th><td>{@code selectPlayer(PlayerView, ITriggerEvent)}</td><td>{@code selectPlayer}</td></tr>
 * <tr><th>{@link SelectStackItem}</th><td>{@code selectStackItem(StackItemView)}</td><td>{@code selectStackItem}</td></tr>
 * <tr><th>{@link Ok}</th><td>{@code selectButtonOk()}</td><td>{@code selectButtonOk}</td></tr>
 * <tr><th>{@link Cancel}</th><td>{@code selectButtonCancel()}</td><td>{@code selectButtonCancel}</td></tr>
 * <tr><th>{@link Concede}</th><td>{@code concede()}</td><td>{@code concede}</td></tr>
 * <tr><th>{@link Yield}</th><td>{@code sendYieldUpdate(YieldUpdate)}</td><td>{@code sendYieldUpdate}</td></tr>
 * <tr><th>{@link NextGameDecision}</th><td>{@code nextGameDecision(NextGameDecision)}</td><td>{@code nextGameDecision}</td></tr>
 * <tr><th>{@link UseMana}</th><td>{@code useMana(byte)}</td><td>{@code useMana}</td></tr>
 * <tr><th>{@link AlphaStrike}</th><td>{@code alphaStrike()}</td><td>{@code alphaStrike}</td></tr>
 * <tr><th>{@link ReorderHand}</th><td>{@code reorderHand(CardView, int)}</td><td>{@code reorderHand}</td></tr>
 * <tr><th>{@link UndoLastAction}</th><td>{@code undoLastAction()}</td><td>{@code undoLastAction}</td></tr>
 * <tr><th>{@link DrawOffer}</th><td>{@code drawOfferAction(DrawOfferMessage.Action)}</td><td>{@code drawOfferAction}</td></tr>
 * <tr><th>{@link RequestResync}</th><td>{@code requestResync()}</td><td>{@code requestResync}</td></tr>
 * </table>
 *
 * <p>Deliberate deltas from the raw controller surface:
 * <ul>
 *   <li>The {@code ITriggerEvent} mouse-metadata parameter is dropped: the wire
 *       protocol already sends {@code null} for it ({@code NetGameController}),
 *       and it carries no decision-relevant information.</li>
 *   <li>{@code getActivateDescription} is a query (it returns a description
 *       string) rather than a state transition, so it is not an action.</li>
 *   <li>{@code cheat()} and {@code macros()} (and the {@code MACRO}-mode protocol
 *       methods) belong to the dev/macro systems, outside this contract.</li>
 *   <li>The local-only yield setters ({@code setShouldAutoYield},
 *       {@code setTriggerDecision}, {@code setYieldPref},
 *       {@code setDisableAutoYields}, {@code setDisableAutoTriggers}) are
 *       expressed as {@link Yield} with the matching {@code YieldUpdate} variant
 *       — the same synthesis {@code NetGameController} performs on the wire.</li>
 *   <li>Server&rarr;client pushes ({@code applyYieldUpdate}, …) are not actions.</li>
 * </ul>
 *
 * <p>Prompt families whose answer travels on the {@code IGuiGame} prompt channel
 * instead of through {@code IGameController} (list ordering, numeric/string/color
 * entry) are exposed as {@code BaselinePrompt} state, not as actions.
 *
 * <p>Swing-free by construction: payloads are the game view model plus the pure
 * protocol data types of {@code forge.gamemodes.match}.
 */
public sealed interface BaselineAction extends Serializable
        permits BaselineAction.SelectCard,
                BaselineAction.SelectAbility,
                BaselineAction.SelectPlayer,
                BaselineAction.SelectStackItem,
                BaselineAction.Ok,
                BaselineAction.Cancel,
                BaselineAction.Concede,
                BaselineAction.Yield,
                BaselineAction.NextGameDecision,
                BaselineAction.UseMana,
                BaselineAction.AlphaStrike,
                BaselineAction.ReorderHand,
                BaselineAction.UndoLastAction,
                BaselineAction.DrawOffer,
                BaselineAction.RequestResync {

    /** Maps to {@code selectCard(CardView, List, ITriggerEvent)} / {@code ProtocolMethod.selectCard}. */
    record SelectCard(CardView card, List<CardView> otherCards) implements BaselineAction {}

    /** Maps to {@code selectAbility(SpellAbilityView)} / {@code ProtocolMethod.selectAbility}. */
    record SelectAbility(SpellAbilityView ability) implements BaselineAction {}

    /** Maps to {@code selectPlayer(PlayerView, ITriggerEvent)} / {@code ProtocolMethod.selectPlayer}. */
    record SelectPlayer(PlayerView player) implements BaselineAction {}

    /** Maps to {@code selectStackItem(StackItemView)} / {@code ProtocolMethod.selectStackItem}. */
    record SelectStackItem(StackItemView item) implements BaselineAction {}

    /** Maps to {@code selectButtonOk()} / {@code ProtocolMethod.selectButtonOk}. */
    record Ok() implements BaselineAction {}

    /** Maps to {@code selectButtonCancel()} / {@code ProtocolMethod.selectButtonCancel}. */
    record Cancel() implements BaselineAction {}

    /** Maps to {@code concede()} / {@code ProtocolMethod.concede}. */
    record Concede() implements BaselineAction {}

    /** A user-initiated yield-state change; maps to {@code sendYieldUpdate(YieldUpdate)} / {@code ProtocolMethod.sendYieldUpdate}. */
    record Yield(YieldUpdate update) implements BaselineAction {}

    /** Maps to {@code nextGameDecision(NextGameDecision)} / {@code ProtocolMethod.nextGameDecision}. */
    record NextGameDecision(forge.gamemodes.match.NextGameDecision decision) implements BaselineAction {}

    /** Maps to {@code useMana(byte)} / {@code ProtocolMethod.useMana}. */
    record UseMana(byte color) implements BaselineAction {}

    /** Maps to {@code alphaStrike()} / {@code ProtocolMethod.alphaStrike}. */
    record AlphaStrike() implements BaselineAction {}

    /** Maps to {@code reorderHand(CardView, int)} / {@code ProtocolMethod.reorderHand}. */
    record ReorderHand(CardView card, int index) implements BaselineAction {}

    /** Maps to {@code undoLastAction()} / {@code ProtocolMethod.undoLastAction}. */
    record UndoLastAction() implements BaselineAction {}

    /** Maps to {@code drawOfferAction(DrawOfferMessage.Action)} / {@code ProtocolMethod.drawOfferAction}. */
    record DrawOffer(DrawOfferMessage.Action action) implements BaselineAction {}

    /** Maps to {@code requestResync()} / {@code ProtocolMethod.requestResync}. */
    record RequestResync() implements BaselineAction {}
}
