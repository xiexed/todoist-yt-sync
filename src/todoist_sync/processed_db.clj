(ns todoist-sync.processed-db
  (:require [clojure.java.io :as io]
            [honeysql.core :as sql]
            [honeysql.helpers :refer :all :as helpers]
            [java-time :as t])
  (:import (com.typesafe.config ConfigFactory)
           (java.time LocalDateTime)
           (javax.naming InitialContext)))

(require '[next.jdbc :as jdbc])

(def ds (delay (let [ds (or
                          (try (.lookup (InitialContext.) "java:jboss/datasources/todoist-sync-pg") (catch Exception e (println e) nil))
                          (let [conf (ConfigFactory/load)]
                            (jdbc/get-datasource {:dbtype   "postgresql"
                                                  :dbname   "todoist-sync"
                                                  :user     (.getString conf "pg.user")
                                                  :password (.getString conf "pg.password")})))]
                 (jdbc/execute! ds [(slurp (io/resource "create_db.sql"))])
                 ds)))


(defn store-tag-sync-query [user tag html]
  (jdbc/execute! @ds
                 (-> (insert-into :tag_syncs)
                     (values [{:yt_user_id user :date (LocalDateTime/now) :tag tag :html html}])
                     (sql/format))))