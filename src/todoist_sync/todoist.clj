(ns todoist-sync.todoist
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.edn :as edn]
            [promesa.core :as p])
  (:import (com.github.benmanes.caffeine.cache Caffeine CacheLoader)
           (java.util.concurrent TimeUnit)
           (java.time LocalDate)
           (java.time.format DateTimeFormatter)))

(def token-agents (-> (Caffeine/newBuilder)
                      (.expireAfterAccess 8 TimeUnit/HOURS)
                      (.build (reify CacheLoader
                                (load [_ token]
                                  (agent {:token token})
                                  )))))

(defn updating-state-off [token f]
  (p/create
    (fn [resolve reject]
      (try
        (send-off (.get token-agents token)
              (fn [agent-state]
                (try
                  (let [[new-state result] (f agent-state)] (resolve result) new-state)
                  (catch Exception e (reject e)))))
        (catch Exception e (reject e))))))

(defn post-task [token data]
  (updating-state-off token
                      (fn [a]
                        (let [b (-> (client/get "https://api.todoist.com/sync/v8/sync"
                                                {:query-params
                                                         {"token"    token,
                                                          "commands" (json/write-str
                                                                       [{:type    "item_add",
                                                                         :args    data,
                                                                         :temp_id (.toString (java.util.UUID/randomUUID))
                                                                         :uuid    (.toString (java.util.UUID/randomUUID))
                                                                         }])}
                                                 :accept :json, :as :json}))]
                          [a b]))))

(defn post-as-issue [token md-text]
  (let [today (.format (LocalDate/now) (DateTimeFormatter/ISO_LOCAL_DATE))]
    (post-task token {:content    md-text,
                      :project_id 2246332511, :section_id 26579827
                      :due        {:date today}
                      :priority   2})))
