# dispacio

dispacio is an _"predicate stack dispatch system"_ for Clojure/Script.

## Table of Contents

* [What](#what)
* [Getting Started](#getting-started)
* [Polymethods](#polymethods)
* [Deriving isa? Dispatch](#deriving-isa?-dispatch)
* [Prefer Dispatch Functions](#preferring-dispatch-functions)
* [Spec Validation Dispatch](#spec-validation-dispatch)
* [Function Extension](#function-extension)
* [Choose Your Own Adventure](#choose-your-own-adventure)
* [Bugs](#bugs)
* [Help!](#help)

## What

dispacio is a very simple _predicate dispatch system_ for Clojure and Clojurescript.

[Predicate dispatch](https://en.wikipedia.org/wiki/Predicate_dispatch) systems usually offer a comprehensive mechanism that helps users efficiently match against different aspects of arguments being passed to polymorphic functions.

In _predicate *stack* dispatch_, on the other hand, dispatch predicates are tried in the opposite order that they are defined and the first to return a truthy value wins.

While dispacio provides no sophisticated matching conveniences out of the box, you do get most of what multimethods provides you: `isa?` hierarchies and `prefer`ing one [polymethod](#polymethods) over another explicitly.

## Getting Started
``` clojure
:deps  {net.clojars.john/dispacio {:mvn/version "0.1.0-alpha.3"}}
```
For the purposes of this tutorial, require dispacio in a REPL and refer `defp`.
``` clojure
(require '[dispacio.alpha.core :refer [defp]])
```

## Polymethods

Polymethods are similar to Clojure's [_multimethods_](https://clojure.org/reference/multimethods). In Clojure, `multimethod` definitions take static values to be matched against the return value of a single dispatch function, provided in a `defmulti`.
```clojure
(defmulti my-inc class)
(defmethod my-inc Number [n] (inc n))
```
With `polymethods` each method can have its own dispatch function.
``` clojure
(defp my-inc number? [x] (inc x))
(my-inc 1)
;#_=> 2
```
`1` is passed directly to the `number?` function.

Unlike with defmethods, all params are passed to each polymethod's dispatch function.

#### A Canonical Example
Let's look at a more involved example, inspired by the [Clojure documentation on multimethods](https://clojure.org/about/runtime_polymorphism).

First, a helper function:
```clojure
(defn are-species? [& animals-species]
  (->> animals-species
       (partition 2)
       (map (fn [[animal species]] (= species (:Species animal))))
       (into #{})
       (= #{true})))
```
Then, a series of encounters based on heterogeneous conditions:
```clojure
(defp encounter #(are-species? %1 :Bunny %2 :Lion)
  [b l]
  :run-away)

(defp encounter #(and (:tired %1)
                      (are-species? %1 :Bunny %2 :Lion))
  [b l]
  :hide)

(defp encounter #(are-species? %1 :Lion %2 :Bunny)
  [l b]
  :eat)

(defp encounter #(and (:tired %1)
                      (are-species? %1 :Lion %2 :Bunny))
  [l b]
  :play)

(defp encounter #(= (:Species %1) (:Species %2))
  [b1 b2]
  :mate)

(defp encounter #(and (or (:angry %1) (:angry %2))
                      (are-species? %1 :Lion %2 :Lion))
  [l1 l2]
  :fight)
```
Then let's try it out:
```clojure
(def b1 {:Species :Bunny :tired true})
(def b2 {:Species :Bunny :other :stuff})
(def l1 {:Species :Lion :tired true})
(def l2 {:Species :Lion :angry true})

(encounter b1 b2)
;#_=> :mate
(encounter b1 l1)
;#_=> :hide
(encounter b2 l1)
;#_=> :run-away
(encounter l1 b1)
;#_=> :play
(encounter l2 b1)
;#_=> :eat
(encounter l1 l2)
;#_=> :fight
(encounter l1 (assoc l2 :angry false))
;#_=> :mate
```
Notice that a `polymethod`'s predicate functions are evaluated in the opposite order they are defined. In the example above, the condition `(= (:Species %1) (:Species %2))` will catch all cases where the species is the same, causing them to `:mate`. The `:fight`ing lions condition is then defined, which shadows the _same species_ logic of the prior condition, but only when a lion is angry.

This is an important distinction between a `polymethod` and a `defmethod`: The static values associated with a `defmethod`, checked against the return value of a `defmulti`, make a set that will match exclusively of one another. `polymethod`s on the other hand can be defined disjointly or their predicative scope can overlap. A more generally defined predicate can shadow a more specifically defined predicate if it was defined after the more specifically defined predicate. Therefore, it is probably best to define your more general, catch-all predicates earlier rather than later. That is, unless we want a more general case to short-curcuit the rest of the stack, like we did with the `:mate`ing example - we could have defined that one first, but this way we don't have to check all the other predicates before deciding to `:mate`. Useful for when you're in a rush. However, our `:tired` scenarios had to be defined after their more general `:run-away` and `:eat` scenarios, otherwise they would have been fully shadowed and prevented from catching the condition.

#### Mutual Recursion
There's lots of interesting things you can do with predicate dispatch. Here's a cool recursive definition of zipmap I copped from [this paper on predicate dispatch](https://homes.cs.washington.edu/~mernst/pubs/dispatching-ecoop98.pdf):
```clojure
(defp zip-map #(or (empty? %1) (empty? %2))
  [_ _]
  nil)

(defp zip-map #(and (seq %1) (seq %2))
  [a b]
  (apply merge
         {(first a) (first b)}
         (zip-map (rest a) (rest b))))

(zip-map (range 10) (range 10))
;#_=> {0 0, 7 7, 1 1, 4 4, 6 6, 3 3, 2 2, 9 9, 5 5, 8 8}
```
Now, you wouldn't want to actually do that to replace actual `zipmap`, as it'll run slower and you'll blow your stack for large sequences. But the point is that you can construct mutually recursive definitions with `polymethods` to create interesting algorithms.

#### Across Namespaces

Imagine you want to expose a low-level email function and a high level `polymethod` from a namespace called `emails`:
```clojure
(ns emails
  (:require [dispacio.alpha.core :refer [defp]]))

(defn post-email! [email]
  (println :sending-email :msg (:msg email)))

(defp send! :poly/default
  [email]
  (println :don't-know-what-to-do-with-this email))
```
You can then implement the polymethods across namespaces of different domains:
```clojure
(ns promotion
  (:require [dispacio.alpha.core :refer [defp]]
            [emails :as emails]))

(defp emails/send! #(-> % :email-type (= :promotion))
  [email]
  (emails/post-email!
   (assoc email
          :msg (str "Congrats! You got a promotion " (:name email) "!"))))
```
```clojure
(ns welcome
  (:require [dispacio.alpha.core :refer [defp]]
            [emails :as emails]))

(defp emails/send! #(-> % :file-type namespace (= "welcome"))
  [email]
  (emails/post-email!
   (assoc email
          :msg (str "Welcome! Glad you're here " (:name email) "!"))))
```
```clojure
(ns confirmation
  (:require [dispacio.alpha.core :refer [defp]]
            [emails :as emails]))

(defp emails/send! #(-> % :file-kind (= :confirmation))
  [email]
  (emails/post-email!
   (assoc email
          :msg (str "Confirmed! It's true " (:name email) "."))))
```
And then you could just call the email namespace from jobs namespace or whatever:

```clojure
(ns jobs
  (:require [emails :as emails]))

(def files ; <- dispatching on heterogeneous/inconsistent data
  [{:file-kind  :confirmation :name "Bob"}
   {:file-type  :welcome/new  :name "Mary"}
   {:email-type :promotion    :name "Jules"}])

(->> files (map emails/send!))

;#_=> :sending-email :msg Confirmed! It's true Bob.
;#_=> :sending-email :msg Welcome! Glad you're here Mary!
;#_=> :sending-email :msg Congrats! You got a promotion Jules!
;#_=> (nil nil nil)
```
This gives you the cross-namespace abilities of defmethods with the flexibility of arbitrary predicate dispatch.

### Troubleshooting

Let's go back to our `my-inc` example.

Imagine we pass in some mysterious data.
``` clojure
(my-inc "1")
;#_=> Execution error (ExceptionInfo) at dispacio.core/poly-impl (core.clj:75).
;No dispatch in polymethod user/eval253$my-inc for arguments: "1"
```
We can see the error is thrown by `poly-impl` because the poly `my-inc` has no method for the argument `"1"`.

Let's give `my-inc` some default behavior so that we can diagnose this anomoly.
``` clojure
(defp my-inc :poly/default [x] (inc x))
;#_=> #object[user$eval253$my-inc__254 0x2b95e48b "user$eval253$my-inc__254@2b95e48b"]
(my-inc "1")
;#_=> Execution error (ClassCastException) at user/eval268$my-inc>poly-default>x (REPL:1).
;java.lang.String cannot be cast to java.lang.Number
```
Mmmm, we're passing a string to something that expects a number...

Notice that reference to `user/eval268$my-inc>poly-default>x` attempts to inform us which polymethod threw the error. Specifically, it was the one named `my-inc`, with a predicate of `:poly/default`, translated to `poly-default`, and an argument of `x`.

With this information, we can tell that the default implementation we just created is passing the error `java.lang.String cannot be cast to java.lang.Number`.

Let's add a new implementation for strings.
``` clojure
(defp my-inc string? [x] (inc (read-string x)))
;#_=> #object[user$eval253$my-inc__254 0x2b95e48b "user$eval253$my-inc__254@2b95e48b"]
(my-inc "1")
;#_=> 2
```
That's better.

But what about multiple arguments? Just make sure your dispatch function conforms to the manner in which you're passing in arguments.
``` clojure
(defp my-inc
  #(and (number? %1) (number? %2) (->> %& (filter (complement number?)) empty?))
  [x y & z]
  (inc (apply + x y z)))
;#_=> #object[user$eval253$my-inc__254 0x2b95e48b "user$eval253$my-inc__254@2b95e48b"]
(my-inc 1 2 3)
;#_=> 7
(my-inc 1 2 3 "4")
;#_=> Execution error (ArityException) at dispacio.core/poly-impl (core.clj:73).
;Wrong number of args (4) passed to: user/eval268/my-inc>poly-default>x--269
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
