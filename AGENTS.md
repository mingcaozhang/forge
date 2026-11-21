# Forge — Single-Player Commander/EDH Refactor Guidelines

## Already Removed Modules (Do Not Re-Add)
- `forge-gui-mobile`, `forge-gui-ios`, `forge-gui-android`, `forge-gui-mobile-dev`, `adventure-editor`, `forge-installer`, `forge-lda`.

## Build & Verification Commands
- Use `forge-test-runner` (`.opencode/skills/forge-test-runner/SKILL.md`) for unit/integration test runs and `forge-ui-e2e` (`.opencode/skills/forge-ui-e2e/SKILL.md`) for real desktop UI/gameplay verification (with a `600000` ms timeout on long Maven/test/UI runs).
- Always use a `600000` ms (10-minute) timeout on `mvn test` and `mvn package` commands.
- Keep unit tests clean so `mvn test -T 1C` passes; remove or update obsolete tests tied to deleted modes (`quest`, `adventure`, `puzzle`, `draft`, `sealed`, etc.) rather than leaving broken tests behind.

## Target Subsystems to Strip
- Remaining non-Commander game modes and UI flows (`adventure` runtime inside `forge-gui`/`forge-gui-desktop`, `quest`, `puzzle`, `gauntlet`, `conquest`, `draft`, `sealed`, and non-Commander constructed mode menus/tabs).
- Unused home-screen tabs, wizards, and menu entries not needed for single-player Commander, deck import, deck selection, or deck editing.

## Plan & Verification Rules
- Before finishing a final phase of `PLAN.md`—or before wrapping up a standalone task/milestone when no `PLAN.md` is used—run both `mvn package -DskipTests -T 1C` and `mvn test -T 1C` (with a `600000` ms timeout) before committing.
