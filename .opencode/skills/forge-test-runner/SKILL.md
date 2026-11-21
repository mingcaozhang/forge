---
name: forge-test-runner
description: Run Maven unit and integration tests in Forge across the full reactor, a single module, a specific test file/class/pattern, or compile-only checks without Surefire or dependency-resolution failures
---

# Forge Maven Test Runner

Use this skill whenever running or filtering JUnit/TestNG tests in /home/ming/Projects/forge.

## Core Rules
- **Never declare something done without testing it:** Do not claim a task, bug fix, refactor, or phase is complete/done unless you have actually executed the relevant test/verification commands and observed them pass. After making any fix, re-run verification to confirm the issue no longer reproduces; if failures or runtime errors remain, report them and do not mark the work done.
- Always pass a `600000` ms (10-minute) timeout on `mvn test` commands so long test runs are not killed at the default 120s timeout.
- Never run `mvn test -Dtest=...` across the multi-module reactor without `-Dsurefire.failIfNoSpecifiedTests=false` and `-DfailIfNoTests=false`. In Surefire 3.1.2, early reactor modules like `forge-core` will fail the entire build if they contain zero tests matching `-Dtest=...`.
- Whenever scoping a Maven command to a specific module with `-pl <module>`, always include `-am` (`--also-make`) so upstream sibling modules (`forge-core`, `forge-game`, `forge-ai`, `forge-gui`) are built and resolved in reactor order.

## 1. Run the Full Test Suite (All Modules)
`mvn test -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`

## 2. Run All Tests in a Specific Module
`mvn test -pl <module> -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`
Common modules: `forge-core`, `forge-game`, `forge-ai`, `forge-gui`, `forge-gui-desktop`.

## 3. Run a Specific Test File, Class, or Pattern
Map the test file path to `<module>` (the directory before `/src/test/java/...`) and `<TestClassName>` (the `.java` filename without `.java`, or `ClassName#methodName` for a single test method):
- Known test file (`forge-gui-desktop/src/test/java/forge/ai/AiReportScenarioTest.java` -> module `forge-gui-desktop`, test `AiReportScenarioTest`): `mvn test -pl forge-gui-desktop -am -Dtest=AiReportScenarioTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`
- Any specific test file / class: `mvn test -pl <module> -am -Dtest=<TestClassOrPattern> -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`
- Single test method: `mvn test -pl <module> -am -Dtest=<TestClassName>#<methodName> -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`
- Across all modules when owning module is unknown: `mvn test -Dtest=<TestClassOrPattern> -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`
- With extra system properties: `mvn test -pl forge-gui-desktop -am -Dtest=AiReportScenarioTest -Dforge.test.issue=kw-targeted-flying-block -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -T 1C`

## 4. Fast Compile-Only Check (No Tests Executed)
`mvn test-compile -T 1C`
