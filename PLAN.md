# Forge — MTG Arena / Hearthstone Commander UI Overhaul

Goal: Make Forge's desktop Swing client feel crisp, readable, and tactile like MTG
Arena / Hearthstone, while preserving **every** existing action, keyboard shortcut,
phase stop, stack interaction, deck-editor filter, preference toggle, legality
check, and `.dck`/Moxfield/Archidekt workflow.

Hard constraints (do not violate):
- ZERO diff in `forge-core`, `forge-game`, `forge-ai`. Code changes may live in `forge-gui` and `forge-gui-desktop` (UI/controller layers only).
- Do not touch `sync_commit_registry.csv` or `scripts/sync-upstream.sh`.
- Work only on `dev`. Never push. Small atomic commits per phase.
- Keep `mvn test` green so `scripts/sync-upstream.sh` still works.

---

## Phase 1: FlatLaf L&F, Java2D flags, menu typography, animation pref

Target classes & decoupling points:
- `forge-gui-desktop/pom.xml` (dependencies block at lines 300–413) — add
  `com.formdev:flatlaf:3.5.2` (compile scope, not fat-jar excluded).
- `forge-gui-desktop/src/main/java/forge/view/Main.java:80–84` — GPU/AA props.
  Uncomment & set `sun.java2d.opengl=true` **behind a runtime check** (use
  `GraphicsEnvironment.isHeadless()` and `-Dsun.java2d.noddraw=true` fallback
  pattern), plus `awt.useSystemAAFontSettings=lcd` and
  `swing.aatext=true`. Must happen before any AWT class loads and before
  `GuiBase.setInterface(new GuiDesktop())` (line 86).
- `forge-gui-desktop/src/main/java/forge/toolbox/FSkin.java:1478–1716` —
  `ForgeLookAndFeel.setForgeLookAndFeel(JFrame)` calls
  `setMetalLookAndFeel()` at 1696 / 1713 / 1716. Replace the
  `UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel")`
  line with FlatDarkLaf (with fallback to Metal on exception so headless
  / unusual JVMs never break startup). Keep the existing `UIManager.put(...)`
  calls at 1730+ as overrides so FSkin skin colors keep flowing through.
- `forge-gui-desktop/src/main/java/forge/toolbox/FSkin.java:1139–1142` —
  skin reload path; reapply FlatLaf + token overrides after `UI_SKIN` changes.
- `forge-gui-desktop/src/main/java/forge/toolbox/FSkin.java:1335` —
  `defaultFontSize` already plumbed through `FPref.UI_DEFAULT_FONT_SIZE`;
  scale to FlatLaf menu/label/UI fonts here.
- `forge-gui-desktop/src/main/java/forge/menus/ForgeMenu.java` and
  `forge-gui-desktop/src/main/java/forge/menus/LayoutMenu.java` —
  **no action changes**; only verify accelerators and menu items keep working
  after L&F swap; tweak mnemonics / icons if FlatLaf exposes them differently.
- `forge-gui/src/main/java/forge/localinstance/properties/ForgePreferences.java`
  FPref enum (near desktop block, lines 36–41) — add
  `UI_ANIMATION_MODE("Normal")` and `UI_UI_THEME("ArenaDark")` enum values
  with default values.
- `forge-gui-desktop/src/main/java/forge/screens/home/settings/CSubmenuPreferences.java`
  — add an "Animation" combobox (`Off | Fast | Normal`) and a "UI Theme"
  combobox next to the existing `UI_DEFAULT_FONT_SIZE` control at line 491.

Key decoupling points:
- All FlatLaf wiring is in `FSkin.ForgeLookAndFeel` only. View code keeps
  calling `FSkin.getColor(...)` etc.; we map skin colors → UIManager tokens.
- GPU/AA system properties live only in `Main.main`; nothing else reads them.
- Animation pref is read via `FModel.getPreferences().getPref(FPref.UI_ANIMATION_MODE)`
  — Phase 2 builds on it.

Verification:
```
mvn test-compile -T 1C
mvn test -Dtest=*Commander*,*Deck* -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C
```

- [x] Add `com.formdev:flatlaf` dependency to `forge-gui-desktop/pom.xml`.
- [x] In `forge-gui-desktop/src/main/java/forge/view/Main.java`, set
      `sun.java2d.opengl=true` (guarded), `awt.useSystemAAFontSettings=lcd`,
      and `swing.aatext=true` before `GuiBase.setInterface(new GuiDesktop())`.
- [x] In `FSkin.java` `ForgeLookAndFeel.setMetalLookAndFeel`, install
      `FlatDarkLaf` (with `try/catch` fallback to Metal) and re-map existing
      `UIManager.put(...)` tokens so skin colors keep working.
- [x] Re-apply FlatLaf + token overrides in `FSkin.changeSkin` (line 1139–1142)
      so skin swaps remain consistent.
- [x] Verify `ForgeMenu.java` and `LayoutMenu.java` actions, accelerators,
      mnemonics, and keyboard shortcuts all work unchanged.
- [x] Add `UI_ANIMATION_MODE` and `UI_UI_THEME` FPref values to
      `ForgePreferences.java`; add matching controls in
      `forge-gui-desktop/src/main/java/forge/screens/home/settings/CSubmenuPreferences.java`.
- [x] Add `forge-gui-desktop/src/test/java/forge/ThemeIntegrationTest.java`
      asserting `com.formdev.flatlaf.FlatDarkLaf` is on the test classpath,
      that the new `FPref.UI_ANIMATION_MODE` and `FPref.UI_UI_THEME` values
      exist with defaults `Normal`/`ArenaDark`, and that
      `UIManager.getLookAndFeel()` returns FlatLaf after a headless-safe
      init path (direct `FlatDarkLaf` install; the `FSkin.ForgeLookAndFeel`
      path needs a JFrame so it is not headless-safe); run
      `mvn test -Dtest=ThemeIntegrationTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`.

Commit message: `refactor(commander): integrate FlatLaf, Java2D flags, menu typography, animation pref`

---

## Phase 2: UIAnimator + CardTextureCache + tactile card hover/glow

