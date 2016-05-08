(ns toadie.advanced-query-test
  (:require [clojure.test :refer :all]
            [toadie.core :as toadie]
            [clojure.java.jdbc :as sql]
            [clj-time.core :as time]
            [clj-time.coerce :as c]
            [environ.core :refer [env]]
            [toadie.test-helpers :refer :all]))

(use-fixtures :each drop-tables)

(def people-multi-search
  [{:name "m1" :age 12 :height 1.86 :member-since (c/to-date (time/date-time 1982 11 30))},
   {:name "m2" :age 13 :height 1.74 :member-since (c/to-date (time/date-time 1982 11 30))}
   {:name "m3" :age 14 :height 2.06}])

(deftest multi-where
  (let [_ (toadie/save test-store :people people-multi-search)]
    (testing "can have :and queries"
      (let [result (toadie/query test-store :people {:where [[:> :age 12] :and [:> :height 2.00]]})]
        (is (= (count result) 1))
        (is (= (:name (first result) "m3")))))
    (testing "can have multiple :and queries"
      (let [res (toadie/query test-store :people {:where [[:= :age 12] :or [:= :age 13] :or [:= :age 14]]})]
        (is (= (count res) 3))))
    (testing "can have nested queries"
      (let [result (toadie/query test-store :people {:where [[[:like :name "m%"] :or [:> :age 12]] :and [:> :height 1.80]]})]
        (is (= (count result) 2))))
    (testing "super nested one"
      (let [w [[[:= :name "m1"] :or [:= :name "m2"] :or [:> :height 2.0]] :and [:>= :age 13]]
            res (toadie/query test-store :people {:where w})]
        (is (= (count res) 2))))))
