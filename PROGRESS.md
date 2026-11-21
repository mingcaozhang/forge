# PROGRESS.md — Iteration 1 (pre-rewrite cleanup)

## Verified baseline facts
- Branch `dev` (from plan commit `399acbbd29a`).
- GATE green after 6.3: desktop TestSuite 2736 run / 12 skipped, 0 failures/errors.
- Desktop surefire quirk: "Results" line is 1 less than the TestSuite line (off-by-one) — normal.
- `Localizer.getMessage` falls back locale → en-US: non-EN key deletion safe. `HomeNavigationRegistryTest` enforces submenu set.

## Completed items
- 1.1 Content Downloader section + 8 download services + i18n gone from all 10 locales.
- 2.1 Release Notes section + Help entry + orphaned `CHANGES_FILE*`/`getFormattedChangelog` + i18n.
- 3.1 How To Play tutorial (`forge.gamemodes.tutorial`, TutorialOverlayPanel, res/tutorial/, howto.txt) + i18n.
- 4.1 Avatar selection UI gone; PlayerPanel shows commander card image (fallback `ICO_PLAYERS`) + i18n.
- 4.2 Match panels show commander image: CMatchUI `getPlayerImage(PlayerView)`; VField `playerImageArea` naming; `IHasIcon` deleted.
- 4.3 Avatar index dropped from lobby/game model (LobbyPlayer/Player/PlayerView/TrackableProperty; LobbySlot 8→7 args;
  avatar params gone from protocol events/handlers; GamePlayerUtil restructured).
- 4.4 Avatar pref/skin/sprite gone: `FPref.UI_AVATARS`, `IGuiBase.getAvatarCount`, all FSkin avatar state, `SPRITE_AVATARS_FILE`, sprite png.
- 5.1 Random-name generator UI gone (PlayerPanel randomiser, VLobby `getNewName`, 6 keys × 10 locales); NameGenerator + auto-naming kept.
- 6.1 Bracket UI gone: views/tests/scrape script deleted; `ColumnDef.DECK_BRACKET`, SColumnUtil 3-arg overload,
    DeckManager/FDeckViewer views, VField bracket tooltip (→ `player.getDetailsHtml()`); 22 keys × 10 locales.
- 6.2 Bracket calculator/caps/protocol gone: calculator + 4 list txts + `COMMANDER_BRACKET_*_FILE`, `FPref.DECKGEN_MAXIMUM_COMMANDER_BRACKET`,
    DeckgenUtil cap, VLobby/CLobby combo+binder, `GameLobbyData` field, FServerManager/AbstractGuiGame accessors.
- 6.3 AI combos data renamed to `res/lists/commander-combos.txt` + `COMMANDER_COMBOS_FILE`; FModel/AiCardLists/ComboCatalog/CommanderPlan
    javadoc reworded; tests updated. All "bracket" java refs now unrelated matches only.
- 7.1 Docs updated for removed avatar/bracket features (User-Guide, FAQ, Network-Play). FAQ content-download line kept (auto-download remains).

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future", `FSkin > can't find skins directory!`.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.

## Active caveats / follow-ups
- i18n key removal: `rg -n "<key>" --type java` zero refs first; the 10 locale files must stay consistent.
- `CardView.getImageKey()` does NOT exist — use `card.getCurrentState().getImageKey()` (ImageCache pattern).
- Per-format banlist validation (`DeckFormat`, `res/formats/**`) is NOT the Commander bracket system — keep.
- Vanguard "avatar" (`DeckSection.Avatar`, `GamePieceType.AVATAR`, `AvatarVanguard`) is unrelated — keep.
- Networking files get minimal field-level edits only (networking removal is a later iteration).
- `res/defaults/home.xml` is legacy (stale HOME_* ids); only item-specific lines get removed.
