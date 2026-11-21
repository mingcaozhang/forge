# PLAN.md — Pre-Rewrite Cleanup, Iteration 1: Remove Unused Features

## Context & Goal
Per `REWRITE.md`, this cleanup trims the JVM baseline BEFORE the rewrite phases so less code is ported. End state: a **single-player Commander (EDH)** experience (no Brawl/Oathbreaker/Tiny Leaders/Commander variants; those come in later iterations).

**Iteration 1 removes:** How To Play tutorial, avatars (replaced by commander card image), release notes, bracket/banlist validation, Content Downloader section, player name generator — plus all preference options and UI elements for them.

**KEEP (do not touch):** core Commander rules/gameplay, AI players, game UI/navigation, preferences for surviving features, deck editor + saved decks, in-turn undo, "Provide Deck URL", Moxfield/Archidekt import, card art fetching, deck validation, audio.

**Networking note:** networking is removed in a LATER iteration and may return after the rewrite. This iteration makes only minimal field-level edits in networking files (avatar/bracket fields); no restructuring, no single-player assumptions baked into shared seams.

## Confirmed Decisions
1. Remove BOTH the interactive tutorial and the static `res/howto.txt` doc (stale 1v1-vs-computer intro) + its Help-menu entry.
2. Keep `DialogDownloadCardImages` + the first-run bulk-CDN-sync prompt in `FView` (card-art UX); reword the prompt text that references the removed section.
3. Lobby slot panels show the slot's **selected deck's commander card image**, falling back to the generic player icon (`FSkinProp.ICO_PLAYERS`) when no deck is selected.
4. "Bracket/banlist validation" = the Commander **bracket** (EDHREC-style skill-tier) system. Per-format **banlist** validation (`GameFormat.bannedCardNames`, `DeckFormat.isLegalCard/isLegalCommander`, `DeckRecognizer.isBannedInFormat`, `res/formats/**`) is separate and STAYS.
5. `res/lists/commander-bracket-combos.txt` feeds AI two-card-combo knowledge (2,176 pairs via `FModel` → `AiCardLists`); AI is a KEEP feature → keep the data, rename to `commander-combos.txt` (+ constant `COMMANDER_COMBOS_FILE`).

## Hard KEEP Lists (card-art / shared infra)
- Card art: `ImageFetcher`, `SwingImageFetcher`, `CdnUuidCache`, `ScryfallSetSync`, `ScryfallBulkDataSync`, `ScryfallRateLimiter`, `GuiDownloadFilteredCardImages`, `GuiDownloadService`, `GuiDownloadZipService`, `GuiDownloader` (forge-gui-desktop), `DialogDownloadCardImages`, `FView` first-run prompt (L239-250).
- App update: `AutoUpdater`, `RSSReader`, `GITHUB_*`/`RELEASE_URL` constants, `FControl`/`FTitleBarBase` updater paths, `CSubmenuPreferences` auto-updater channel combo.
- Vanguard "avatar" (unrelated feature): `DeckSection.Avatar`, `GamePieceType.AVATAR`, `FSkinProp.IMG_ZONE_AVATAR`, `LobbySlot.AvatarVanguard`, vanguard i18n keys, `CEditorConstructed` avatar pool.
- Core deck validation (all of it, see decision 4), sleeves (`FPref.UI_SLEEVES` + sleeve UI), audio, `ImportDialog`, `BugReporter`, bundled `README.txt`/`CHANGES.txt` + `git-changelog-maven-plugin` packaging.
- `NameGenerator` class + auto-naming (`GameLobby.randomName`, `GuiDisplayUtil.getRandomAiName`, `PlayerPanel` auto-fill at L296/L695) + manual `PLAYER_NAME` pref (only the visible generator UI goes).
- `GameType.Puzzle` core remnants (`GameType`, `DeckFormat.Puzzle`, `GameAction`, `GameState` puzzle parsing, `PuzzleAchievements`, `TotalPuzzlesSolved`, `lblPuzzle*`/`lblRestartPuzzle`/`lblQuitPuzzle` keys) — separate mode, follow-up iteration.
- Core `Match.startGame(Game, Runnable)` / `GameAction.startGame(...)` / `PhaseHandler.setupFirstTurn/startFirstTurn(..., Runnable)` hook params — generic seam, keep.
- `IGuiBase.isMatchInProgress()` (used by `ReplayLauncher`).

