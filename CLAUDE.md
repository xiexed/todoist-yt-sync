# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands
- `JAVA_OPTS='-Dconfig.file=application-dev.conf' lein ring server-headless` - Run server locally
- `lein ring uberwar` - Create uberwar for deployment
- `lein test` - Run all tests
- `lein test :only todoist-sync.workdash-test/test-error-detector` - Run a specific test
- `lein test :only todoist-sync.workflow-test` - Run tests in a namespace

## Development
- Use `-Dconfig.file=application-dev.conf` JVM arg for dev configuration
- Check application.conf and application-dev.conf for configuration

## Code Style
- **Namespaces**: Follow `todoist-sync.feature` pattern
- **Functions**: Use kebab-case with descriptive names
- **Imports**: Group with `:require` and `:import`, use consistent aliases
- **Formatting**: 2-space indentation, consistent parentheses alignment
- **Error Handling**: Use nil-safe operations (`some->`), pattern matching with `condp`
- **Testing**: Use `deftest`, `testing` and `is` macros; EDN test data in separate files
- **Data Structures**: Maps with keyword keys, nested structures for complex relationships

## Git Workflow
- Create a separate commit after each logical, self-contained change
- Each commit should be buildable and testable when possible
- Commit message should describe the semantic change, not implementation details
- For multi-step tasks, commit after completing each semantically meaningful step
- Examples of atomic commits:
  - "Add validation for user input"
  - "Extract configuration to separate namespace"
  - "Fix null pointer in dashboard report generation"

This is a Clojure project using Ring for HTTP handling and Compojure for routing.