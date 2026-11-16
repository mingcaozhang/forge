#!/bin/bash

# Sync upstream changes to your fork's dev branch
# This script helps automate the process of cherry-picking upstream commits 
# while being more selective about what conflicts to skip
# This script is designed for local-only operations - no pushes or remote changes

echo "Fetching upstream changes..."
git fetch upstream

# Get current dev branch commit
LOCAL_COMMIT=$(git rev-parse dev)

# Get upstream master commit
UPSTREAM_COMMIT=$(git rev-parse upstream/master)

if [ "$UPSTREAM_COMMIT" = "$LOCAL_COMMIT" ]; then
    echo "Upstream and local dev are already in sync."
    exit 0
fi

echo "New upstream commits found. Starting cherry-pick process..."

# Get the list of commits to be applied (upstream commits that are not on dev)
COMMITS_TO_APPLY=$(git log --oneline $LOCAL_COMMIT..$UPSTREAM_COMMIT)

if [ -z "$COMMITS_TO_APPLY" ]; then
    echo "No new commits to apply."
    exit 0
fi

echo "Commits to apply:"
echo "$COMMITS_TO_APPLY"

# Create a temporary file to store results
TEMP_FILE=$(mktemp)
SKIPPED_COMMITS_FILE=".git/sync_commit_registry.csv"

# Initialize skipped commits registry if it doesn't exist
if [ ! -f "$SKIPPED_COMMITS_FILE" ]; then
    echo "commit_hash,commit_message,status" > "$SKIPPED_COMMITS_FILE"
fi

# Process each commit
for commit in $(git log --format="%H" $LOCAL_COMMIT..$UPSTREAM_COMMIT); do
    echo "Processing commit: $(git log --oneline -1 $commit)"
    
    # Check if this commit has already been processed (skipped or applied)
    if grep -q "^$commit," "$SKIPPED_COMMITS_FILE" 2>/dev/null; then
        echo "⚠ Commit $commit was previously processed. Skipping again to avoid reprocessing."
        echo "SKIPPED: $commit - previously processed" >> "$TEMP_FILE"
        continue
    fi
    
    # Get commit message (truncated to 100 chars)
    COMMIT_MESSAGE=$(git log --oneline -1 $commit | cut -d' ' -f2- | cut -c1-100)
    
    # Check if this commit touches any of our removed modules
    # Get list of files changed in this commit
    CHANGED_FILES=$(git show --name-only --format="" $commit)
    
    # Check for references to removed modules
    HAS_REMOVED_MODULES=false
    for file in $CHANGED_FILES; do
        # Check if file path contains any of our removed module names
        if [[ "$file" == *"forge-gui-mobile"* ]] || 
           [[ "$file" == *"forge-gui-ios"* ]] || 
           [[ "$file" == *"forge-gui-android"* ]] || 
           [[ "$file" == *"forge-gui-mobile-dev"* ]] || 
           [[ "$file" == *"adventure-editor"* ]] || 
           [[ "$file" == *"forge-installer"* ]] || 
           [[ "$file" == *"forge-lda"* ]]; then
            HAS_REMOVED_MODULES=true
            break
        fi
    done
    
    if [ "$HAS_REMOVED_MODULES" = true ]; then
        echo "⚠ Commit $commit references removed modules. Skipping."
        echo "$commit,\"$COMMIT_MESSAGE\",SKIPPED" >> "$SKIPPED_COMMITS_FILE"
        echo "SKIPPED: $commit - references removed modules" >> "$TEMP_FILE"
    else
        # Try to cherry-pick the commit
        # Use a subshell to handle errors gracefully without triggering global trap
        (git cherry-pick -n "$commit" 2>/dev/null) && {
            # If successful, add it to safe commits
            echo "✓ Commit $commit applied successfully" >> "$TEMP_FILE"
            git commit -m "Sync: $(git log --oneline -1 $commit)"
            echo "$commit,\"$COMMIT_MESSAGE\",APPLIED" >> "$SKIPPED_COMMITS_FILE"
        } || {
            # If it fails, check if it's due to removed modules or legitimate conflict
            if git diff --name-only --diff-filter=U | grep -q -E "(forge-gui-mobile|forge-gui-ios|forge-gui-android|forge-gui-mobile-dev|adventure-editor|forge-installer|forge-lda)"; then
                # Conflict due to removed modules - skip and record
                echo "⚠ Commit $commit had conflicts due to removed modules. Skipping."
                echo "$commit,\"$COMMIT_MESSAGE\",SKIPPED" >> "$SKIPPED_COMMITS_FILE"
                echo "SKIPPED: $commit - conflicts due to removed modules" >> "$TEMP_FILE"
                git cherry-pick --abort 2>/dev/null || true
            else
                # Legitimate conflict - for this workflow, we'll leave it for manual resolution
                # This is where the skill would be invoked to help resolve conflicts
                echo "⚠ Commit $commit had conflicts but not due to removed modules. Manual review needed."
                echo "MANUAL REVIEW NEEDED: $commit" >> "$TEMP_FILE"
                git cherry-pick --abort 2>/dev/null || true
            fi
        }
    fi
done

# Show the results
echo ""
echo "Summary:"
cat "$TEMP_FILE"

# Clean up
rm "$TEMP_FILE"

echo ""
echo "Sync process completed successfully."
echo "Review the summary above for any commits that were skipped or need manual review."
echo "Note: No changes were pushed to remote repositories. Review conflicted files manually if needed."