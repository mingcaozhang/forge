---
name: forge-swing-ui-e2e
description: Build Forge desktop, run the Java Swing UI with an in-process EDT inspection agent in a temporary scratch directory, inspect logs/UI state, and clean up temporary artifacts
---

# Forge Java Swing UI E2E Testing

Use this skill to test the Java Swing desktop UI (`forge-gui-desktop`) via an in-process inspection agent—especially under Wayland/XWayland where external screen capture can fail or return blank frames.

## Scope
- **Java Swing only:** Covers the Swing UI and EDT/`-javaagent` testing mechanics.
- **How vs. what:** Defines **how** to build, launch, drive, inspect, and clean up; specific test scenarios belong in `PLAN.md` or the prompt.

## 1. Build & Prepare Scratch Workspace
- Build the desktop jar (use a `600000` ms timeout):
  `mvn clean package -pl forge-gui-desktop -am -DskipTests -T 1C`
- Verify the shaded jar exists under `forge-gui-desktop/target/forge-gui-desktop-*-jar-with-dependencies.jar`.
- Keep all temporary files outside the git working tree in a scratch directory under `/tmp/` (for example `/tmp/forge-ui-e2e`), with `forge-gui/res` symlinked as `res` inside it.
- Compile an in-process Java `-javaagent` (`Agent.java` -> `agent.jar`) in the scratch directory that:
  - Polls a `cmd` file on a daemon thread and runs UI queries/actions on the Swing Event Dispatch Thread (`SwingUtilities.invokeAndWait`).
  - Dumps the component tree (`ui.txt`), saves offscreen window snapshots via `w.printAll(...)` (`<name>.png`), and logs actions/errors to `agent.log`.

## 2. Launch, Drive UI & Inspect State
- Launch the shaded jar from the scratch directory with `-javaagent:<dir>/agent.jar=<dir>` and redirect stdout/stderr to `app.log`.
- Send commands via `cmd` (`dump`, `shot <name>`, `click <text>`, `clicktext <text>`, `clickpath <path>`, `close`) with brief waits for async UI and background work.
- After each action, inspect `ui.txt`, `app.log`, and `agent.log` for exceptions, stack traces, stalled transitions, or broken controls (ignoring benign environment warnings such as a missing audio `SourceDataLine` when no sound device is present).

## 3. Re-Verify Fixes & Clean Up
- After fixing an issue, run a **clean build and relaunch** with the agent to confirm the fix, update `PROGRESS.md` concisely (pruning resolved/obsolete notes), and make a small local commit per your workflow instructions.
- Before finishing, send `close`, ensure any test Java process has exited, and delete the temporary scratch directory so `git status --short` has no untracked test artifacts.
