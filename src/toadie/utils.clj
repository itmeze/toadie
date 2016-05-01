(ns toadie.utils)

(defn uuid
  ([] (java.util.UUID/randomUUID))
  ([s] (condp instance? s
         java.util.UUID s
         (java.util.UUID/fromString s))))

(defn random-string []
  (-> (java.util.UUID/randomUUID) (str) (clojure.string/replace #"-" "")))
