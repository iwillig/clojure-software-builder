# LLM Agent Instructions

This file contains directives and patterns for LLM coding agents working on the Clojure Software Builder project.

## Critical Rules

### MUST DO

1. **Always run `bb ci` before completing any task** - This runs formatting check, linting, and tests
2. **Add type annotations** using `typed.clojure` for all new records and public functions
3. **Follow the existing Component pattern** for any new system components
4. **Write tests** for all new functionality in the corresponding `test/csb/` directory

### MUST NOT

1. **Never commit code that fails `bb ci`**
2. **Never use `def` for mutable state** - use atoms within components if needed
3. **Never add dependencies without explicit user approval**
4. **Never bypass the Component lifecycle** - all stateful resources must be components
5. **Never use `println` for logging** - use `mulog` instead

## Commands Reference

```bash
# ALWAYS run before completing work
bb ci              # Runs: clean -> fmt-check -> lint -> test

# Individual commands
bb test            # Run tests with Kaocha
bb lint            # Run clj-kondo linter
bb fmt             # Fix code formatting
bb fmt-check       # Check formatting without fixing
bb clean           # Remove build artifacts
bb nrepl           # Start development REPL on port 7889
bb main            # Run the application
```

## Interactive Code Evaluation with clj-nrepl-eval

LLM agents should use `clj-nrepl-eval` to interactively test and evaluate Clojure code against a running nREPL server. This enables rapid feedback during development without running the full test suite.

### Prerequisites

1. Start the nREPL server in a separate terminal: `bb nrepl`
2. The server runs on **port 7889** by default
3. **Initialize the dev environment** by running `(user/fast-dev)` first

### IMPORTANT: Initialize Dev Environment First

Before using dev functions, you MUST run:

```bash
clj-nrepl-eval -p 7889 "(user/fast-dev)"
```

This loads the `dev` namespace which provides:
- `dev/reload` - Reload changed namespaces
- `dev/lint` - Lint code with clj-kondo
- `dev/type-check` - Run typed Clojure checks
- `k/run` - Run tests (from kaocha.repl)
- `k/run-all` - Run all tests

### Dev Namespace Functions

| Function | Purpose | Example |
|----------|---------|---------|
| `(dev/reload)` | Reload changed namespaces | After editing code |
| `(dev/lint)` | Lint src, test, dev dirs | Check for errors |
| `(dev/type-check)` | Type check src dir | Verify type annotations |
| `(k/run)` | Run tests in current ns | Quick test feedback |
| `(k/run 'csb.main-test)` | Run specific test ns | Test one namespace |
| `(k/run-all)` | Run all project tests | Full test suite |

### Recommended Dev Workflow

**Step 1: Initialize dev environment (once per session)**
```
command: clj-nrepl-eval -p 7889 "(user/fast-dev)"
description: Initialize dev environment
```

**Step 2: After editing code, reload namespaces**
```
command: clj-nrepl-eval -p 7889 "(dev/reload)"
description: Reload changed namespaces
```

**Step 3: Run tests for the namespace you changed**
```
command: clj-nrepl-eval -p 7889 "(k/run 'csb.main-test)"
description: Run tests for main-test namespace
```

**Step 4: Lint your changes**
```
command: clj-nrepl-eval -p 7889 "(dev/lint)"
description: Lint project with clj-kondo
```

**Step 5: Type check (optional)**
```
command: clj-nrepl-eval -p 7889 "(dev/type-check)"
description: Run typed Clojure checks
```

### Quick Reference: Dev Commands

```bash
# Initialize dev (REQUIRED FIRST)
clj-nrepl-eval -p 7889 "(user/fast-dev)"

# Reload after code changes
clj-nrepl-eval -p 7889 "(dev/reload)"

# Run all tests
clj-nrepl-eval -p 7889 "(k/run-all)"

# Run tests in current namespace
clj-nrepl-eval -p 7889 "(k/run)"

# Run specific test namespace
clj-nrepl-eval -p 7889 "(k/run 'csb.main-test)"

# Run specific test var
clj-nrepl-eval -p 7889 "(k/run 'csb.main-test/my-test)"

# Lint the project
clj-nrepl-eval -p 7889 "(dev/lint)"

# Type check
clj-nrepl-eval -p 7889 "(dev/type-check)"
```

