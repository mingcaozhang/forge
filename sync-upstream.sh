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

# Function to perform binary search for failing commit
find_failing_commit() {
    local start_commit=$1
    local end_commit=$2
    local last_good_commit=$3
    
    if [ "$start_commit" = "$end_commit" ]; then
        echo "Failing commit identified: $start_commit"
        return 1
    fi
    
    # For simplicity, we'll just skip the problematic commit for now
    echo "Binary search not fully implemented in this version - skipping problematic commit"
    return 0
}

# Process each commit individually using cherry-pick for full control
echo "Processing commits and running tests after each application..."
PROCESSING_COMMITS=()
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
            
            # Run tests after applying commit
            echo "Running tests after applying commit $commit..."
            if ! run_tests; then
                echo "⚠ Tests failed after applying commit $commit"
                echo "This commit will be skipped to maintain build integrity"
                git reset --hard HEAD~1
                echo "$commit,\"$COMMIT_MESSAGE\",SKIPPED,true" >> "$SKIPPED_COMMITS_FILE"
                echo "SKIPPED: $commit - tests failed, build integrity maintained"
            else
                echo "✓ Tests passed after applying commit $commit"
            fi
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
                    
                    # Run tests after applying commit with resolution
                    echo "Running tests after applying commit $commit with resolution..."
                    if ! run_tests; then
                        echo "⚠ Tests failed after applying commit $commit with resolution"
                        echo "This commit will be skipped to maintain build integrity"
                        git reset --hard HEAD~1
                        echo "$commit,\"$COMMIT_MESSAGE\",SKIPPED,true" >> "$SKIPPED_COMMITS_FILE"
                        echo "SKIPPED: $commit - tests failed, build integrity maintained"
                    else
                        echo "✓ Tests passed after applying commit $commit with resolution"
                    fi
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
echo "Sync process completed."
echo "Review the commit registry (.git/sync_commit_registry.csv) for detailed status."
echo "Note: No changes were pushed to remote repositories."

# Run final test to ensure build integrity
echo ""
echo "Running final test suite to ensure build integrity..."
if run_tests; then
    echo "✓ Final test suite passed - build is healthy"
else
    echo "⚠ Final test suite failed - build integrity issues detected"
fi