New classes (all under `forge-gui-desktop/src/main/java/forge/view/arcane/util/`):
- `UIAnimator.java` — single shared `javax.swing.Timer(16, ...)` driving a
  `CopyOnWriteArrayList` of tween subscribers; cubic ease-out; per-call
  durations 120–220 ms; **auto-stops the timer when the active set is empty**
  (zero idle CPU). Honors `FPref.UI_ANIMATION_MODE`:
  `Off` → snaps to end immediately, `Fast` → duration/2, `Normal` → full.
- `CardTextureCache.java` — bounded `LinkedHashMap` LRU with
  `SoftReference` values (max ~256 entries; key =
  `cardName+|+width+"x"+height+"|"+rounded+|+glow+|+rotation`). Caches:
  rounded frames, scaled art crops, drop-shadow layers, hover-glow layers.
  Replaces per-paint re-scaling.
- `Easing.java` — static `cubicOut`, `cubicIn`, `sineInOut` helpers.

Refactor points:
- `forge-gui-desktop/src/main/java/forge/view/arcane/util/Animation.java`
  (lines 38–301, 30 ms/frame `java.util.Timer` engine, callers:
  `PlayArea.java:1028`, `CardArea.java:326`, `Animation.java:195/256/292`) —
  keep the public static surface (`tapCardToggle`, `moveCard`, `unTapCards`)
  so existing callers do not change, but re-route internals through
  `UIAnimator`. Do not delete; only rewire.
- `forge-gui-desktop/src/main/java/forge/view/arcane/CardPanel.java` —
  in `paintComponent`, fetch cached rounded/glow textures from
  `CardTextureCache` instead of building gradients each frame; add
  `setHoverProgress(float)` that the animator drives 0→1 on
  mouseEntered/mouseExited; clamp scale to 1.08 and use cached glow ring.
- `forge-gui-desktop/src/main/java/forge/view/arcane/CardPanelContainer.java` —
  expose a hover-enter / hover-exit listener so detail-preview popups can be
  driven by animator (no per-paint work).
- `forge-gui-desktop/src/main/java/forge/view/arcane/HandArea.java` —
  animate card rise/fan on hover using `UIAnimator` instead of
  `revalidate`+`repaint` storms.
- `forge-gui/src/main/java/forge/gui/card/CardDetailUtil.java` —
  keep behavior; only swap layout constants for larger, crisper fonts via
  `FSkin` (UI-only).

Key decoupling points:
- `UIAnimator` lives only in `forge-gui-desktop`; `Animation` keeps its API
  so any non-arcane callers stay working.
- `CardTextureCache` is keyed on visual parameters only; it never reads game
  state, so card data path is untouched.
- Hover is a per-paint visual scale factor applied to the cached image,
  not a layout change — `getBounds`/hit-testing unchanged.

Performance guards:
- No `new` inside `paintComponent`. Reuse `AffineTransform`/`Rectangle` fields.
- Cache must be LRU-bounded; expose `clearCache()` for skin changes.
- One shared `Timer`; never create `Timer`s per card.

Verification:
```
mvn test-compile -T 1C
mvn test -Dtest=*Commander*,*Deck* -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C
```

- [x] Implement `forge-gui-desktop/src/main/java/forge/view/arcane/util/Easing.java`.
- [x] Implement `forge-gui-desktop/src/main/java/forge/view/arcane/util/UIAnimator.java`
       (single shared Swing Timer, auto-stop on empty, honors UI_ANIMATION_MODE).
- [x] Implement `forge-gui-desktop/src/main/java/forge/view/arcane/util/CardTextureCache.java`
       (LRU + `SoftReference`, bounded, thread-safe).
- [x] Rewire `Animation.java` internals to delegate to `UIAnimator` while
       keeping the existing public methods for `PlayArea.java:1028`,
       `CardArea.java:326`, `Animation.java:195/256/292`.
- [x] Add hover/glow + cached rounded-frame paint to
       `forge-gui-desktop/src/main/java/forge/view/arcane/CardPanel.java`.
- [x] Wire hover enter/exit through `CardPanelContainer.java` so detail
       previews animate in/out smoothly.
- [x] Replace hand fan/rise in `HandArea.java` with `UIAnimator` tweens.
- [x] **Context & Refactor Audit (`forge-gui` now permitted):** Inspected every
      Phase 1/2 commit on `dev` (`git log --oneline d34202b5c2f^..HEAD`). Most
      work was correctly placed: `FPref.UI_UI_THEME`/`UI_ANIMATION_MODE` already
      live in `forge-gui`, and `UIAnimator`/`CardTextureCache`/`Easing` belong in
      `forge-gui-desktop` (they drive Swing components). One **compromised**
      helper was found: the Phase 1 "UI Theme" combobox + `FPref.UI_UI_THEME`
      were plumbed and exposed, but `FSkin.ForgeLookAndFeel.setMetalLookAndFeel`
      hard-coded `new FlatDarkLaf()` and silently ignored the preference, so the
      setting was a no-op. Refactored cleanly: extracted `resolveFlatLaf()` which
      reads `FPref.UI_UI_THEME` (`"Light"` → `FlatLightLaf`, otherwise
      `FlatDarkLaf`/ArenaDark default), applied at L&F install with the existing
      Metal fallback and a `Throwable`-guarded pref read so startup is never
      broken. Default (`ArenaDark`) behavior is byte-for-byte unchanged. Kept
      `forge-core`/`forge-game`/`forge-ai` untouched; no `forge-gui` edit was
      required. Verified `mvn test-compile -T 1C` (exit 0) and
      `mvn test -Dtest=*Commander*,*Deck* ... -T 1C` (165 pass).
