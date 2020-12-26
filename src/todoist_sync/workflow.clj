(ns todoist-sync.workflow
  (:require [todoist-sync.yt-client :as yt-client]
            [todoist-sync.texts-handler :as thd]
            [clojure.data.json :as json]
            [clojure.string :as str]))

(defn to-id-and-summary [resp]
  {:issue   (or (:idReadable resp) (str (get-in resp [:project :shortName]) "-" (:numberInProject resp)))
   :summary (:summary resp)})

(defn add-tag-for-issues [yt-token issues tag-name]
  (let [issues (for [issue issues
                     :let [tags-query-response
                           (yt-client/issue {:key yt-token}
                                            issue {:fields "id,idReadable,summary,tags(name,id)"}
                                            {:throw-exceptions false})]
                     :when (not (:error tags-query-response))
                     :let [tags (:tags tags-query-response)]
                     :when (not-any? #(= tag-name (:name %)) tags)]
                 tags-query-response)]
    (when (not-empty issues)
      (yt-client/yt-request {:key yt-token}
                            :post "commands"
                            {:body (json/write-str {:issues issues
                                                    :query  (str "add tag: " tag-name)})}))
    (not-empty (map to-id-and-summary issues))))

(defn get-all-tagged-issues
  ([yt-token tag-name] (get-all-tagged-issues yt-token tag-name ""))
  ([yt-token tag-name additional-restrictions]
   (->> (yt-client/issues {:key yt-token} (str "tag: " tag-name " " additional-restrictions))
        (map to-id-and-summary)
        (not-empty))))

(defn update-scheduled-tag [{yt-token :youtrack} body]
  (let [issues (thd/extract-issues-from-html (:text body))
        tag-name (:tag (:settings body) "in-my-plan")
        added (add-tag-for-issues yt-token issues tag-name)
        known-scheduled-issues (get-all-tagged-issues yt-token tag-name)]
    {:duplicates     (->> issues (frequencies) (filter (fn [[_ f]] (> f 1))) (map first) (not-empty))
     :issues-added   added
     :issues-missing (let [issues-set (set issues)]
                       (not-empty (remove #(issues-set (:issue %)) known-scheduled-issues)))
     :issues-foreign (get-all-tagged-issues yt-token tag-name "for: -me for: -Unassigned")
     :issues-resolved (get-all-tagged-issues yt-token tag-name "#Resolved ")}))

(def handlers
  [{:name      "Sync tag for Issues"
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