## Git & Execution Rules
- Work locally on **`dev`** (currently on `trim_code`; both point at `aadb87daeaa` with a clean tree — check out `dev` first).
- **Atomic local commit per completed `- [ ]` item**: code changes + `PLAN.md` (`[ ]`→`[x]`) + `PROGRESS.md` (≤ 40 lines, next to `PLAN.md`) in ONE commit. Commit style: `chore(commander): <short summary>`.
- **NEVER** `git push`, **NEVER** `git reset --hard`, no destructive commands outside the workspace.
- Before deleting/renaming any symbol, file, i18n key, or menu entry, search all references first (`rg`); for i18n keys, `rg -n "<key>" --type java` must show zero remaining references before removal.
- All Maven commands run with a **600000 ms** timeout (see `forge-test-runner` skill).
- Scratch/test artifacts only under `/tmp/opencode/` (e.g. `/tmp/opencode/forge-ui-e2e`).

## Verification Commands
- `GATE` — `mvn test -pl forge-gui-desktop -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms; builds+tests forge-core, forge-game, forge-ai, forge-gui, forge-gui-desktop)
- `PKG` — `mvn package -DskipTests -T 1C` (600000 ms)
- `FULL` — `mvn test -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms)

---

## Phase 0 — Baseline

- [x] **0.1 Check out `dev` and verify a green baseline**
  - Targets: repo root. `git checkout dev && git status --short` (expect clean; `dev` == `trim_code` == `aadb87daeaa`).
  - Verify: run `PKG`, then `GATE`. Record baseline results + any environment quirks in `PROGRESS.md`.
  - Commit (PLAN.md + PROGRESS.md creation only): `chore(commander): add iteration-1 cleanup plan`.

## Phase 1 — Content Downloader Section

- [x] **1.1 Remove Content Downloader home section + bulk download services**
  - Delete files: `forge-gui-desktop/src/main/java/forge/screens/home/settings/CSubmenuDownloaders.java`, `.../VSubmenuDownloaders.java`; `forge-gui/src/main/java/forge/gui/download/GuiDownloadPicturesLQ.java`, `GuiDownloadPicturesHQ.java`, `GuiDownloadSetPicturesLQ.java`, `GuiDownloadQuestImages.java`, `GuiDownloadAchievementImages.java`, `GuiDownloadPrices.java`, `GuiDownloadSkins.java`; tests `forge-gui-desktop/src/test/java/forge/GuiDownloadPicturesLQTest.java`, `GuiDownloadSetPicturesLQTest.java`.
  - Surgical: `EDocID.java` (import L18 + `HOME_UTILITIES` L47); `VHomeUI.java` (import L56 + registration L152; hero tiles generate generically from `allSubmenus`); `forge-gui/res/defaults/home.xml` (remove `<doc>HOME_UTILITIES</doc>`, legacy file); `HomeNavigationRegistryTest.java` (L48 + `HOME_UTILITIES` mapping L68).
  - i18n (all 10 files in `forge-gui/res/languages/`): delete section keys — `btnDownloadSetPics`, `btnDownloadPicsHQ`, `btnDownloadPics`, `btnDownloadQuestImages`, `btnDownloadAchievementImages`, `lblProcessingCards`, `lblCardAudit`, `btnListImageData`, `lblListImageData`, `btnImportPictures`, `btnHowToPlay`, `btnDownloadPrices`, `btnDownloadSkins`, `btnDownloadCJKFonts`, `btnLicensing`, `lblCheckForUpdates`, `lblDownloadPics`, `lblDownloadPicsHQ`, `lblDownloadSetPics`, `lblDownloadQuestImages`, `lblDownloadAchievementImages`, `lblDownloadPrices`, `lblDownloadSkins`, `lblDownloadCJKFonts`, `lblLicensing`, `ContentDownloaders`, `lblReportBug` (only if `rg` shows zero Java refs), `lblHowToPlay` (all occurrences), and the dead Java-version keys `lblYourVersionOfJavaIsTooOld` + neighbors. **Keep**: `btnCheckForUpdates`, `btnReportBug` (if referenced), `btnDownloadCardImages`, `lblDownloaded`, `lblMissing`, `btnClearCdnImageCache`, `btnSyncBulkCardData`, `lblFirstRunBulkCdnPrompt` (reworded below).
  - Reword `lblFirstRunBulkCdnPrompt` (en-US L364): drop the "Download Content > Download Card Images" path reference (e.g. "Forge can pre-download card image data to speed up card art loading. Download it now?"); in the other 9 locales delete the stale key line so `Localizer` falls back to English.
  - Do NOT touch the KEEP lists above (AutoUpdater, GuiDownloader, card-art pipeline, ImportDialog, BugReporter, CSubmenuPreferences card-art settings).
  - Verify: `GATE`; `rg -n "VSubmenuDownloaders|CSubmenuDownloaders|GuiDownloadPictures|GuiDownloadSetPictures|GuiDownloadQuestImages|GuiDownloadAchievementImages|GuiDownloadPrices|GuiDownloadSkins" -g '!target/**'` → zero.
  - Commit: `chore(commander): remove Content Downloader section and bulk download services`.

