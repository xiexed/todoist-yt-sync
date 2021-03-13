(ns todoist-sync.processed-db
  (:require [clojure.java.io :as io])
  (:import (com.typesafe.config ConfigFactory)))

(require '[next.jdbc :as jdbc])

(def ds (let [conf (ConfigFactory/load)]
          (jdbc/get-datasource {:dbtype   "postgresql"
                                :dbname   "todoist-sync"
                                :user     (.getString conf "pg.user")
                                :password (.getString conf "pg.password")})))

(jdbc/execute! ds [(slurp (io/resource "create_db.sql"))])