# PROGRESS.md — Iteration 1 (pre-rewrite cleanup)

## Verified baseline facts
- Branch `dev` (started from plan commit `399acbbd29a`, clean tree).
- `PKG` → BUILD SUCCESS. `GATE` baseline → 2736 tests, 0 failures/errors, 11 skipped (~75 s).
- `Localizer.getMessage` falls back locale → en-US (Localizer.java:97-114): non-EN key deletion safe.
- GATE reports only forge-game/gui/gui-desktop (core/ai have no tests); `HomeNavigationRegistryTest` enforces the surfaced-submenu set.

## Completed items
- 1.1 Content Downloader: deleted CSubmenu/VSubmenuDownloaders + 8 download services + 2 tests;
  surgical EDocID/VHomeUI/home.xml/test edits; i18n keys gone from all 10 locales. `GATE` green.
- 2.1 Release Notes: deleted CSubmenu/VSubmenuReleaseNotes; surgical EDocID/VHomeUI/HelpMenu/test edits;
  orphaned `CHANGES_FILE*` consts + `TextUtil.getFormattedChangelog`; i18n gone from all 10 locales. `GATE` green.
- 3.1 How To Play: deleted `forge.gamemodes.tutorial` (7 classes), `TutorialOverlayPanel`, `res/tutorial/`, `res/howto.txt`;
  surgical IGuiBase/GuiDesktop/CMatchUI/ViewWinLose/VHomeUI/HelpMenu/HostedMatch/ForgeConstants (kept `startGame` Runnable seam); tutorial i18n removed; puzzle labels kept.
- 4.1 Avatar selection removed: deleted VSubmenuAvatars/CSubmenuAvatars/AvatarSelector; PlayerPanel
  `playerImageLabel` shows commander card image (fallback `ICO_PLAYERS`); VLobby passes `0` until 4.3
  drops the event avatar param. i18n keys gone from all 10 locales. `GATE` green.
- 4.2 Match player panels show commander card image: CMatchUI `avatarImages`/`getPlayerAvatar`/`setPlayerAvatar`
  → `getPlayerImage(PlayerView)` (`ImageCache.getIcon(cmd.getCurrentState().getImageKey())` else `FSkin.getIcon(ICO_PLAYERS)`);
  VField/CField/TargetingOverlay/VAssign* avatar naming → playerImage; `IHasIcon` + overrides (IGuiGame/ProtocolGuiGame/
  HeadlessNetworkGuiGame) + dead ProtocolMethod TODO deleted. `GATE` → 2787 tests, 0 failures/errors.

## Environment quirks
- Always use a 600000 ms timeout on Maven runs (default 120 s kills long runs).
- `-pl <module>` must pair with `-am`; `-Dtest=` needs `-DfailIfNoTests=false
  -Dsurefire.failIfNoSpecifiedTests=false` (Surefire 3.1.2 fails early modules).
- Non-failing test-log noise: "card ... not assigned to any set", "Upcoming set ... dated in the future",
  `FSkin > can't find skins directory!`.
- Scratch artifacts only under `/tmp/opencode/`. Locale files have no trailing newline; preserve when editing.

## Active caveats / follow-ups
- i18n key removal requires `rg -n "<key>" --type java` zero refs first; 10 locale files must stay consistent.
- `CardView.getImageKey()` does NOT exist — use `card.getCurrentState().getImageKey()` (ImageCache pattern).
- `res/lists/commander-bracket-combos.txt` feeds AI combo knowledge → keep data, rename in 6.3.
- Per-format banlist validation (`DeckFormat`, `res/formats/**`) is NOT the Commander bracket system — keep.
- Vanguard "avatar" feature (`DeckSection.Avatar`, `GamePieceType.AVATAR`, `AvatarVanguard`) is unrelated — keep.
- `GameType.Puzzle` core remnants and pre-existing quest/gauntlet/puzzle i18n orphans are out of scope.
- Networking files get minimal field-level edits only (networking removal is a later iteration).
- `res/defaults/home.xml` is legacy (stale HOME_* ids); per plan only item-specific lines get removed.
