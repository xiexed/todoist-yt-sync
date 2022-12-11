(ns todoist-sync.todoist
  (:require [clj-http.client :as client]
            [promesa.core :as p])
  (:import (com.github.benmanes.caffeine.cache CacheLoader Caffeine LoadingCache)
           (java.time LocalDate)
           (java.time.format DateTimeFormatter)
           (java.util.concurrent TimeUnit)))

(def ^LoadingCache token-agents (-> (Caffeine/newBuilder)
                                    (.expireAfterAccess 8 TimeUnit/HOURS)
                                    (.build (reify CacheLoader
                                              (load [_ token]
                                                (agent {:token token}))))))

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
  (let [data (if (map? data) data {:content data})]
    (updating-state-off token
                        (fn [a]
                          (let [b (-> (client/post "https://api.todoist.com/rest/v2/tasks"
                                                   {:oauth-token  token
                                                    :form-params  data
                                                    :content-type :json
                                                    :accept       :json, :as :json}))]
                            [a b])))))

(def issue-publishing-data
  {:ticket  {:project_id 2246332511, :section_id 26579827, :priority 2}
   :ea      {:project_id 2246332511, :section_id 26579827, :priority 2}
   :review  {:project_id 2246332511, :section_id 26572945, :priority 3}
   :to-read {:project_id 2250955014, :no-date true}})

(defn post-as-issue [token issue-info]
  (let [today (.format (LocalDate/now) (DateTimeFormatter/ISO_LOCAL_DATE))
        type-data (issue-publishing-data (:td-type issue-info))]
    (post-task token {:content    (:md-string issue-info),
                      :project_id (:project_id type-data), :section_id (:section_id type-data)
                      :due_date   (when-not (:no-date type-data nil) today)
                      :priority   (:priority type-data)})))
