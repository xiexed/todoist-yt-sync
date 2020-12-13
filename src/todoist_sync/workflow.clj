(ns todoist-sync.workflow
  (:require [todoist-sync.yt-client :as yt-client]
            [todoist-sync.texts-handler :as thd]
            [clojure.data.json :as json]
            [clojure.string :as str]))

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
  (->> (yt-client/issues {:key yt-token} "assigned to: me tag: in-my-plan")
       (map (fn [resp] {:issue (str (get-in resp [:project :shortName]) "-" (:numberInProject resp)) :summary (:summary resp)}))))

(defn update-scheduled-tag [{yt-token :youtrack} body]
  (let [issues (thd/extract-issues-id-from-lis (:text body))
        _ (add-scheduled-tag-for-issues yt-token issues)
        known-scheduled-issues (get-all-scheduled-issues yt-token)]
    {:issues-added   issues
     :issues-missing (let [issues-set (set issues)]
                       (remove #(issues-set (:issue %)) known-scheduled-issues))}))

(def handlers
  [{:name "Update Scheduled Tag"
   :available #(:youtrack %)
   :handler update-scheduled-tag
   }])

(defn handler-id [{:keys [name]}] (str/replace name #"\s+" ""))

(defn handlers-available [tokens]
  (->> handlers
       (filter #((:available %) tokens))
       (map (fn [h] {:value (handler-id h)
                     :label (:name h)}))))
