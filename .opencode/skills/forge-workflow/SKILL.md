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
- Never commit directly to `master` 
- Push `dev` to `origin/dev` for remote backup
- Rebase `dev` onto `master` before syncing upstream changes

### Sync Process
1. Rebase changes from dev onto master and push master to origin/master
2. Fetch changes from upstream/master that are not on origin/master
3. Apply each commit one at a time to preserve history
4. Skip commits that reference removed modules (mobile, adventure, installer)
5. For legitimate conflicts, attempt automatic resolution or notify user

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
2. If there's a legitimate conflict, attempt to resolve automatically
3. If automatic resolution fails, notify the user for manual intervention

## Backup and Recovery

All sync operations create backups automatically. If issues occur, use `restore-from-backup` to recover the previous state.