---
name: forge-workflow
description: Manage Forge development workflow, including sync processes and branch management
---

# Forge Workflow Manager

You are an expert assistant for managing the Forge development workflow with specific focus on:

1. Branch management (dev vs master)
2. Upstream sync processes with conflict handling
3. Module removal tracking
4. Backup and restore procedures

## Workflow Guidelines

### Branch Usage
- ALWAYS work on the `dev` branch for local development
- NEVER checkout or operate on `master` branch
- Never commit directly to `master` 
- Push `dev` to `origin/dev` for remote backup
- If any operation needs to be done on master, do it manually

### Sync Process
1. Fetch changes from upstream/master that are not on origin/dev
2. Apply each commit one at a time to preserve history
3. Skip commits that reference removed modules (mobile, adventure, installer)
4. For commits with conflicts, resolve them individually and mark appropriately in registry
5. Run mvn test only once at the end of the sync process to verify build integrity
6. If tests fail, use the binary search failure identification tool to locate the problematic commit
7. All operations are local only - no pushes or remote changes

### Failure Identification
When build integrity is compromised, the system uses the `failure-identifier` skill to:
1. Perform binary search across the commit range
2. Identify the first commit that causes test failures
3. Return the commit hash for manual review or skipping

## Commands

### `sync-upstream`
Run the upstream synchronization script to apply changes from upstream/master while preserving your local commits.

### `binary-search-test.sh`
Execute binary search to identify the first commit that breaks the build.
Usage: `./binary-search-test.sh <start_commit> <end_commit>`

### Removed Modules
The following modules have been removed to optimize for single-player Commander/EDH gameplay:
- `forge-gui-mobile`
- `forge-gui-ios`
- `forge-gui-android`
- `forge-gui-mobile-dev`
- `adventure-editor`
- `forge-installer`
- `forge-lda`

## Commands

Use these specific commands when requested:

### `sync-upstream`
Run the upstream synchronization script to apply changes from upstream/master while preserving your local commits.

### `check-branches`
Check the current branch status and upstream synchronization.

### `workflow-status`
Provide a complete overview of the current workflow status.

## Conflict Resolution

When conflicts occur:
1. If the conflict is due to removed modules, skip the commit and record it in the skipped commits registry
2. For legitimate conflicts, resolve using minimal intervention (accept upstream changes with --theirs)
3. Record the resolution requirement in the registry for future reference
4. If resolution fails, notify the user for manual intervention

## Backup and Recovery

All sync operations create backups automatically. If issues occur, use `restore-from-backup` to recover the previous state.