## Phase 2 — Release Notes

- [x] **2.1 Remove Release Notes section + Help menu entry**
  - Delete: `forge-gui-desktop/src/main/java/forge/screens/home/settings/CSubmenuReleaseNotes.java`, `.../VSubmenuReleaseNotes.java`.
  - Surgical: `EDocID.java` (import L20 + `HOME_RELEASE_NOTES` L51); `VHomeUI.java` (import L58, registration L153, hero-icon case L253-254); `HelpMenu.java` (L38 `menu.add(...)` + `getMenuItem_ReleaseNotes()` L130-135; keep shared file-open helpers); `HomeNavigationRegistryTest.java` (L49 + mapping L69); `ForgeConstants.java` (`CHANGES_FILE` L85 + `CHANGES_FILE_NO_RELEASE` L86 — after `rg` confirms no remaining refs); `forge-core .../util/TextUtil.java` (`getFormattedChangelog` ~L400 — sole caller was `CSubmenuReleaseNotes`; verify with `rg` first).
  - i18n (10 files): delete `ReleaseNotes` + `lblReleaseNotes`.
  - Do NOT touch: bundled `README.txt`/`CHANGES.txt` and the `git-changelog-maven-plugin` block in `forge-gui-desktop/pom.xml` (release packaging).
  - Verify: `GATE`; `rg -n -i "releasenotes|release.notes" --type java -g '!target/**'` → zero.
  - Commit: `chore(commander): remove Release Notes section and Help menu entry`.

## Phase 3 — How To Play Tutorial

