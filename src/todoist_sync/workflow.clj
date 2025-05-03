(ns todoist-sync.workflow
  (:require [clojure.data.json :as json]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [todoist-sync.processed-db :as db]
            [todoist-sync.texts-handler :as thd]
            [todoist-sync.todoist :as td]
            [todoist-sync.workdash :as wd]
            [todoist-sync.yt-client :as yt-client])
  (:import (org.jsoup Jsoup))
  (:import (java.net URL)
           (org.jsoup Jsoup)))

(defn to-id-and-summary [resp]
  {:issue   (or (:idReadable resp) (str (get-in resp [:project :shortName]) "-" (:numberInProject resp)))
   :summary (:summary resp)})

(defn load-issues [yt-token issues]
  (for [issue issues
        :let [tags-query-response
              (yt-client/issue {:key yt-token}
                               issue {:fields "id,idReadable,summary,tags(name,id),resolved,customFields(name,value(name))"}
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

(defn resolved? [issue]
  (and (:resolved issue)
       (as-> issue ff
             (:customFields ff)
             (filter (fn [cf] (= "State" (:name cf))) ff)
             (first ff)
             (get-in ff [:value :name])
             (not= "Shelved" ff))))

(def ^:private issue-to-uid-cache (atom {}))

(defn get-issue-uid [issue yt-token]
  (cond
    (string? issue) (or (get @issue-to-uid-cache issue)
                            (let [uid (try
                                        (:id (yt-client/issue {:key yt-token} (str issue) {:fields "id"}))
                                        (catch clojure.lang.ExceptionInfo e
                                          (if (= 404 (:status (.getData e)))
                                            issue
                                            (throw e))))]
                              (swap! issue-to-uid-cache assoc issue uid)
                              uid))
    (associative? issue) (get-issue-uid (:issue issue) yt-token)))

(defn update-scheduled-tag [{yt-token :youtrack} body]
  (let [input-issues-info (thd/extract-issues-from-html (:text body))
        get-issue-uid (fn [issue-str] (get-issue-uid issue-str yt-token))
        keep-summaries (let [input-issues-info-map (into {} (map (juxt get-issue-uid identity) input-issues-info))]
                         (fn [id-and-summary-seq]
                           (->> id-and-summary-seq
                                (keep (fn [id-and-summary]
                                        (when-let [input-text (get-in input-issues-info-map
                                                                      [(get-issue-uid id-and-summary) :input-text])]
                                          (assoc id-and-summary :summary input-text))))
                                (not-empty))))
        issues (map :issue input-issues-info)
        tag-name (:tag (:settings body) "in-my-plan")
        _ (db/store-tag-sync-query (:id (yt-client/me yt-token)) tag-name (:text body))
        should-be-tagged? (if (:resolved (:settings body) false) (constantly true) (complement resolved?))
        issues-yt-data-by-state (->> (load-issues yt-token issues) (group-by (fn [issue] (if (should-be-tagged? issue) ::to-tag ::dont-tag))))
        added (add-tag-for-issues yt-token (::to-tag issues-yt-data-by-state) tag-name)
        known-scheduled-issues (get-all-tagged-issues yt-token tag-name)]
    {:duplicates                 (->> issues (frequencies) (filter (fn [[_ f]] (> f 1))) (map first) (not-empty))
     :issues-added               (keep-summaries added)
     :issues-missing             (let [issues-set (set (map get-issue-uid issues))]
                                   (not-empty (remove #(issues-set (get-issue-uid (:issue %))) known-scheduled-issues)))
     :issues-foreign             (keep-summaries (get-all-tagged-issues yt-token tag-name "for: -me for: -Unassigned"))
     :issues-resolved            (keep-summaries (remove should-be-tagged? (get-all-tagged-issues yt-token tag-name "#Resolved")))
     :not-added-because-resolved (keep-summaries (map to-id-and-summary (::dont-tag issues-yt-data-by-state)))}))

(defn mk-link [issue-str]
  (str "<a target=\"_blank\" rel=\"noopener noreferrer\" href=\"https://youtrack.jetbrains.com/issue/"
       issue-str "\">" issue-str "</a>"))


(defn issue-type-and-url [issue-str]
  (or (when-let [[_ rid num] (re-matches #"(\w+)-[CM]R-(\d+)" issue-str)]
        {:url     (cond
                    (#{"KT" "IJ"} rid) (str "https://jetbrains.team/p/" rid "/review/" num "/timeline")
                    (= "KOTLIN" rid) (str "https://kotlin.jetbrains.space/p/" rid "/review/" num "/timeline")
                    :else (str "https://upsource.jetbrains.com/intellij/review/" issue-str))
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

(def domains-to-read #{"jetbrains.team" "medium.com" "republic.ru" "journal.tinkoff.ru"})

(defn- extract-by-domain [text]
  (let [parsed-html (thd/parse-html-text text)
        domains (->> (thd/nodes-seq parsed-html)
                     (keep (fn [n] (:href (:attributes n))))
                     (map (fn [url] (.getHost (URL. url))))
                     (into #{}))
        markdown (thd/to-markdown parsed-html)]
    (list {:md-string markdown
           :td-type   (if (or (not-empty (set/intersection domains domains-to-read)) (str/includes? markdown "#read"))
                        :to-read nil)})))

(defn post-to-todoist [{token :todoist} {:keys [settings text]}]
  (let [issue-infos (or (not-empty
                          (map issue-to-markdown-and-type (classify-with-review-preference (thd/extract-issues-from-html text settings))))
                        (extract-by-domain text))]
    (doseq [issue issue-infos] @(td/post-as-issue token issue))
    {:text-out (str/join "\n" (map :md-string issue-infos))}))

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
                               (map :issue)
                               (filter (fn [entry] (or (empty? prefixes)
                                                       (some (fn [prefix] (str/starts-with? entry prefix)) prefixes))))
                               (apply sorted-set)
                               (map mk-link)
                               (str/join sprts))}))}
   {:name      "Post tasks to Todoist"
    :available #(:todoist %)
    :handler   post-to-todoist}
   {:name      "Update Dashboards"
    :available #(:youtrack %)
    :handler   (fn [{yt-token :youtrack} body]
                 (wd/update-dashboards-on-server yt-token))}
   {:name      "Update Plan"
    :available #(:youtrack %)
    :handler   (fn [{yt-token :youtrack} body]
                 (wd/patch-outdated-plan-on-server yt-token))}
   {:name      "Update Priority lists"
    :available #(:youtrack %)
    :handler   (fn [{yt-token :youtrack} body]
                 (wd/update-prioirity-lists-on-server yt-token))}
   {:name      "Export Issues"
    :available #(:youtrack %)
    :handler   (fn [{yt-token :youtrack} {:keys [settings text]}]
                 (let [query (if (not-empty text) 
                               (.text (Jsoup/parseBodyFragment text))
                               (or (:query settings) yt-client/default-query))
                       _ (println "query:" query)
                       issues (yt-client/issues-to-analyse {:key yt-token} query)
                       timestamp (System/currentTimeMillis)
                       file-id (str "issues-analysis-" timestamp)
                       filename (str file-id ".json")
                       _ (when-not (.exists todoist-sync.core/export-dir)
                           (.mkdirs todoist-sync.core/export-dir))
                       temp-file (io/file todoist-sync.core/export-dir filename)
                       _ (spit temp-file (json/write-str issues))]
                   {:download-id file-id
                    :message (str "Analysis complete. " (count issues) " issues found.")}))}])

(defn handler-id [{:keys [name]}] (str/replace name #"\s+" ""))

(defn handlers-available [tokens]
  (->> handlers
       (filter #((:available %) tokens))
       (map (fn [h] {:value (handler-id h)
                     :label (:name h)}))))

(defn handler-by-id [id] (:handler (first (filter (fn [h] (= (handler-id h) id)) handlers))))
