# refx.http

Port of [`re-frame-http-fx`][http-fx], using [`cljs-ajax`][cljs-ajax] under the hood.

Provides two flavors:

* `refx.http` -- uses [`ajax-request`][ajax-request], as `:http` and `:http-xhrio`
* `refx.http.easy` -- uses the "easy API" (provides more convenience), as `:http` and `:http-easy`

The simple API requires you to specify full request and response format descriptions,
as described in [Advanced Formats][ajax-formats].  The easy API also support keywords
such as `:json` or `:transit`.

Since both register the `:http` fx key, you should pick one and don't use both.

[ajax-formats]: https://github.com/JulianBirch/cljs-ajax/blob/master/docs/formats.md
[ajax-request]: https://github.com/JulianBirch/cljs-ajax#ajax-request
[cljs-ajax]: https://github.com/JulianBirch/cljs-ajax
[http-fx]: https://github.com/day8/re-frame-http-fx