### Common Errors and Fixes

| Error | Cause | Fix |
|-------|-------|-----|
| `No such var: dev/reload` | Dev not loaded | Run `(user/fast-dev)` first |
| `No such var: k/run` | Dev not loaded | Run `(user/fast-dev)` first |
| `Could not locate...` | Namespace not found | Check file path and ns declaration |
| Stale code running | Forgot to reload | Run `(dev/reload)` |

### Basic Usage

```bash
# Evaluate a simple expression
clj-nrepl-eval -p 7889 "(+ 1 2 3)"

# Evaluate code that prints output
clj-nrepl-eval -p 7889 "(println \"Hello from REPL\")"

# Require a namespace and call a function
clj-nrepl-eval -p 7889 "(require '[csb.models :as m]) (m/valid-user? {:id (random-uuid) :name \"Alice\" :email \"alice@example.com\"})"
```

### Multi-line Code with Heredocs

For more complex code, use heredocs:

```bash
clj-nrepl-eval -p 7889 <<'EOF'
(require '[csb.config :as config])
(def test-config (config/load-config))
(println "Config loaded:" test-config)
EOF
```

### Session Persistence

Sessions are persistent by default. State (vars, namespaces, loaded libraries) persists across invocations:

```bash
# Define a var in one call
clj-nrepl-eval -p 7889 "(def my-data {:foo 1 :bar 2})"

# Use it in a later call
clj-nrepl-eval -p 7889 "(update my-data :foo inc)"

# Reset session when needed (clears all state)
clj-nrepl-eval -p 7889 --reset-session
```

### Discovering nREPL Servers

```bash
# Find nREPL servers in current directory
clj-nrepl-eval --discover-ports

# List previously connected servers
clj-nrepl-eval --connected-ports
```

### Example: Testing a New Function

```bash
# 1. Load the namespace with the new function
clj-nrepl-eval -p 7889 "(require '[csb.controllers :as ctrl] :reload)"

# 2. Test the function with sample data
clj-nrepl-eval -p 7889 "(ctrl/my-new-function {:param \"value\"})"

# 3. Check for edge cases
clj-nrepl-eval -p 7889 "(ctrl/my-new-function nil)"
clj-nrepl-eval -p 7889 "(ctrl/my-new-function {})"
```

### Important Notes

- **Always provide a description** when running shell commands - LLM agents must include a clear, concise description (5-10 words) explaining what each command does
- **Always ensure the nREPL server is running** before using `clj-nrepl-eval`
- **Use `:reload` flag** when requiring namespaces after editing files: `(require '[ns.name] :reload)`
- **Session state persists** - use `--reset-session` if you need a clean slate
- **Timeout default is 120 seconds** - use `--timeout` for long-running operations
- **This supplements but does not replace `bb ci`** - always run the full CI before completing work

### Example Shell Command with Description

When using the Bash tool to run `clj-nrepl-eval`, always include a description:

```
Command: clj-nrepl-eval -p 7889 "(require '[csb.models :as m] :reload)"
Description: Reload csb.models namespace via nREPL
```

```
Command: clj-nrepl-eval -p 7889 "(m/valid-user? {:id (random-uuid) :name \"Test\" :email \"test@example.com\"})"
Description: Test valid-user? with sample data
```

## Bash Tool Reference

This section documents the Bash tool parameters for LLM agents.

### Bash Tool Parameters (JSON Schema)

```json
{
  "type": "object",
  "required": ["command", "description"],
  "properties": {
    "command": {
      "type": "string",
      "description": "The shell command to execute"
    },
    "description": {
      "type": "string",
      "description": "5-10 word summary of what the command does"
    },
    "timeout": {
      "type": "number",
      "description": "Timeout in milliseconds (default: 120000, max: 600000)"
    },
    "workdir": {
      "type": "string",
      "description": "Working directory path (use instead of cd)"
    }
  }
}
```

