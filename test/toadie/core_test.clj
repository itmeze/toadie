(ns toadie.core-test
  (:require [clojure.test :refer :all]
            [toadie.core :as toadie]
            [clojure.java.jdbc :as sql]
            [clj-time.core :as time]
            [clj-time.coerce :as c]
            [environ.core :refer [env]]))

(def test-store
  (toadie/docstore (env :test-db-url)))

(defn drop-table [table-name]
  (sql/db-do-commands (:db-spec test-store) (str "drop table if exists " table-name)))

(defn db-fixtures [f]
  (f) (drop-table "people") (drop-table "posts"))

(use-fixtures :each db-fixtures)

(deftest insert
  (testing "after inserting to database"
    (let [inserted (toadie/save test-store :people {:name "maria" :surname "johnson" :age 42})]
      (testing "should assoc id to map"
        (is (> (inserted :id) 0)))
      (testing "should store in database"
        (let [res (toadie/raw-query test-store "select count(*) from people")
              c (count res)]
          (is (= c 1)))))))

(deftest update-with-id
  (testing "saving object with an id should update it"
    (let [inserted (toadie/save test-store :people {:name "michal"})
          updated  (toadie/save test-store :people (assoc inserted :name "maria"))
          result   (toadie/query test-store :people {})]
      (is (= (count result) 1))
      (is (= (:name (first result)) "maria"))
      (is (= (:id inserted) (:id updated) (:id (first result)))))))

(deftest load-by-id
  (let [maria (toadie/save test-store :people {:name "maria" :age 56})
        michal (toadie/save test-store :people {:name "michal" :age 34})]
    (testing "should be able to retrive by id"
      (let [result (toadie/load-by-id test-store :people (:id maria))]
        (is (= (:name result) "maria"))))))


(deftest limit-and-offset-search
  (let [m1 (toadie/save test-store :people {:name "m1"})
        m2 (toadie/save test-store :people {:name "m2"})
        m3 (toadie/save test-store :people {:name "m3"})]
    (testing "with limit"
      (let [result (toadie/query test-store :people {:limit 1})]
        (is (= (count result) 1)))
      (let [result (toadie/query test-store :people {:limit 2})]
        (is (= (count result) 2))))
    (testing "offset"
      (let [result (toadie/query test-store :people {:offset 3})]
        (is (= (count result) 0)))
      (let [result (toadie/query test-store :people {:offset 1})]
        (is (= (count result) 2))))))

(deftest simple-search
  (let [m1 (toadie/save test-store :people {:name "m1" :age 12 :height 1.86 :member-since (c/to-date (time/date-time 1982 11 30))})
        m2 (toadie/save test-store :people {:name "m2" :age 13 :height 1.74 :member-since (c/to-date (time/date-time 1982 11 30))})
        m3 (toadie/save test-store :people {:name "m3" :age 14 :height 2.06})]
    (testing "where property equals string"
      (let [result (toadie/query test-store :people {:where [:= :name "m1"]})]
        (is (= (count result) 1))
        (is (= (:name (first result)) "m1"))))
    (testing "where property equals a value"
      (let [result (toadie/query test-store :people {:where [:= :age 13]})]
        (is (= (count result)) 1)
        (is (= (:name (first result)) "m2"))))
    (testing "integer comparison"
      (let [result (toadie/query test-store :people {:where [:> :age 13]})]
        (is (= (count result)) 1)
        (is (= (:name (first result)) "m3")))
      (let [result (toadie/query test-store :people {:where [:>= :age 12]})]
        (is (= (count result)) 3))
      (let [result (toadie/query test-store :people {:where [:< :age 14]})]
        (is (= (count result)) 2)))
    (testing "double comparison"
      (let [result (toadie/query test-store :people {:where [:> :height 1.90]})]
        (is (= (count result) 1))
        (is (= (:name (first result) "m3")))))))

(deftest contains-condition
  (let [t1 (toadie/save test-store :posts {:title "t1" :tags ["web" "testing"]})
        t2 (toadie/save test-store :posts {:title "t2" :tags []})
        t3 (toadie/save test-store :posts {:title "t3" :tags ["clojure" "parinfer"]})]
    (testing "contains single array"
      (let [result (toadie/query test-store :posts {:where [:contains {:tags ["clojure"]}]})]
        (is (= (count result) 1))
        (is (= (:title (first result)) "t3"))))
    (testing "contains all array elements"
      (let [result (toadie/query test-store :posts {:where [:contains {:tags ["clojure" "web"]}]})]
        (is (= (count result) 0)))
      (let [result (toadie/query test-store :posts {:where [:contains {:tags ["testing" "web"]}]})]
        (is (= (count result) 1))
        (is (= (:title (first result) "t1")))))))
