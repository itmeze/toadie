(defproject toadie "0.1.0-SNAPSHOT"
  :description "Turn postgresql database into document storage!!"
  :url "https://github.com/itmeze/toadie"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :profiles { :dev {
                    :source-paths ["dev" "src" "test"]
                    :dependencies [[org.clojure/tools.namespace "0.2.11"]]}}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/java.jdbc "0.4.2"]
                 [org.postgresql/postgresql "9.4-1201-jdbc41"]
                 [cheshire "5.5.0"]
                 [clj-time "0.11.0"]
                 [proto-repl "0.1.2"]
                 [environ "1.0.2"]])
