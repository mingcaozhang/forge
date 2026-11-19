# Forge — MTG Arena / Hearthstone Commander UI Overhaul

Goal: Make Forge's desktop Swing client feel crisp, readable, and tactile like MTG
Arena / Hearthstone, while preserving **every** existing action, keyboard shortcut,
phase stop, stack interaction, deck-editor filter, preference toggle, legality
check, and `.dck`/Moxfield/Archidekt workflow.

Hard constraints (do not violate):
- ZERO diff in `forge-core`, `forge-game`, `forge-ai`. New code lives only in
  `forge-gui-desktop` (and the FPref enum in `forge-gui`).
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
mvn test -Dtest=*Commander*,*Deck* -DfailIfNoTests=false -T 1C
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
- [ ] Add `forge-gui-desktop/src/test/java/forge/ThemeIntegrationTest.java`
      asserting `com.formdev.flatlaf.FlatDarkLaf` is on the test classpath,
      that the new `FPref.UI_ANIMATION_MODE` and `FPref.UI_UI_THEME` values
      exist with defaults `Normal`/`ArenaDark`, and that
      `UIManager.getLookAndFeel()` returns FlatLaf after a headless-safe
      `FSkin.ForgeLookAndFeel` init path; run
      `mvn test -Dtest=ThemeIntegrationTest -DfailIfNoTests=false -T 1C`.

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
mvn test -Dtest=*Commander*,*Deck* -DfailIfNoTests=false -T 1C
```

- [ ] Implement `forge-gui-desktop/src/main/java/forge/view/arcane/util/Easing.java`.
- [ ] Implement `forge-gui-desktop/src/main/java/forge/view/arcane/util/UIAnimator.java`
      (single shared Swing Timer, auto-stop on empty, honors UI_ANIMATION_MODE).
- [ ] Implement `forge-gui-desktop/src/main/java/forge/view/arcane/util/CardTextureCache.java`
      (LRU + `SoftReference`, bounded, thread-safe).
- [ ] Rewire `Animation.java` internals to delegate to `UIAnimator` while
      keeping the existing public methods for `PlayArea.java:1028`,
      `CardArea.java:326`, `Animation.java:195/256/292`.
- [ ] Add hover/glow + cached rounded-frame paint to
      `forge-gui-desktop/src/main/java/forge/view/arcane/CardPanel.java`.
- [ ] Wire hover enter/exit through `CardPanelContainer.java` so detail
      previews animate in/out smoothly.
- [ ] Replace hand fan/rise in `HandArea.java` with `UIAnimator` tweens.
- [ ] Tune `CardDetailUtil.java` typography constants (UI-only, no logic).
- [ ] Add `forge-gui-desktop/src/test/java/forge/view/arcane/util/UIAnimatorTest.java`
      asserting a submitted tween reaches 100% in its declared duration
      and that the shared Swing `Timer` reports `isRunning() == false`
      once the active set drains to empty; add
      `CardTextureCacheTest.java` asserting bounded LRU eviction at the
      configured cap and that a `clearCache()` call forces recompute;
      run
      `mvn test -Dtest=UIAnimatorTest,CardTextureCacheTest -DfailIfNoTests=false -T 1C`.

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
mvn test -Dtest=*Commander*,*Deck* -DfailIfNoTests=false -T 1C
```

- [ ] Implement `HitFlashOverlay.java`, `DamagePopup.java`, `CastGlow.java`
      under `forge-gui-desktop/src/main/java/forge/view/arcane/effects/`.
- [ ] Add a transparent overlay `JLayer` to the match window in
      `forge-gui-desktop/src/main/java/forge/screens/match/VMatchUI.java`.
- [ ] Hook draw / tap / attack triggers in `CMatchUI.java` to dispatch
      animator effects (no logic changes).
