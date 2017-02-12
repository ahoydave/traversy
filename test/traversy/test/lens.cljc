(ns traversy.test.lens
  (:refer-clojure :exclude [update])
  (:require [traversy.lens :refer [view-single view update it nothing each only in update
                                   indexed all-entries all-values all-keys select-entries
                                   conditionally put xth combine both *> +> maybe]]
            [smidjen.core #?(:clj :refer :cljs :refer-macros) [fact facts]]))

(fact "The 'it' lens is the identity."
      (-> 9 (view-single it)) => 9
      (-> 9 (view it)) => [9]
      (-> 9 (update it inc)) => 10)

(fact "The 'nothing' lens doesn't have a focus."
      (-> 9 (view nothing)) => '()
      (-> 9 (update nothing inc)) => 9)

(fact "Trying to 'view-single' a lens that doesn't have exactly one focus throws an error."
      (-> [9 10] (view-single each)) => (throws #?(:clj AssertionError :cljs js/Error))
      (-> [] (view-single each)) => (throws #?(:clj AssertionError :cljs js/Error)))

(fact "Using 'view-single' with a multi-focus lens that happens to only have a single focus is fine."
      (-> [9 10] (view-single (only even?))) => 10)

(fact "The 'in' lens focuses into a map based on a path."
      (-> {} (view (in [:foo]))) => []
      (-> {} (update (in [:foo]) str)) => {}
      (-> {:foo 1} (view-single (in [:foo]))) => 1
      (-> {:foo 1} (view (in [:foo]))) => [1]
      (-> {:foo 1} (update (in [:foo]) inc)) => {:foo 2}
      (-> {:foo nil} (update (in [:foo]) str)) => {:foo ""}
      (-> {:foo 1} (view-single (in [:bar] "not-found"))) => "not-found"
      (-> {:foo 1} (view-single (in [:bar]))) => (throws #?(:clj AssertionError :cljs js/Error)))

(fact "Unlike 'update-in', 'in' does nothing if the specified path does not exist."
      (-> {} (update (in [:foo]) identity)) => {})

(fact "The 'each' lens focuses on each item in a sequence."
      (-> [1 2 3] (view each)) => [1 2 3]
      (-> [] (view each)) => '()
      (-> [1 2 3] (update each inc)) => #(and (= % [2 3 4]) (vector? %)))

(fact "The 'each' lens focuses on each element in a set."
      (-> #{1 2 3} (view each) set) => #{1 2 3}
      (-> #{} (view each)) => '()
      (-> #{1 2 3} (update each inc) set) => #{2 3 4})

(fact "The 'each' lens focuses on the entries of a map."
      (-> {:foo 3 :bar 4} (view each) set) => #{[:foo 3] [:bar 4]}
      (-> {} (view each)) => '()
      (-> {:foo 3 :bar 4} (update each (fn [[k v]] [v k]))) => {3 :foo 4 :bar})

(fact "The 'indexed' lens focuses on indexed pairs in a sequence."
      (-> [1 2 3] (view indexed)) => [[0 1] [1 2] [2 3]]
      (-> [1 2 3] (update indexed (fn [[i v]] [i (+ i v)]))) => [1 3 5])

(fact "The 'all-entries' lens focuses on the entries of a map."
      (-> {:foo 3 :bar 4} (view all-entries) set) => #{[:foo 3] [:bar 4]}
      (-> {} (view all-entries)) => '()
      (-> {:foo 3 :bar 4} (update all-entries (fn [[k v]] [v k]))) => {3 :foo 4 :bar})

(fact "The 'all-values' lens focuses on the values of a map."
      (-> {:foo 1 :bar 2} (view all-values) set) => #{1 2}
      (-> {:foo 1 :bar 2} (update all-values inc)) => {:foo 2 :bar 3})

(fact "The 'all-keys' lens focuses on the keys of a map."
      (-> {:foo 1 :bar 2} (view all-keys) set) => #{:foo :bar}
      (-> {:foo 1 :bar 2} (update all-keys {:foo :frag :bar :barp})) => {:frag 1 :barp 2})

(fact "The 'conditionally' lens focuses only on foci that match a condition."
      (-> 1 (view (conditionally odd?))) => [1]
      (-> 1 (view (conditionally even?))) => '()
      (-> {:foo 1 :bar 2} (view (*> (+> (in [:foo]) (in [:bar])) (conditionally odd?)))) => [1]
      (-> 1 (update (conditionally odd?) inc)) => 2
      (-> 1 (update (conditionally even?) inc)) => 1)

(fact "The 'maybe' lens focuses only on foci that are present."
      (-> {:foo 1} (view (*> (in [:foo]) maybe))) => [1]
      (-> {:foo 1} (view (*> (in [:bar]) maybe))) => '()
      (-> {} (view (*> (in [:bar]) maybe))) => '()
      (-> {:foo 1} (view (*> (+> (in [:foo]) (in [:bar])) maybe))) => [1]
      (-> 1 (update maybe inc)) => 2
      (-> nil (update maybe inc)) => nil?)

(fact "The 'only' lens focuses on the items in a sequence matching a condition."
      (-> [1 2 3] (view (only even?))) => [2]
      (-> [1 2 3] (update (only even?) inc)) => [1 3 3]
      (-> #{1 2 3} (update (only even?) inc)) => #{1 3})

(fact "The 'select-entries' lens focuses on entries of a map specified by key."
      (-> {:foo 3 :bar 4 :baz 5} (view (select-entries [:foo :bar])) set) => #{[:foo 3] [:bar 4]}
      (-> {:foo 3 :bar 4 :baz 5} (update (select-entries [:foo :bar]) (fn [[k v]] [v k]))) => {3 :foo 4 :bar :baz 5})

(fact "put sets the value at all the foci of a lens."
      (-> [1 2 3] (update (only even?) (put 7))) => [1 7 3]
      (-> #{1 2 3} (update each (put 7))) => #{7}
      (-> {:foo 3 :bar 4} (update (select-entries [:foo]) (put [:baz 7]))) => {:bar 4 :baz 7})

(fact "The 'xth' lens focuses on the nth item of a sequence."
      (-> [2 3 4] (view-single (xth 1))) => 3
      (-> [2 3 4] (view (xth 1))) => [3]
      (-> [2 3 4] (update (xth 1) inc)) => [2 4 4]
      (-> [2 3 4] (view-single (xth 4 "not found"))) => "not found"
      (-> [2 3 4] (view (xth 4 "not found"))) => ["not found"])

(fact "We can 'combine' single-focus lenses."
      (-> {:foo {:bar 9}} (view-single (combine (in [:foo]) (in [:bar])))) => 9
      (-> {:foo {:bar 9}} (view (combine (in [:foo]) (in [:bar])))) => [9]
      (-> {:foo {:bar 9}} (update (combine (in [:foo]) (in [:bar])) inc)) => {:foo {:bar 10}})

(fact "We can 'combine' multiple-focus lenses with single-focus lenses."
      (-> [{:foo 1} {:foo 2}] (view (combine each (in [:foo])))) => [1 2]
      (-> [{:foo 1} {:foo 2}] (update (combine each (in [:foo])) inc)) => [{:foo 2} {:foo 3}])

(fact "We can 'combine' multiple-focus lenses with multiple-focus lenses."
      (-> [[1 2] [3]] (view (combine each each))) => [1 2 3]
      (-> [[1 2] [3]] (update (combine each each) inc)) => [[2 3] [4]])

(fact "We can combine single-focus lenses with multiple-focus lenses."
      (-> {:foo [1 2]} (view (combine (in [:foo]) each))) => [1 2]
      (-> {:foo [1 2]} (update (combine (in [:foo]) each) inc)) => {:foo [2 3]})

(fact "We can combine n lenses with '*>'."
      (-> {:foo {:bar {:baz 9}}} (view-single (*> (in [:foo]) (in [:bar]) (in [:baz])))) => 9
      (-> {:foo {:bar {:baz 9}}} (view (*> (in [:foo]) (in [:bar]) (in [:baz])))) => [9]
      (-> {:foo {:bar {:baz 9}}} (update (*> (in [:foo]) (in [:bar]) (in [:baz])) inc)) => {:foo {:bar {:baz 10}}})

(fact "We can combine lenses in parallel with 'both'."
      (-> {:foo 8 :bar 9} (view (both (in [:foo]) (in [:bar])))) => [8 9]
      (-> {:foo 8 :bar 9} (update (both (in [:foo]) (in [:bar])) inc)) => {:foo 9 :bar 10})

(fact "We can combine lenses in parallel with '+>'."
      (-> {:foo 8 :bar 9 :baz 10} (view (+> (in [:foo]) (in [:bar]) (in [:baz])))) => [8 9 10]
      (-> {:foo 8 :bar 9 :baz 10} (update (+> (in [:foo]) (in [:bar]) (in [:baz])) inc)) => {:foo 9 :bar 10 :baz 11})
