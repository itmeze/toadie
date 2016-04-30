(ns toadie.delete-test
  (:require [clojure.test :refer :all]
            [toadie.core :as toadie]
            [clojure.java.jdbc :as sql]
            [clj-time.core :as time]
            [clj-time.coerce :as c]
            [environ.core :refer [env]]
            [toadie.test-helpers :refer :all]))

(use-fixtures :each drop-tables)

(deftest delete-by-id
  (let [maria (toadie/save test-store :people {:name "maria" :age 56})
        michal (toadie/save test-store :people {:name "michal" :age 34})]
    (testing "should be able to delete by id"
      (let [_ (toadie/delete-by-id test-store :people (:id maria))
            result (toadie/query test-store :people {})]
        (is (= (count result) 1))
        (is (= (:id (first result)) (:id michal)))))))