### Parameter Rules

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `command` | YES | - | The shell command to run |
| `description` | YES | - | Short description (5-10 words) |
| `timeout` | NO | 120000ms | Max wait time in milliseconds |
| `workdir` | NO | project root | Directory to run command in |

### CRITICAL: Description is Required

Every Bash tool call MUST include a `description` parameter.

**Good descriptions (5-10 words):**
- "Run project tests with Kaocha"
- "Check code formatting"
- "Evaluate expression via nREPL"
- "Reload namespace after edit"

**Bad descriptions (avoid):**
- "run command" (too vague)
- "This command will execute the clj-nrepl-eval tool to evaluate the given Clojure expression" (too long)

### Bash Tool Call Examples

**Example 1: Simple evaluation**
```
command: clj-nrepl-eval -p 7889 "(+ 1 2 3)"
description: Evaluate arithmetic expression via nREPL
```

**Example 2: Load a namespace**
```
command: clj-nrepl-eval -p 7889 "(require '[csb.models :as m] :reload)"
description: Reload csb.models namespace
```

**Example 3: Test a function**
```
command: clj-nrepl-eval -p 7889 "(m/valid-user? {:id (random-uuid) :name \"Test\" :email \"test@test.com\"})"
description: Test valid-user? with sample data
```

**Example 4: Run CI checks**
```
command: bb ci
description: Run formatting, linting, and tests
```

**Example 5: Using workdir parameter**
```
command: bb test
workdir: /path/to/project
description: Run tests in specific directory
```

**Example 6: Using timeout for long operations**
```
command: bb ci
timeout: 300000
description: Run full CI with extended timeout
```

### Common Patterns for This Project

| Task | Command | Description |
|------|---------|-------------|
| Run all CI checks | `bb ci` | Run formatting, linting, and tests |
| Run tests only | `bb test` | Run tests with Kaocha |
| Fix formatting | `bb fmt` | Auto-format Clojure code |
| Check formatting | `bb fmt-check` | Check code formatting |
| Run linter | `bb lint` | Lint code with clj-kondo |
| Start nREPL | `bb nrepl` | Start nREPL server on port 7889 |
| Evaluate code | `clj-nrepl-eval -p 7889 "(...)"` | Evaluate Clojure via nREPL |
| Reset REPL session | `clj-nrepl-eval -p 7889 --reset-session` | Clear nREPL session state |

### Do NOT Use Bash For

- Reading files → use the **Read** tool
- Writing files → use the **Write** tool
- Editing files → use the **Edit** tool
- Finding files → use the **Glob** tool
- Searching content → use the **Grep** tool
- Communicating with user → output text directly

## Typed Clojure REPL Usage

Use Typed Clojure at the REPL to verify type annotations interactively.

### Key Functions

| Function | Purpose | Use Case |
|----------|---------|----------|
| `(t/cf expr)` | Check form, infer type | Quick type check |
| `(t/cf expr expected-type)` | Check form against type | Verify specific type |
| `(t/check-ns-clj)` | Check current namespace | Full namespace check |
| `(t/check-ns-clj 'ns.name)` | Check specific namespace | Check other namespace |
| `(t/check-dir-clj "src")` | Check all files in dir | Full project check |

### t/cf - Check Form (Most Used)

`t/cf` is the primary REPL tool for type checking expressions.

**Basic usage:**
```
command: clj-nrepl-eval -p 7889 "(t/cf (inc 1))"
description: Type check inc expression
```
Returns: `Long`

**With expected type:**
```
command: clj-nrepl-eval -p 7889 "(t/cf (fn [x] (inc x)) [t/Int :-> t/Int])"
description: Type check function with expected type
```
Returns: `[(ct/I [ct/Int :-> ct/Int] Fn) {:then tt, :else ff}]`

