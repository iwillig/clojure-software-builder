# LLM Agent Instructions

Instructions for coding agents working on the Clojure Software Builder project.

## Critical Rules

### MUST DO
1. **Always run `bb ci` before completing any task** - runs formatting, linting, and tests
2. **Add type annotations** using `typed.clojure` for all new records and public functions
3. **Follow the Component pattern** for any new system components
4. **Write tests** for new functionality in the corresponding `test/csb/` directory

### MUST NOT
1. **Never commit code that fails `bb ci`**
2. **Never use `def` for mutable state** - use atoms within components if needed
3. **Never add dependencies without explicit user approval**
4. **Never bypass Component lifecycle** - all stateful resources must be components
5. **Never use `println` for logging** - use `mulog` instead
6. **Never use emojis** - in code, comments, commit messages, documentation, or any output

## Commands Reference

```bash
bb ci              # REQUIRED: Run before completing work (clean -> fmt-check -> lint -> test)
bb test            # Run all tests with Kaocha
bb lint            # Run clj-kondo linter
bb fmt             # Fix code formatting
bb fmt-check       # Check formatting without fixing
bb clean           # Remove build artifacts
bb nrepl           # Start development REPL on port 7889
bb main            # Run the application
```

### Running Single Tests

```bash
# Run a specific test namespace
clojure -M:jvm-base:dev:test --focus csb.models-test

# Run a specific test var
clojure -M:jvm-base:dev:test --focus csb.models-test/type-check

# Via nREPL (if running bb nrepl)
clj-nrepl-eval -p 7889 "(k/run 'csb.main-test)"
clj-nrepl-eval -p 7889 "(k/run 'csb.main-test/my-test)"
```

## Code Style Guidelines

### Namespace Declaration
Use sorted, aligned requires with single-space indent:

```clojure
(ns csb.example
  (:require
   [com.stuartsierra.component :as c]
   [typed.clojure :as t])
  (:import
   (java.io
    PushbackReader)))

(set! *warn-on-reflection* true)
```

### Type Annotations
Annotate BEFORE the definition:

```clojure
;; For records
(t/ann-record Database [db-path :- t/Str
                        connection :- t/Any])
(defrecord Database [db-path connection] ...)

;; For functions
(t/ann process-data [t/Str t/Int :-> t/Bool])
(defn process-data [name count] (pos? count))

;; For side-effecting functions (return nil)
(t/ann log-event! [t/Str :-> nil])
(defn log-event! [msg] (u/log ::event :message msg) nil)

;; Type aliases
(t/defalias Result (t/All [a] (t/U a Failure)))
```

### Component Pattern (REQUIRED for stateful resources)

```clojure
(t/ann-record ExampleComponent [config :- t/Str
                                connection :- (t/Option SomeType)])
(defrecord ExampleComponent [config connection]
  c/Lifecycle
  (start [this] (assoc this :connection (create-connection config)))
  (stop [this]
    (when connection (close-connection connection))
    (assoc this :connection nil)))

(t/ann new-example-component [t/Str :-> ExampleComponent])
(defn new-example-component [config]
  (map->ExampleComponent {:config config}))
```

### Error Handling (Failjure)

```clojure
(defn fetch-user [id]
  (f/try*
    (if-let [user (db/get-user id)] user (f/fail "User not found"))))

(f/if-let-ok? [user (fetch-user 123)]
  (handle-success user)
  (handle-error user))
```

### Logging (Mulog)

```clojure
(u/log ::operation-name :key value :other-key other-value)
```

## Test Patterns

```clojure
(ns csb.feature-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [typed.clojure :as t]))

(deftest feature-works
  (testing "specific behavior" (is (= expected actual))))

;; Type checking test (include in every namespace test)
(deftest type-check
  (testing "types are valid" (is (t/check-ns-clj 'csb.feature))))
```

## Formatting Rules (cljstyle)

- List indent: 1 space, namespace indent: 1 space
- Inline comments: `; ` prefix (space after semicolon)
- Run `bb fmt` to auto-fix

## Common Type Annotations

| Type | Meaning |
|------|---------|
| `t/Int` | Integer |
| `t/Str` | String |
| `t/Bool` | Boolean |
| `t/Any` | Any type (escape hatch) |
| `[A :-> B]` | Function A to B |
| `(t/Vec X)` | Vector of X |
| `(t/Map K V)` | Map from K to V |
| `(t/Option X)` | X or nil |

## Where to Put New Code

| Type of code | Location |
|--------------|----------|
| New component | `src/csb/components/name.clj` |
| Business logic | `src/csb/controllers.clj` |
| Data shapes/types | `src/csb/models.clj` |
| Routes | `src/csb/routes.clj` |
| Type annotations for libs | `src/csb/annotations/` |

## Pre-Submission Checklist

Run `bb ci` which executes:
1. `bb clean` - Remove build artifacts
2. `bb fmt-check` - Verify formatting
3. `bb lint` - No linting errors
4. `bb test` - All tests pass

Also verify:
- Type annotations added for new records/functions
- Tests written for new functionality
- No `println` statements (use mulog)