#!/bin/bash

# Sync upstream changes to your fork's master branch
# This script helps automate the process of cherry-picking upstream commits 
# while being more selective about what conflicts to skip

echo "Fetching upstream changes..."
git fetch upstream

echo "Checking for new upstream commits..."
UPSTREAM_COMMIT=$(git rev-parse upstream/master)
LOCAL_COMMIT=$(git rev-parse master)

if [ "$UPSTREAM_COMMIT" = "$LOCAL_COMMIT" ]; then
    echo "Upstream and local are already in sync."
    exit 0
fi

echo "New upstream commits found. Starting cherry-pick process..."

# Get the list of commits to be applied
COMMITS_TO_APPLY=$(git log --oneline $LOCAL_COMMIT..$UPSTREAM_COMMIT)

if [ -z "$COMMITS_TO_APPLY" ]; then
    echo "No new commits to apply."
    exit 0
fi

echo "Commits to apply:"
echo "$COMMITS_TO_APPLY"

# Create a temporary file to store results
TEMP_FILE=$(mktemp)

# Process each commit
for commit in $(git log --format="%H" $LOCAL_COMMIT..$UPSTREAM_COMMIT); do
    echo "Processing commit: $(git log --oneline -1 $commit)"
    
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
        echo "SKIPPED: $commit - references removed modules" >> "$TEMP_FILE"
    else
        # Try to cherry-pick the commit
        if git cherry-pick -n "$commit" 2>/dev/null; then
            # If successful, add it to safe commits
            echo "✓ Commit $commit applied successfully" >> "$TEMP_FILE"
            git commit -m "Sync: $(git log --oneline -1 $commit)"
        else
            # If it fails, but not due to removed modules, let user decide
            echo "⚠ Commit $commit had conflicts but not due to removed modules. Manual review needed."
            echo "MANUAL REVIEW NEEDED: $commit" >> "$TEMP_FILE"
            git cherry-pick --abort 2>/dev/null || true
        fi
    fi
done

# Show the results
echo ""
echo "Summary:"
cat "$TEMP_FILE"

# Clean up
rm "$TEMP_FILE"

echo ""
echo "Sync process completed."
echo "Review the summary above for any commits that were skipped or need manual review."