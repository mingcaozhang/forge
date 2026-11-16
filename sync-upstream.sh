#!/bin/bash

# Sync upstream changes to your fork's master branch
# This script will cherry-pick commits from upstream/master that don't conflict with your removed modules

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

# Create a temporary file to store safe commits
TEMP_FILE=$(mktemp)

# Process each commit
for commit in $(git log --format="%H" $LOCAL_COMMIT..$UPSTREAM_COMMIT); do
    echo "Processing commit: $(git log --oneline -1 $commit)"
    
    # Try to cherry-pick the commit
    if git cherry-pick -n "$commit" 2>/dev/null; then
        # If successful, add it to safe commits
        echo "✓ Commit $commit applied successfully" >> "$TEMP_FILE"
        git commit -m "Sync: $(git log --oneline -1 $commit)"
    else
        # If it fails, check if it's due to removed modules
        echo "⚠ Commit $commit had conflicts (likely due to removed modules). Skipping."
        # Revert the failed cherry-pick
        git cherry-pick --abort 2>/dev/null || true
        # Store this commit for manual review
        echo "SKIPPED: $commit" >> "$TEMP_FILE"
    fi
done

# Show the results
echo ""
echo "Summary:"
cat "$TEMP_FILE"

# Clean up
rm "$TEMP_FILE"

echo ""
echo "Sync process completed. Check the output above for any skipped commits."
echo "Review any commits that were skipped in the TEMP_FILE for manual handling."