(ns toadie.test-helpers
  (:require [clojure.test :refer :all]
            [toadie.core :as toadie]
            [clojure.java.jdbc :as sql]
            [environ.core :refer [env]]))

(def test-store
  (toadie/docstore (env :test-db-url)))

(defn drop-table [table-name]
  (sql/db-do-commands (:db-spec test-store) (str "drop table if exists " table-name)))

(defn drop-tables [f]
  (f) (drop-table "people") (drop-table "posts"))
