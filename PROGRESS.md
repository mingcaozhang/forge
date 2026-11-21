# PROGRESS.md — Iteration 1 (pre-rewrite cleanup)

## Verified baseline facts
- Branch `dev` (from plan commit `399acbbd29a`).
- GATE green after 6.2: desktop TestSuite 2736 run / 12 skipped, 0 failures/errors.
- Desktop surefire quirk: "Results" line is 1 less than the TestSuite line (off-by-one) — normal.
- `Localizer.getMessage` falls back locale → en-US: non-EN key deletion safe. `HomeNavigationRegistryTest` enforces submenu set.

## Completed items
- 1.1 Content Downloader section + 8 download services + i18n gone from all 10 locales.
- 2.1 Release Notes section + Help entry + orphaned `CHANGES_FILE*`/`getFormattedChangelog` + i18n.
- 3.1 How To Play tutorial (`forge.gamemodes.tutorial`, TutorialOverlayPanel, res/tutorial/, howto.txt) + i18n.
- 4.1 Avatar selection UI gone; PlayerPanel shows commander card image (fallback `ICO_PLAYERS`) + i18n.
- 4.2 Match panels show commander image: CMatchUI `getPlayerImage(PlayerView)`; VField `playerImageArea` naming; `IHasIcon` deleted.
- 4.3 Avatar index dropped from lobby/game model (LobbyPlayer/Player/PlayerView/TrackableProperty; LobbySlot 8→7 args;
  avatar params gone from UpdateLobbyPlayerEvent/LoginEvent/FServerManager/GameClientHandler; GamePlayerUtil restructured).
- 4.4 Avatar pref/skin/sprite gone: `FPref.UI_AVATARS`, `IGuiBase.getAvatarCount`, all FSkin avatar state, `SPRITE_AVATARS_FILE`, sprite png.
- 5.1 Random-name generator UI gone (PlayerPanel randomiser, VLobby `getNewName`, 6 keys × 10 locales); NameGenerator + auto-naming kept.
- 6.1 Bracket UI gone: CommanderBracketView/TextView/DeckView + CommanderBracketDataTest + tools/scrape_bracket_details.py deleted;
  `ColumnDef.DECK_BRACKET` removed; SColumnUtil 3-arg `getDecksDefaultColumns` folded to 2-arg (ItemManagerConfig 3 call sites);
  DeckManager/FDeckViewer bracket views removed (FDeckChooser → 1-arg `show`); VField tooltip = plain `player.getDetailsHtml()`;
   22 keys × 10 locales. Remaining `CommanderBracket` refs = 6.3 (combos file + AI javadoc) only.
- 6.2 Bracket calculator/caps/protocol gone: `CommanderBracketCalculator` + 4 bracket list txt files + `COMMANDER_BRACKET_*_FILE` consts (COMBOS_FILE kept for 6.3),
   `FPref.DECKGEN_MAXIMUM_COMMANDER_BRACKET`, DeckgenUtil 5-arg overload + `limitCardsToCommanderBracket`, VLobby bracket combo/binder/frame (start row
   collapsed to pre-bracket layout), CLobby binder wiring, `GameLobbyData.maximumCommanderBracket`, FServerManager/AbstractGuiGame accessors.

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future", `FSkin > can't find skins directory!`.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.

## Active caveats / follow-ups
- i18n key removal: `rg -n "<key>" --type java` zero refs first; the 10 locale files must stay consistent.
- `CardView.getImageKey()` does NOT exist — use `card.getCurrentState().getImageKey()` (ImageCache pattern).
- `res/lists/commander-bracket-combos.txt` feeds AI combo knowledge → keep data, rename in 6.3.
- Per-format banlist validation (`DeckFormat`, `res/formats/**`) is NOT the Commander bracket system — keep.
- Vanguard "avatar" (`DeckSection.Avatar`, `GamePieceType.AVATAR`, `AvatarVanguard`) is unrelated — keep.
- Networking files get minimal field-level edits only (networking removal is a later iteration).
- `res/defaults/home.xml` is legacy (stale HOME_* ids); only item-specific lines get removed.
