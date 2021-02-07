(ns todoist-sync.workflow
  (:require [todoist-sync.yt-client :as yt-client]
            [todoist-sync.todoist :as td]
            [todoist-sync.texts-handler :as thd]
            [clojure.data.json :as json]
            [clojure.string :as str]))

(defn to-id-and-summary [resp]
  {:issue   (or (:idReadable resp) (str (get-in resp [:project :shortName]) "-" (:numberInProject resp)))
   :summary (:summary resp)})

(defn load-issues [yt-token issues]
  (for [issue issues
        :let [tags-query-response
              (yt-client/issue {:key yt-token}
                               issue {:fields "id,idReadable,summary,tags(name,id),resolved"}
                               {:throw-exceptions false})]
        :when (not (:error tags-query-response))]
    tags-query-response))

(defn add-tag-for-issues [yt-token issues tag-name]
  (let [issues (filter (fn [tags-query-response]
                         (let [tags (:tags tags-query-response)]
                           (not-any? #(= tag-name (:name %)) tags)))
                       issues)]
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
  (let [input-issues-info (thd/extract-issues-from-html (:text body))
        keep-summaries (let [input-issues-info-map (into {} (map (juxt :issue identity) input-issues-info))]
                         (fn [id-and-summary-seq]
                           (->> id-and-summary-seq
                                (keep (fn [id-and-summary]
                                        (when-let [input-text (get-in input-issues-info-map
                                                                      [(:issue id-and-summary) :input-text])]
                                          (assoc id-and-summary :summary input-text))))
                                (not-empty))))
        issues (map :issue input-issues-info)
        tag-name (:tag (:settings body) "in-my-plan")
        issues-yt-data-by-state (->> (load-issues yt-token issues) (group-by (fn [issue] (if (:resolved issue) :resolved :unresolved))))
        added (add-tag-for-issues yt-token (:unresolved issues-yt-data-by-state) tag-name)
        known-scheduled-issues (get-all-tagged-issues yt-token tag-name)]
    {:duplicates                 (->> issues (frequencies) (filter (fn [[_ f]] (> f 1))) (map first) (not-empty))
     :issues-added               (keep-summaries added)
     :issues-missing             (let [issues-set (set issues)]
                                   (not-empty (remove #(issues-set (:issue %)) known-scheduled-issues)))
     :issues-foreign             (keep-summaries (get-all-tagged-issues yt-token tag-name "for: -me for: -Unassigned"))
     :issues-resolved            (keep-summaries (get-all-tagged-issues yt-token tag-name "#Resolved "))
     :not-added-because-resolved (keep-summaries (map to-id-and-summary (:resolved issues-yt-data-by-state)))}))

(defn mk-link [issue-str]
  (str "<a target=\"_blank\" rel=\"noopener noreferrer\" href=\"https://youtrack.jetbrains.com/issue/"
       issue-str "\">" issue-str "</a>"))


(defn issue-type-and-url [issue-str]
  (or (when-let [[_ rid num] (re-matches #"(\w+)-CR-(\d+)" issue-str)]
        {:url     (if (#{"KT" "IJ"} rid)
                    (str "https://jetbrains.team/p/" rid "/review/" num "/timeline")
                    (str "https://upsource.jetbrains.com/intellij/review/" issue-str))
         :td-type :review})
      (when-let [[_ num] (re-matches #"EA-(\d+)" issue-str)]
        {:url     (str "https://ea.jetbrains.com/browser/ea_problems/" num)
         :td-type :ea})
      {:url     (str "https://youtrack.jetbrains.com/issue/" issue-str "")
       :td-type :ticket}))

(defn issue-to-markdown-and-type [issue]
  {:md-string (reduce (fn [input-text {:keys [issue url]}] (str/replace input-text issue (str "[" issue "](" url ")")))
                      (issue :input-text)
                      (cons issue (:others issue)))
   :td-type   (:td-type issue)})

(defn classify-with-review-preference [extracted-issues]
  (->> extracted-issues
       (map (fn [issue] (merge issue (issue-type-and-url (:issue issue)))))
       (group-by :input-text)
       (vals)
       (map (fn [gr]
                 (let [[head & others] (let [has-preference #(= :review (:td-type %))]
                                         (concat (filter has-preference gr) (remove has-preference gr)))]
                   (if (not-empty others)
                     (assoc head :others (map #(select-keys % [:issue :url]) others))
                     head))))))

(defn post-to-todoist [{token :todoist} {:keys [settings text]}]
  (let [issue-info (map issue-to-markdown-and-type (classify-with-review-preference (thd/extract-issues-from-html text)))]
    (doseq [issue issue-info] @(td/post-as-issue token issue))
    {:text-out (str/join "\n" (map :md-string issue-info))}))

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
                 (let [sprts (str/replace (:separator settings " ") "\\n" "\n")
                       prefixes (or (some-> (:prefixes settings) (str/split #"[,\s]+")) [])]
                   {:html (->> (thd/extract-issues-from-html text)
                               (filter (fn [entry] (or (empty? prefixes)
                                                       (some (fn [prefix] (str/starts-with? entry prefix)) prefixes))))
                               (apply sorted-set)
                               (map mk-link)
                               (str/join sprts))}))}
   {:name      "Post tasks to Todoist"
    :available #(:todoist %)
    :handler   post-to-todoist}])

(defn handler-id [{:keys [name]}] (str/replace name #"\s+" ""))

(defn handlers-available [tokens]
  (->> handlers
       (filter #((:available %) tokens))
       (map (fn [h] {:value (handler-id h)
                     :label (:name h)}))))

(defn handler-by-id [id] (:handler (first (filter (fn [h] (= (handler-id h) id)) handlers))))