- [x] **3.1 Remove How To Play tutorial + howto.txt**
  - Delete files: entire `forge-gui/src/main/java/forge/gamemodes/tutorial/` package (`HowToPlay`, `Puzzle`, `PuzzleIO`, `TutorialDirector`, `TutorialGameController`, `TutorialLayout`, `TutorialTarget`); `forge-gui-desktop/src/main/java/forge/screens/match/TutorialOverlayPanel.java`; `forge-gui/res/tutorial/` (dir + `HowToPlay.pzl`); `forge-gui/res/howto.txt`.
  - Surgical:
    - `IGuiBase.java`: imports L4-5 + defaults `showCoach` (L43), `hideCoach` (L47), `isTutorialBoardReady` (L53), `getTutorialLayout` (L58), `highlightGui` (L65). KEEP `isMatchInProgress()`.
    - `GuiDesktop.java`: import L33; `currentMatchUI` field + assignment (L373-374, L379 — the `getNewGuiGame` method itself stays); method bodies L383-416.
    - `CMatchUI.java`: `tutorialOverlay` field + `getTutorialOverlay()` (L370-378), `getTutorialDoc` (L380-383), `showTutorialCoach` (L385-388), `hideTutorialCoach` (L390-395), `highlightTutorial` (L397-402), `tutorialOverlay.detach()` in `afterGameEnd` (L1349-1351).
    - `ViewWinLose.java`: import L20, `tutorial` boolean (L109); collapse ternaries, KEEP the `GameType.Puzzle` branch (`lblRestartPuzzle`/`lblQuitPuzzle`).
    - `VHomeUI.java`: import L44, `lblHowToPlay` field (L118-122), `howToPlayHeight` (L131) + fix `pnlMainMenuHeight` math (L132), panel add (L136-137).
    - `HelpMenu.java`: L64 `mnu.add(getMenuItem_HowToPlayFile())` + method L77-82; keep "Getting Started" submenu with the Forge Wiki link.
    - `HostedMatch.java`: `startGameHook`/`endGameHook` fields (L53-54), setters (L67-70), uses (L290 → `match.startGame(currentGame, null)`; delete L298-299). KEEP core `Match`/`GameAction`/`PhaseHandler` Runnable hook params (generic seam).
    - `ForgeConstants.java`: `HOWTO_FILE` (L88), `TUTORIAL_DIR` (L101), `USER_PUZZLE_DIR` (L219 — sole consumer `Puzzle.java` is deleted; verify with `rg`).
  - i18n (10 files): delete every `msgTut*`/`lblTut*`/`btnTut*` key (en-US: block L3852-4063; each other locale: 99-key end-of-file block), `lblHowToPlayTooltip`, all `lblHowToPlay`, `lblHowtoPlay` (en-US L495), orphaned `lblTutorial`/`lblTutorialMode` + stale `#VSubmenuTutorial` comments (9 non-EN files only). **KEEP in every file**: `lblRestartPuzzle`, `lblQuitPuzzle`.
  - Out of scope: `GameType.Puzzle` core remnants (see Hard KEEP Lists).
  - Verify: `GATE`; `rg -n -i "howtoplay|tutorial" --type java -g '!target/**'` → only `lblTutor` (dev-mode key) + "Combat Tutorial" card data remain.
  - Commit: `chore(commander): remove How To Play tutorial and howto.txt`.

## Phase 4 — Avatars → Commander Card Image

- [x] **4.1 Remove avatar selection UI + home submenu; lobby slots show deck commander image**
  - Delete: `forge-gui-desktop .../screens/home/settings/VSubmenuAvatars.java`, `.../CSubmenuAvatars.java`, `.../screens/home/sanctioned/AvatarSelector.java`.
  - Surgical: `EDocID.java` (import L17 + `HOME_AVATARS` L46); `VHomeUI.java` (import L55, registration L151, hero-icon case L251-252); `home.xml` (remove `<doc>HOME_AVATARS</doc>`); `HomeNavigationRegistryTest.java` (L47 + mapping L67).
  - `PlayerPanel.java`: replace `createAvatar()` (L717-759) with a player-image label: `slot.getDeck()` non-null and `deck.getCommanders()` non-empty → `ImageCache.getIcon(deck.getCommanders().get(0).getImageKey())`, else `FSkin.n(FSkinProp.ICO_PLAYERS)`; refresh the icon in `update()` (L237); remove `avatarIndex` field (L73), `setAvatarIndex` call (L228), avatar-icon lines in `update()` (L238-240), avatar-focus listeners (L215-219, L300, L332-338), `focusOnAvatar()` (L491-493), `setRandomAvatar` (L858-874), `getAvatarIndex`/`setAvatarIndex` (L909-917), `AvatarSelector` import (L41); move the focus border (L898) from `avatarLabel` to the panel/player-image label.
  - `VLobby.java`: remove `panel.setAvatarIndex(...)` (L312), `updateAvatarPrefs()` (L888-895), `getUsedAvatars()` (L911-917).
  - `VSubmenuOnlineLobby.java`: delete the `lobby.focusOnAvatar()` listener line (L170) — minimal networking edit.
  - i18n (10 files): delete `lblAvatars`, `lblSelectAvatarFor`, `ttlblAvatar`.
  - Verify: `GATE`; `rg -n "AvatarSelector|VSubmenuAvatars|CSubmenuAvatars|focusOnAvatar|setRandomAvatar|updateAvatarPrefs" --type java -g '!target/**'` → zero.
  - Commit: `chore(commander): remove avatar selection; lobby slots show deck commander image`.

