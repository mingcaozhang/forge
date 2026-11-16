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
./binary-search-test.sh <start_commit> <end_commit>
```

The script will:
1. Perform binary search on the commit range
2. Test each commit selection with `mvn test`
3. Return the hash of the first commit that causes test failures

## Workflow

1. Take a range of commits (start_commit to end_commit)
2. Use binary search to minimize test runs
3. Execute `mvn test` on selected commits
4. Return the failing commit hash when found
5. Handle any git cherry-pick errors gracefully