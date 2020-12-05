(ns todoist-sync.workflow
  (:require [todoist-sync.yt-client :as yt-client]
            [clojure.data.json :as json]))

(def scheduled-tag-id "68-204271")

(defn add-scheduled-tag-for-issues [yt-token issues]
  (doseq [issue issues
          :let [tags-query-response (yt-client/issue {:key yt-token} issue {:fields "tags(name,id)"} {:throw-exceptions false})]
          :when (not (:error tags-query-response))
          :let [tags (:tags tags-query-response)]
          :when (not-any? #(= scheduled-tag-id (:id %)) tags)
          :let [new-tags {:tags (conj tags {:id scheduled-tag-id})}]]
    (println "issue" issue "tags" tags "filter" new-tags)
    (yt-client/yt-request {:key yt-token} :post (str "issues/" issue) {:query-params {:fields "tags"} :body (json/write-str new-tags)})))

(defn get-all-scheduled-issues [yt-token]
  (->> (yt-client/issues {:key yt-token} "assigned to: me tag: Scheduled")
       (map (fn [resp] {:issue (str (get-in resp [:project :shortName]) "-" (:numberInProject resp)) :summary (:summary resp)}))))