- [x] **4.2 Match UI: player panels show commander card image; drop avatar image API**
  - `CMatchUI.java`: remove `avatarImages` map (L180); replace `getPlayerAvatar(PlayerView,int)` (L417-424) with `getPlayerImage(PlayerView p)` — `p.getCommanders()` non-empty → `ImageCache.getIcon(p.getCommanders().get(0).getImageKey())`, else `FSkin.n(FSkinProp.ICO_PLAYERS)`; in `initMatch` drop the `FPref.UI_AVATARS` split (L438) and call `f.setPlayerImage(getPlayerImage(p))` (L454); remove `setPlayerAvatar(LobbyPlayer,IHasIcon)` (L1415-1418); `addSmallImageToStackModalPanel` (L1763-1768) → `getPlayerImage`; L1865 `getAvatarArea()` → `getPlayerImageArea()`.
  - `VField.java`: rename `avatarArea`→`playerImageArea`, `getAvatarArea()`→`getPlayerImageArea()`, `setAvatar(SkinImage)`→`setPlayerImage(SkinImage)`, `lblAvatar`→`lblPlayerImage`, `updateAvatarBorder()`→`updatePlayerImageBorder()`. The anchor panel STAYS (player select, full-control menu, targeting arrows, life row, AI thinking bar all hang off it).
  - `CField.java` (L136), `TargetingOverlay.java` (L395-402, L468): use `getPlayerImageArea()`.
  - `VAssignCombatDamage.java` (L270-271), `VAssignGenericAmount.java` (L211-212): `matchUI.getPlayerImage(p)` (MiscCardPanel signature unchanged).
  - `IGuiGame.java`: remove `IHasIcon` import (L16) + `setPlayerAvatar` (L279); `ProtocolGuiGame.java`: remove import (L15) + empty override (L491-494); DELETE `forge-game/src/main/java/forge/game/player/IHasIcon.java`; test `HeadlessNetworkGuiGame.java`: remove import (L15) + override (L169).
  - Verify: `GATE`; `rg -n "avatarImages|getPlayerAvatar|setPlayerAvatar|IHasIcon|getAvatarArea|updateAvatarBorder" --type java -g '!target/**'` → zero.
  - Commit: `chore(commander): match player panels show commander card image`.

- [x] **4.3 Drop avatar index from lobby/game data model**
  - `LobbyPlayer.java` (forge-core): remove `avatarIndex` + `avatarCardImageKey` fields/getters/setters (L15, L17, L60-78).
  - `Player.java` (forge-game): delete `updateAvatar()` (L2693-2697); in `dangerouslySetController` (L2687) keep the `view.setHasLost(false)` effect inline.
  - `PlayerView.java`: remove `getAvatarIndex`/`updateAvatarIndex`/`getAvatarCardImageKey`/`updateAvatarCardImageKey` (L74-86).
  - `TrackableProperty.java`: remove `AvatarIndex` (L158) + `AvatarCardImageKey` (L159).
  - `LobbySlot.java` (forge-gui): remove `avatarIndex` field/ctor param/assign/`apply()` line/getter/setter (L19, L33, L36, L49, L100-108); update every ctor call site found via `rg -n "new LobbySlot(" --type java`.
  - `LocalLobby.java`: remove `avatarIndices` (L13, L16, L19).
  - `GameLobby.java`: remove `localAvatarIndices()` (L308-310) + avatar args at `createAiPlayer`/`getGuiPlayer` (L614, L624, L631).
  - `HostedMatch.java`: remove `avatarIndices` (L182) + per-player avatar fallback block incl. `p.updateAvatar()` (L192-200).
  - `UpdateLobbyPlayerEvent.java`: remove `avatarIndex` field/`create()` param/ctor args/getter/`toString` part (L20, L36-37, L84, L114, L123, L163-164, L216).
  - `GamePlayerUtil.java`: drop avatar params from `getGuiPlayer` (L26-40) + `createAiPlayer` overloads (L49-71), remove `getAvatarCount()` use + `player.setAvatarIndex` (L97).
  - `LobbyPlayerHuman.java`: simplify ctors (L15-22).
  - Verify: `GATE`; `rg -n "avatarIndex|AvatarIndex|avatarCardImageKey|AvatarCardImageKey" --type java -g '!target/**'` → zero (except `AvatarVanguard`).
  - Commit: `chore(commander): drop avatar index from lobby/game data model`.

