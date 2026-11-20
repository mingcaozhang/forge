---
name: forge-ui-e2e
description: Clean-compile Forge desktop, launch the Swing UI with an in-process inspection agent, exercise UI flows and 2-4 player Commander matches, fix runtime errors with clean-build + relaunch verification, commit fixes, and clean up temp files
---

# Forge Desktop Real E2E / UI Testing

Use this skill to verify real end-to-end UI behavior, gameplay flows, and runtime stability beyond unit/integration tests—especially under Wayland/XWayland where external screen capture can fail or return blank frames.

## 1. Per-Iteration Clean Build & Isolated Workspace
- Start each iteration with a clean desktop build (using a `600000` ms / 10-minute command timeout):
  `mvn clean package -pl forge-gui-desktop -am -DskipTests -T 1C`
- Confirm the shaded jar exists under `forge-gui-desktop/target/forge-gui-desktop-*-jar-with-dependencies.jar`.
- Never place temporary agent source files, compiled classes, logs, or screenshots inside the git working tree.
- Create an isolated scratch directory (for example `/tmp/forge-ui-e2e`) and symlink `forge-gui/res` as `res` inside it so skin and asset lookup (`res/` / `ASSETS_DIR`) succeeds cleanly.
- Compile an in-process Java `-javaagent` (`Agent.java` -> `agent.jar`) in the scratch directory that polls a command file (`cmd`) on a daemon thread, executes UI inspection/actions on the Swing Event Dispatch Thread (`SwingUtilities.invokeAndWait`), dumps the live component tree (`ui.txt`), renders offscreen window snapshots via `w.printAll(...)` (`<name>.png`), and records actions/errors in `agent.log`.

## 2. Launch, Explore Features & Play 2–4 Player Commander
- Launch the built jar from the scratch directory with `-javaagent:<dir>/agent.jar=<dir>` and redirect stdout/stderr to `app.log`.
- Send commands via `cmd` (`dump`, `shot <name>`, `click <text>`, `clicktext <text>`, `clickpath <path>`, `close`) with short waits for asynchronous UI, deck/card loading, and match/AI updates.
- Explore broad UI functionality beyond unit/integration tests:
  - Main navigation, lobbies, menus, and settings/preferences controls.
  - Deck selection, deck building/editing/importing flows, and Commander setup.
  - **Real Commander games with 2, 3, and 4 players:** configure and start 2-player, 3-player, and 4-player Commander matches, advance turns/phases/actions across seats, and check for multiplayer state, layout, prompt, or runtime errors.
- After each interaction, inspect `ui.txt`, `app.log`, and `agent.log` for exceptions, stack traces, broken/disabled controls, or stalled transitions (distinguishing benign environment messages like missing audio `SourceDataLine` when no sound device is present from real runtime errors).

## 3. Fix, Clean-Build & Relaunch Verify, Commit & Clean Up
- If a runtime error or broken UI/gameplay interaction occurs:
  1. Fix the root cause while keeping changes strictly within allowed UI/controller modules (`forge-gui` and `forge-gui-desktop`), keeping `forge-core`, `forge-game`, and `forge-ai` with zero diff, and never modifying `sync_commit_registry.csv` or `scripts/sync-upstream.sh`.
  2. Run a **clean build and relaunch** with the UI agent to verify the error is no longer reproducible.
  3. Update `PROGRESS.md` (concise high-signal notes; prune resolved/obsolete entries) and create a small atomic local commit on `dev` (never run `git push`)[cite: 3].
- The verification loop is not complete while runtime errors or broken 2–4 player Commander flows still reproduce.
- Before finishing an iteration or session, stop any test Java process and remove the temporary scratch directory (`/tmp/forge-ui-e2e`) so `git status --short` has no untracked test or debug artifacts.