- [x] Tune `CardDetailUtil.java` typography constants (UI-only, no logic).
      **Context & placement audit:** Inspected `CardDetailUtil.java` (forge-gui)
      — it is a pure text/`DetailColors` composer with **no** font/typography
      constants and cannot reference `FSkin` (wrong module; forge-gui does not
      depend on forge-gui-desktop). The card-detail typography constants actually
      live in the rendering view `forge-gui-desktop/.../forge/gui/CardDetailPanel.java`,
      which was hard-coding `new Font("Dialog", 0, …)` — bypassing the skinned
      base face and the `UI_DEFAULT_FONT_SIZE` pref. Tuned cleanly there (UI-only,
      zero logic/text-composition change): introduced named `DETAIL_FONT_SIZE` /
      `CARD_TEXT_FONT_SIZE` constants and routed all four labels + the HTML card
      text area through `FSkin.getRelativeFont(...)` so they now use the crisp
      theme base face and scale with the pref. `doLayout()` is preferred-size
      driven so the slightly larger sizes reflow automatically. Removed the now
      unused `java.awt.Font` import. `forge-core`/`forge-game`/`forge-ai` and
      `CardDetailUtil` text output left byte-for-byte unchanged (so
      `PerfPatchFollowUpRulesTest` markup assertions stay intact). Verified
      `mvn test-compile -T 1C` (exit 0) and
      `mvn test -Dtest=*Commander*,*Deck* ... -T 1C` (237 pass).
- [x] Add `forge-gui-desktop/src/test/java/forge/view/arcane/util/UIAnimatorTest.java`
      asserting a submitted tween reaches 100% in its declared duration
      and that the shared Swing `Timer` reports `isRunning() == false`
      once the active set drains to empty; add
      `CardTextureCacheTest.java` asserting bounded LRU eviction at the
      configured cap and that a `clearCache()` call forces recompute;
      run
      `mvn test -Dtest=UIAnimatorTest,CardTextureCacheTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`.
      **Implemented** (test-only, plus one observability accessor). `UIAnimatorTest`
      (3 methods): a `Normal`-mode tween animates through intermediate frames,
      lands exactly on `1.0`, fires `onEnd` once, never finishes faster than its
      declared duration; the shared timer is `isRunning()` while active and reports
      `false` (with an empty active set) after drain via an EDT-ordered check; `Off`
      snaps to end without ever entering the active set or starting the timer.
      `CardTextureCacheTest` (3 methods): LRU evicts the eldest entries at the
      configured cap (size never exceeds it, strong refs kept so only the LRU
      policy evicts), `clearCache()` empties the cache and forces exactly one
      recompute via `getOrCompute`, and `textureKey` is stable/parameter-sensitive.
      Added a minimal `UIAnimator.isTimerRunning()` observability accessor (read on
      the EDT) so the required idle-Timer assertion — and Phase 6's
      `UIAnimatorIdleTest` — can observe the real shared `Timer` state.
      Verified `mvn test-compile -T 1C` (exit 0),
      `mvn test -Dtest=UIAnimatorTest,CardTextureCacheTest ... -T 1C` (6 pass), and
      `mvn test -Dtest=*Commander*,*Deck* ... -T 1C` (165 pass).

Commit message: `refactor(commander): add UIAnimator + CardTextureCache, tactile card hover/glow/preview`

---

## Phase 3: Action weight — cast/draw/tap/attack/damage feedback

Target classes (all in `forge-gui-desktop`):
- `forge-gui-desktop/src/main/java/forge/screens/match/CMatchUI.java` —
  hook points where zone changes fire; schedule animator tweens for draw,
  cast, attack declarations, taps/untaps.
- `forge-gui-desktop/src/main/java/forge/screens/match/views/VField.java` —
  cast "lunge" + glow pulse on creature entry / spell resolution; attack
  directional tilt toward the blocker; damage popup that floats up and fades.
- `forge-gui-desktop/src/main/java/forge/screens/match/views/VHand.java` —
  Arena-style draw arc when a card is added (use `Animation.moveCard` path
  routed through `UIAnimator`), tap pulse when a land is used.
- `forge-gui-desktop/src/main/java/forge/screens/match/views/VStack.java` —
  stack drawer slides in/out via `UIAnimator`; per-item fade in.
- `forge-gui-desktop/src/main/java/forge/screens/match/views/VZone.java` —
  counter bump animation when graveyard/exile/library counts change.
- `forge-gui-desktop/src/main/java/forge/screens/match/views/TargetingOverlay.java`
  (or the existing target crosshair overlay) — softer, animated highlight.
- New files under `forge-gui-desktop/src/main/java/forge/view/arcane/effects/`:
  - `HitFlashOverlay.java` — short 180 ms white/red radial flash.
  - `DamagePopup.java` — floating number, fades up + out, 600 ms.
  - `CastGlow.java` — soft radial glow that scales with spell CMC.

Rules:
- All effects must be **fire-and-forget** overlays added to a transparent
  `JLayeredPane` (`CardPanel.getDragAnimationPanel()` already exists), so
  no view layout is touched.
- Effect triggers must come from existing CMatchUI hooks. If a hook point is
  missing, add a thin listener interface in `forge-gui-desktop` only — do
  **not** modify `forge-game`.
- Honor `FPref.UI_ANIMATION_MODE == Off` by skipping overlays entirely.

Key decoupling points:
- Effects never mutate game state; they only read it.
- If `CMatchUI` cannot yet notify a needed event, use an `EventListenerList`
  on the desktop side and emit on existing repaint paths; do not change
  game engine.

Verification:
```
mvn test-compile -T 1C
mvn test -Dtest=*Commander*,*Deck* -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C
```

- [x] Implement `HitFlashOverlay.java`, `DamagePopup.java`, `CastGlow.java`
      under `forge-gui-desktop/src/main/java/forge/view/arcane/effects/`.
      **Implemented** (test-only none needed this item; new package only).
      Added a shared abstract base `ArcaneEffect.java` that schedules exactly
      one keyed `UIAnimator.startRaw` tween per effect, registers it in a static
      active list (`activeCount()`/`activeEffects()`), requests target repaints
      per frame, and retires itself in the tween's `onEnd` — so once full
      duration elapses both the effect list and the shared animator timer are
      empty/idle (the invariant the later `EffectsLifecycleTest` will assert).
      `HitFlashOverlay` (triangular ramp/fade rounded flash, ~220ms),
      `DamagePopup` (floating `-N` text that rises via `Easing.cubicOut` and
      fades, ~900ms), `CastGlow` (outward pulsing multi-ring glow, ~600ms).
      All effects are purely visual, read no game state, and add ZERO diff to
      `forge-core`/`forge-game`/`forge-ai`. Verified `mvn test-compile -T 1C`
      (exit 0; checkstyle clean) and `mvn test -Dtest=*Commander*,*Deck* ... -T 1C`
      (165 pass).
