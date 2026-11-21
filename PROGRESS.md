# PROGRESS.md — Iteration 1 (pre-rewrite cleanup)

## Verified baseline facts
- Branch `dev` (from plan commit `399acbbd29a`).
- Final gate (9.1): PKG + FULL green, 2735 run / 11 skipped, 0 failures/errors, all 6 modules SUCCESS.
- Desktop surefire: "Results" line is 1 less than TestSuite line (off-by-one) — normal. `Localizer` falls back locale → en-US (non-EN key deletion safe); `HomeNavigationRegistryTest` enforces submenu set.

## Completed items
- 1.1 Content Downloader section + 8 download services + i18n gone from all 10 locales.
- 2.1 Release Notes section + Help entry + orphaned `CHANGES_FILE*`/`getFormattedChangelog` + i18n.
- 3.1 How To Play tutorial (`forge.gamemodes.tutorial`, TutorialOverlayPanel, res/tutorial/, howto.txt) + i18n.
- 4.1 Avatar selection UI gone; PlayerPanel shows commander card image (fallback `ICO_PLAYERS`) + i18n.
- 4.2 Match panels show commander image: CMatchUI `getPlayerImage(PlayerView)`; VField `playerImageArea` naming; `IHasIcon` deleted.
- 4.3 Avatar index dropped from lobby/game model (LobbyPlayer/Player/PlayerView/TrackableProperty; LobbySlot 8→7 args; protocol events/handlers; GamePlayerUtil).
- 4.4 Avatar pref/skin/sprite gone: `FPref.UI_AVATARS`, `IGuiBase.getAvatarCount`, all FSkin avatar state, `SPRITE_AVATARS_FILE`, sprite png.
- 5.1 Random-name generator UI gone (PlayerPanel randomiser, VLobby `getNewName`, 6 keys × 10 locales); NameGenerator + auto-naming kept.
- 6.1 Bracket UI gone: views/tests/scrape, `ColumnDef.DECK_BRACKET`, SColumnUtil 3-arg, DeckManager/FDeckViewer views, VField tooltip (→ `getDetailsHtml()`), 22 keys × 10 locales.
- 6.2 Bracket calculator/caps/protocol gone (calculator, 4 list txts, `COMMANDER_BRACKET_*_FILE`, Deckgen cap, lobby binder, `GameLobbyData` field, FServer/AbstractGui accessors).
- 6.3 AI combos renamed to `res/lists/commander-combos.txt` + `COMMANDER_COMBOS_FILE`; FModel/AI javadoc reworded; tests updated; "bracket" refs now unrelated only.
- 7.1 Docs updated for removed avatar/bracket features (User-Guide, FAQ, Network-Play). FAQ content-download line kept (auto-download remains).
- 8.1 UI e2e (scratch `/tmp/opencode/forge-ui-e2e`): hero tiles = CONSTRUCTED/PREFERENCES/ACHIEVEMENTS only; no How To Play/Release Notes; CDN prompt reworded + "Not Now" persists;
  lobby + match field show commander art (generic w/o deck); 3+ turn game, zero new exceptions. E2e fix: match image set only at init → CMatchUI.updateZones refreshes on Command-zone updates.
- 9.1 Final gate: PKG + FULL green (2735 run / 11 skipped, 0 F/E). Orphan i18n sweep: 0 iteration-1 orphans (all 6 features' keys gone from
  all 10 locales); 1584 pre-existing orphans (quest/gauntlet/puzzle/adv*/misc) left as follow-ups. Final grep: only Vanguard/card-name/bracket-literal matches.

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- E2E launch: `setsid nohup java -Dsun.java2d.opengl=false -javaagent:<agent.jar>=<scratch> -jar <shaded-jar> &` (plain nohup dies on shell timeout); log audio `SourceDataLine` errors = expected.
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`.
- Non-failing noise: "card ... not assigned to any set", "Upcoming set ... dated in the future", `FSkin > can't find skins directory!`.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.

## Active caveats / follow-ups
- Iteration 1 COMPLETE. Pre-existing orphan i18n keys remain (1584 union; ~146 `adv*`, 74 quest, 42 gauntlet, 17 puzzle, ~1305 misc) — remove with their modes.
- i18n key removal: `rg -n "<key>" --type java` zero refs first; the 10 locale files must stay consistent.
- `CardView.getImageKey()` does NOT exist — use `card.getCurrentState().getImageKey()` (ImageCache pattern).
- Per-format banlist validation (`DeckFormat`, `res/formats/**`) is NOT the Commander bracket system — keep.
- Vanguard "avatar" (`DeckSection.Avatar`, `GamePieceType.AVATAR`, `AvatarVanguard`) is unrelated — keep.
- Networking files get minimal field-level edits only (networking removal is a later iteration).
- `res/defaults/home.xml` is legacy (stale HOME_* ids); only item-specific lines get removed.
