# Justfile for Clojure Software Builder

# Default goal
default: ci

# Clean build artifacts
clean:
	bb clean

# Run tests
test:
	bb test

# Lint the codebase
lint:
	bb lint

# Run the main command line interface
main:
	bb main

# Compile the main Clojure namespace
compile:
	bb compile

# Build UberJar
build-uberjar:
	bb build-uberjar

# Build GraalVM native image
build-gvm:
	bb build-gvm

# Rebuild Command Line Tool
build-cli:
	just clean
	just compile
	just build-uberjar
	just build-gvm

# Check for outdated dependencies
outdated:
	bb outdated

# Run nREPL server
nrepl:
	bb nrepl

# Format Clojure code
fmt:
	bb fmt

# Check Clojure code formatting
fmt-check:
	bb fmt-check

# CI workflow - runs linting and tests
ci:
	just clean
	just fmt-check
	just lint
	just test
