# PROGRESS.md — Iteration 1 (pre-rewrite cleanup)

## Verified baseline facts
- Branch `dev` (started from plan commit `399acbbd29a`, clean tree).
- GATE green: reactor total 2787 tests (forge-game 23 + forge-gui 27 + desktop 2737), 0 failures/errors.
- Desktop surefire quirk: "Results" line reads 2736/11-skipped vs 2737/12-skipped TestSuite line (off-by-one) — normal.
- `Localizer.getMessage` falls back locale → en-US: non-EN key deletion safe. `HomeNavigationRegistryTest` enforces submenu set.

## Completed items
- 1.1 Content Downloader: deleted CSubmenu/VSubmenuDownloaders + 8 services + 2 tests; EDocID/VHomeUI/home.xml; i18n gone from all 10 locales.
- 2.1 Release Notes: deleted CSubmenu/VSubmenuReleaseNotes + orphaned `CHANGES_FILE*` + `TextUtil.getFormattedChangelog`; i18n gone.
- 3.1 How To Play: deleted `forge.gamemodes.tutorial` (7 classes), TutorialOverlayPanel, res/tutorial/, res/howto.txt + i18n.
- 4.1 Avatar selection removed: deleted VSubmenuAvatars/CSubmenuAvatars/AvatarSelector; PlayerPanel shows commander card image (fallback ICO_PLAYERS); i18n gone.
- 4.2 Match player panels: CMatchUI `getPlayerImage(PlayerView)`; VField/CField/TargetingOverlay/VAssign* → playerImage naming; `IHasIcon` deleted.
- 4.3 Avatar index dropped from lobby/game model: avatar fields gone from LobbyPlayer/Player/PlayerView/TrackableProperty
  (`setHasLost(false)` inlined in `dangerouslySetController`); LobbySlot 8→7 args (LocalLobby/GameLobby/OfflineLobby/ServerGameLobby);
  avatar params dropped from UpdateLobbyPlayerEvent/LoginEvent/FServerManager/GameClientHandler; dead `avatarUpdate` deleted;
  GamePlayerUtil overloads restructured (dropped dead `(name,profileOverride)`); VLobby.getSlot + LobbySlotAuthorizationTest updated. `GATE` green; grep zero.
- 4.4 Avatar pref/skin/sprite removed: `FPref.UI_AVATARS`, `IGuiBase.getAvatarCount` (+ GuiDesktop/ForgeMatrixWriter overrides),
  all FSkin avatar state (`avatars` map, bim*Avatars, f4/f5, assembleAvatars/getAvatars, splash 21→20), `SPRITE_AVATARS_FILE`,
  deleted res/skins/default/sprite_avatars.png. `GATE` green; `rg -i avatar --type java` → only Vanguard/card matches.
- 5.1 Random-name UI removed: PlayerPanel `nameRandomiser`/`createNameRandomizer`; VLobby `genderOptions`/`typeOptions`/`getNewName`
  (+ now-unused `SkinImage` import caught by checkstyle); 6 i18n keys × 10 locales. Kept NameGenerator + auto-naming
  (PlayerPanel open-slot AI naming, GameLobby.randomName, GuiDisplayUtil.getRandomAiName). `GATE` green; plan grep zero.

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