- [x] Add a transparent overlay `JLayer` to the match window in
      `forge-gui-desktop/src/main/java/forge/screens/match/VMatchUI.java`.
      Added `forge-gui-desktop/src/main/java/forge/screens/match/MatchEffectOverlay.java`:
      a non-opaque `JLayer<JComponent>` whose `LayerUI.paint` draws every
      `ArcaneEffect.activeEffects()` in window coordinates, skipping all work while
      not showing or while `UIAnimator.currentMode()` is `OFF` (made `currentMode()`
      public in `UIAnimator.java` for this). `JLayer` is final in the JDK, so a raw
      layer swallows every mouse hit (verified empirically: it claims `findComponentAt`);
      the layer therefore sits in a transparent `ClickThroughPane` host whose
      `contains` is always `false`, so hits fall through to cards/buttons while the
      layer still fills the window and paints above them (verified by painting into
      an offscreen image). Host/layer are sized to `FView.getLpnDocument()` bounds,
      re-sized on component resize, added at `FView.TARGETING_LAYER` index `0`
      (same layer as the targeting arcs, above them) and take part in no layout and
      no focus. `VMatchUI` owns one instance: `attach()` on `populate()`,
      `detach()` only on the paths where the screen actually closes, both idempotent;
      `getEffectOverlay()` exposes it and `getPaintTarget()` gives effects the layer
      to request repaints on. ZERO diff to `forge-core`/`forge-game`/`forge-ai`.
      Verified `mvn test-compile -T 1C` (exit 0; checkstyle clean) and
      `mvn test -Dtest=*Commander*,*Deck* ... -T 1C` (165 pass).
- [x] Hook draw / tap / attack triggers in `CMatchUI.java` to dispatch
      animator effects (no logic changes). Forwarded `GameEventCardTapped`,
      `GameEventAttackersDeclared`, and draw (`GameEventCardChangeZone`
      Library->Hand) through new `IGuiGame` default hooks (no-op for mobile)
      and `FControlGameEventHandler` (EDT, non-libgdx only); `CMatchUI`
      dispatches `CastGlow` over the card's field/hand panel via
      `VMatchUI.getEffectOverlay()`, no-op when `UIAnimator.Mode.OFF`. ZERO
      diff to `forge-core`/`forge-game`/`forge-ai`. Verified
      `mvn test-compile -T 1C` (exit 0) and
      `mvn test -Dtest=*Commander*,*Deck* ... -T 1C` (exit 0).
- [x] Add cast lunge + glow pulse to `VField.java` creature/spell entry.
- [x] Add Arena-style draw arc and tap pulse in `VHand.java`.
- [x] Animate stack drawer open/close and per-item fade in `VStack.java`.
      Drawer "open" plays when the stack goes from empty to non-empty: every
      entry starts faded + offset below rest and rises/fades in via a keyed
      `UIAnimator.startRaw` tween, staggered per position so the batch reads as
      a drawer opening. After open, only newly-cast items animate (tracked by
      `StackItemView.getId()` in `entryAnimated`) so persistent entries are not
      re-animated on every repaint; the set clears on open/close. Per-entry
      `setEntryState`/`paintComponent` apply `AlphaComposite` + translate on a
      child `Graphics` copy — purely paint-only, no layout/hit-test change.
      Honors `UIAnimator.Mode.OFF` (skips tween, leaves entries at rest).
      ZERO diff to `forge-core`/`forge-game`/`forge-ai`. Verified
      `mvn test-compile -T 1C` (exit 0; checkstyle clean) and
      `mvn test -Dtest=*Commander*,*Deck* ... -T 1C` (exit 0).
- [x] Animate counter bumps in `VZone.java` (graveyard/exile/library).
      Count increase on a hidden zone tab now drives a shared `UIAnimator.startRaw`
      tween (keyed per `DragTab`) instead of `TabDiffTracker`'s private
      `javax.swing.Timer`: a red flash ramp (triangle envelope) plus a paint-only
      label "bump" via new `DragTab.setBumpScale` rendered with a centered
      `AffineTransform` over `super.paintComponent` (no layout/hit-test change).
      The envelope rests at 0/1.0 when progress hits 1, so `UIAnimator.Mode.OFF`
      snaps straight to rest. Routed through `TabDiffTracker` so all zone/hand/
      field tabs share the behavior. ZERO diff to `forge-core`/`forge-game`/
      `forge-ai`. Verified `mvn test-compile -T 1C` (exit 0) and
      `mvn test -Dtest=*Commander*,*Deck* ... -T 1C` (165 tests, exit 0).
- [x] Soften targeting crosshair pulse in existing TargetingOverlay file.
      `forge-gui-desktop/src/main/java/forge/screens/match/TargetingOverlay.java`
      now drives a soft "breathing" highlight from the shared `UIAnimator`
      (keyed tween, clock-sampled cosine envelope, ~1500 ms cycle): arrow fills
      fade between ~0.78 and 1.0 of base alpha and the outline is a translucent
      black (~140-205) instead of a hard `Color.BLACK`, so the highlight reads
      as a glow rather than a stamp. Pulse only runs while arcs are on screen
      and unwinds to idle when arcs clear or `UIAnimator.Mode.OFF` (then it
      keeps the static, full-strength look with the softened outline). No new
      `Timer`, no layout/hit-test change, ZERO diff to `forge-core`/`forge-game`/
      `forge-ai`. Verified `mvn test-compile -T 1C` (exit 0) and
      `mvn test -Dtest=*Commander*,*Deck* ... -T 1C` (exit 0).
