(ns todoist-sync.workdash
  (:require [clojure.string :as str]
            [todoist-sync.yt-client :as yt-client]
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

(defn parse-md-to-sections [text]
  (loop [[^String head & tail] (s/split-lines text)
         header nil
         prev-pref Integer/MAX_VALUE
         collected []]
    (if head
      (let [pref (re-find #"^\s*[-*]?\s*" head)
            pref-len (count pref)
            trimmed-line (.substring head pref-len)]

        (cond
          (s/starts-with? trimmed-line "#")
          (recur tail (s/replace-first trimmed-line #"^#\s*" "") Integer/MAX_VALUE collected)

          (re-matches #"^\w+-\d+.*" trimmed-line)
          (let [issue-id (first (s/split trimmed-line #"\s+"))
                item {:header    header
                      :idParsed  issue-id}]
            (if (or (> pref-len prev-pref)
                    (and (= pref-len prev-pref) (:inner (peek collected))))
              (recur tail header pref-len
                     (update-in collected [(dec (count collected)) :inner] (fn [prev ] (conj (or prev []) item))))
              (recur tail header pref-len
                     (conj collected item))))

          :else
          (recur tail header prev-pref collected)))
      collected))
  )

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

