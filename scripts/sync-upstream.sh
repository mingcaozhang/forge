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

# Initialize skipped commits registry if it doesn't exist
SKIPPED_COMMITS_FILE="sync_commit_registry.csv"

# Initialize CSV with header if it doesn't exist
if [ ! -f "$SKIPPED_COMMITS_FILE" ]; then
    echo "commit_hash,commit_message,status,requires_resolution" > "$SKIPPED_COMMITS_FILE"
fi

# Function to run tests and return status
run_tests() {
    echo "Running mvn test to verify build integrity..."
    if mvn test 2>/dev/null; then
        echo "✓ Tests passed successfully"
        return 0
    else
        echo "✗ Tests failed"
        return 1
    fi
}

# Function to identify the failing commit using binary search
find_failing_commit() {
    local start_commit=$1
    local end_commit=$2

    echo "Identifying failing commit using binary search between $start_commit and $end_commit" >&2

    # This will call the binary search script from the failure-identifier skill
    # The script prints progress to stderr and the failing commit hash to stdout
    if [ -x "scripts/binary-search-test.sh" ]; then
        scripts/binary-search-test.sh "$start_commit" "$end_commit"
        return $?
    else
        echo "Binary search script not found, skipping binary search" >&2
        return 1
    fi
}

# Process each commit individually using cherry-pick for full control
# Use rev-list --reverse to apply commits in chronological order (oldest first)
echo "Processing commits..."
for commit in $(git rev-list --reverse $LOCAL_COMMIT..$UPSTREAM_COMMIT); do
    echo ""
    echo "Processing commit: $(git log --oneline -1 $commit)"
    
    # Check if this commit has already been processed (skipped or applied)
    if grep -q "^$commit," "$SKIPPED_COMMITS_FILE" 2>/dev/null; then
        echo "⚠ Commit $commit was previously processed. Skipping again to avoid reprocessing."
        continue
    fi
    
    # Get commit message (truncated to 100 chars, quotes escaped for CSV)
    COMMIT_MESSAGE=$(git log --oneline -1 $commit | cut -d' ' -f2- | cut -c1-100 | sed 's/"/""/g')
    
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
        echo "$commit,\"$COMMIT_MESSAGE\",SKIPPED,false" >> "$SKIPPED_COMMITS_FILE"
        echo "SKIPPED: $commit - references removed modules"
        continue
    fi

    # Merge commits cannot be cherry-picked; their changes arrive via the individual commits
    PARENT_COUNT=$(git cat-file -p "$commit" | grep -c "^parent ")
    if [ "$PARENT_COUNT" -gt 1 ]; then
        echo "⚠ Commit $commit is a merge commit. Skipping (changes arrive via individual commits)."
        echo "$commit,\"$COMMIT_MESSAGE\",SKIPPED,false" >> "$SKIPPED_COMMITS_FILE"
        continue
    fi

    # Try to cherry-pick the commit individually
    echo "Attempting to cherry-pick commit $commit..."

    if git cherry-pick "$commit" 2>/dev/null; then
        echo "✓ Commit $commit applied successfully"
        echo "$commit,\"$COMMIT_MESSAGE\",APPLIED,false" >> "$SKIPPED_COMMITS_FILE"
    elif git rev-parse -q --verify CHERRY_PICK_HEAD >/dev/null; then
        # Cherry-pick is in progress (conflict or empty result)
        CONFLICTED_FILES=$(git diff --name-only --diff-filter=U)
        if [ -z "$CONFLICTED_FILES" ]; then
            # No conflicts: the pick is empty (changes already present on dev)
            git cherry-pick --abort 2>/dev/null || true
            echo "✓ Commit $commit is empty (changes already present). Recorded as applied."
            echo "$commit,\"$COMMIT_MESSAGE\",APPLIED,false" >> "$SKIPPED_COMMITS_FILE"
        elif echo "$CONFLICTED_FILES" | grep -q -E "(forge-gui-mobile|forge-gui-ios|forge-gui-android|forge-gui-mobile-dev|adventure-editor|forge-installer|forge-lda)"; then
            # Conflict due to removed modules - skip and record
            echo "⚠ Commit $commit had conflicts due to removed modules. Skipping."
            echo "$commit,\"$COMMIT_MESSAGE\",SKIPPED,false" >> "$SKIPPED_COMMITS_FILE"
            git cherry-pick --abort 2>/dev/null || true
        else
            # Legitimate conflict - resolve by accepting upstream changes (--theirs)
            # Only touch the conflicted files so the index is never polluted
            # with unrelated files (e.g. the commit registry)
            echo "⚠ Commit $commit had conflicts. Resolving with --theirs (upstream changes)..."
            echo "$CONFLICTED_FILES" | xargs -r git checkout --theirs --
            echo "$CONFLICTED_FILES" | xargs -r git add --
            if git cherry-pick --continue 2>/dev/null; then
                echo "✓ Commit $commit applied successfully with conflict resolution"
                echo "$commit,\"$COMMIT_MESSAGE\",APPLIED,true" >> "$SKIPPED_COMMITS_FILE"
            else
                echo "✗ Failed to resolve conflicts for commit $commit. Manual intervention needed."
                git cherry-pick --abort 2>/dev/null || true
                echo "$commit,\"$COMMIT_MESSAGE\",MANUAL_REVIEW,false" >> "$SKIPPED_COMMITS_FILE"
            fi
        fi
    else
        # Cherry-pick was rejected before starting (e.g. dirty index)
        echo "✗ Commit $commit could not be applied. Manual intervention needed."
        echo "$commit,\"$COMMIT_MESSAGE\",MANUAL_REVIEW,false" >> "$SKIPPED_COMMITS_FILE"
    fi
