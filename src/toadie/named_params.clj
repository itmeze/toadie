(ns toadie.named-params
  (:require [clojure.string :as str]))

(defn to-statement
  ([s] [s])
  ([s params]
   (let [found (re-seq #"@:\w+" s)]
     (loop [query s
            vals []
            els found]
       (if
         (= 0 (count els))
         (into [query] vals)
         (let [upd (str/replace-first query (first els) "?")
               key (keyword (subs (first els) 2))
               pval (key params)]
           (recur upd (conj vals pval) (rest els))))))))
