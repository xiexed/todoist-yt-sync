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

(defn get-in-sync [token]
  (client/get "https://api.todoist.com/sync/v8/sync"
              {:query-params
                       {"token"          token,
                        "sync_token"     "*"
                        "resource_types" (json/write-str ["sections"])}
               :accept :json, :as :json}))

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

(def issue-publishing-data
  {:ticket {:project_id 2246332511, :section_id 26579827, :priority 2}
   :review {:project_id 2246332511, :section_id 26572945, :priority 3}})

(defn post-as-issue [token issue-info]
  (let [today (.format (LocalDate/now) (DateTimeFormatter/ISO_LOCAL_DATE))
        type-data (issue-publishing-data (:td-type issue-info))]
    (post-task token {:content    (:md-string issue-info),
                      :project_id (:project_id type-data), :section_id (:section_id type-data)
                      :due        {:date today}
                      :priority   (:priority type-data)})))