- [ ] Add cast lunge + glow pulse to `VField.java` creature/spell entry.
- [ ] Add Arena-style draw arc and tap pulse in `VHand.java`.
- [ ] Animate stack drawer open/close and per-item fade in `VStack.java`.
- [ ] Animate counter bumps in `VZone.java` (graveyard/exile/library).
- [ ] Soften targeting crosshair pulse in existing TargetingOverlay file.
- [ ] Add `forge-gui-desktop/src/test/java/forge/view/arcane/util/AnimationModeTest.java`
      asserting `UIAnimator` snaps to end for mode `Off`, halves duration
      for `Fast`, and uses full duration for `Normal`; plus
      `forge-gui-desktop/src/test/java/forge/view/arcane/effects/EffectsLifecycleTest.java`
      asserting that scheduling a `HitFlashOverlay`/`DamagePopup`/`CastGlow`
      leaves the active effect list empty and the `UIAnimator` timer stopped
      after its full duration has elapsed; run
      `mvn test -Dtest=AnimationModeTest,EffectsLifecycleTest -DfailIfNoTests=false -T 1C`.

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
mvn test -Dtest=*Commander*,*Deck* -DfailIfNoTests=false -T 1C
```

- [ ] Implement `HomeTile.java`, `RecentDecksCarousel.java`,
      `LastMatchTile.java` under `forge-gui-desktop/src/main/java/forge/screens/home/tiles/`.
- [ ] Refactor `VHomeUI.java` to lay out hero tiles + recent decks carousel.
- [ ] Update `CHomeUI.java` so tile clicks dispatch through existing
      `itemClick(itemEnum)` path; keep `EDocID.HOME_*` behavior intact.
- [ ] Add smooth transition (`UIAnimator` fade+slide) between dashboard and
      submenus without breaking `IVSubmenu` contract.
- [ ] Preserve screen-reader / accessibility path: if
      `FPref.UI_SCREENREADER_OPTIMIZE == true`, fall back to list layout.
- [ ] Add `forge-gui-desktop/src/test/java/forge/screens/home/HomeNavigationRegistryTest.java`
      asserting that the set of `EDocID` values surfaced by the new
      dashboard tiles is exactly the same as the set returned by
      `IVSubmenu.getItemEnum()` across every existing
      `VSubmenu*` class (Play / Decks / Collection / Preferences), and that
      the `EDocID.HOME_CONSTRUCTED` default still resolves; run
      `mvn test -Dtest=HomeNavigationRegistryTest -DfailIfNoTests=false -T 1C`.

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
mvn test -Dtest=*Commander*,*Deck* -DfailIfNoTests=false -T 1C
```

- [ ] Restyle `VDeckEditorUI.java` tabs/panels; keep `editor.xml` layout IDs.
- [ ] Modernize `ItemManager.java` toolbar, search, and view-switch UI.
- [ ] Restyle `ItemListView.java` table (zebra rows, slim scrollbars, header).
- [ ] Upgrade `ImageView.java` card tiles with hover scale/glow via cache.
- [ ] Restyle `VCurrentDeck.java` toolbar/header; keep button accessors intact.
- [ ] Restyle `VAllDecks.java` and `VCardCatalog.java`; preserve auto-edit.
- [ ] Redesign `VStatistics.java` + `CStatistics.java` curve/identity visuals.
- [ ] Add commander/partner showcase header in `VCommanderDecks.java`.
- [ ] Restyle `DeckImport.java` and `DeckFileMenu.java`; keep all actions.
- [ ] Add `forge-gui-desktop/src/test/java/forge/itemmanager/DeckEditorFilterParityTest.java`
      asserting that the existing filter predicates
      (`TextSearchFilter`, `CardFormatFilter`, `CardCMCRangeFilter`,
      `ToggleButtonsFilter`) return the exact same card set after the UI
      refactor as they do on the pre-refactor baseline deck, and that the
      existing `*Deck*` suite continues to pass; run
      `mvn test -Dtest=DeckEditorFilterParityTest,*Deck* -DfailIfNoTests=false -T 1C`.

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
mvn test -Dtest=*Commander*,*Deck* -DfailIfNoTests=false -T 1C
mvn package -DskipTests -T 1C
mvn test -T 1C
```

- [ ] Restyle player panels / life counters in `VMatchUI.java`.
- [ ] Style unified phase label pill (existing `UI_UNIFIED_PHASE_LABEL`).
- [ ] Slim zone tab strip and stack/log column in `VMatchUI.java`.
- [ ] Restyle floating zone frames in `CMatchUI.java` (no behavior change).
- [ ] Redesign `VPrompt.java` Arena-style prompt bar; keep all pref toggles.
- [ ] Audit hot paint loops (`CardPanel.java`, `VField.java`, `VMatchUI.java`):
      no `new` inside `paintComponent`; reuse fields.
- [ ] Add `forge-gui-desktop/src/test/java/forge/view/arcane/util/UIAnimatorIdleTest.java`
      asserting `UIAnimator.isActive() == false` and its shared Swing `Timer`
      reports `isRunning() == false` once no tweens are scheduled, and
      `forge-gui-desktop/src/test/java/forge/view/arcane/util/CardTextureCacheBoundTest.java`
      asserting the cache never exceeds its configured bound after 1000+
      distinct key insertions; run
      `mvn test -Dtest=UIAnimatorIdleTest,CardTextureCacheBoundTest -DfailIfNoTests=false -T 1C`.
- [ ] Run final automated gates and confirm exit code 0 for each:
      - `mvn test-compile -T 1C`
      - `mvn test -Dtest=*Commander*,*Deck* -DfailIfNoTests=false -T 1C`
      - `mvn package -DskipTests -T 1C`
      - `mvn test -T 1C`

Commit message: `feat(commander): modern match HUD, prompts, final perf polish`