**Detecting type errors:**
```
command: clj-nrepl-eval -p 7889 "(t/cf (fn [x] (inc x)) [t/Str :-> t/Int])"
description: Type check with wrong type (will error)
```
Returns: Type error showing `inc` cannot accept `String`

### Common Type Annotations

| Type | Meaning | Example |
|------|---------|---------|
| `t/Int` | Integer | `42` |
| `t/Str` | String | `"hello"` |
| `t/Bool` | Boolean | `true` |
| `t/Num` | Number | `3.14` |
| `t/Any` | Any type | Escape hatch |
| `t/Nothing` | Bottom type | Unreachable code |
| `[A :-> B]` | Function type | `[t/Int :-> t/Str]` |
| `(t/Vec X)` | Vector of X | `(t/Vec t/Int)` |
| `(t/Map K V)` | Map type | `(t/Map t/Str t/Int)` |
| `(t/Option X)` | X or nil | `(t/Option t/Str)` |

### Checking Functions

**Anonymous function with expected type:**
```clojure
(t/cf (fn [x] (+ x 1)) [t/Int :-> t/Int])
```

**Higher-order functions infer types:**
```clojure
(t/cf (map #(inc %) [1 2 3]))  ;; Infers types from usage
```

**Symbolic closures - type checked when called:**
```clojure
;; This passes - body not checked until called
(t/cf #(inc %))

;; This fails - body checked because function is invoked
(t/cf (let [f #(inc %)] (f true)))
```

### Workflow: Type Check at REPL

**Step 1: Check a single expression**
```
command: clj-nrepl-eval -p 7889 "(t/cf (+ 1 2))"
description: Type check addition
```

**Step 2: Check function with expected type**
```
command: clj-nrepl-eval -p 7889 "(t/cf (fn [a b] (+ a b)) [t/Int t/Int :-> t/Int])"
description: Type check two-arg function
```

**Step 3: Check entire namespace**
```
command: clj-nrepl-eval -p 7889 "(t/check-ns-clj 'csb.models)"
description: Type check models namespace
```

**Step 4: Check entire src directory**
```
command: clj-nrepl-eval -p 7889 "(dev/type-check)"
description: Type check all src files
```

### Common Type Errors and Fixes

| Error | Cause | Fix |
|-------|-------|-----|
| `could not be applied to arguments` | Wrong argument type | Check function signature |
| `Expected X, found Y` | Type mismatch | Add/fix type annotation |
| `Cannot find type annotation` | Missing annotation | Add `t/ann` before function |
| `StackOverflowError` | Infinite type inference | Add explicit type annotations |

### Tips for LLM Agents

1. **Use `t/cf` first** - Quick feedback before full namespace check
2. **Add expected types** - `(t/cf expr type)` gives better errors
3. **Check incrementally** - Test small expressions, not whole files
4. **Annotate when stuck** - If inference fails, add explicit `t/ann`
5. **Use `t/tc-ignore`** - Wrap problematic code to skip type checking

## Fixing Delimiter Errors with clj-paren-repair

Use `clj-paren-repair` to automatically fix unbalanced parentheses, brackets, and braces in Clojure files.

### When to Use

- After editing Clojure code that has mismatched delimiters
- When `bb lint` or `bb ci` fails due to parse errors
- To quickly fix syntax errors before running tests

### Basic Usage

```bash
# Fix a single file
clj-paren-repair path/to/file.clj

# Fix multiple files
clj-paren-repair src/csb/models.clj src/csb/config.clj
```

### Tool Behavior

| Input State | Output | Status |
|-------------|--------|--------|
| Valid code | No changes | `No changes needed` |
| Missing delimiters | Adds missing `)`, `]`, `}` | `Delimiter errors fixed and formatted` |
| Already valid | Formats only | `No changes needed` |

### Output Examples

**Fixing missing parenthesis:**
```
Input:  (defn foo [x] (+ x 1)
Output: (defn foo [x] (+ x 1))
```

**Fixing missing bracket and brace:**
```
Input:  (defn bar [x y] {:a x :b y
Output: (defn bar [x y] {:a x :b y})
```

