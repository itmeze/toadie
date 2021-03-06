(ns toadie.core
  (require [clojure.java.jdbc :as sql]
           [toadie.utils :refer :all]
           [toadie.named-params :as nparams]
           [clojure.core :refer :all]
           [clojure.string :as string]
           [cheshire.core :as json]
           [clojure.data.csv :as csv])
  (:import (org.postgresql.util PGobject))
  (:import org.postgresql.copy.CopyManager)
  (:import (java.io StringWriter StringReader)))

(defn sql-create-table [name]
  (str "create table " name "(id uuid primary key not null,body jsonb not null);"))

(defn sql-create-json-index [table-name]
  (str "create index idx_" table-name " on " table-name " using GIN(body jsonb_path_ops);"))

(defn sql-load-doc [table-name id]
  [(str "select * from " table-name " where id = ?") (uuid id)])

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
  (map #(:body %) d))

(defn- save-single [db n data]
  (try
    (->
      (cond
        (:id data) (clojure.java.jdbc/query (:db-spec db) [(str "Update " (name n) " set body = ? where id = ? returning *") data (uuid (:id data))])
        :else (let [uuid (uuid)]
                (sql/insert! (:db-spec db) n {:id uuid :body (assoc data :id uuid)})))
      (row-data-to-map)
      (first))
    (catch java.sql.SQLException e
      ;(sql/print-sql-exception e)
      (create-table db (name n))
      (save-single db n data))
    (catch Exception e
      ;(println (.toString e))
      (throw e))))

(defn save [db n data]
  (if (vector? data)
    (doall (map #(save-single db n %) data))
    (save-single db n data)))

(defn to-reader [db data]
  (let [data-with-ids (map #(assoc % :id (uuid)) data)
        els (map #(vector (str (:id %)) ((:serialize db) %)) data-with-ids)
        sw (StringWriter.)
        writer (csv/write-csv sw els)]
    (StringReader. (.toString sw))))

(defn batch-insert [db n data]
  (try
    (let [rec (to-reader db data)
          conn (sql/get-connection (:db-spec db))
          man (CopyManager. conn)]
      (.copyIn man (str "COPY " (name n) " from STDIN with (format csv)") rec))
    (catch java.sql.SQLException e
      ;(sql/print-sql-exception e)
      (create-table db (name n))
      (batch-insert db n data))))

(defn raw-query [db query]
  (try
    (row-data-to-map (sql/query (:db-spec db) query))
    (catch Exception e
      (throw e))))

(defn load-by-id [db n id]
  (try
    (->
      (sql/query (:db-spec db) (sql-load-doc (name n) id))
      (row-data-to-map)
      (first))
    (catch Exception e
      (create-table db (name n))
      (load db n id))))

(defn delete-by-id [db n id]
  (try
    (->
      (sql/delete! (:db-spec db) n ["id = ?" (uuid id)]))
    (catch Exception e
      (throw e))))

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
  (let [pname (random-string)]
    [(str "(body->>'" (name path) "')" (to-db-type-cast value) " " (name compop) " @:" pname) {(keyword pname) value}]))

(defn- where-contains-sql [db val]
  (let [pname (random-string)
        js ((:serialize db) val)]
    [(str "body @> '" js "'")]))

(defn- where-sql-simple [db part-where]
  (let [op (first part-where)]
    (cond
      (some #{op} [:> :>= := :<= :< :like]) (where-compop-sql op (second part-where) (last part-where))
      (= op :contains) (where-contains-sql db (second part-where))
      :else (throw (Exception. (str "Provided condition:" (name op) " is not supported."))))))

(defn where-sql [db where]
    (if
      (vector? (first where))
      (let [m (map-indexed (fn [idx item] (if (even? idx) (where-sql db item) [(name item) {}])) where)
            r (reduce (fn [[sj ps] [s p]] [(str sj " " s) (merge ps p)]) (vec m))]
        (let [[q ps] r]
          [(str "(" q ")") ps]))
      (where-sql-simple db where)))

(defn to-sql [db col q]
  (if-let [where (:where q)]
    (let [[where-s where-ps] (where-sql db where)]
      [(str (select-sql col) " where " where-s " " (offset-sql q) " " (limit-sql q)) where-ps])
    [(str (select-sql col) " " (offset-sql q) " " (limit-sql q))]))

(defn query [db col q]
  (let [[q-sql q-params] (to-sql db col q)
        parsed (nparams/to-statement q-sql q-params)]
    (raw-query db parsed)))
