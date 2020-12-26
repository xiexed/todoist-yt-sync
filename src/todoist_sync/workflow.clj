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
  (let [issues (thd/extract-issues-from-html (:text body))
        _ (add-scheduled-tag-for-issues yt-token issues)
        known-scheduled-issues (get-all-scheduled-issues yt-token)]
    {:issues-added   issues
     :issues-missing (let [issues-set (set issues)]
                       (remove #(issues-set (:issue %)) known-scheduled-issues))}))

(def handlers
  [{:name      "Update Scheduled Tag"
    :available #(:youtrack %)
    :handler   update-scheduled-tag}
   {:name      "Convert Html to Markdown"
    :available (constantly true)
    :handler   (fn [_ {:keys [text]}] {:text-out (thd/to-markdown (thd/parse-html-text text))})}
   {:name      "Extract issues mentions from HTML"
    :available (constantly true)
    :handler   (fn [_ {:keys [settings text]}]
                 (println "settings = " settings)
                 (let [sprts (str/replace (:separator settings " ") "\\n" "\n")
                       prefixes (or (some-> (:prefixes settings) (str/split #"[,\s]+")) [])]
                   {:text-out (->> (thd/extract-issues-from-html text)
                                   (filter (fn [entry] (or (empty? prefixes)
                                                           (some (fn [prefix] (str/starts-with? entry prefix)) prefixes))))
                                   (sort)
                                   (str/join sprts))}))}])

(defn handler-id [{:keys [name]}] (str/replace name #"\s+" ""))

(defn handlers-available [tokens]
  (->> handlers
       (filter #((:available %) tokens))
       (map (fn [h] {:value (handler-id h)
                     :label (:name h)}))))

(defn handler-by-id [id] (:handler (first (filter (fn [h] (= (handler-id h) id)) handlers))))
