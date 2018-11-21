# dispacio

dispacio is an _"**open** dispatch"_ system for Clojure.

## Table of Contents

* [Why?](#why?)
* [Why Not?](#why-not?)
* [Getting Started](#getting-started)
* [Polymethods](#polymethods)
* [Deriving isa? Dispatch](#deriving-isa?-dispatch)
* [Prefer Dispatch Functions](#preferring-dispatch-functions)
* [Spec Validation Dispatch](#spec-validation-dispatch)
* [Choose Your Own Adventure](#choose-your-own-adventure)
* [Bugs](#bugs)
* [Help!](#help)

## Why?

dispacio is similar to a _predicate dispatch_ system.

[Predicate dispatch](https://en.wikipedia.org/wiki/Predicate_dispatch) systems usually offer a comprehensive mechanism that helps users efficiently match against different aspects of arguments being passed to polymorphic functions.

_Open dispatch_, on the other hand, lets user bring in their own opinionated dispatch system or just dispatch off of simple functions.

As an open dispatch system, dispacio provides no matching conveniences out of the box, except most of what multimethods provides you: `isa?` hierarchies and `prefer`ing one [polymethod](#polymethods) over another explicitly.

Outside of those hierarchies, dispacio simply attempts to run through each defined polymethod, in the order you've defined them, as fast as possible, with a simple "first match wins" strategy.

You can optionally override the default function that handles the sort order of dispatches.

If you know how to use multimethods, you probably won't have a difficult time figuring out how to use dispacio.

## Why Not?

When Rich Hickey designed multimethods, he could have easily made them more open like polymethods in dispacio. However, polymethods put a great deal of responsibility on the user. It is probably quite easy for users to shoot themselves in the foot with polymethods. One could potentially define their polymethods in such an order and manner that dispatching becomes slow or confusing. In that regard, dispacio could be seen a sort of 'open experiment' - letting users explore what dispatch strategies are better or worse for their own needs.

Until this library matures, and if and when there's consensus among seasoned Clojure devs that this idiom is not generally harmful, I would not recommend using this library in production systems.


## Getting Started
``` clojure
:deps  {org.clojure/clojure    {:mvn/version "1.9.0"}
        johnmn3/dispacio       {:git/url "https://github.com/johnmn3/dispacio.git"
                                :sha     "818aa84d3308908fc22340654c1390278b7fb088"}}
```
For the purposes of this tutorial, require dispacio in a REPL and refer `defpoly`, `defp`, `prefer` and `<-state`.
``` clojure
(require '[dispacio.core :refer [defpoly defp prefer <-state]])
```

## Polymethods

Polymethods are similar to Clojure's _multimethods_.

We first declare them with `defpoly`:

``` clojure
(defpoly foo)
;#_=> #'user/foo
```

Or we could initialize the poly with a predefined state map, with potentially hand-written ordering and a customized sort function.

``` clojure
(defpoly foo {:version 2 :dispatch ["..."] :sort-fn "..."})
;#_=> #'user/foo
```
Calling `<-state` on a poly returns its internal atom for your further extension:

``` clojure
@(<-state foo)
;#_=> {:version 2, :dispatch ["..."], :sort-fn "..."}
```
Let's use `defp` to define our first polymethod.
``` clojure
(defpoly myinc)
;#_=> #'user/myinc
(defp myinc number? [x] (inc x))
;#_=> #object[user$eval253$myinc__254 0x2b95e48b "user$eval253$myinc__254@2b95e48b"]
(myinc 1)
;#_=> 2
```
`1` is passed directly to the `number?` function.

Unlike with defmethods, all params are passed to each polymethod's dispatch function.

Again, the first match wins.

### Troubleshooting

Let's pass in some mysterious data.
``` clojure
(myinc "1")
;#_=> Execution error (ExceptionInfo) at dispacio.core/poly-impl (core.clj:75).
;No dispatch in polymethod user/eval253$myinc for arguments: "1"
```
We can see the error is thrown by `poly-impl` because the poly `myinc` has no method for the argument `"1"`.

Let's give `myinc` some default behavior so that we can diagnose this anomoly.
``` clojure
(defp myinc :poly/default [x] (inc x))
;#_=> #object[user$eval253$myinc__254 0x2b95e48b "user$eval253$myinc__254@2b95e48b"]
(myinc "1")
;#_=> Execution error (ClassCastException) at user/eval268$myinc>poly-default>x (REPL:1).
;java.lang.String cannot be cast to java.lang.Number
```
Mmmm, we're passing a string to something that expects a number...

Notice that reference to `user/eval268$myinc>poly-default>x` attempts to inform us which polymethod threw the error. Specifically, it was the one named `myinc`, with a predicate of `:poly/default`, translated to `poly-default`, and an argument of `x`.

With this information, we can tell that the the default implementation we just created is passing the error `java.lang.String cannot be cast to java.lang.Number`.

Let's add a new implementation for strings.
``` clojure
(defp myinc string? [x] (inc (read-string x)))
;#_=> #object[user$eval253$myinc__254 0x2b95e48b "user$eval253$myinc__254@2b95e48b"]
(myinc "1")
;#_=> 2
```
That's better.

But what about multiple arguments? Just make sure your dispatch function conforms to the manner in which you're passing in arguments.
``` clojure
(defp myinc
  #(and (number? %1) (number? %2) (->> %& (filter (complement number?)) empty?))
  [x y & z]
  (inc (apply + x y z)))
;#_=> #object[user$eval253$myinc__254 0x2b95e48b "user$eval253$myinc__254@2b95e48b"]
(myinc 1 2 3)
;#_=> 7
(myinc 1 2 3 "4")
;#_=> Execution error (ArityException) at dispacio.core/poly-impl (core.clj:73).
;Wrong number of args (4) passed to: user/eval268/myinc>poly-default>x--269
```
Because we are not catching strings on more than one argument, the last call took the default path, which we can see takes only one argument, `x`.

## Deriving isa? Dispatch

Similar to multimethods, we can use Clojure's `isa?` hierarchy to resolve arguments.
``` clojure
(defpoly foo)
;#_=> #'user/foo
(derive java.util.Map ::collection)
;#_=> nil
(derive java.util.Collection ::collection)
;#_=> nil
```
NOTE: Always put predicate parameters in a vector when you want arguments resolved against `isa?`.
``` clojure
(defp foo [::collection] [c] :a-collection)
;#_=> #object[user$eval301$foo__302 0x3f363cf5 "user$eval301$foo__302@3f363cf5"]
(defp foo [String] [s] :a-string)
;#_=> #object[user$eval301$foo__302 0x3f363cf5 "user$eval301$foo__302@3f363cf5"]
```
Ad hoc hierarchies for dispatch a la carte!
``` clojure
(foo [])
;#_=> :a-collection

(foo "bob")
;#_=> :a-string
```

## Prefer Dispatch Functions

As with multimethods, we can prefer some dispatch functions over others.
``` clojure
(defpoly bar)
;#_=> #'user/bar
(derive ::rect ::shape)
;#_=> nil
(defp bar [::rect ::shape] [x y] :rect-shape)
;#_=> #object[user$eval325$bar__326 0x366ef90e "user$eval325$bar__326@366ef90e"]
(defp bar [::shape ::rect] [x y] :shape-rect)
;#_=> #object[user$eval325$bar__326 0x366ef90e "user$eval325$bar__326@366ef90e"]
(bar ::rect ::rect)
;#_=> :rect-shape
```
We didn't pass in an exact match but, because `::rect` derives from `::shape`, we could match on both. The first dispatch function we find, in the order we made them, that matches the arguments will return its implementation. But what if, for this poly, we wanted the `:shape-rect` implementation?

To override the default behavior of the hierarchy, use `prefer`.
``` clojure
(prefer bar [::shape ::rect] [::rect ::shape])
;#_=> nil
(bar ::rect ::rect)
;#_=> :shape-rect
```
See the [official docs on multimethods](https://clojure.org/reference/multimethods) for more context.

## Spec Validation Dispatch

Let's try an example from Clojure's docs on spec.
``` clojure
(require '[clojure.spec.alpha :as s])
(s/def :animal/kind string?)
(s/def :animal/says string?)
(s/def :animal/common (s/keys :req [:animal/kind :animal/says]))
(s/def :dog/tail? boolean?)
(s/def :dog/breed string?)
(s/def :animal/dog (s/merge :animal/common
                            (s/keys :req [:dog/tail? :dog/breed])))
```
We can leverage spec hierarchies to do very complex dispatching.
``` clojure
(defpoly make-noise)
;#_=> #'user/make-noise
(defp make-noise (partial s/valid? :animal/dog)
  [animal]
  (println (-> animal :dog/breed) "barks" (-> animal :animal/says)))
;#_=> #object[user$eval373$make_noise__374 0x2b491fee "user$eval373$make_noise__374@2b491fee"]
(make-noise
  {:animal/kind "dog"
   :animal/says "woof"
   :dog/tail? true
   :dog/breed "retriever"})
;#_=> retriever barks woof
;nil
```
## Choose Your Own Adventure

You can supply a dispatch function that catches in all cases and looks elsewhere for resolution information. You're freedom to choose is completely ad-hoc, arbitrary and yours alone.

You could bring in `core.logic`, `core.match` or any other number of inference systems to define your resolution strategy. The world's your oyster. And remember: if there's something you don't like about how Rich Hickey is maintaining Clojure, you can _always_ massage the language into something more suitable for your needs - you don't need to ask for Rich's permission. As long as the core Clojure team keeps things open in this way, we're good!

## Bugs

If you find a bug, submit a
[Github issue](https://github.com/johnmn3/dispacio/issues).

## Help

This project is looking for team members who can help this project succeed!
If you are interested in becoming a team member please open an issue.

## License

Copyright © 2018 John M. Newman III

Distributed under the MIT License. See LICENSE
