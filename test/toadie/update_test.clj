(ns toadie.update-test
  (:require [clojure.test :refer :all]
            [toadie.core :as toadie]
            [clojure.java.jdbc :as sql]
            [clj-time.core :as time]
            [clj-time.coerce :as c]
            [environ.core :refer [env]]
            [toadie.test-helpers :refer :all]))

(use-fixtures :each drop-tables)

(deftest update-with-id
  (testing "saving object with an id should update it"
    (let [inserted (toadie/save test-store :people {:name "michal"})
          updated  (toadie/save test-store :people (assoc inserted :name "maria"))
          result   (toadie/query test-store :people {})]
      (is (= (count result) 1))
      (is (= (:name (first result)) "maria"))
      (is (= (:id inserted) (:id updated) (:id (first result)))))))
