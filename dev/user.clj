(ns user
  (:require [clojure.tools.namespace.repl :as tnr]
            [proto]
            [clojure.repl :refer :all]
            [clojure.test :refer :all]))

(defn start
  [])

(defn reset []
  (tnr/refresh :after 'user/start))

(println "dev/user.clj loaded correctly.")
