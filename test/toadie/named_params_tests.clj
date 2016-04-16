(ns toadie.named-params-tests
  (:require [clojure.test :refer :all]
            [toadie.named-params :as nparams]))

(deftest to-statement
  (testing "should do nothing for simple statement"
    (is (= ["select * from people"] (nparams/to-statement "select * from people"))))
  (testing "should be fine with an empty map"
    (is (= ["select * from people"] (nparams/to-statement "select * from people" {:name "michal"}))))
  (testing "should convert named parameter"
    (let [s (nparams/to-statement "select * from people where name = @:name" {:name "mike"})]
      (is (= ["select * from people where name = ?" "mike"] s))))
  (testing "should convert multiple params in order"
    (let [s (nparams/to-statement "select * from people where name = @:name and surname = @:surname" {:name "mike" :surname "none"})]
      (is (= ["select * from people where name = ? and surname = ?" "mike" "none"] s))))
  (testing "should convert multiple params in order even if duplicated"
    (let [s (nparams/to-statement "select * from people where name = @:name and surname = @:surname and child_name = @:name" {:name "mike" :surname "none"})]
      (is (= ["select * from people where name = ? and surname = ? and child_name = ?" "mike" "none" "mike"] s))))
  (testing "should avoid casted elements"
    (let [s (nparams/to-statement "select * from people where age::int = @:age" {:age 23})]
      (is (= ["select * from people where age::int = ?" 23] s)))))
