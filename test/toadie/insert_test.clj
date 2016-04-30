(ns toadie.insert-test
  (:require [clojure.test :refer :all]
            [toadie.core :as toadie]
            [clojure.java.jdbc :as sql]
            [clj-time.core :as time]
            [clj-time.coerce :as c]
            [environ.core :refer [env]]
            [toadie.test-helpers :refer :all]))

(use-fixtures :each drop-tables)

(deftest insert
  (testing "after inserting to database"
    (let [inserted (toadie/save test-store :people {:name "maria" :surname "johnson" :age 42})]
      (testing "should assoc id to map"
        (is (> (inserted :id) 0)))
      (testing "should store in database"
        (let [res (toadie/raw-query test-store "select count(*) from people")
              c (count res)]
          (is (= c 1)))))))
