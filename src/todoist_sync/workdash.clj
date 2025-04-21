(ns todoist-sync.workdash
  (:require [clojure.string :as str]
            [todoist-sync.yt-client :as yt-client :refer [custom-field]]
            [todoist-sync.utils.utils :as u]
            [clojure.string :as s]))

(defn load-dashboard-articles
  ([yt-token article]
   (yt-client/get-from-yt {:key yt-token} (str "/articles/" article "/childArticles") {:fields "id,summary,idReadable"}))
  ([yt-token]
   (load-dashboard-articles yt-token "IDEA-A-2100662287")))

(defn load-article [yt-token article]
  (yt-client/get-from-yt {:key yt-token} (str "/articles/" article) {:fields "idReadable,content,summary"}))

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

(defn find-duplicates-by [criteria-fn coll]
  (->> coll
       (group-by criteria-fn)
       (filter (fn [[_ items]] (> (count items) 1)))
       (mapcat second)))

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
   (let [suffix (render-suffix issue keys)]
     {:body   (u/str-spaced (:idReadable issue)
                            (:summary issue))
      :suffix (when suffix (str " " suffix))}))

  ([issue] (render issue [])))

(defn wd-line-render [issue]
  (let [{:keys [body suffix]} (render issue)]
    (if (:resolved issue)
      {:suffix suffix}
      {:suffix suffix
       :body   body})))

(defn patch-outdated
  ([yt-token src renderer]
   (let [lines (str/split-lines src)
         render-fn (cond
                     (fn? renderer) renderer
                     (vector? renderer) (fn [is] (render is renderer)))
         processed-lines (map (fn [line]
                                (let [parsed (parse-md-line line)]
                                  (if (= :issue (:type parsed))
                                    (let [issue (load-issue-data yt-token (:value parsed))
                                          spaces (re-find line-space-pattern line)
                                          {:keys [body suffix]} (render-fn (merge issue
                                                                                  {:line (str/replace-first line spaces "")}))
                                          new-line (when body (str spaces body suffix))]
                                      {:line  new-line
                                       :issue (:idReadable issue)
                                       :diff  (when (not= line new-line)
                                                {:old line :new new-line :suffix suffix})})
                                    {:line line})))
                              lines)
         diffs (->> processed-lines
                    (keep :diff)
                    (filter some?))]
     {:text   (->> processed-lines
                   (keep :line)
                   (str/join "\n"))
      :issues (map :issue processed-lines)
      :diffs  diffs}))
  ([yt-token src] (patch-outdated yt-token src wd-line-render)))

(defn patch-outdated-plan [yt-token src]
  (patch-outdated yt-token src
                  (fn [issue]
                    (let [line (:line issue)
                          parts (str/split line #" ::")
                          base (str/join " ::" (butlast parts))
                          suffix (render-suffix (update issue :planned-for
                                                        (fn [old] (if (empty? old) ["Not-planned"] (remove #(= "2025.2" %) old)))) [:assignee :state])]
                      {:suffix (str " ::" suffix)
                       :body   (if (> (count parts) 1)
                                 base
                                 line)}))))

(defn patch-outdated-plan-on-server [yt-token]
  (let [article (load-article yt-token "IDEA-A-2100662404")
        original-content (:content article)
        patched (patch-outdated-plan yt-token original-content)]
    (update-article yt-token (:idReadable article) (:text patched))
    [{:id    (:idReadable article)
      :name  (:summary article)
      :diffs (:diffs patched)}]))

(defn patch-dashboards [yt-token dir]
  (doseq [article (load-dashboard-articles yt-token)]
    (let [article (load-article yt-token (:id article))
          assignee (:summary article)
          content (:content article)
          patched (patch-outdated yt-token content)]
      (when (not-empty (:diffs patched))
        (let [dir (clojure.java.io/file dir)]
          (.mkdirs dir)
          (spit (clojure.java.io/file dir (str assignee "-orig.md")) content)
          (spit (clojure.java.io/file dir (str assignee "-patched.md")) (:text patched)))))
    ))

(defn update-dashboards-on-server [yt-token]
  (->> (load-dashboard-articles yt-token)
       (keep (fn [article]
               (let [article-data (load-article yt-token (:id article))
                     content (:content article-data)
                     patched (patch-outdated yt-token content)]
                 (when (not-empty (:diffs patched))
                   (update-article yt-token (:id article) (:text patched))
                   {:id    (:idReadable article)
                    :name  (:summary article)
                    :diffs (:diffs patched)}))))
       (doall)))

(defn update-metaissue-on-server [yt-token issue-id]
  (let [issue (yt-client/clean-up (yt-client/issue {:key yt-token} issue-id {:fields "idReadable,summary,description,links(direction,linkType(name),issues(idReadable,numberInProject,project(shortName)))"}))
        patched (patch-outdated yt-token (:description issue) [:planned-for :assignee :state])
        detected-from-patched (into #{} (:issues patched))]
    (yt-client/update-on-yt {:key yt-token} (str "/issues/" issue-id) {:description (:text patched)})
    (yt-client/add-issue-link {:key yt-token} detected-from-patched (:idReadable issue) "Epic link")
    {:id    (:idReadable issue)
     :name  (u/str-spaced (:idReadable issue) (:summary issue))
     :missed (remove detected-from-patched (get-in issue [:links "Epic"]))
     :diffs (:diffs patched)}
    ))

(defn update-prioirity-lists-on-server [yt-token]
  (->> (yt-client/issues {:key yt-token} "tag: {Priority-list}")
       (keep (fn [issue]
               (let [patched (update-metaissue-on-server yt-token (:id issue))]
                 (when (not-empty (concat (:diffs patched) (:missed patched)))
                   patched))))
       (doall)))