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

### Sync Process
1. Fetch changes from upstream/master that are not on origin/dev
2. Apply each commit one at a time to preserve history
3. Skip commits that reference removed modules (mobile, adventure, installer)
4. For commits with conflicts, resolve them individually and mark appropriately in registry
5. All operations are local only - no pushes or remote changes

### Commit Processing Steps
1. Process commits one-by-one using git cherry-pick
2. Skip commits referencing removed modules immediately
3. For commits with conflicts:
   - Resolve conflicts with minimal intervention (using --theirs)
   - Mark commit as APPLIED with requires_resolution=true in registry
   - Continue with cherry-pick --continue
4. Update the CSV registry with commit status and resolution requirements

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