done

echo ""
echo "Sync process completed. Running final test suite to verify build integrity..."

# Run final test to ensure build integrity
if run_tests; then
    echo "✓ Final test suite passed - build is healthy"
    echo "All commits applied successfully with no build integrity issues."
else
    echo "⚠ Final test suite failed - build integrity issues detected"
    echo "Attempting to identify the first failing commit..."

    LAST_GOOD_COMMIT=$LOCAL_COMMIT
    HEAD_COMMIT=$(git rev-parse HEAD)

    if FAILING_COMMIT=$(find_failing_commit "$LAST_GOOD_COMMIT" "$HEAD_COMMIT"); then
        if git rev-parse --quiet --verify "$FAILING_COMMIT" >/dev/null; then
            echo "First failing commit: $FAILING_COMMIT ($(git log --oneline -1 "$FAILING_COMMIT"))"

            # Revert the failing commit to restore build health
            if git revert --no-edit --quiet "$FAILING_COMMIT"; then
                echo "✓ Reverted failing commit to restore build health"
            else
                echo "✗ Failed to revert commit $FAILING_COMMIT. Manual intervention needed."
                git revert --abort 2>/dev/null || true
            fi

            # Map the failing cherry-picked commit back to its upstream hash via the registry
            FAILING_SUBJECT=$(git log --format="%s" -1 "$FAILING_COMMIT")
            FAILING_SUBJECT_CSV=$(echo "$FAILING_SUBJECT" | sed 's/"/""/g')
            REGISTRY_LINE=$(grep -F "\"$FAILING_SUBJECT\"" "$SKIPPED_COMMITS_FILE" 2>/dev/null | head -n 1)
            if [ -n "$REGISTRY_LINE" ]; then
                UPSTREAM_HASH=$(echo "$REGISTRY_LINE" | cut -d',' -f1)
                awk -v h="$UPSTREAM_HASH" -v msg="$FAILING_SUBJECT_CSV" '
                    index($0, h ",") == 1 { print h "," "\"" msg "\",SKIPPED,true"; next }
                    { print }
                ' "$SKIPPED_COMMITS_FILE" > "$SKIPPED_COMMITS_FILE.tmp" && mv "$SKIPPED_COMMITS_FILE.tmp" "$SKIPPED_COMMITS_FILE"
                echo "Registry updated: upstream commit $UPSTREAM_HASH marked as SKIPPED (test failure)"
            else
                echo "$FAILING_COMMIT,\"$FAILING_SUBJECT_CSV\",SKIPPED,true" >> "$SKIPPED_COMMITS_FILE"
                echo "Registry updated: commit $FAILING_COMMIT marked as SKIPPED (test failure)"
            fi
        else
            echo "✗ Binary search did not identify a valid failing commit"
        fi
    else
        echo "✗ Could not identify failing commit. Manual intervention needed."
    fi
fi

echo ""
echo "Review the commit registry (sync_commit_registry.csv) for detailed status."
echo "Note: No changes were pushed to remote repositories."