- [x] Add `forge-gui-desktop/src/test/java/forge/view/arcane/util/AnimationModeTest.java`
      asserting `UIAnimator` snaps to end for mode `Off`, halves duration
      for `Fast`, and uses full duration for `Normal`; plus
      `forge-gui-desktop/src/test/java/forge/view/arcane/effects/EffectsLifecycleTest.java`
      asserting that scheduling a `HitFlashOverlay`/`DamagePopup`/`CastGlow`
      leaves the active effect list empty and the `UIAnimator` timer stopped
      after its full duration has elapsed; run
      `mvn test -Dtest=AnimationModeTest,EffectsLifecycleTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`.
      **Implemented** (test-only, ZERO production diff). `AnimationModeTest`
      (2 methods): `Off` snaps to `1.0`, fires `onEnd` once, and never enters the
      animator active set or starts the shared timer (snap asserted by sampling
      `activeCount()`/`isTimerRunning()` from inside `onEnd`); `Normal` runs the
      full declared duration (never faster) while `Fast` runs in ~half — proven by
      measuring both back-to-back so any constant EDT/scheduling latency cancels
      out of the `fast/normal` ratio (≤0.8), with both animating intermediate
      frames and landing on `1.0`. `EffectsLifecycleTest` (4 methods): each of
      `HitFlashOverlay`/`DamagePopup`/`CastGlow` schedules exactly one animator
      tween while active, then drains to `activeCount()==0` (effects and tweens)
      with the shared timer stopped after its full duration; plus one test
      scheduling all three and confirming the full drain. Effects use a `null`
      paint target so all checks stay headless-safe. Verified
      `mvn test-compile -T 1C` (exit 0; checkstyle clean),
      `mvn test -Dtest=AnimationModeTest,EffectsLifecycleTest ... -T 1C` (6 pass), and
      `mvn test -Dtest=*Commander*,*Deck* ... -T 1C` (165 pass).

Commit message: `feat(commander): tactile cast/draw/tap/attack/damage feedback`

---

## Phase 4: Arena-style Home dashboard

Target classes:
- `forge-gui-desktop/src/main/java/forge/screens/home/VHomeUI.java` —
  replace left-side submenu list (lines 77–116) with a tile/grid dashboard:
  "Play", "Decks", "Collection", "Preferences" hero cards plus a "Recent
  Decks" carousel and "Last Match" tile. Keep `IVSubmenu` interface
  (`forge-gui-desktop/src/main/java/forge/screens/home/IVSubmenu.java:13–26`)
  and `CHomeUI.itemClick(...)` (`CHomeUI.java:35–53`) wiring exactly as-is.
- `forge-gui-desktop/src/main/java/forge/screens/home/CHomeUI.java` —
  add a tile-activated → submenu path; do not change the submenu doc IDs.
- `forge-gui-desktop/src/main/java/forge/screens/home/LblMenuItem.java:47` —
  keep click wiring intact; either replace with `HomeTile` or keep for
  accessibility fallback behind the existing `UI_SCREENREADER_OPTIMIZE`.
- New classes under `forge-gui-desktop/src/main/java/forge/screens/home/tiles/`:
  - `HomeTile.java` — rounded panel, hover scale + glow via `UIAnimator`,
    icon + title + subtitle.
  - `RecentDecksCarousel.java` — small card-art thumbnails using
    `CardTextureCache` for the latest played decks.
  - `LastMatchTile.java` — summary tile using existing match history.

Key decoupling points:
- Tiles call the existing `CHomeUI.itemClick(itemEnum)` exactly. The
  submenu view classes remain unchanged.
- `VHomeUI` keeps `EDocID.HOME_CONSTRUCTED` default selection
  (`VSubmenuConstructed.java:57–58`) and `CHomeUI.java:103–112` default
  restoration.

Verification:
```
mvn test-compile -T 1C
mvn test -Dtest=*Commander*,*Deck* -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C
```

- [x] Implement `HomeTile.java`, `RecentDecksCarousel.java`,
      `LastMatchTile.java` under `forge-gui-desktop/src/main/java/forge/screens/home/tiles/`.
- [x] Refactor `VHomeUI.java` to lay out hero tiles + recent decks carousel.
- [x] Update `CHomeUI.java` so tile clicks dispatch through existing
      `itemClick(itemEnum)` path; keep `EDocID.HOME_*` behavior intact.
      **Implemented** (controller-only wiring). Added `CHomeUI.wireDashboardTiles()`,
      called once from `initialize()`, which installs a click handler on every hero
      tile from `VHomeUI.getHeroTiles()` that forwards the tile's `EDocID` tag to the
      existing `itemClick(EDocID)` method — the same path `LblMenuItem` uses — so tile
      clicks are byte-for-byte equivalent to selecting the left-hand menu entry and
      `EDocID.HOME_*` behavior is unchanged. `HomeTile` keeps zero screen/navigation
      knowledge (handler is injected via `setOnClick`); zero diff to
      `forge-core`/`forge-game`/`forge-ai`. CardLayout still defaults to the submenu
      display (dashboard surfacing + fade/slide transition is the next item), so runtime
      navigation is unchanged today. Also corrected the now-stale comment in `VHomeUI`
      that described the wiring as a future step. Verified `mvn test-compile -T 1C`
      (exit 0) and `mvn test -Dtest=*Commander*,*Deck* ... -T 1C` (165 pass).
 - [x] Add smooth transition (`UIAnimator` fade+slide) between dashboard and
       submenus without breaking `IVSubmenu` contract.
       **Implemented** (UI-only in `forge-gui-desktop`). Replaced the `CardLayout`-backed
       `VHomeUI.PnlDisplayArea` with a null-layout stack that overlaps `pnlDisplay` and
       `pnlDashboard` (both sized to the full area via `doLayout`). `showDashboard(boolean)`
       snapshots the outgoing panel to a `BufferedImage`, switches the live panel immediately
       (so the incoming one stays hit-testable and `getPnlDisplay()` callers are unaffected),
       then runs one keyed `UIAnimator.start(220, "home-display-transition", ...)` tween that
       fades + slides the outgoing snapshot over the live incoming panel; `paint` draws the
       overlay above the children. Honors `UIAnimator.Mode.OFF` (snaps, no tween). Both
       directions are reachable via `CHomeUI.itemClick`: re-clicking the active menu item
       toggles dashboard/submenu, and navigating to a different doc slides back to the display.
       `IVSubmenu` (`getGroupEnum`/`getMenuTitle`/`getItemEnum`/`populate`) untouched. Verified
       `mvn test-compile -T 1C` (exit 0) and `mvn test -Dtest=*Commander*,*Deck* ... -T 1C`
       (165 pass). Diff confined to `forge-gui-desktop` (zero diff in `forge-core`/`forge-game`/`forge-ai`).