- [x] **4.4 Remove avatar preference, skin assembly, and sprite file**
  - `ForgePreferences.java`: remove `FPref.UI_AVATARS` (L68).
  - `IGuiBase.java`: remove `getAvatarCount()` (L77); `GuiDesktop.java`: remove the override (L258-264).
  - `FSkin.java`: remove `avatars` map (L945), `bimDefaultAvatars`/`bimPreferredAvatars` (L1135-1138), `f4`/`f5` (L1265-1266), image reads (L1305-1306, L1327), `assembleAvatars()` call (L1411-1412), flushes (L1425, L1441), null-outs (L1447, L1454), `getAvatars()` (L1520-1522), `assembleAvatars()` method (L1597-1636).
  - `ForgeConstants.java`: remove `SPRITE_AVATARS_FILE` (L128); DELETE `forge-gui/res/skins/default/sprite_avatars.png`.
  - Verify: `GATE`; `rg -n "UI_AVATARS|getAvatars|SPRITE_AVATARS|getAvatarCount" --type java -g '!target/**'` → zero; `rg -n -i "avatar" --type java -g '!target/**'` → only Vanguard/card-name matches remain.
  - Commit: `chore(commander): remove avatar preference, skin assembly, and sprite`.

## Phase 5 — Player Name Generator

- [x] **5.1 Remove random-name generator UI (keep auto-naming)**
  - `PlayerPanel.java`: remove `nameRandomiser` field (L70) + layout add (L154-155) + enable (L252) + `createNameRandomizer()` (L664-684).
  - `VLobby.java`: remove `genderOptions`/`typeOptions` (L927-928) + `getNewName()` (L929-953); drop the `NameGenerator` import if now unused.
  - i18n (10 files): delete `lblGetNewRandomName`, `lbltypeofName`, `lblconfirmName`, `lblUseThisName`, `lblTryAgain`, `lblGetaNewRandomName`.
  - KEEP: `forge-core .../util/NameGenerator.java`, `GameLobby.randomName()` (L298-304), `GuiDisplayUtil.getRandomAiName()` (L37-39), `PlayerPanel` auto-naming (L296, L695), manual `PLAYER_NAME` pref + its Preferences-screen UI.
  - Verify: `GATE`; `rg -n "getNewName|createNameRandomizer|nameRandomiser" --type java -g '!target/**'` → zero.
  - Commit: `chore(commander): remove random name generator UI`.

## Phase 6 — Commander Bracket

- [x] **6.1 Remove bracket UI surfaces (deck views, column, in-game tooltip)**
  - Delete: `forge-gui-desktop .../itemmanager/views/CommanderBracketView.java`, `CommanderBracketTextView.java`, `CommanderBracketDeckView.java`; test `forge-gui-desktop/src/test/java/forge/deck/CommanderBracketDataTest.java`; `forge-gui/tools/scrape_bracket_details.py`.
  - `ColumnDef.java`: remove import (L41) + `DECK_BRACKET` constant (L323-331).
  - `SColumnUtil.java`: fold 3-arg `getDecksDefaultColumns` into the 2-arg form (L250-284), drop `includeBracket` block (L267-269).
  - `ItemManagerConfig.java`: drop the trailing `true` arg (L96, L120, L136).
  - `DeckManager.java`: remove import (L16) + `addView(new CommanderBracketView(...))` (L75-77) + now-unused `DeckFormat` import (L23).
  - `FDeckViewer.java`: remove import (L16) + 2-arg `show(Deck, boolean)` (L59-75), keep 1-arg `show`.
  - `FDeckChooser.java` (L100): → `FDeckViewer.show(getDeck())`; KEEP its `DeckFormat` import (used L142/187/203).
  - `VField.java`: remove import (L43) + `commanderBracketTooltip*` fields (L91-92); L633 → `setToolTipText(player.getDetailsHtml())`; delete `getPlayerDetailsHtml()` + `getCommanderBracketTooltipLine()` (L636-676).
  - i18n (10 files): delete the 22 bracket keys — `lblBracket`, `ttCommanderBracket`, `lblBracketView`, `lblCommanderBracketMinimum`, `lblCommanderBracketSelectDeck`, `lblCommanderBracketGameChangers`, `lblCommanderBracketMassLandDenial`, `lblCommanderBracketExtraTurns`, `lblCommanderBracketChainedExtraTurns`, `lblCommanderBracketTwoCardCombos`, `lblCommanderBracketLateGame`, `lblCommanderBracketEarlyGame`, `lblCommanderBracketReasonGameChangersFour`, `...ReasonGameChangersOne`, `...ReasonMassLandDenial`, `...ReasonChainedExtraTurn`, `...ReasonExtraTurnsFour`, `...ReasonExtraTurnsThree`, `...ReasonExtraTurnsTwo`, `...ReasonExtraTurnsFew`, `...ReasonLateGameCombo`, `...ReasonEarlyGameCombo` (en-US L3759-3780; per-locale blocks in the same relative area).
  - Verify: `GATE`; `rg -n "CommanderBracket" --type java -g '!target/**'` → remaining only `CommanderBracketCalculator`, `DeckgenUtil`, `FModel`, `AiCardLists` javadoc (items 6.2/6.3).
  - Commit: `chore(commander): remove bracket UI views, column, and in-game tooltip`.

