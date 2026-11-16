# Failure Identifier Skill

This skill identifies the first commit in a range that causes mvn test failures.

## Usage

The skill provides the `binary-search-test.sh` script which implements binary search to locate failing commits efficiently.

## Requirements

- Git repository with commits to test
- Maven installed and configured
- Proper Java environment for Forge build

## Implementation Details

The binary search algorithm minimizes the number of test runs needed to identify the first failing commit in a given range.