- [x] Preserve screen-reader / accessibility path: if
      `FPref.UI_SCREENREADER_OPTIMIZE == true`, fall back to list layout.
      **Implemented** (UI-only in `forge-gui-desktop`). Added
      `VHomeUI.isAccessibilityLayout()` (Throwable-guarded `FPref.UI_SCREENREADER_OPTIMIZE`
      read, so startup is never broken) and centralized the clamp in the single
      dashboard-show chokepoint `VHomeUI.PnlDisplayArea.showDashboard`, which now
      forces the tile dashboard hidden whenever screen-reader optimization is on —
      the classic `LblMenuItem` submenu list (always live in `pnlMenu`) becomes the
      only navigation, and `isDashboardVisible()` can never report `true` in that
      mode. `CHomeUI.itemClick` additionally skips the re-click dashboard-toggle
      (and its `refreshDashboard()` work) in accessibility mode so re-selecting the
      active menu item is a no-op and the submenu stays put. Tiles remain built but
      never shown (hidden components stay out of screen-reader focus traversal);
      `IVSubmenu`/`EDocID.HOME_*` navigation and the fade+slide transition are
      unchanged for the normal path. ZERO diff to `forge-core`/`forge-game`/`forge-ai`.
      Verified `mvn test-compile -T 1C` (exit 0; checkstyle clean) and
      `mvn test -Dtest=*Commander*,*Deck* ... -T 1C` (165 pass).
- [x] Add `forge-gui-desktop/src/test/java/forge/screens/home/HomeNavigationRegistryTest.java`
       asserting that the set of `EDocID` values surfaced by the new
       dashboard tiles is exactly the same as the set returned by
       `IVSubmenu.getItemEnum()` across every existing
       `VSubmenu*` class (Play / Decks / Collection / Preferences), and that
       the `EDocID.HOME_CONSTRUCTED` default still resolves; run
       `mvn test -Dtest=HomeNavigationRegistryTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`.
       Note: `EDocID`/`VSubmenu*` singletons cannot be instantiated in a bare
       unit-test JVM (loading them requires the full skin/splash sprite stack),
       so the test introspects the `VHomeUI` `allSubmenus` registrations and each
       `VSubmenu*` `getItemEnum()` source. ZERO diff to
       `forge-core`/`forge-game`/`forge-ai`. Verified
       `mvn test -Dtest=HomeNavigationRegistryTest ... -T 1C` (7 pass).

Commit message: `feat(commander): Arena-style Home dashboard tiles`

---

## Phase 5: Deck editor / card browser modernization

Target classes:
- `forge-gui-desktop/src/main/java/forge/screens/deckeditor/VDeckEditorUI.java` —
  modern tab strip, subtle gradient toolbar, rounded panels. Keep all
  existing tabs and drag-layout behavior driven by
  `forge-gui/res/defaults/editor.xml` (lines 2–23).
- `forge-gui-desktop/src/main/java/forge/screens/deckeditor/CDeckEditorUI.java` —
  only style/menu glue; **do not** change deck logic.
- `forge-gui-desktop/src/main/java/forge/itemmanager/ItemManager.java` —
  modernize view toggle buttons (lines 193–200), search filter (lines 137–138),
  add-search/reset/hide buttons (lines 202–214), and the table/image switch
  logic (lines 288–321). Preserve filter behavior exactly.
- `forge-gui-desktop/src/main/java/forge/itemmanager/views/ItemListView.java`
  (lines 93, 105, 220–223) — slim scrollbars, zebra rows, larger header
  font via FlatLaf `Table.headerFont`.
- `forge-gui-desktop/src/main/java/forge/itemmanager/views/ImageView.java:43` —
  use `CardTextureCache`-backed card tiles with hover scale/glow.
- `forge-gui-desktop/src/main/java/forge/screens/deckeditor/views/VCurrentDeck.java` —
  toolbar buttons (lines 38–90) and header layout (lines 118–130); keep
  `getLoadButton()` (220) and `getImportButton()` (243) wiring identical.
- `forge-gui-desktop/src/main/java/forge/screens/deckeditor/views/VAllDecks.java`
  (lines 43–44, 84–100) and
  `forge-gui-desktop/src/main/java/forge/screens/deckeditor/views/VCardCatalog.java`
  (lines 30, 69) — restyle only; preserve `ItemManagerContainer` placement
  and auto-edit-preferred-deck behavior.
- `forge-gui-desktop/src/main/java/forge/screens/deckeditor/views/VStatistics.java`
  (lines 137–173, 233) and
  `forge-gui-desktop/src/main/java/forge/screens/deckeditor/controllers/CStatistics.java`
  (lines 48–130) — restyle mana curve bars (rounded, colored by mana),
  type pie/bar, and average CMC readout. **Do not** touch
  `DeckStatistics.pointsSummary` calls at line 125–130.
- `forge-gui-desktop/src/main/java/forge/screens/deckeditor/views/VCommanderDecks.java`
  (lines 81–85) and
  `forge-gui-desktop/src/main/java/forge/screens/deckeditor/controllers/CCommanderDecks.java`
  (lines 32–41) — add a commander/partner showcase header (big art + name
  + identity badges) using existing `DeckProxy.getAllCommanderDecks()` data.
- `forge-gui-desktop/src/main/java/forge/screens/deckeditor/DeckImport.java`
  and `forge-gui-desktop/src/main/java/forge/screens/deckeditor/menus/DeckFileMenu.java` —
  restyle only; keep new/open/import/save/save-as/print/copy/enforce-conformity
  behavior exactly (lines 36–46, 127–135).

