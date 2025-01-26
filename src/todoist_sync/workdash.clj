(ns todoist-sync.workdash
  (:require [clojure.string :as str]
            [todoist-sync.yt-client :as yt-client]
            [todoist-sync.utils.utils :as u]
            [clojure.string :as s]))

(defn load-dashboard-articles
  ([yt-token article]
   (yt-client/get-from-yt {:key yt-token} (str "/articles/" article "/childArticles") {:fields "id,summary,idReadable"}))
  ([yt-token]
   (load-dashboard-articles yt-token "IDEA-A-2100662287")))

(defn load-article [yt-token article]
  (yt-client/get-from-yt {:key yt-token} (str "/articles/" article) {:fields "content,summary"}))

(defn load-mentioned-in [yt-token article]
  (->> (yt-client/issues {:key yt-token} (str "mentioned in: " article) {:fields "id,idReadable"})
       (map #(select-keys % [:idReadable :id]))))


(defrecord MdLine [type value prefix])

(defn parse-md-line [line]
  (let [pref (re-find #"^\s*[-*]?\s*" line)
        pref-len (count pref)
        trimmed-line (.substring line pref-len)]
    (cond
      (s/starts-with? trimmed-line "#")
      (->MdLine :header (s/replace-first trimmed-line #"^#\s*" "") 0)

      (re-matches #"^\w+-\d+.*" trimmed-line)
      (->MdLine :issue (first (s/split trimmed-line #"\s+")) pref-len)

      :else nil)))

(defn parse-md-to-sections [text]
  ((fn collect-on-level [[head & tail :as input] header]
     (if head
       (cond
         (= :header (:type head))
         (recur tail (:value head))

         (= :issue (:type head))
         (let [item (u/non-zero-map :header header
                                    :idParsed (:value head))
               next-level (some-> (first tail) :prefix)]
           (if (and next-level (> next-level (:prefix head)))
             (let [[inner remaining] (split-with #(<= next-level (:prefix %)) tail)
                   collected-inner (collect-on-level inner nil)
                   item (assoc item :inner collected-inner)]
               (cons item (collect-on-level remaining header)))
             (cons item (collect-on-level tail header)))))
       nil))
   (keep parse-md-line (s/split-lines text)) nil))

(defn find-duplicates-by [criteria-fn coll]
  (->> coll
       (group-by criteria-fn)
       (filter (fn [[_ items]] (> (count items) 1)))
       (mapcat second)))

(defn- custom-field [issue-data name]
  (->> (:customFields issue-data)
       (filter #(= name (:name %)))
       (first)
       :value))

; (map #(wd/load-issue-data my-yt-token %) (mapcat :issues (wd/parse-md-to-sections text)))
(defn load-issue-data [yt-token issue]
  ;(map (fn [issue] (filter (fn [e] (#{"Assignee" "Planned for"} (:name e))) (yt-client/get-from-yt {:key yt-token} (str "issues/" issue "/customFields") {:fields "id,name,value(name, value, id)"}))))
  (let [issue-data (yt-client/get-from-yt {:key yt-token}
                                          (str "issues/" issue)
                                          {:fields "id,idReadable,resolved,customFields(id,name,value(name, value, id))"})]
    {:idReadable  (:idReadable issue-data)
     :id          (:id issue-data)
     :resolved    (:resolved issue-data)
     :assignee    (:name (custom-field issue-data "Assignee"))
     :state       (:name (custom-field issue-data "State"))
     :planned-for (map :name (custom-field issue-data "Planned for"))}))

(defn load-dashboards-data [yt-token]
  (->> (load-dashboard-articles yt-token)
       (map (fn [d]
              (let [loaded (load-article yt-token (:id d))]
                {:assignee     (:summary loaded)
                 :idReadable   (:idReadable d)
                 :issues       (->> (parse-md-to-sections (:content loaded))
                                    (pmap (fn [issue]
                                            (merge issue (load-issue-data yt-token (:idParsed issue))))))
                 :mentioned-in (load-mentioned-in yt-token (:idReadable d))})))))

(defn to-remove [loaded-data]
  (let [all-assignees (into #{} (map :assignee loaded-data))]
    (->> loaded-data
         (map (fn [db] (let [all-issues (:issues db)
                             parsed-ids (into #{} (map :id all-issues))]
                         {:assignee    (:assignee db)
                          :to-remove   (->> all-issues
                                            (filter (fn [issue] (or (:resolved issue) (= "Backlog" (:state issue)))))
                                            (map :idReadable))
                          :reassign    (->> all-issues
                                            (filter (fn [issue]
                                                      (if (= "Not team members" (:assignee db))
                                                        (contains? all-assignees (:assignee issue))
                                                        (and (contains? all-assignees (:assignee issue)) (not= (:assignee db) (:assignee issue))))))
                                            (map :idReadable)
                                            (str/join " "))
                          :not-parsed  (->> (:mentioned-in db)
                                            (remove (fn [m] (parsed-ids (:id m))))
                                            (map :idReadable))
                          :not-planned (->> all-issues
                                            (filter #(= "Planned for current release" (:header %)))
                                            (filter (fn [issue] (not-any? #(= "2025.1" %) (:planned-for issue))))
                                            (map :idReadable)
                                            (str/join " "))
                          :duplicates  (->> all-issues
                                            (find-duplicates-by :id)
                                            (map :idReadable)
                                            (str/join " "))
                          }
                         )))
         (map (fn [entry]
                (->> entry
                     (filter (fn [[k v]] (not (empty? v))))
                     (into {}))))))
  )

