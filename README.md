# toadie

A Clojure library designed to let you work with PostgreSQL as a document store!

## Why

Since version 9.4 Postres allows for storing, querying and indexing on json column types. This allows to easily persist clojure's map in database.  

This concept is not new, take a look at Elixir's Moebius (https://github.com/robconery/moebius). Especially part with JSONB support.

##Geting stared

Toadie artifacts are [deployed to clojars] (https://clojars.org/toadie)

With Leiningen:

    [toadie "0.3.0"]

With Gradle:

    compile "toadie:toadie:0.3.0"

With Maven:

    <dependency>
      <groupId>toadie</groupId>
      <artifactId>toadie</artifactId>
      <version>0.3.0</version>
    </dependency>

We get started by calling _docstore_ function:

``` clojure
(use '[toadie.core :as toadie])

;pass connection string to docstore method
(def db (toadie/docstore conn-str))

;initilize with map
(def db (toadie/docstore {
          :serialize (fn [x] (to-json x))
          :deserialize (fn [x] (from-json x))
          :classname "org.postgresql.Driver"
          :subprotocol "postgresql"
          :subname "localhost:1234"
          :user "a_user"
          :password "secret"}))
```

Docstore' map can be created with connection string or map that is further on passed when initializing connection to clojure.java.jdbc. Optionally, docstore's map can contain _serialize_ and _deserialize_ functions that are used to convert between clojure's map and it's json representation. Toadie uses Cheshire by default.

## Inserting/Update

``` clojure

;insert map into :people collection
(toadie/save db :people {:name "maria" :surname "johnson" :age 42})
user=>
{:age 42,
 :name "maria",
 :surname "johnson",
 :id "26ecbf13-4628-430e-b998-6022db44b334"}

;insert vector of maps
(toadie/save db :people [{:name "michal"} {:name "marcelina"}])
user=>
({:name "michal", :id "f5ec1d0b-6f13-433f-b8c5-f9643231f1ff"}
 {:name "marcelina", :id "57107e40-e0d6-4146-9d3d-a16912add53f"})

;update map by passing :id key
(toadie/save db :people {:name "maria" :surname "johnson" :age 43, :id "26ecbf13-4628-430e-b998-6022db44b334"})
```

At first call to _save_ will create destination table. _save_ returns map with assoc id key from database. As from the example above, function works with both maps and vectors of maps.

When id key is present in map, record is going to be updated, instead of being inserted.

Toadie supports batch inserts. Those use Postgres Copy functionality:
``` clojure
(toadie/batch-insert db :people [{:name "maria" :surname "johnson" :age 43, :id "26ecbf13-4628-430e-b998-6022db44b334"}{:name "other"}])
```

When testing on local machine, batch insert via 'copy' turned out to be 100x faster than 'insert' one by one :)

## Querying

Querying is best explained by examples:

``` clojure

;quey :people collection taking just 3 entities
(toadie/query db :people {:limit 3})

;quey :people collection skiping first 10 results and taking just 4 entities
(toadie/query db :people {:limit 4 offset 10})

;query :people collection where :name equals "m1"
(toadie/query db :people {:where [:= :name "m1"]})

;query :people collection where age is > 13
(toadie/query db :people {:where [:> :age 13]})

;query :people with name starting with "ma"
(toadie/query db :people {:where [:like :name "ma%"]})

;query :posts collection where any of tags is "clojure"
(toadie/query db :posts {:where [:contains {:tags ["clojure"]}]})

;query :posts collection where posts' tags are "clojure" and "web"
(toadie/query db :posts {:where [:contains {:tags ["clojure" "web"]}]})

;multiple where conditions with :and and :or
(toadie/query db :people {:where [[[:like :name "m%"] :or [:> :age 12]] :and [:> :height 1.80]]})

;where with nested conditions
(toadie/query db :people {:where [[[:= :name "m1"] :or [:= :name "m2"] :or [:> :height 2.0]] :and [:>= :age 13]]})

;and so on, and so on
```

## Delete

Currently only delete-by-id is supported

``` clojure
(toadie/delete-by-id db :people (:id "26ecbf13-4628-430e-b998-6022db44b334"))
```

# Change Log

## [0.3.0] - 2016-07-27
### Added
- toadie now supports batch-insert

## License

The MIT License (MIT)