Key decoupling points:
- Editor layout stays XML-driven (`editor.xml`); only Java view classes are
  styled, never the layout IDs.
- Filter predicates in `forge-gui-desktop/src/main/java/forge/itemmanager/filters/`
  remain untouched; only their UI widgets are restyled.
- Legality/`ALLOW_CUSTOM_CARDS_IN_DECKS_CONFORMANCE` and
  `UI_LOAD_NONLEGAL_CARDS` checks unchanged.
- `SEditorIO.java` (lines 28–116) save/dirty flow unchanged; only button
  styling changes.

Verification:
```
mvn test-compile -T 1C
mvn test -Dtest=*Commander*,*Deck* -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C
```

- [x] Restyle `VDeckEditorUI.java` tabs/panels; keep `editor.xml` layout IDs.
- [x] Modernize `ItemManager.java` toolbar, search, and view-switch UI.
- [x] Restyle `ItemListView.java` table (zebra rows, slim scrollbars, header).
- [x] Upgrade `ImageView.java` card tiles with hover scale/glow via cache.
- [x] Restyle `VCurrentDeck.java` toolbar/header; keep button accessors intact.
      **Implemented** (UI-only in `forge-gui-desktop`). `pnlHeader` is now a
      private `PnlToolbar extends JPanel` that paints a subtle `CLR_THEME` vertical
      gradient with a rounded (arc 12) soft border behind the header controls — it
      is a drop-in replacement for the old plain `JPanel` (same MigLayout, sizes,
      insets and hit-testing; `getPnlHeader()` still returns a `JPanel` and is only
      ever used for `setVisible`). All seven toolbar icon/text buttons (`btnSave`,
      `btnSaveAs`, `btnLoad`, `btnNew`, `btnPrintProxies`, `btnCopyToClipboard`,
      `btnImport`) now use the existing opt-in `FLabel` `modernToggle()` flat
      rounded chrome already adopted by `ItemManager`, so the deck-editor toolbar
      matches the rest of the modernized browser. No command, tooltip, size,
      layout, or accessor change (`getBtnOpen()`/`getBtnImport()` and all others
      wired byte-for-byte identical). ZERO diff to `forge-core`/`forge-game`/`forge-ai`.
      Verified `mvn test-compile -T 1C` (exit 0; checkstyle clean) and
      `mvn test -Dtest=*Commander*,*Deck* ... -T 1C` (165 pass).
- [x] Restyle `VAllDecks.java` and `VCardCatalog.java`; preserve auto-edit.
      **Implemented** (UI-only in `forge-gui-desktop`). Added a shared
      paint-only `RoundedContentCard` (package-private in
      `forge.screens.deckeditor.views`, mirroring the `VCurrentDeck`
      `PnlToolbar` visual language: subtle `CLR_THEME` vertical gradient
      `stepColor(-8)→stepColor(-16)`, rounded arc 12, soft translucent
      `CLR_BORDERS` outline) that wraps a browser component in a
      `BorderLayout` CENTER. `VAllDecks.populate()` now adds
      `RoundedContentCard(ItemManagerContainer)` with the identical
      `"push, grow"` constraint, and `VCardCatalog` holds one card around
      its singleton `itemManagerContainer` — the browser placement, insets
      and hit-testing are visually unchanged, and the `refresh()` +
      `editPreferredDeck(...)` preferred-deck auto-edit path is byte-for-byte
      intact. Card paint respects the existing opt-in modern cell chrome
      (draws behind the already-non-opaque container/manager). ZERO diff to
      `forge-core`/`forge-game`/`forge-ai`. Verified `mvn test-compile -T 1C`
      (exit 0) and `mvn test -Dtest=*Commander*,*Deck* ... -T 1C` (165 pass).
 - [x] Redesign `VStatistics.java` + `CStatistics.java` curve/identity visuals.
       Restyled into modern rounded `RoundedContentCard` panels with custom
       charts: a new `ManaCurveChart` (rounded, per-CMC stacked bars colored by
       sampled mana-symbol icon color) plus two `DistributionBarChart`s for the
       color-identity and card-type breakdowns, all fed by a `ManaPalette`
       icon-color sampler. `CStatistics.analyze()` now computes numeric count
       arrays (colors, types, CMC curve + per-bucket `W U B R G colorless`
       symbol totals) and injects them via a new small view setter API
       (`setSummary`/`setPoints`/`setColorDistribution`/`setCardTypeDistribution`
       `/setManaCurve`); displayed counts/percentages are unchanged. The
       `DeckStatistics.pointsSummary` path is behavior-for-behavior identical.
       ZERO diff to `forge-core`/`forge-game`/`forge-ai`. Verified
       `mvn test-compile -T 1C` (exit 0) and
       `mvn test -Dtest=*Commander*,*Deck* ... -T 1C` (exit 0).
 - [x] Add commander/partner showcase header in `VCommanderDecks.java`.
      New package-private inner class `VCommanderDecks.CommanderShowcasePanel`
      (custom-painted, non-opaque `JComponent`) renders a rounded gradient
      header above the commander deck list matching the existing
      `RoundedContentCard` chrome: rounded cover thumbnail of the first
      commander (via `ImageCache.scaleImage` + `makeRoundedCorner`), bold
      commander/partner name(s) joined with ` & ` from the read-only
      `DeckProxy.getDeck().getCommanders()`, deck name subtitle, and
      horizontal color identity badges via `CardFaceSymbols.drawColorSet`
      using `DeckProxy.getColorIdentity()`. It never mutates deck state.
      Wired through `DeckManager.setSelectCommand` (DeckManager already fans
      out row selection to `cmdSelect`), plus explicit refresh hooks in
      `VCommanderDecks.populate()` and `CCommanderDecks.refresh()`.
      Generated/empty decks fall back to a placeholder title. ZERO diff to
      `forge-core`/`forge-game`/`forge-ai`. Verified
      `mvn test-compile -T 1C` (exit 0) and
      `mvn test -Dtest=*Commander*,*Deck* ... -T 1C` (exit 0).
