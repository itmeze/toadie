(ns toadie.batch-insert
  (:require [clojure.test :refer :all]
            [toadie.core :as toadie]
            [clojure.java.jdbc :as sql]
            [clj-time.core :as time]
            [clj-time.coerce :as c]
            [environ.core :refer [env]]
            [toadie.test-helpers :refer :all]))

(use-fixtures :each drop-tables)

(def people
  [{:name "a" :surname "b" :age 42}{:name "c" :surname "d" :age 43}{:name "e" :surname "f" :age 44}])

(deftest insert
  (testing "after batch-insert to database"
    (let [inserted (toadie/batch-insert test-store :people people)]
      (testing "should return count of inserted elements"
        (is (= inserted 3))))))
