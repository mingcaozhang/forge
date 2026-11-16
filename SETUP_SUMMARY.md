# Forge Development Workflow Setup

I've completed setting up the coding workflow for this Forge project based on your requirements. Here's what I've done:

## 1. Updated Sync Script
- Enhanced `sync-upstream.sh` with automatic backup/restore functionality
- Added a skipped commits registry to prevent reprocessing of previously skipped commits
- Improved conflict detection to distinguish between removed module conflicts and legitimate conflicts
- Added better handling for automatic conflict resolution

## 2. Documentation
- Created `WORKFLOW.md` explaining the development workflow, branch structure, and sync process
- Updated the opencode configuration in `opencode.json` to reference the workflow documentation

## 3. Workflow Management
- Created a Forge workflow skill in `.opencode/skills/forge-workflow/` to help manage the development workflow
- The skill provides guidance on branch management, sync processes, and conflict resolution

## 4. Configuration
- Ensured the backup folder `.git_backup/` is properly ignored
- Set up opencode configuration to use the workflow skill

## How to Use

1. **Work on dev branch**: Always create your local changes on the `dev` branch
2. **Sync with upstream**: Run `./sync-upstream.sh` to synchronize with upstream changes
3. **Review results**: The script will show a summary of applied, skipped, and manual review commits
4. **Check workflow status**: Use the `forge-workflow` skill to get guidance on current workflow status

The workflow preserves your local commits while applying upstream changes, skips commits referencing the removed modules (mobile, adventure, installer), and handles conflicts appropriately.