(ns toadie.core
  (require [clojure.java.jdbc :as sql]
           [toadie.utils :as utils]
           [toadie.named-params :as nparams]
           [clojure.core :refer :all]
           [cheshire.core :as json])
  (:import (org.postgresql.util PGobject)))

(defn sql-create-table [name]
  (str "create table " name "(id serial primary key not null,body jsonb not null,created_at timestamptz not null default now());"))

(defn sql-create-json-index [table-name]
  (str "create index idx_" table-name " on " table-name " using GIN(body jsonb_path_ops);"))

(defn sql-load-doc [table-name id]
  [(str "select * from " table-name " where id = ?") id])

(defn serialize [s]
  (json/generate-string s))

(defn deserialize [s]
  (json/parse-string s true))

(defn to-pg-jsonb-value [str-val]
  (doto
    (PGobject.)
    (.setType "jsonb")
    (.setValue str-val)))

(defn setup [serialize-json deserialize-json]
  (extend-protocol sql/ISQLValue
    clojure.lang.IPersistentMap
    (sql-value [value] (to-pg-jsonb-value (serialize-json value)))
    clojure.lang.IPersistentVector
    (sql-value [value] (to-pg-jsonb-value (serialize-json value))))

  (extend-protocol sql/IResultSetReadColumn
    PGobject
    (result-set-read-column [pgobj _metadata _index]
      (let [type  (.getType pgobj)
            value (.getValue pgobj)]
        (if (= type "jsonb")
          (deserialize-json value)
          value)))))

(setup serialize deserialize)

(defn docstore [ps]
  "Sets up a doc store"
  (let [defaults {:serialize serialize :deserialize deserialize}
        sett (if (string? ps) {:db-spec ps} ps)
        merged (merge defaults sett)]
    (setup (:serialize merged) (:deserialize merged))
    merged))

(defn- create-table [db name]
  (sql/db-do-commands (:db-spec db) (sql-create-table name))
  (sql/db-do-commands (:db-spec db) (sql-create-json-index name)))

(defn row-data-to-map [d]
  (map #(assoc (:body %) :id (:id %)) d))

(defn save [db n data]
  (try
    (->
      (cond
        (vector? data) (doall (map #(save db n %) data))
        (:id data) (clojure.java.jdbc/query (:db-spec db) ["Update people set body = ? where id = ? returning *" (dissoc data :id) (:id data)])
        :else (sql/insert! (:db-spec db) n {:body data}))
      (row-data-to-map)
      (first))
    (catch java.sql.SQLException e
      ;(sql/print-sql-exception e)
      (create-table db (name n))
      (save db n data))
    (catch Exception e
      ;(println (.toString e))
      (throw e))))

(defn raw-query [db query]
  (try
    (row-data-to-map (sql/query (:db-spec db) query))
    (catch Exception e
      (println e))))

(defn load-by-id [db n id]
  (try
    (->
      (sql/query (:db-spec db) (sql-load-doc (name n) id))
      (row-data-to-map)
      (first))
    (catch Exception e
      (create-table db (name n))
      (load db n id))))

(defn- limit-sql [query]
  (if-let [limit (:limit query)]
    (str "limit " limit)))

(defn- offset-sql [query]
  (if-let [offset (:offset query)]
    (str "offset " offset)))

(defn- select-sql [col]
  (str "select * from " (name col)))

(defn to-sql-value [val]
  (condp instance? val
    String (str "'" val "'")
    val))

(defn to-db-type-cast [val]
  (condp instance? val
    Long "::bigint"
    Integer "::bigint"
    Double "::numeric"
    ""))

(defn- where-compop-sql [compop path value]
  (let [pname (utils/random-string)]
    [(str "(body->>'" (name path) "')" (to-db-type-cast value) " " (name compop) " @:" pname) {(keyword pname) value}]))

(defn- where-contains-sql [db val]
  (let [pname (utils/random-string)
        js ((:serialize db) val)]
    [(str "body @> '" js "'")]))

(defn where-sql [db query]
  (if-let [where (:where query)]
    (let [op (first where)]
      (cond
        (some #{op} [:> :>= := :<= :<])
        (where-compop-sql op (second where) (last where))
        (= op :contains) (where-contains-sql db (second where))
        :else (Exception. (str "Provided condition:" (name op) " is not supported."))))))


(defn to-sql [db col q]
  (if-let [[where-s where-ps] (where-sql db q)]
    [(str (select-sql col) " where " where-s " " (offset-sql q) " " (limit-sql q)) where-ps]
    [(str (select-sql col) " " (offset-sql q) " " (limit-sql q))]))

(defn query [db col q]
  (let [[q-sql q-params] (to-sql db col q)
        parsed (nparams/to-statement q-sql q-params)]
    (raw-query db parsed)))