### Command Output

```
clj-paren-repair Results
========================

  src/csb/models.clj: Delimiter errors fixed and formatted [delimiter-fixed]
  src/csb/config.clj: No changes needed

Summary:
  Success: 2
  Failed:  0
```

### Bash Tool Call Examples

**Example 1: Fix a single file**
```
command: clj-paren-repair src/csb/models.clj
description: Fix delimiter errors in models.clj
```

**Example 2: Fix multiple files**
```
command: clj-paren-repair src/csb/models.clj src/csb/controllers.clj
description: Fix delimiters in models and controllers
```

### Important Notes

- **Only works on `.clj`, `.cljs`, `.cljc`, `.edn` files** - other extensions are skipped
- **Modifies files in place** - no backup is created
- **Also formats code** - applies cljfmt formatting after repair
- **Run before `bb ci`** - to fix syntax errors before linting/testing

### Recommended Workflow

1. Edit Clojure code
2. If you suspect delimiter errors → run `clj-paren-repair <file>`
3. Run `bb ci` to verify all checks pass

## File Organization

```
src/csb/
├── main.clj              # Entry point - CLI handling only
├── config.clj            # Configuration record and loading
├── system.clj            # Component system assembly
├── routes.clj            # Bidi route definitions
├── models.clj            # Domain models and Malli schemas
├── controllers.clj       # Business logic handlers
└── components/
    ├── database.clj      # Datalevin database component
    ├── http_server.clj   # HTTP Kit server component
    └── ring_app.clj      # Ring application component

test/csb/
└── *_test.clj            # Test files mirror src structure

resources/
└── db/
    └── schema.edn        # Datalevin schema definitions
```

## Code Patterns

### Namespace Declaration

Always use this exact format with sorted, aligned requires:

```clojure
(ns csb.example
  (:require
   [com.stuartsierra.component :as c]
   [typed.clojure :as t]))
```

### Component Pattern (REQUIRED for stateful resources)

```clojure
(ns csb.components.example
  (:require
   [com.stuartsierra.component :as c]
   [typed.clojure :as t]))

;; Type annotation BEFORE defrecord
(t/ann-record ExampleComponent [config :- t/Str
                                connection :- (t/Option SomeType)])

(defrecord ExampleComponent [config connection]
  c/Lifecycle
  (start [this]
    ;; Initialize resources, return updated component
    (assoc this :connection (create-connection config)))
  (stop [this]
    ;; Clean up resources, return component
    (when connection
      (close-connection connection))
    (assoc this :connection nil)))

;; Constructor function with type annotation
(t/ann ->example-component [t/Str :-> ExampleComponent])
(defn ->example-component [config]
  (map->ExampleComponent {:config config}))
```

### Record with Type Annotations

```clojure
;; Define record first
(defrecord Config [port db-path])

;; Then annotate (can be before or after, but be consistent)
(t/ann-record Config [port :- t/Int
                      db-path :- t/Str])
```

### Function Type Annotations

```clojure
;; Annotation before function definition
(t/ann process-data [t/Str t/Int :-> t/Bool])
(defn process-data [name count]
  (pos? count))

;; For functions returning the unit type (side effects)
(t/ann log-event! [t/Str :-> nil])
(defn log-event! [msg]
  (mu/log ::event :message msg)
  nil)
```

### Malli Schema Definitions

Place in `csb.models`:

```clojure
(ns csb.models
  (:require
   [malli.core :as m]))

(def User
  [:map
   [:id :uuid]
   [:name :string]
   [:email :string]])

(def CreateUserRequest
  [:map
   [:name :string]
   [:email :string]])

;; Validation helper
(defn valid-user? [data]
  (m/validate User data))
```

### Route Definitions (Bidi)

Place in `csb.routes`:

```clojure
(ns csb.routes
  (:require
   [bidi.bidi :as bidi]))

(def routes
  ["/" {"api" {"/users" {:get :list-users
                         :post :create-user
                         ["/" :id] {:get :get-user
                                    :delete :delete-user}}}}])
```

