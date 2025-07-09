(ns todoist-sync.workdash
  (:require [clojure.string :as str]
            [todoist-sync.yt-client :as yt-client :refer [custom-field]]
            [todoist-sync.utils.utils :as u]
            [java-time :as t]
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

(defn normalize-version [version]
  "Converts build version (e.g. '252.18515') to planned version format (e.g. '2025.2')"
  (when version
    (let [parts (str/split version #"\.")
          major (first parts)]
      (when (and major (>= (count major) 3))
        (let [year-part (subs major 0 2)
              version-part (subs major 2)]
          (str "20" year-part "." version-part))))))

(defn backport-necessary? [issue-data]
  "Detects if backport is necessary based on backport tags or planned-for/included-in mismatch"
  (let [{:keys [tags planned-for included-in]} issue-data
        has-backport-tag? (some #(str/starts-with? % "backport-to-") tags)
        planned-for-set (->> planned-for
                             (remove #(= % "Requested"))
                             (map (fn [pf] (->> (str/split pf #"\.") (take 2) (str/join "."))))
                             (set))
        normalized-included-in (set (keep normalize-version included-in))
        missing-versions? (not (every? normalized-included-in planned-for-set))]
    (and (not-empty planned-for-set) (or has-backport-tag? missing-versions?))))

; (map #(wd/load-issue-data my-yt-token %) (mapcat :issues (wd/parse-md-to-sections text)))
(defn load-issue-data [yt-token issue]
  ;(map (fn [issue] (filter (fn [e] (#{"Assignee" "Planned for"} (:name e))) (yt-client/get-from-yt {:key yt-token} (str "issues/" issue "/customFields") {:fields "id,name,value(name, value, id)"}))))
  (let [issue-data (->> (yt-client/get-from-yt {:key yt-token}
                                               (str "issues/" issue)
                                               {:fields "id,idReadable,summary,resolved,customFields(id,name,value(name, value, id)),tags(name)"})
                        (u/enhance-error (str "issue:" issue)))]
    {:idReadable  (:idReadable issue-data)
     :summary     (:summary issue-data)
     :id          (:id issue-data)
     :resolved    (:resolved issue-data)
     :assignee    (:name (custom-field issue-data "Assignee"))
     :state       (:name (custom-field issue-data "State"))
     :type        (:name (custom-field issue-data "Type"))
     :tags        (map :name (:tags issue-data))
     :included-in (map :name (custom-field issue-data "Included in builds"))
     :planned-for (map :name (custom-field issue-data "Planned for"))
     :verified    (:name (custom-field issue-data "Verified"))
     :triaged     (:name (custom-field issue-data "Triaged"))}))

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
  (let [assignee #(or (some-> (:assignee issue) (get-first-letters)) "Unassigned")]
    (not-empty
      (str
        (when-let [type (:type issue)]
          (str "[" (if (= "Meta Issue" type) "Meta" (get-first-letters type)) "]"))
        (when-let [pf (not-empty (:planned-for issue))]
          (str "[" (str/join "," pf) "]"))
        (when (some #{:assignee} keys)
          (str "[" (assignee) "]"))
        (when-let [bold (not-empty
                          (str
                            (when (some #{:assignee-b} keys)
                              (str "\\[" (assignee) "\\]"))
                            (when-let [pf (not-empty (:planned-for-b issue))]
                              (str "\\[" (str/join "," pf) "\\]"))
                            (when (some #{:state} keys)
                              (str "\\[" (:state issue) "\\]"))))]
          (str "**" bold "**"))))))

(defn render [issue keys]
  (let [suffix (render-suffix issue keys)]
    {:body   (u/str-spaced (:idReadable issue)
                           (:summary issue))
     :suffix (when suffix (str " " suffix))}))

(defn wd-line-render [issue keys]
  (let [{:keys [body suffix]} (render issue keys)]
    (if (#{"Verified" "Fixed"} (:state issue))
      {:suffix suffix}
      {:suffix suffix
       :body   body})))

(defn wd-conditional-assignee-renderer [main-assignee]
  (fn [iss] (wd-line-render iss [(when (not= main-assignee (:assignee iss)) :assignee-b)
                                 (when (not= "Open" (:state iss)) :state)])))

(defn enhance-issue-state [issue-data]
  (assoc issue-data :state
                    (let [recorded-state (:state issue-data)
                          verified (:verified issue-data)]
                      (cond
                        (= recorded-state "Fixed")
                        (cond
                          (backport-necessary? issue-data) "Backporting"
                          (= "Yes" verified) "Verified"
                          (= "Not Needed" verified) "Fixed"
                          :else "Not-verified")

                        (= (:triaged issue-data) "Escalated") "Escalated"
                        :else recorded-state))))

(defn patch-outdated [yt-token src renderer]
  (let [lines (str/split-lines src)
        render-fn (cond
                    (fn? renderer) renderer
                    (vector? renderer) (fn [is] (render is renderer)))
        processed-lines (map (fn [line]
                               (let [parsed (parse-md-line line)]
                                 (if (= :issue (:type parsed))
                                   (let [issue (enhance-issue-state (load-issue-data yt-token (:value parsed)))
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
     :issues (keep :issue processed-lines)
     :diffs  diffs}))

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
  (map (fn [article] (let [original-content (:content article)
                           patched (patch-outdated-plan yt-token original-content)]
                       (update-article yt-token (:idReadable article) (:text patched))
                       {:id    (:idReadable article)
                        :name  (:summary article)
                        :diffs (:diffs patched)}))
       (yt-client/get-all-from-yt-lazy {:key yt-token} "articles" {:query "tag: managed-plan" :fields "idReadable,content,summary"} {})))

(defn patch-dashboards [yt-token dir]
  (doseq [article (load-dashboard-articles yt-token)]
    (let [article (load-article yt-token (:id article))
          assignee (:summary article)
          content (:content article)
          patched (patch-outdated yt-token content (wd-conditional-assignee-renderer (:summary article)))]
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
                     patched (patch-outdated yt-token content (wd-conditional-assignee-renderer (:summary article)))]
                 (when (not-empty (:diffs patched))
                   (update-article yt-token (:id article) (:text patched))
                   {:id    (:idReadable article)
                    :name  (:summary article)
                    :diffs (:diffs patched)}))))
       (doall)))

(defn update-metaissue-on-server [yt-token issue-id]
  (let [issue (yt-client/clean-up (yt-client/issue {:key yt-token} issue-id {:fields "idReadable,summary,description,links(direction,linkType(name),issues(idReadable,numberInProject,project(shortName))),customFields(id,name,value(name, value, id))"}))
        patched (patch-outdated yt-token (:description issue)
                                (fn [iss] (render iss (->> [:planned-for
                                                            (when (not= (:assignee iss) (:assignee issue)) :assignee)
                                                            (when (not= (:state iss) (:state issue)) :state)]
                                                           (filter some?) (vec)))))
        detected-from-patched (into #{} (:issues patched))]
    (yt-client/update-on-yt {:key yt-token} (str "/issues/" issue-id) {:description (:text patched)})
    (yt-client/add-issue-link {:key yt-token} detected-from-patched (:idReadable issue) "Epic link")
    {:id                    (:idReadable issue)
     :name                  (u/str-spaced (:idReadable issue) (:summary issue))
     :missed                (remove detected-from-patched (get-in issue [:links "Epic"]))
     :detected-from-patched detected-from-patched
     :diffs                 (:diffs patched)}
    ))

(defn update-prioirity-lists-on-server [yt-token]
  (let [update-result (->> (yt-client/issues {:key yt-token} "tag: {Priority-list}")
                           (keep (fn [issue] (update-metaissue-on-server yt-token (:id issue)))))]
    (yt-client/command {:key yt-token} (mapcat :detected-from-patched update-result) "add tag: has-priority-list")
    (->> update-result
         (keep (fn [patched]
                 (when (not-empty (concat (:diffs patched) (:missed patched)))
                   patched))))))


(defn load-article-versions [yt-token article dir]
  (doseq [change (yt-client/get-from-yt {:key yt-token} (str "articles/" article "/activities") {:categories "ArticleDescriptionCategory" :fields "timestamp,added,removed"} {})]
    (let [dir (clojure.java.io/file dir)]
      (.mkdirs dir)
      (spit (clojure.java.io/file dir (str (-> (:timestamp change) (t/instant) (t/local-date-time "GMT0") (t/format)) ".md")) (:added change)))))
