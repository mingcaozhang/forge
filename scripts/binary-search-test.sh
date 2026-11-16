#!/bin/bash

# Binary search script to identify the first failing commit
# Usage: ./binary-search-test.sh <start_commit> <end_commit>

START_COMMIT=$1
END_COMMIT=$2

# Function to run tests and return status
run_tests() {
    echo "Running mvn test for commit $1..."
    if mvn test 2>/dev/null; then
        echo "✓ Tests passed for commit $1"
        return 0
    else
        echo "✗ Tests failed for commit $1"
        return 1
    fi
}

# Binary search function to find the first failing commit
find_first_failing_commit() {
    local left=$1
    local right=$2
    
    echo "Starting binary search between commits $left and $right"
    
    # Get the list of commits in range
    local commits=($(git log --format="%H" $left..$right))
    
    # If there's only one commit, check it directly
    if [ ${#commits[@]} -eq 1 ]; then
        if run_tests "${commits[0]}"; then
            echo "No failing commit found in range"
            return 1
        else
            echo "${commits[0]}"
            return 0
        fi
    fi
    
    # Binary search
    while [ $left != $right ]; do
        local mid=$(( ( $(git rev-list --count $left..$right) + 1 ) / 2 + $(git rev-list --count $left..$left) ))
        
        # Get the middle commit
        local mid_commit=$(git log --format="%H" -n 1 $left..$right | head -n $mid | tail -n 1)
        
        echo "Checking commit $mid_commit (midpoint)"
        
        # Create a temporary branch for testing
        git checkout -b temp_test_branch $left
        
        # Cherry-pick commits up to the midpoint
        local commit_index=0
        for commit in $(git log --format="%H" $left..$mid_commit); do
            if git cherry-pick "$commit" 2>/dev/null; then
                echo "✓ Cherry-picked commit $commit"
            else
                echo "✗ Failed to cherry-pick commit $commit"
                git cherry-pick --abort 2>/dev/null || true
                git checkout $left
                git branch -D temp_test_branch 2>/dev/null || true
                echo "Failed to cherry-pick commits up to $mid_commit"
                return 1
            fi
            ((commit_index++))
        done
        
        # Run tests
        if run_tests "$mid_commit"; then
            # Tests pass, so the failure is after this point
            left=$mid_commit
            echo "Tests passed, moving search to right half"
        else
            # Tests fail, so this is our first failing commit (or we need to search left)
            right=$mid_commit
            echo "Tests failed, searching left half"
        fi
        
        # Cleanup
        git checkout $left
        git branch -D temp_test_branch 2>/dev/null || true
        
        # Check if we've narrowed down to 1 commit
        if [ "$left" == "$right" ]; then
            break
        fi
    done
    
    echo "$left"
    return 0
}

# Check if we have valid arguments
if [ -z "$START_COMMIT" ] || [ -z "$END_COMMIT" ]; then
    echo "Usage: $0 <start_commit> <end_commit>"
    echo "Example: $0 abc123def456 def789abc123"
    exit 1
fi

# Validate commits exist
if ! git rev-parse --quiet $START_COMMIT >/dev/null; then
    echo "Error: Start commit $START_COMMIT does not exist"
    exit 1
fi

if ! git rev-parse --quiet $END_COMMIT >/dev/null; then
    echo "Error: End commit $END_COMMIT does not exist"
    exit 1
fi

# Run binary search
find_first_failing_commit $START_COMMIT $END_COMMIT