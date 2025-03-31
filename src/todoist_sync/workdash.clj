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

(defn update-article [yt-token article-id text]
  (yt-client/update-on-yt {:key yt-token} (str "/articles/" article-id) {:content text}))

(defn load-mentioned-in [yt-token article]
  (->> (yt-client/issues {:key yt-token} (str "mentioned in: " article) {:fields "id,idReadable"})
       (map #(select-keys % [:idReadable :id]))))


(defrecord MdLine [type value full prefix])

(def line-space-pattern #"^\s*[-*\d\.]*\s*")

(defn parse-md-line [line]
  (let [line (s/replace line " " " ")
        pref (re-find line-space-pattern line)
        pref-len (count pref)
        trimmed-line (.substring line pref-len)]
    (cond
      (s/starts-with? trimmed-line "#")
      (->MdLine :header (s/replace-first trimmed-line #"^#\s*" "") trimmed-line 0)

      (re-matches #"^\w+-\d+.*" trimmed-line)
      (->MdLine :issue (first (s/split trimmed-line #"\s+")) trimmed-line pref-len)

      :else nil)))

(defn parse-md-to-sections [text]
  ((fn collect-on-level [[head & tail :as input] header]
     (if head
       (cond
         (= :header (:type head))
         (recur tail (:value head))

         (= :issue (:type head))
         (let [item (u/non-zero-map :header header
                                    :text (:full head)
                                    :idParsed (:value head))
               next-level (some-> (first tail) :prefix)]
           (if (and next-level (> next-level (:prefix head)))
             (let [[inner remaining] (split-with #(<= next-level (:prefix %)) tail)
                   collected-inner (collect-on-level inner nil)
                   item (assoc item :inner collected-inner)]
               (cons item (collect-on-level remaining header)))
             (cons item (collect-on-level tail header)))))
       nil))
   (->> (s/split-lines text)
        (keep parse-md-line)) nil))

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
  (let [issue-data (->> (yt-client/get-from-yt {:key yt-token}
                                               (str "issues/" issue)
                                               {:fields "id,idReadable,summary,resolved,customFields(id,name,value(name, value, id))"})
                        (u/enhance-error (str "issue:" issue)))]
    {:idReadable  (:idReadable issue-data)
     :summary     (:summary issue-data)
     :id          (:id issue-data)
     :resolved    (:resolved issue-data)
     :assignee    (:name (custom-field issue-data "Assignee"))
     :state       (:name (custom-field issue-data "State"))
     :planned-for (map :name (custom-field issue-data "Planned for"))}))

(defmacro record-issue-data-loads [filename & body]
  `(let [old# load-issue-data
         store# (atom {})
         value# (with-redefs [load-issue-data (fn [yt-token# issue#]
                                                (let [result# (old# yt-token# issue#)]
                                                  (swap! store# assoc issue# result#)
                                                  result#))]
                  ~@body)]
     (clojure.pprint/pprint (deref store#) (clojure.java.io/writer ~filename))
     value#))

(defn get-first-letters [text]
  (->> (str/split text #"\s+")
       (map #(first %))
       (filter some?)
       (apply str)))

(defn render-suffix [issue keys]
  (not-empty
    (str
      (when (some #{:assignee} keys)
        (str "\\[" (get-first-letters (:assignee issue)) "\\]"))
      (when-let [bold (not-empty
                        (str (when-let [pf (not-empty (:planned-for issue))]
                               (str "\\[" (str/join "," pf) "\\]"))
                             (when (not= "Open" (:state issue))
                               (str "\\[" (:state issue) "\\]"))))]
        (str "**" bold "**")))))

(defn render
  ([issue keys]
   (u/str-spaced (:idReadable issue)
                 (:summary issue)
                 (render-suffix issue keys)))

  ([issue] (render issue [])))

(defn load-dashboards-data [yt-token]
  (->> (load-dashboard-articles yt-token)
       (map (fn [d]
              (let [loaded (load-article yt-token (:id d))]
                {:assignee     (:summary loaded)
                 :idReadable   (:idReadable d)
                 :issues       (->> (parse-md-to-sections (:content loaded))
                                    (pmap (fn enhance [issue]
                                            (let [loaded (load-issue-data yt-token (:idParsed issue))]
                                              (merge issue
                                                     loaded
                                                     {:render (render loaded)}
                                                     (u/non-zero-map {:inner (not-empty (map enhance (:inner issue)))}))))))
                 :mentioned-in (load-mentioned-in yt-token (:idReadable d))})))))

(defn to-remove [loaded-data]
  (let [all-assignees (into #{} (map :assignee loaded-data))]
    (->> loaded-data
         (map (fn [db] (let [all-issues (:issues db)
                             all-issues-flatten ((fn flat-issue [issues]
                                                   (mapcat (fn [issue] (cons issue (flat-issue (:inner issue)))) issues)) all-issues)
                             parsed-ids (into #{} (map :id all-issues-flatten))
                             to-remove (->> all-issues-flatten
                                            (filter (fn [issue] (or (:resolved issue) (= "Backlog" (:state issue)) (= "Shelved" (:state issue))))))]
                         {
                          :assignee            (:assignee db)
                          :to-remove           (->> to-remove
                                                    (map :idParsed)
                                                    (str/join " "))
                          :to-remove-not-fixed (->> to-remove
                                                    (remove (fn [issue] (= "Fixed" (:state issue))))
                                                    (map :idParsed)
                                                    (str/join " "))
                          :submitted           (when-not (#{"Polina Popova" "Not team members"} (:assignee db))
                                                 (->> all-issues-flatten
                                                      (filter (fn [issue] (= "Submitted" (:state issue))))
                                                      (map :idParsed)
                                                      (str/join " ")))
                          :reassign            (->> all-issues-flatten
                                                    (filter (fn [issue]
                                                              (if (= "Not team members" (:assignee db))
                                                                (contains? all-assignees (:assignee issue))
                                                                (and (contains? all-assignees (:assignee issue)) (not= (:assignee db) (:assignee issue))))))
                                                    (map :idParsed)
                                                    (str/join " "))
                          :not-parsed          (->> (:mentioned-in db)
                                                    (remove (fn [m] (parsed-ids (:id m))))
                                                    (map :idReadable))
                          ;:outdated            (->> all-issues
                          ;                          (filter (fn [issue] (not= (:text issue) (:render issue))))
                          ;                          (map :render))
                          :duplicates          (->> all-issues
                                                    (find-duplicates-by :id)
                                                    (map :idParsed)
                                                    (str/join " "))
                          }
                         )))
         (map (fn [entry]
                (->> entry
                     (filter (fn [[k v]] (not (empty? v))))
                     (into {})))))))

(defn patch-outdated
  ([yt-token src render]
   (let [lines (str/split-lines src)
         parsed-lines (->> lines
                           (map (fn [line]
                                  (when-let [parsed (parse-md-line line)]
                                    (when (= :issue (:type parsed))
                                      {:line  line
                                       :issue (load-issue-data yt-token (:value parsed))
                                       }))))
                           (keep identity))]
     (->> lines
          (map (fn [line]
                 (if-let [matched (->> parsed-lines
                                       (filter #(= (:line %) line))
                                       first)]
                   (let [spaces (re-find line-space-pattern line)]
                     (render (merge (:issue matched) {:line (:line matched) :spaces spaces})))
                   line)))
          (str/join "\n"))))
  ([yt-token src] (patch-outdated yt-token src (fn [issue] (str (:spaces issue) (render issue))))))

(defn patch-outdated-plan [yt-token src]
  (patch-outdated yt-token src
                  (fn [issue]
                    (str
                      (:line issue)
                      " ::"
                      (render-suffix (update issue :planned-for
                                             (fn [old] (if (empty? old) ["Not-planned"] (remove #(= "2025.2" %) old)))) [:assignee :state])))))

(defn patch-outdated-plan-on-server [yt-token]
  (let [original-content (:content (load-article yt-token "IDEA-A-2100662404"))
        patched-content (patch-outdated-plan yt-token original-content)]
    (update-article yt-token "IDEA-A-2100662410" patched-content)))

(defn patch-dashboards [yt-token dir]
  (doseq [article (load-dashboard-articles yt-token)]
    (let [article (load-article yt-token (:id article))
          assignee (:summary article)
          content (:content article)
          patched (patch-outdated yt-token content)]
      (when (not= content patched)
        (let [dir (clojure.java.io/file dir)]
          (.mkdirs dir)
          (spit (clojure.java.io/file dir (str assignee "-orig.md")) content)
          (spit (clojure.java.io/file dir (str assignee "-patched.md")) patched))))
    ))
