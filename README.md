# refx

[![Build Status](https://img.shields.io/github/actions/workflow/status/ferdinand-beyer/refx/main.yml?branch=main)](https://github.com/ferdinand-beyer/refx/actions)
[![cljdoc](https://cljdoc.org/badge/com.fbeyer/refx)][cljdoc]
[![Clojars](https://img.shields.io/clojars/v/com.fbeyer/refx.svg)][clojars]

[re-frame] port using React hooks instead of [Reagent][reagent].

Designed to be used with [Helix][helix] or [UIx][uix].

## Status

**This library is in alpha state.**  While most of the code was copied
directly from re-frame, and should be pretty stable, refx's additions
need more testing, and the API might still change drastically.

## Installation

Releases are available from [Clojars][clojars].

deps.edn:

```edn
com.fbeyer/refx {:mvn/version "<VERSION>"}
```

Leiningen/Boot:

```edn
[com.fbeyer/refx "<VERSION>"]
```

You can use also use the Git coordinate:

```edn
{:deps {refx.core {:git/url "https://github.com/ferdinand-beyer/refx.git"
                   :git/sha "COMMIT_SHA"
                   :deps/root "core"}}}
```

## Rationale

[See this blog post](https://fbeyer.com/posts/refx-origins/)
for an explanation why I started working on refx.

## Differences from re-frame

* `subscribe` has been replaced with two functions:
  * `sub` returns a subscription _signal_, to be used in input functions
    in `reg-sub`
  * `use-sub` is a React hook that return's a subscription's _value_.
    These are not atoms and don't need to be dereferenced.
* query vectors with `sub` and `use-sub` do not support the rarely used
  extra `dyn-v` argument.  See the next point for a superior alternative.
* query vectors can contain signals returned by `sub` for dynamic
  subscriptions
* subscriptions support other signals than `app-db` as input.  Anything
  that satisfies the `ISignal` protocol, which includes atoms.

## Examples

See the [`examples`](examples/) directory for re-frame's **todomvc**,
implemented with [Helix][helix] and [UIx][uix].

## License

Distributed under the [MIT License](LICENSE).  
Copyright &copy; 2022 [Ferdinand Beyer]

This software uses third-party open-source software.  
See [NOTICE](NOTICE) for individual copyright and license notices.


[cljdoc]: https://cljdoc.org/jump/release/com.fbeyer/refx
[clojars]: https://clojars.org/com.fbeyer/refx
[re-frame]: https://github.com/day8/re-frame
[reagent]: https://github.com/reagent-project/reagent
[uix]: https://github.com/roman01la/uix
[helix]: https://github.com/lilactown/helix

[Ferdinand Beyer]: https://fbeyer.com
