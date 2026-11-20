# Forge — Single-Player Commander/EDH Refactor Guidelines

## Workflow & Branch Rules
- Always load and follow the `forge-workflow` skill (`.opencode/skills/forge-workflow/SKILL.md`).
- Never run `git push`; leave all commits local on `dev` for user review.
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
  `mvn test -Dtest=*Commander*,*Deck* -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`

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
- Stage and commit the updated `PLAN.md` (`- [x]`) alongside your code changes in each phase commit so subsequent runs track progress accurately.
- On the final phase of `PLAN.md`, run both `mvn package -DskipTests -T 1C` and `mvn test -T 1C` before marking `[x]` and committing.


## Available Skills & Sync Test Compatibility
- `forge-workflow` (`.opencode/skills/forge-workflow/SKILL.md`): Governs `dev` branch rules and `scripts/sync-upstream.sh` compatibility.
- `forge-ui-e2e` (`.opencode/skills/forge-ui-e2e/SKILL.md`): Clean-compiles the desktop jar, launches the Swing UI with an in-process inspection agent, verifies real UI flows and runtime logs, and cleans up temporary artifacts.
- `failure-identifier` — **disabled** for now (parked at `.opencode/skills-disabled/failure-identifier/` so the skill scanner ignores it). Re-enable by moving the folder back to `.opencode/skills/`. When enabled, it uses `./scripts/binary-search-test.sh <start_commit> <end_commit>` to bisect build/test regressions.
- **Keep `mvn test` clean:** Because `scripts/sync-upstream.sh` runs `mvn test` after syncing upstream commits, remove or update obsolete unit tests tied to deleted modes (`quest`, `adventure`, `puzzle`, `draft`, `sealed`, etc.) rather than leaving broken tests behind. Run `mvn test -Dtest=*Commander*,*Deck* -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C` after each phase and `mvn test -T 1C` at the end of Phase 5.

## Cross-Iteration Progress (`PROGRESS.md`)

At the end of each iteration (alongside updating `PLAN.md` and committing), update `PROGRESS.md` so the next fresh iteration starts with high-signal context without re-discovering settled facts:

### What to Record (Keep Concise, Target <= 40 Lines)
- **Key Codebase & Architecture Facts:** Important classes, controllers, UI/state hooks, and shared helpers discovered or introduced that affect upcoming tasks.
- **Build & Test Gotchas:** Verified test/build commands, flags (such as Surefire flags or timeouts), and environment quirks that prevent failed runs.
- **Active Caveats & Follow-ups:** Open edge cases, behavioral nuances, or dependencies relevant to the next unchecked `PLAN.md` items.

### What to Prune Every Iteration
- Remove notes from completed phases that are no longer needed for remaining tasks.
- Delete resolved errors, abandoned approaches, and superseded workarounds.
- Never paste raw command logs, long diffs, or play-by-play narration—keep entries to short, factual bullets and rely on `git log` and `PLAN.md` for history.
