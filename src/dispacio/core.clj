(ns dispacio.core)

(defn <-state [poly]
  (poly :poly/state))

(defn- get-parent [pfn x] (->> (parents x) (filter pfn) first))

(defn- in-this-or-parent-prefs? [poly v1 v2 f1 f2]
  (if-let [p (-> @(-> poly <-state) (get-in [:prefer v1]))]
    (or (contains? p v2) (get-parent f1 v2) (get-parent f2 v1))))

(defn- default-sort [v1 v2]
  (if (= v1 :poly/default)
    1
    (if (= v2 :poly/default)
      -1
      0)))

(defn- pref [poly v1 v2]
  (if (-> poly (in-this-or-parent-prefs? v1 v2 #(pref poly v1 %) #(pref poly % v2)))
    -1
    (default-sort v1 v2)))

(defn- sort-disp [poly]
  (if-let [sort-fn (-> @(-> poly <-state) :sort-fn)]
    (sort-fn poly)
    (swap! (-> poly <-state) update :dispatch
      #(->> % (sort-by first (partial pref poly)) vec))))

(defn prefer [poly v1 v2]
  (swap! (-> poly <-state) update-in [:prefer v1] #(-> % (or #{}) (conj v2)))
  (sort-disp poly)
  nil)




(defn- get-disp [poly filter-fn]
  (-> @(-> poly <-state) (get :dispatch) (->> (filter filter-fn)) first))

(defn- pred->disp [poly pred]
  (get-disp poly #(-> % first (= pred))))

(defn- pred->poly-fn [poly pred]
  (-> poly (pred->disp pred) second))

(defn- check-args-length [disp args]
  ((if (= '& (-> disp (nth 3) first)) >= =) (count args) (nth disp 2)))

(defn- args-are? [disp args]
  (or (isa? (vec args) (first disp)) (isa? (mapv class args) (first disp))))

(defn- check-dispatch-on-args [disp args]
  (if (-> disp first vector?)
    (-> disp (args-are? args))
    (-> disp first (apply args))))

(defn- disp*args? [disp args]
  (and (check-args-length disp args)
    (check-dispatch-on-args disp args)))

(defn- args->poly-fn [poly args]
  (-> poly (get-disp #(disp*args? % args)) second))



(require '[clojure.main :as main])

(defn poly-impl [poly args]
  (if-let [poly-fn (-> poly (args->poly-fn args))]
    (-> poly-fn (apply args))
    (if-let [default-poly-fn (-> poly (pred->poly-fn :poly/default))]
      (-> default-poly-fn (apply args))
      (throw
       (ex-info
        (str "No dispatch in polymethod "
          (#'main/java-loc->source (str poly) 'invoke)
          " for arguments: "
          (apply pr-str args))
        {})))))

(defn- remove-disp [poly pred]
  (when-let [disp (pred->disp poly pred)]
    (swap! (-> poly <-state) update :dispatch #(->> % (remove #{disp}) vec))))

(defn- til& [args]
  (count (take-while (partial not= '&) args)))

(defn- add-disp [poly poly-fn pred params]
  (swap! (-> poly <-state) update :dispatch
    #(-> % (or []) (conj [pred poly-fn (til& params) (filter #{'&} params)]))))

(defn setup-poly [poly poly-fn pred params]
  (remove-disp poly pred)
  (add-disp poly poly-fn pred params)
  (sort-disp poly))


(defmacro defpoly [poly-name & [init]]
  `(let [state# (atom (or ~init {}))]
     (defn ~poly-name [& args#]
       (if (= (-> args# first) :poly/state)
         state#
         (poly-impl ~poly-name args#)))))

(defn stringify-class [C]
  (apply str (drop 5 (remove #{\space} (str C)))))

(defn classify-key [k]
  (str (namespace k) "-" (name k)))

(defn fix-classes-and-keywords [v]
  (if (keyword? v) (classify-key v)
    (if (class? v)
      (stringify-class v)
      "")))

(defn mk-poly-name [poly-name pred params]
  (let [param-name (apply str (interpose "_" params))
        pred-name (if (vector? pred)
                    (apply str (interpose "_" (mapv fix-classes-and-keywords pred)))
                    (if (keyword? pred)
                      (classify-key pred)
                      ""))]
    (symbol (str poly-name ">" pred-name ">" param-name))))

(defmacro defp [poly-name pred params body]
  `(do (let [poly-fn# (fn ~(mk-poly-name poly-name pred params) ~params ~body)]
         (setup-poly ~poly-name poly-fn# ~pred (quote ~params)))
     ~poly-name))

;
;
;
; (defpoly myinc number? [x] (inc x))
;
; (defpoly myinc string? [x] (inc (read-string x)))
;
; (defn -main [& args]
;   (println (apply myinc args)))
