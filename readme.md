# clojure-software-builder

A LLM Coding Tool designed for the Clojure programming language.

## Project Rationale

Software development is changing rapidly. LLM based coding agents can
be an effective method for developing software.

The Clojure language is position to take advange of this change. Using
the Clojure programming language allows for you to build small,
focused, immutabled and well tested software applications.

Additionally, there is some edivnce that Clojure is one of the most
Concise languages for encoding in LLM style tokenization. See this
[blog
post](https://martinalderson.com/posts/which-programming-languages-are-most-token-efficient/)
for mode details.

The mainstream LLM companies are focusing on the most commonly used
languages. This makes sense, they have limited resources and need to
focus on their business.

As Clojure engineerins, we want to Clojure to be releveant in the age
of LLM develop systems. This project is an attempt to do that and
highlight Clojure's strengths in a LLM driven world.

## Design Rationale

Clojure-software-builder is an HTML application built using datastar
and a Clojure/Dataelvin backend. It is designed to boardly follow
common Clojure development practices in order to make it easier to
contribute.

We use the [TypedClojure](https://typedclojure.org/) dialect of the
Clojure programming language.

## Techology Stack

- Clojure
- TypedClojure
- HTTP-kit
- Bidi
- Liberator
- DataStar
- Pure-css
- Code Mirror

## Development

### Lint

```shell
bb lint
```

### Test

```shell
bb test
```

### Type-check

Type checking is enforced via our test system. Each namespace in src
should have a corresponding namespace in the test folder. That test
namespace should always use `TypedClojure` to validate the namespace.

Like the following

```clojure
(ns csb.main-test
  (:require
   [typed.clojure :as t]
   [csb.main]
   [clojure.test :refer [deftest is testing]]))

(deftest type-check
  (testing "checking-types-main"
    (is (t/check-ns-clj 'csb.main))))

```

```shell
bb test
```

### Build

### Run development version

```shell
bb main --help
```