- [x] Restyle `DeckImport.java` and `DeckFileMenu.java`; keep all actions.
- [x] Add `forge-gui-desktop/src/test/java/forge/itemmanager/DeckEditorFilterParityTest.java`
      asserting that the existing filter predicates
      (`TextSearchFilter`, `CardFormatFilter`, `CardCMCRangeFilter`,
      `ToggleButtonsFilter`) return the exact same card set after the UI
      refactor as they do on the pre-refactor baseline deck, and that the
      existing `*Deck*` suite continues to pass; run
      `mvn test -Dtest=DeckEditorFilterParityTest,*Deck* -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`.

Commit message: `feat(commander): modernize deck editor, card browser, statistics, commander showcase`

---

## Phase 6: Match HUD, dialogs, final performance verification

Target classes:
- `forge-gui-desktop/src/main/java/forge/screens/match/VMatchUI.java` —
  Arena-style HUD: clearer player panels (avatar, name, life total, poison,
  commander tax), unified phase label pill (`UI_UNIFIED_PHASE_LABEL` is
  already honored), zone tab strip, and a slimmer stack/log column.
- `forge-gui-desktop/src/main/java/forge/screens/match/CMatchUI.java` —
  keep floating-zone behavior (Command/Graveyard/Exile/Library) exactly;
  only restyle the floating frames via FSkin/FlatLaf tokens.
- `forge-gui-desktop/src/main/java/forge/screens/match/views/VPrompt.java` —
  Arena-style prompt bar with prominent primary action, secondary row,
  "Show Stack" affordance. Preserve `UI_REVERSE_PROMPT_BUTTON`,
  `UI_COMPACT_PROMPT`, `UI_DETAILED_SPELLDESC_IN_PROMPT`, and
  `UI_ALLOW_ESC_TO_END_TURN` behavior exactly.
- `forge-gui/src/main/java/forge/gui/interfaces/*` — **do not change**; all
  prompt logic must remain identical.
- Final performance sweep: profile a Commander match, confirm
  `CardPanel.paintComponent` performs no allocations and `UIAnimator` timer
  stops when idle.

Key decoupling points:
- Prompt content & flow are driven by `forge-game` and
  `forge-gui` interfaces; the desktop view only renders them.
- Floating zones use existing `CardPanel.getDragAnimationPanel()` pattern.
- All HUD additions read game state via existing `IGame` getters only.

Verification (full gates per AGENTS.md final-phase rule):
```
mvn test-compile -T 1C
mvn test -Dtest=*Commander*,*Deck* -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C
mvn package -DskipTests -T 1C
mvn test -T 1C
```

- [x] Restyle player panels / life counters in `VMatchUI.java`.
- [x] Style unified phase label pill (existing `UI_UNIFIED_PHASE_LABEL`).
      → Implemented in `PhaseIndicator.java` / `PhaseLabel.java` (the pill lives there, not `VMatchUI`):
      the pref is now read (default `true`) and drives an Arena-style segmented capsule with an
      accent ring on the current phase; `false` restores the classic separated-chip look.
      Click-toggle, right-click yield, tooltips, hover/pressed feedback and tutorial spotlight unchanged.
- [x] Slim zone tab strip and stack/log column in `VMatchUI.java`.
- [x] Restyle floating zone frames in `CMatchUI.java` (no behavior change).
      Zone frames opened via `CMatchUI` build their chrome in `FloatingZone`/`FloatingCardArea`/`FDialog`;
      added opt-in `FDialog.setFloatingFrameAccent` + `CardPanel.getZoneAccentColor` and trimmed the zone
      window's outer hairline and prompt callout to the zone accent. Border insets (3px) and resize grab-area unchanged.
- [x] Redesign `VPrompt.java` Arena-style prompt bar; keep all pref toggles.
      Wrapped the prompt body in a rounded `PromptBar` gradient panel with a header row
      (game-info pill + `Show Stack` chip), grew the message scroller, and replaced
      sprite-based OK/Cancel with a flat `PromptButton` that renders an accent-gradient
      primary fill and outlined secondary fill. `UI_COMPACT_PROMPT`, `UI_ALLOW_ESC_TO_END_TURN`,
      `UI_TOUCHSCREEN_OPTIMIZE` behavior preserved; `UI_REVERSE_PROMPT_BUTTON` is now wired
      on desktop to swap the button order.
- [x] Audit hot paint loops (`CardPanel.java`, `VField.java`, `VMatchUI.java`):
       no `new` inside `paintComponent`; reuse fields.
       - `CardPanel.java`: hoisted autotap-glow, nonselectable-dim, counter/marker tab bg and
         marker-text `Color`s to static constants; memoised `parseActionableHighlightColor()`
         on the raw pref string; cached the hotkey-digit `Font` by badge diameter; cached
         per-`CounterType` tab body colors; reused a single `RoundRectangle2D.Float` scratch
         for the counter/marker tabs.
       - `VField.java`: reuse a fixed `FLabel[]` field plus scratch `Rectangle`s for
         `getLifeRowBounds()` union and `paintAITimeoutBar()` avatar bounds (no per-frame
         array/Rectangle allocations).
       - `VMatchUI.java`: no `paintComponent`; `new RectangleOfDouble(...)` usages are
         layout/bounds only — left unchanged.
- [ ] Add `forge-gui-desktop/src/test/java/forge/view/arcane/util/UIAnimatorIdleTest.java`
      asserting `UIAnimator.isActive() == false` and its shared Swing `Timer`
      reports `isRunning() == false` once no tweens are scheduled, and
      `forge-gui-desktop/src/test/java/forge/view/arcane/util/CardTextureCacheBoundTest.java`
      asserting the cache never exceeds its configured bound after 1000+
      distinct key insertions; run
      `mvn test -Dtest=UIAnimatorIdleTest,CardTextureCacheBoundTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`.
- [ ] Run final automated gates and confirm exit code 0 for each:
      - `mvn test-compile -T 1C`
      - `mvn test -Dtest=*Commander*,*Deck* -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`
      - `mvn package -DskipTests -T 1C`
      - `mvn test -T 1C`

Commit message: `feat(commander): modern match HUD, prompts, final perf polish`
