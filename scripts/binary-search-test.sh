#!/bin/bash

# Binary search script to identify the first failing commit in a range
# Usage: ./binary-search-test.sh <start_commit> <end_commit>

START_COMMIT=$1
END_COMMIT=$2

# Function to run tests and return status
run_tests() {
    echo "Running mvn test..."
    if mvn test > /dev/null 2>&1; then
        echo "✓ Tests passed"
        return 0
    else
        echo "✗ Tests failed"
        return 1
    fi
}

# Binary search function to find the first failing commit
find_first_failing_commit() {
    local start=$1
    local end=$2
    
    # Get the list of commits in the range in chronological order
    # We use rev-list --reverse to get them from oldest to newest
    local commits=($(git rev-list --reverse "$start..$end"))
    local count=${#commits[@]}
    
    if [ "$count" -eq 0 ]; then
        echo "No commits found in range $start..$end"
        return 1
    fi

    echo "Starting binary search on $count commits..."
    
    local low=0
    local high=$((count - 1))
    local first_failing_index=-1

    while [ $low -le $high ]; do
        local mid=$(( (low + high) / 2 ))
        local mid_commit=${commits[$mid]}
        
        echo "Checking midpoint commit: $mid_commit (index $mid/$((count-1)))"
        
        # Create a temporary branch to test the state up to this commit
        git checkout -q -b temp_test_branch "$start"
        
        # Apply commits from start up to the mid commit
        local success=true
        for (( i=0; i<=$mid; i++ )); do
            local c=${commits[$i]}
            if ! git cherry-pick --quiet "$c" 2>/dev/null; then
                echo "✗ Failed to cherry-pick commit $c"
                success=false
                break
            fi
        done
        
        if [ "$success" = true ]; then
            if run_tests; then
                # Tests passed, failure must be after this commit
                low=$((mid + 1))
            else
                # Tests failed, this could be the first failing commit
                first_failing_index=$mid
                high=$((mid - 1))
            fi
        else
            echo "✗ Critical error: Could not reconstruct commit state. Aborting."
            git checkout -q "$start"
            git branch -D temp_test_branch 2>/dev/null
            return 1
        fi
        
        # Cleanup for next iteration
        git checkout -q "$start"
        git branch -D temp_test_branch 2>/dev/null
    done

    if [ $first_failing_index -ne -1 ]; then
        echo "${commits[$first_failing_index]}"
        return 0
    else
        echo "No failing commit found in range"
        return 1
    fi
}

# Check if we have valid arguments
if [ -z "$START_COMMIT" ] || [ -z "$END_COMMIT" ]; then
    echo "Usage: $0 <start_commit> <end_commit>"
    exit 1
fi

# Validate commits exist
if ! git rev-parse --quiet "$START_COMMIT" >/dev/null; then
    echo "Error: Start commit $START_COMMIT does not exist"
    exit 1
fi

if ! git rev-parse --quiet "$END_COMMIT" >/dev/null; then
    echo "Error: End commit $END_COMMIT does not exist"
    exit 1
fi

# Run binary search
find_first_failing_commit "$START_COMMIT" "$END_COMMIT"
