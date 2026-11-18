# Forge — Single-Player Commander/EDH Refactor Guidelines

## Workflow & Branch Rules
- Always load and follow the `forge-workflow` skill (`.opencode/skills/forge-workflow/SKILL.md`).
- Work exclusively on the `dev` branch. Never touch `master`, `sync_commit_registry.csv`, or `scripts/sync-upstream.sh`.
- Make small, atomic commits after each passing phase (`refactor(commander): <phase summary>`) so `scripts/sync-upstream.sh` can cleanly rebase future upstream card updates.

## Already Removed Modules (Do Not Re-Add)
- `forge-gui-mobile`, `forge-gui-ios`, `forge-gui-android`, `forge-gui-mobile-dev`, `adventure-editor`, `forge-installer`, `forge-lda`.

## Build & Verification Commands
- Fast incremental compile check (run after every edit batch):
  `mvn test-compile -T 1C`
- Full desktop package check (run at the end of each major phase):
  `mvn package -DskipTests -T 1C`
- Targeted Commander & deck tests:
  `mvn test -Dtest=*Commander*,*Deck* -DfailIfNoTests=false -T 1C`

## Protected Core Subsystems (Preserve & Keep Upstream-Sync Friendly)
- `forge-core` and `forge-game`: Card scripts, zone rules, Commander zone/tax/damage rules, `DeckFormat.Commander`, and deck serialization/import/export (`DeckSerializer`, `.dck` / Moxfield / Archidekt / text importers). Minimize diffs in `forge-core` and `forge-game` to avoid upstream merge conflicts.
- `forge-ai`: Single-player AI opponent decision engine and Commander AI logic.
- `forge-gui` & `forge-gui-desktop`: Desktop Swing UI, match screen, Commander setup/lobby, deck chooser, deck import dialogs, and deck editor/modifier.

## Target Subsystems to Strip
- Remaining non-Commander game modes and UI flows (`adventure` runtime inside `forge-gui`/`forge-gui-desktop`, `quest`, `puzzle`, `gauntlet`, `conquest`, `draft`, `sealed`, and non-Commander constructed mode menus/tabs).
- Unused home-screen tabs, wizards, and menu entries not needed for single-player Commander, deck import, deck selection, or deck editing.

## Autonomous Loop Rules
- Execute strictly one unchecked `- [ ]` phase at a time from `PLAN.md`.
- Always `grep` for all references before deleting classes, enums, or packages.
- Never stop after explaining your next step; invoke tools continuously until the current phase compiles (`mvn test-compile -T 1C` exits 0) and is committed to `dev`.
- Only stop and output `TASK_COMPLETE` when every phase in `PLAN.md` is marked `[x]` and `mvn package -DskipTests -T 1C` exits 0.


## Available Skills & Sync Test Compatibility
- `forge-workflow` (`.opencode/skills/forge-workflow/SKILL.md`): Governs `dev` branch rules and `scripts/sync-upstream.sh` compatibility.
- `failure-identifier` (`.opencode/skills/failure-identifier/SKILL.md`): Uses `./scripts/binary-search-test.sh <start_commit> <end_commit>` to bisect build/test regressions.
- **Keep `mvn test` clean:** Because `scripts/sync-upstream.sh` runs `mvn test` after syncing upstream commits, remove or update obsolete unit tests tied to deleted modes (`quest`, `adventure`, `puzzle`, `draft`, `sealed`, etc.) rather than leaving broken tests behind. Run `mvn test -Dtest=*Commander*,*Deck* -DfailIfNoTests=false -T 1C` after each phase and `mvn test -T 1C` at the end of Phase 5.
