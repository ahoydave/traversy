(ns traversy.lens)

(defn lens
  "Construct a lens from a focus :: x -> seq and an fmap :: f x -> x."
  [focus fmap]
  {:focus focus :fmap fmap})

(defn view
  "Return a seq of the lens' foci."
  [x lens]
  ((:focus lens) x))

(defn view-single
  "Return the sole focus, throwing an error if there are other foci."
  [x lens]
  (let [[focus & other-foci] (view x lens)]
    (assert (nil? other-foci) "'view' should only be used when the caller can guarantee there is a single focus.")
    focus))

(defn update
  "Apply f to the foci of x, as specified by lens."
  [x lens f]
  ((:fmap lens) f x))

(defn put
  "When supplied as the f to update, sets all the foci to x."
  [x]
  (constantly x))

(def delete-entry
  "When supplied as the f to update an entry, deletes the foci of the lens."
  (put nil))

(defn fapply [f x] (f x))

(def it
  "The identity lens (under 'combine')."
  (lens list fapply))

(defn fconst [f x] x)

(def nothing
  "The null lens. The identity under 'both'."
  (lens (constantly []) fconst))

(defn zero [x]
  (cond
    (map? x) {}
    (set? x) #{}
    :otherwise []))

(defn map-conj [f x] (->> x (map f) (reduce conj (zero x))))

(def each
  "A lens from collection -> item."
  (lens seq map-conj))

(def index (partial map vector (range)))
(defn findexed [f x] (map (comp second f) (index x)))

(defn by-key
  "Update a key/value pair's value by looking up the key in 'associative',
  leaving the pair intact if a corresponding value is not found."
  [associative]
  (fn [[k v]] [k (get associative k v)]))

(def indexed
  "A lens from sequence -> index/item pair."
  (lens index findexed))

(defn fnth [n f [x & xs]]
  (if (zero? n)
    (cons (f x) xs)
    (cons x (fnth (dec n) f xs))))

(defn xth
  "A lens from collection -> nth item."
  [n]
  (lens (comp list #(nth % n)) (partial fnth n)))

(defn fapply-in [path f x] (update-in x path f))

(defn in
  "A lens from map -> value at path."
  [path]
  (lens (fn [x] (list (get-in x path))) (partial fapply-in path)))

(defn fwhen [applicable? f x] (if (applicable? x) (f x) x))
(defn fsome [applicable? f x] (map-conj (partial fwhen applicable? f) x))

(defn only
  "A lens from collection -> applicable items."
  [applicable?]
  (lens (partial filter applicable?) (partial fsome applicable?)))

(defn combine
  "Combine two lenses to form a new lens."
  [outer inner]
  (lens
    (fn [x] (mapcat #(view % inner) (view x outer)))
    (fn [f x] (update x outer #(update % inner f)))))

(defn *>
  "Combine lenses to form a new lens."
  [& lenses]
  (reduce combine it lenses))

(def maybe
  "A lens to an optional value."
  (*> (lens (comp list list) fapply) (only (complement nil?))))

(defn both
  "Combine two lenses in parallel to form a new lens."
  [one another]
  (lens
    (fn [x] (concat (view x one) (view x another)))
    (fn [f x] (-> x (update one f) (update another f)))))

(defn +>
  "Combine lenses in parallel to form a new lens."
  [& lenses]
  (reduce both nothing lenses))

(def all-entries
  "A lens from map -> each entry."
  each)

(def all-values
  "A lens from map -> each value."
  (*> all-entries (in [1])))

(def all-keys
  "A lens from map -> each key."
  (*> all-entries (in [0])))

(defn select-entries
  "A lens from map -> the entries corresponding to ks."
  [ks]
  (only (fn [[k v]] ((set ks) k))))
