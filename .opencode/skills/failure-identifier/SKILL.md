---
name: failure-identifier
description: Identifies the first commit in a range that causes build failures
---

# Failure Identifier Skill

This skill is responsible for identifying the first commit in a range that causes mvn test to fail.

## Functionality

The skill uses binary search to efficiently locate the first failing commit in a given range of commits.

## Commands

### `binary-search-test.sh`
Run the binary search test script to identify the first commit that breaks the build.

Usage:
```
./scripts/binary-search-test.sh <start_commit> <end_commit>
```

The script will:
1. Perform binary search on the commit range (applying commits oldest-first on a temporary branch)
2. Test each midpoint with `mvn test`
3. Print the hash of the first commit that causes test failures to stdout (progress goes to stderr)
4. Return exit code 0 when a failing commit is found, 1 otherwise

Note: `scripts/sync-upstream.sh` calls this automatically when the final test suite fails,
then reverts the failing commit and records it as `SKIPPED` in `sync_commit_registry.csv`.

## Workflow

1. Take a range of commits (start_commit to end_commit)
2. Use binary search to minimize test runs
3. Execute `mvn test` on selected commits
4. Return the failing commit hash when found
5. Handle any git cherry-pick errors gracefully