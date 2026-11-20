# PROGRESS

## Iteration 1 — Phase 1 item 1 (clean build)

### Build & Test Gotchas
- Clean build (10-min timeout): `mvn clean package -pl forge-gui-desktop -am -DskipTests -T 1C` exits 0; shaded jar at `forge-gui-desktop/target/forge-gui-desktop-2.0.17-SNAPSHOT-jar-with-dependencies.jar` (~32 MB).
- Sync-compat targeted tests green: 169 tests, 0 failures/errors via `mvn test -Dtest=*Commander*,*Deck* -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`.
- Always pass `timeout: 600000` on mvn commands; default 120000 ms kills long builds.

### Zero-Diff Guard
- Work on `dev` only; `forge-core`/`forge-game`/`forge-ai` must stay zero-diff. Verified clean this iteration.
- Code changes allowed only in `forge-gui` and `forge-gui-desktop`.

### Active Caveats / Next Steps
- Phase 1 remaining items require exercising navigation/menus/settings via `cmd` + `ui.txt` dumps. A clean build alone never justifies `[x]` — each feature item needs exercised UI evidence.
- Benign env messages (e.g. missing audio `SourceDataLine` on headless box) are noted, not counted as runtime errors.

## Iteration 2 — Phase 1 UI launch verification

### Key Codebase & Architecture Facts
- Live `ui.txt` confirms `FNavigationBar` is present in the main component tree: `forge.view.FNavigationBar vis=true` plus nested `NavigationTab` entries.
- Splash clears: `agent.log` shows `windows=3`, `FFrame visible=true`, and `SplashFrame visible=false`; `ui.txt` confirms the splash window is invisible.
- `app.log` scan found no runtime exception/stack trace; only benign startup line `Error handling registered!`.

### Build & Test Gotchas
- UI agent verification must use flat `/tmp/opencode` files accessed through `bash`; normal `read`/`grep` tool calls are restricted.
- Detached launch pattern confirmed: `DISPLAY=:0`, `-Djava.awt.headless=false`, and command-file-driven agent control.
- Stop the app with agent `close` through `/tmp/opencode/cmd`; `pkill` is disallowed due `Unknown: ChildProcess.kill`.

### Active Caveats / Next Steps
- Next Phase 1 item: exercise home tabs/tiles (`HomeTile`, `LastMatchTile`, `RecentDecksCarousel`) via `cmd` + `dump` and confirm screens render without exceptions.
- Keep `/tmp/opencode/cmd` clear before launch so stale `close` commands do not terminate a fresh agent session.
