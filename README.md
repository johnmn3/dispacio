# dispacio

dispacio is an _"predicate stack dispatch system"_ for Clojure/Script.

## Table of Contents

* [Why?](#why?)
* [Why Not?](#why-not?)
* [Getting Started](#getting-started)
* [Polymethods](#polymethods)
* [Deriving isa? Dispatch](#deriving-isa?-dispatch)
* [Prefer Dispatch Functions](#preferring-dispatch-functions)
* [Spec Validation Dispatch](#spec-validation-dispatch)
* [Function Extension](#function-extension)
* [Choose Your Own Adventure](#choose-your-own-adventure)
* [Bugs](#bugs)
* [Help!](#help)

## Why?

dispacio is a very simple _predicate dispatch system_ for Clojure and Clojurescript.

[Predicate dispatch](https://en.wikipedia.org/wiki/Predicate_dispatch) systems usually offer a comprehensive mechanism that helps users efficiently match against different aspects of arguments being passed to polymorphic functions.

In _predicate *stack* dispatch_, on the other hand, dispatch predicates are tried in the order that they are defined and the first to return a truthy value wins.

While dispacio provides no sophisticated matching conveniences out of the box, you do get most of what multimethods provides you: `isa?` hierarchies and `prefer`ing one [polymethod](#polymethods) over another explicitly.

## Why Not?

Multimethods could have been designed to be more open like polymethods in dispacio. However, polymethods put much more responsibility on the user. One could potentially define a set of polymethods in way that becomes slow or confusing. In that regard, dispacio could be seen a sort of 'open experiment' - letting users explore what dispatch strategies are better or worse for their own needs. If you don't need the flexibility that dispacio provides, you're probably better off sticking with multimethods.

## Getting Started
``` clojure
:deps  {johnmn3/dispacio       {:git/url "https://github.com/johnmn3/dispacio.git"
                                :sha     "bdf4f012efd5f96d663b057f3941066075d73363"}}
```
For the purposes of this tutorial, require dispacio in a REPL and refer `defpoly`, `defp`, `prefer` and `<-state`.
``` clojure
(require '[dispacio.alpha.core :refer [defp]])
```

## Polymethods

Polymethods are similar to Clojure's _multimethods_.

We first declare them with `defpoly`:

``` clojure
(defpoly foo)
;#_=> #'user/foo
```

Calling `defpoly` explicitly is optional. Calling `defp` will call `defpoly` for you if necessary.

However, with `defpoly`, we can initialize the poly with a predefined state map, with potentially hand-written ordering and a customized sort function.

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

With this information, we can tell that the default implementation we just created is passing the error `java.lang.String cannot be cast to java.lang.Number`.

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
## Function Extension

In Clojure, we usually extend data types to functions. With polymethods, we can shadow extend functions to arbitrary data types.

``` clojure  
(defp inc string? [x] (inc (read-string x)))
;WARNING: inc already refers to: #'clojure.core/inc in namespace: user, being replaced by: #'user/inc
;#_=> #object[user$eval231$inc__232 0x75ed9710 "user$eval231$inc__232@75ed9710"]
```
Here, we are shadowing the `#'clojure.core/inc` function, while storing that original function as the default polymethod implementation.

``` clojure  
(inc "1")
;#_=> 2
(inc 1)
;#_=> 2
```
Let's extend `assoc` to associate by index on strings:

``` clojure
(defp assoc string? [s i c] (str (subs s 0 i) c (subs s (inc i))))
;WARNING: assoc already refers to: cljs.core/assoc being replaced by: cljs.user/assoc at line 1 <cljs repl>
;#_=> #object[cljs$user$assoc]
(assoc "abc" 2 'x)
;#_=> "abx"
```

For now, you can `:exclude` core functions when referring Clojure in order to suppress var replacement warnings.

## Choose Your Own Adventure

You could bring in `core.logic`, `core.match`, Datomic's datalog queries or any other number of inference systems to define your resolution strategy. The world's your oyster.

## Bugs

If you find a bug, submit a
[Github issue](https://github.com/johnmn3/dispacio/issues).

## Help

This project is looking for team members who can help this project succeed!
If you are interested in becoming a team member please open an issue.

## License

Copyright © 2018 John M. Newman III

Distributed under the MIT License. See [LICENSE](https://github.com/johnmn3/dispacio/blob/master/LICENSE)