### Liberator Resource

```clojure
(ns csb.controllers
  (:require
   [liberator.core :refer [defresource]]))

(defresource user-resource [db]
  :available-media-types ["application/json"]
  :allowed-methods [:get :post]
  :handle-ok (fn [ctx]
               (get-users db)))
```

### Database Operations (Datalevin)

```clojure
(ns csb.db
  (:require
   [datalevin.core :as d]))

;; Transact data
(d/transact! conn [{:db/id -1
                    :user/name "Alice"
                    :user/email "alice@example.com"}])

;; Query data
(d/q '[:find ?e ?name
       :where [?e :user/name ?name]]
     (d/db conn))
```

### Error Handling (Failjure)

```clojure
(ns csb.services
  (:require
   [failjure.core :as f]))

(defn fetch-user [id]
  (f/try*
    (let [user (db/get-user id)]
      (if user
        user
        (f/fail "User not found")))))

;; Using the result
(f/if-let-ok? [user (fetch-user 123)]
  (handle-success user)
  (handle-error user))  ; user is the failure here
```

## Test Patterns

### Basic Test Structure

```clojure
(ns csb.feature-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [matcher-combinators.test :refer [match?]]))

(deftest feature-works
  (testing "specific behavior description"
    (is (= expected actual))))

;; With matcher-combinators for complex assertions
(deftest complex-data-test
  (testing "returns expected structure"
    (is (match? {:status 200
                 :body {:users seq?}}
                (handler request)))))
```

### Component Testing

```clojure
(ns csb.components.database-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [com.stuartsierra.component :as c]
   [csb.components.database :refer [->Database]]))

(def test-db-path "test-db")

(defn with-test-db [f]
  (let [db (c/start (->Database test-db-path))]
    (try
      (f db)
      (finally
        (c/stop db)))))

(deftest database-lifecycle
  (testing "database starts and stops cleanly"
    (with-test-db
      (fn [db]
        (is (some? (:connection db)))))))
```

## Anti-Patterns (DO NOT USE)

### Wrong: Global state

```clojure
;; BAD - global mutable state
(def db-conn (atom nil))

(defn init! []
  (reset! db-conn (connect)))
```

### Wrong: Missing type annotations

```clojure
;; BAD - no type annotation
(defrecord Service [config])
```

### Wrong: Println logging

```clojure
;; BAD - use mulog instead
(println "Processing request" request-id)
```

### Wrong: Inline component creation

```clojure
;; BAD - components should be assembled in system.clj
(defn handler [request]
  (let [db (->Database "path")]  ; Wrong!
    ...))
```

## Decision Guide

### When to create a new component?

Create a component when you have:

- External connections (database, HTTP clients, message queues)
- Resources that need cleanup (file handles, threads)
- Stateful services that other parts of the system depend on

### When to use Malli vs Typed Clojure?

- **Malli**: Runtime validation at boundaries (API inputs, external data)
- **Typed Clojure**: Compile-time type checking for internal code

### Where to put new code?

| Type of code | Location |
|--------------|----------|
| New component | `src/csb/components/name.clj` |
| Business logic | `src/csb/controllers.clj` or new controller file |
| Data shapes | `src/csb/models.clj` |
| Routes | `src/csb/routes.clj` |
| Database schema | `resources/db/schema.edn` |
| Configuration | `src/csb/config.clj` |

## Formatting Rules

The project uses cljstyle with these key settings (from `.cljstyle`):

- List indent: 1 space
- Namespace indent: 1 space
- Inline comments: `; ` prefix (space after semicolon)
- No forced blank lines between top-level forms

Run `bb fmt` to auto-fix formatting before committing.

## Pre-Submission Checklist

Before marking any task complete:

1. [ ] `bb fmt` - Format code
2. [ ] `bb lint` - No linting errors
3. [ ] `bb test` - All tests pass
4. [ ] Type annotations added for new records/functions
5. [ ] Tests written for new functionality
6. [ ] No new warnings in output

Or simply run `bb ci` which does all checks.
