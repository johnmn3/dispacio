(ns test
  (:require
   [clojure.edn :as edn]
   [dispacio.alpha.core :refer [defp]]))

(defp my-inc string? [x] (inc (edn/read-string x)))

(my-inc "1")
