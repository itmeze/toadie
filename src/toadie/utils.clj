(ns toadie.utils)

(defn random-string []
  (-> (java.util.UUID/randomUUID) (str) (clojure.string/replace #"-" "")))
