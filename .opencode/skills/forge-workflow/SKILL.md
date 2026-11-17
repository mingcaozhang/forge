# Forge Development Workflow

## Branch Structure

- **dev**: Development branch (always use this for local work)
- **master**: Production branch (synced with upstream)
- **origin/dev**: Remote tracking of dev branch
- **origin/master**: Remote tracking of master branch
- **upstream/master**: Original repository's master branch

## Workflow Rules

1. **Always work on the dev branch** - never commit directly to master
2. **Sync process**:
   - Rebase changes from dev onto master and push master to origin/master
   - Fetch changes from upstream/master that are not on origin/master
   - Apply each commit one at a time to preserve history
   - Skip commits that reference removed modules (mobile, adventure, installer)
   - For legitimate conflicts, attempt automatic resolution or notify user

## Sync Script Usage

Use the `scripts/sync-upstream.sh` script to synchronize with upstream changes:

```bash
./scripts/sync-upstream.sh
```

The script will:
- Fetch upstream changes
- Process commits one-by-one
- Skip commits referencing removed modules
- Attempt automatic conflict resolution
- Record skipped commits in a registry to prevent reprocessing
- Provide detailed summary of actions taken

## Removed Modules

The following modules have been removed to optimize for single-player Commander/EDH gameplay:

- `forge-gui-mobile`
- `forge-gui-ios`
- `forge-gui-android`
- `forge-gui-mobile-dev`
- `adventure-editor`
- `forge-installer`
- `forge-lda`

## Important Notes

- The sync process preserves your local commits while applying upstream changes
- Skipped commits are recorded to avoid repeated attempts
- Manual review is needed for conflicts not related to removed modules
- Always backup your work before running sync operations

Review the commit registry (sync_commit_registry.csv) for detailed status.