- [x] **6.2 Remove bracket calculator, setting, lobby cap, AI deckgen cap, protocol field**
  - Delete `forge-gui/src/main/java/forge/deck/CommanderBracketCalculator.java`.
  - Delete `forge-gui/res/lists/`: `gamechangers.txt`, `mass-land-denial.txt`, `extra-turns.txt`, `chained-extra-turns.txt` (KEEP `commander-bracket-combos.txt` — item 6.3).
  - `ForgeConstants.java`: remove the 4 `COMMANDER_BRACKET_*_FILE` list constants (L80-83); KEEP `COMMANDER_BRACKET_COMBOS_FILE` (L76) until 6.3.
  - `ForgePreferences.java`: remove `FPref.DECKGEN_MAXIMUM_COMMANDER_BRACKET` (L248).
  - `DeckgenUtil.java`: merge overloads (L591-596) dropping `maxBracket` + pref read; remove `limitCardsToCommanderBracket` calls (L627, L660, L676, L690) + method (L727-748).
  - `VLobby.java`: remove side-width constants (L54-55), combo/binder/frame fields (L69-71), frame setup with hardcoded "Maximum Bracket:" label (L179-181), collapse the two now-identical branches in `addConstructedStartControls` (L202-215), delete `getMaximumCommanderBracketBinder()` (L1144-1147).
  - `CLobby.java`: remove binder load (L66) + action listener (L84-86).
  - `GameLobby.java`: remove `GameLobbyData.maximumCommanderBracket` field (L753) + getter/setter (L783-788) (protocol change; same-version clients only — acceptable, networking is removed later).
  - `FServerManager.java`: remove `setMaximumCommanderBracket(...)` (L502-503) + now-unused `FPref` import (L25).
  - `AbstractGuiGame.java`: remove `getMaximumCommanderBracket()` (L135-139) + now-unused `FPref` import (L25).
  - Verify: `GATE`; `rg -n -i "bracket" --type java -g '!target/**'` → remaining only `COMMANDER_BRACKET_COMBOS_FILE` + `FModel` supplier + `forge-ai` javadoc (item 6.3) + unrelated matches (`DeckSerializer.enclosedBracket`, `Tracker` "bracket a region", `CardDetailUtil` "Bracket P/T", test square-bracket parsing).
  - Commit: `chore(commander): remove bracket calculator, lobby cap, deckgen cap, and protocol field`.

