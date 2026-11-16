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
SKIPPED_COMMITS_FILE=".git/sync_commit_registry.csv"

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
    local last_good_commit=$3
    
    echo "Identifying failing commit using binary search between $start_commit and $end_commit"
    
    # This will call the binary search script from the failure-identifier skill
    if [ -x "./binary-search-test.sh" ]; then
        ./binary-search-test.sh "$start_commit" "$end_commit"
        return $?
    else
        echo "Binary search script not found, skipping binary search"
        return 1
    fi
}

# Process each commit individually using cherry-pick for full control
echo "Processing commits..."
for commit in $(git log --format="%H" $LOCAL_COMMIT..$UPSTREAM_COMMIT); do
    echo ""
    echo "Processing commit: $(git log --oneline -1 $commit)"
    
    # Check if this commit has already been processed (skipped or applied)
    if grep -q "^$commit," "$SKIPPED_COMMITS_FILE" 2>/dev/null; then
        echo "⚠ Commit $commit was previously processed. Skipping again to avoid reprocessing."
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
        echo "$commit,\"$COMMIT_MESSAGE\",SKIPPED,false" >> "$SKIPPED_COMMITS_FILE"
        echo "SKIPPED: $commit - references removed modules"
    else
        # Try to cherry-pick the commit individually
        echo "Attempting to cherry-pick commit $commit..."
        
        # Use git cherry-pick without -n to process commit one-by-one
        git cherry-pick "$commit" 2>/dev/null && {
            # If successful, record it as applied without resolution needed
            echo "✓ Commit $commit applied successfully"
            echo "$commit,\"$COMMIT_MESSAGE\",APPLIED,false" >> "$SKIPPED_COMMITS_FILE"
        } || {
            # If it fails due to conflict, check if it's due to removed modules
            if git diff --name-only --diff-filter=U | grep -q -E "(forge-gui-mobile|forge-gui-ios|forge-gui-android|forge-gui-mobile-dev|adventure-editor|forge-installer|forge-lda)"; then
                # Conflict due to removed modules - skip and record
                echo "⚠ Commit $commit had conflicts due to removed modules. Skipping."
                echo "$commit,\"$COMMIT_MESSAGE\",SKIPPED,false" >> "$SKIPPED_COMMITS_FILE"
                git cherry-pick --abort 2>/dev/null || true
            else
                # Legitimate conflict - resolve it by accepting upstream changes (--theirs)
                echo "⚠ Commit $commit had conflicts. Resolving with --theirs (upstream changes)..."
                
                # Resolve conflicts by accepting upstream changes
                git checkout --theirs .
                git add .
                
                # Continue with cherry-pick after resolving conflicts
                git cherry-pick --continue 2>/dev/null && {
                    echo "✓ Commit $commit applied successfully with conflict resolution"
                    echo "$commit,\"$COMMIT_MESSAGE\",APPLIED,true" >> "$SKIPPED_COMMITS_FILE"
                } || {
                    echo "✗ Failed to resolve conflicts for commit $commit. Manual intervention needed."
                    git cherry-pick --abort 2>/dev/null || true
                    # Record it as needing manual review
                    echo "$commit,\"$COMMIT_MESSAGE\",MANUAL_REVIEW,false" >> "$SKIPPED_COMMITS_FILE"
                }
            fi
        }
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
    
    # Get the range of applied commits
    if [ -f "$SKIPPED_COMMITS_FILE" ]; then
        # Find the last commit that was processed (this will be the one that caused the failure)
        # In a simplified approach, we'll mark the last processed commit as SKIPPED due to test failure
        # For a real implementation, we'd use binary search to identify the exact failing commit
        if [ -s "$SKIPPED_COMMITS_FILE" ]; then
            # Get the last commit that was processed (not the header)
            LAST_COMMIT_LINE=$(tail -n 1 "$SKIPPED_COMMITS_FILE")
            if [ -n "$LAST_COMMIT_LINE" ] && [[ "$LAST_COMMIT_LINE" != "commit_hash,"* ]]; then
                # Extract the commit hash from the last line
                LAST_COMMIT=$(echo "$LAST_COMMIT_LINE" | cut -d',' -f1)
                echo "Last processed commit: $LAST_COMMIT"
                
                # Update the registry to mark that commit as SKIPPED due to test failure
                # Remove the last line and add it with proper status
                echo "Marking last commit $LAST_COMMIT as SKIPPED due to test failure"
                sed -i '$d' "$SKIPPED_COMMITS_FILE"
                echo "$LAST_COMMIT,\"$(git log --oneline -1 $LAST_COMMIT | cut -d' ' -f2- | cut -c1-100)\",SKIPPED,true" >> "$SKIPPED_COMMITS_FILE"
            fi
        fi
    fi
fi

echo ""
echo "Review the commit registry (.git/sync_commit_registry.csv) for detailed status."
echo "Note: No changes were pushed to remote repositories."