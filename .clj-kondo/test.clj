(ns test
  (:require [dispacio.alpha.core :refer [defp]]))

(defp my-inc string? [x] (inc (read-string x)))

(my-inc "1")