- [ ] **6.3 Rename AI combos data file + constant (keep AI knowledge)**
  - `git mv forge-gui/res/lists/commander-bracket-combos.txt forge-gui/res/lists/commander-combos.txt`.
  - `ForgeConstants.java`: `COMMANDER_BRACKET_COMBOS_FILE` → `COMMANDER_COMBOS_FILE` (L76).
  - `FModel.java` (L261-265): update supplier + comment.
  - `forge-ai`: reword "bracket" in javadoc — `AiCardLists.java` (L24-27), `ComboCatalog.java` (L284), `CommanderPlan.java` (L74); update `AiCardListsTest.java` (L35) + `AiCardListsDataTest.java` (L92-129) to the new path; optionally reword the header comment in `res/lists/ai-commander-combos.txt`.
  - Verify: `mvn test -pl forge-ai -am -Dtest=AiCardListsTest,AiCardListsDataTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` (600000 ms), then `GATE`; `rg -n -i "bracket" --type java --type py -g '!target/**'` → only the unrelated matches above.
  - Commit: `chore(commander): rename bracket combos file to commander-combos.txt for AI`.

## Phase 7 — Docs

- [ ] **7.1 Update docs for removed features**
  - `docs/User-Guide.md` L221: "Right click/long tap on your player avatar" → right-click on the player panel; L252: "Avatar and life total" → "Player image (commander) and life total" and drop ", when a Commander bracket cap is active, estimated Commander bracket".
  - `docs/Frequently-Asked-Questions.md` L71: "click on the player's Avatar in the Player Panel" → "click on the player's panel in the match view".
  - `docs/Network-Play.md` L61: "decks, sleeves, and avatars" → "decks and sleeves" (file is removed entirely in the later networking iteration).
  - Verify: `mvn test-compile -T 1C` (600000 ms; docs-only change, compile gate for consistency) + review `git diff -- docs/`.
  - Commit: `docs(commander): update docs for removed avatar/bracket features`.

## Phase 8 — UI Smoke

- [ ] **8.1 UI e2e verification (forge-swing-ui-e2e)**
  - Build: `PKG` (600000 ms), then run the `forge-swing-ui-e2e` skill (build jar, launch with `-javaagent` EDT inspection agent, scratch dir `/tmp/opencode/forge-ui-e2e`).
  - Verify in the live UI:
    - Home screen: no How To Play panel; no Avatars / Content Downloaders / Release Notes hero tiles.
    - Help menu: no "How To Play (file)" or "Release Notes" entries.
    - Lobby: each slot panel shows the selected deck's commander card image; generic player icon when no deck selected.
    - A single-player Commander game starts and plays a few turns without exceptions; match-view player panel shows the commander card image.
  - Verify: scan the run log for `Exception|Error` and review every match (expect zero new).
  - Commit (PLAN.md + PROGRESS.md update only): `chore(commander): record UI smoke verification for iteration 1`.

## Phase 9 — Final Gate

- [ ] **9.1 Full build + test + orphan sweep**
  - Run `PKG` (600000 ms), then `FULL` (600000 ms); both must exit 0.
  - Orphan i18n sweep: per locale file, diff keys present vs. keys referenced in Java; remove only orphans created by this iteration (the 6 features); leave pre-existing quest/gauntlet/puzzle orphans — list them in `PROGRESS.md` as follow-ups.
  - Final grep sweep: `rg -n -i "avatar|bracket|releasenotes|howtoplay|getnewrandomname|contentdownloaders" --type java -g '!target/**'` → only whitelisted matches (Vanguard, card names, `lblRestartPuzzle`/`lblQuitPuzzle`, `DeckSerializer.enclosedBracket`, `Tracker` "bracket a region", `CardDetailUtil` "Bracket P/T").
  - Update `PROGRESS.md`: iteration 1 done, caveats/follow-ups recorded.
  - Commit: `chore(commander): finalize iteration-1 cleanup (final gate + plan update)`.

## Follow-ups (explicitly out of scope for iteration 1)
- Networking / hosted games / replays removal (separate iteration; current edits to networking files are minimal field-level only).
- `GameType.Puzzle`, `DeckFormat.Puzzle`, puzzle achievements, `GameAction`/`GameState` puzzle parsing, remaining puzzle i18n keys.
- Orphaned `FPref.SUBMENU_*` quest/gauntlet keys; legacy `res/defaults/home.xml` (delete outright or leave — decide then).
- Brawl / Oathbreaker / Tiny Leaders / Commander-variant menus (later Commander-only iteration).
- Leftover `lblTutor` dev-mode key.
