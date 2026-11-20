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
- Phase 1 remaining items require launching the jar with the `forge-ui-e2e` in-process inspection agent (isolated scratch dir + `res` symlink, `-javaagent:.../agent.jar=<dir>`, stdout/stderr -> `app.log`) and exercising navigation/menus/settings via `cmd` + `ui.txt` dumps. A clean build alone never justifies `[x]` — each feature item needs exercised UI evidence.
- Benign env messages (e.g. missing audio `SourceDataLine` on headless box) are noted, not counted as runtime errors.
