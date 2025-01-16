(ns todoist-sync.workdash
  (:require [todoist-sync.yt-client :as yt-client]
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
  (->> (s/split-lines text)
       (reduce (fn proccor [acc line]
                 (let [trimmed-line (s/trim (s/replace line " " " "))]
                   (cond
                     (s/starts-with? trimmed-line "#")      ;; If it's a header
                     (conj acc {:header (s/replace-first trimmed-line #"^#\s*" "")
                                :issues []})

                     (re-matches #"^\w+-\d+.*" trimmed-line)
                     ;; If it's an issue
                     (let [issue-id (first (s/split trimmed-line #"\s+"))]
                       (if (empty? acc)
                         (proccor [{:header nil
                                    :issues []}] line)
                         (update-in acc [(dec (count acc)) :issues] conj issue-id)))

                     :else acc)))
               []))
  )


(defn find-duplicates [lst]
  (let [frequencies (frequencies lst)]
    (keep (fn [[k v]] (when (> v 1) k)) frequencies)))


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
                                    (map (fn [entry]
                                           (update-in entry [:issues] (fn [issues]
                                                                        (map #(load-issue-data yt-token %) issues))))))
                 :mentioned-in (load-mentioned-in yt-token (:idReadable d))})))))

(defn to-remove [loaded-data]
  (map (fn [db] (let [all-issues  (mapcat (fn [en] (:issues en)) (:issues db))]
                  {:assignee  (:assignee db)
                   :to-remove (->> all-issues
                                   (filter (fn [issue] (or (:resolved issue) (= "Backlog" (:state issue)))))
                                   (map :idReadable))
                   :reassign (->> all-issues
                                   (filter (fn [issue] (or  (not= (:assignee db) (:assignee issue)))))
                                   (map :idReadable))
                   }
                  )) loaded-data)
  )

