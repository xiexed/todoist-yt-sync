(ns todoist-sync.processed-db
  (:require [clojure.java.io :as io]
            [honeysql.core :as sql]
            [honeysql.helpers :refer :all :as helpers]
            [java-time :as t])
  (:import (com.typesafe.config ConfigFactory)
           (java.time LocalDateTime)))

(require '[next.jdbc :as jdbc])

(def ds (let [conf (ConfigFactory/load)]
          (jdbc/get-datasource {:dbtype   "postgresql"
                                :dbname   "todoist-sync"
                                :user     (.getString conf "pg.user")
                                :password (.getString conf "pg.password")})))

(jdbc/execute! ds [(slurp (io/resource "create_db.sql"))])

(defn store-tag-sync-query [user tag html]
  (jdbc/execute! ds 
                 (-> (insert-into :tag_syncs) 
                     (values [{:yt_user_id user :date (LocalDateTime/now) :tag tag :html html}]) 
                     (sql/format))))