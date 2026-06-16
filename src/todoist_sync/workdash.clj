(ns todoist-sync.workdash
  (:require [clojure.string :as str]
            [todoist-sync.yt-client :as yt]
            [todoist-sync.yt-client :as yt-client :refer [custom-field]]
            [todoist-sync.utils.utils :as u]
            [java-time :as t]
            [clojure.string :as s]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [html5]]))

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

(defn- normalize-pub-version [pf]
  (->> (str/split pf #"\.") (take 2) (str/join ".")))

(defn backport-necessary? [issue-data]
  "Detects if backport is necessary based on backport tags or planned-for/included-in/available-in mismatch"
  (let [{:keys [tags planned-for included-in available-in]} issue-data
        has-backport-tag? (some #(str/starts-with? % "backport-to-") tags)
        planned-for-set (->> planned-for
                             (remove #(= % "Requested"))
                             (map normalize-pub-version)
                             (set))
        normalized-included-in (set (keep normalize-version included-in))
        normalized-available-in (set (keep normalize-pub-version available-in))
        all-available-versions (clojure.set/union normalized-included-in normalized-available-in)
        missing-versions? (not (every? all-available-versions planned-for-set))]
    (and (not-empty planned-for-set) (or has-backport-tag? missing-versions?))))

; (map #(wd/load-issue-data my-yt-token %) (mapcat :issues (wd/parse-md-to-sections text)))
(defn load-issue-data [yt-token issue]
  ;(map (fn [issue] (filter (fn [e] (#{"Assignee" "Planned for"} (:name e))) (yt-client/get-from-yt {:key yt-token} (str "issues/" issue "/customFields") {:fields "id,name,value(name, value, id)"}))))
  (let [issue-data (->> (yt-client/get-from-yt {:key yt-token}
                                               (str "issues/" issue)
                                               {:fields "id,idReadable,summary,resolved,customFields(id,name,value(name, value, id)),tags(name)"})
                        (u/enhance-error (str "issue:" issue)))]
    {:idReadable   (:idReadable issue-data)
     :summary      (:summary issue-data)
     :id           (:id issue-data)
     :resolved     (:resolved issue-data)
     :assignee     (:name (custom-field issue-data "Assignee"))
     :state        (:name (custom-field issue-data "State"))
     :type         (:name (custom-field issue-data "Type"))
     :tags         (map :name (:tags issue-data))
     :priority     (:name (custom-field issue-data "Priority"))
     :included-in  (map :name (custom-field issue-data "Included in builds"))
     :planned-for  (map :name (custom-field issue-data "Planned for"))
     :available-in (map :name (custom-field issue-data "Available in"))
     :qa           (:name (custom-field issue-data "QA"))
     :verified     (:name (custom-field issue-data "Verified"))
     :triaged      (:name (custom-field issue-data "Triaged"))}))

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

(defn capitalize- [text]
  (->> (str/split text #"\-")
       (map str/capitalize)
       (str/join "-")))

(defn render-suffix [issue keys]
  (let [assignee #(or (some-> (:assignee issue) (get-first-letters)) "Unassigned")]
    (not-empty
      (str
        (when-let [d (:duplicate-number issue)]
          (str "[" d "]"))
        (when-let [type (:type issue)]
          (str "[" (if (= "Meta Issue" type) "Meta" (get-first-letters type)) "]"))
        (when-let [pf (not-empty (:planned-for issue))]
          (str "[" (str/join "," pf) "]"))
        (when (some #{:assignee} keys)
          (str "[" (assignee) "]"))
        (when (some #{:state} keys)
          (str "[" (:state issue) "]"))
        (when-let [bold (not-empty
                          (str
                            (when (some #{:assignee-b} keys)
                              (str "\\[" (assignee) "\\]"))
                            (when-let [p (or
                                           (->> (:tags issue) (filter #{"blocking-release" "blocking-eap"}) (first))
                                           (->> (:priority issue) (u/take-if (complement #{"Minor" "Normal"}))))]
                              (str "\\[" (capitalize- p) "\\]"))
                            (when (some #{:state-b} keys)
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
                                 (when (not= "Open" (:state iss)) (if (#{"No QA" "Duplicate" "Incomplete" "In progress"} (:state iss)) :state-b :state))])))

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
                          (= nil (:qa issue-data)) "No QA"
                          :else "Testing")

                        (= (:triaged issue-data) "Escalated") "Escalated"
                        :else recorded-state))))

(defn patch-outdated [yt-token src renderer]
  (let [lines (str/split-lines (or src ""))
        render-fn (cond
                    (fn? renderer) renderer
                    (vector? renderer) (fn [is] (render is renderer)))
        parse-md-line (memoize parse-md-line)
        all-issues (->> lines
                        (map parse-md-line)
                        (filter #(= :issue (:type %)))
                        (map :value))
        duplicate-issues (set (->> all-issues
                                   (frequencies)
                                   (filter (fn [[_ count]] (> count 1)))
                                   (map first)))
        issue-counters (atom {})
        processed-lines (map (fn [line]
                               (let [parsed (parse-md-line line)]
                                 (if (= :issue (:type parsed))
                                   (let [issue-id (:value parsed)
                                         issue (enhance-issue-state (load-issue-data yt-token issue-id))
                                         ; Add numbering for duplicates
                                         issue-with-number (if (duplicate-issues issue-id)
                                                             (let [current-count (swap! issue-counters update issue-id (fnil inc 0))]
                                                               (assoc issue :duplicate-number (current-count issue-id)))
                                                             issue)
                                         spaces (re-find line-space-pattern line)
                                         {:keys [body suffix]} (render-fn (merge issue-with-number
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

(defn update-dashboards-on-server
  ([yt-token] (update-dashboards-on-server yt-token false))
  ([yt-token dry-run?]
   (let [all-team-open-issues (map yt/clean-up
                                   (yt/issues {:key yt-token}
                                              "#{CnD Team Related} state: Open state: {In Progress} Assignee: -Sergey.Simonchik Type: -{User Story} "
                                              {:fields "id,idReadable,customFields(id,name,value(name, value, id))"}))]
     (->> (load-dashboard-articles yt-token)
          (keep (fn [article]
                  (let [article-data (load-article yt-token (:id article))
                        content (:content article-data)
                        patched (patch-outdated yt-token content (wd-conditional-assignee-renderer (:summary article)))
                        included (into #{} (:issues patched))
                        missed (->> all-team-open-issues
                                    (filter #(= (:assignee %) (:summary article)))
                                    (map :idReadable)
                                    (remove included))]
                    (when (and (not dry-run?) (not-empty (:diffs patched)))
                      (update-article yt-token (:id article) (:text patched)))
                    (when (or (not-empty (:diffs patched)) (not-empty missed))
                      {:id     (:idReadable article)
                       :name   (:summary article)
                       :missed missed
                       :diffs  (:diffs patched)}))))
          (doall)))))

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

(defn- exception-causes [^Throwable e]
  (take-while some? (iterate (fn [^Throwable cause] (.getCause cause)) e)))

(defn- request-error-message [^Throwable e]
  (let [data (some ex-data (exception-causes e))
        details (u/str-spaced (when-let [status (:status data)] (str "HTTP " status))
                              (:reason-phrase data)
                              (:body data))]
    (or (not-empty details)
        (some #(not-empty (.getMessage ^Throwable %)) (exception-causes e))
        (str (class e)))))

(defn- issue-update-error [issue ^Throwable e]
  (let [issue-id (or (:idReadable issue) (:id issue))]
    {:id     issue-id
     :name   (u/str-spaced issue-id (:summary issue))
     :missed []
     :diffs  []
     :errors [{:message (request-error-message e)}]}))

(defn- result-with-details? [result]
  (or (not-empty (:errors result))
      (not-empty (concat (:diffs result) (:missed result)))))

(defn update-prioirity-lists-on-server [yt-token]
  (let [update-result (->> (yt-client/issues {:key yt-token}
                                             "tag: {Priority-list}"
                                             {:fields "id,idReadable,summary"})
                           (map (fn [issue]
                                  (try
                                    (update-metaissue-on-server yt-token (:id issue))
                                    (catch Exception e
                                      (issue-update-error issue e)))))
                           (doall))
        detected-from-patched (->> update-result
                                   (remove :errors)
                                   (mapcat :detected-from-patched)
                                   (vec))
        command-error (try
                        (yt-client/command {:key yt-token} detected-from-patched "add tag: has-priority-list")
                        nil
                        (catch Exception e
                          (issue-update-error {:id "Priority-list tag command"} e)))]
    (->> update-result
         (concat (when command-error [command-error]))
         (keep (fn [patched]
                 (when (result-with-details? patched)
                   patched))))))


(defn load-article-versions [yt-token article dir]
  (doseq [change (yt-client/get-from-yt {:key yt-token} (str "articles/" article "/activities") {:categories "ArticleDescriptionCategory" :fields "timestamp,added,removed"} {})]
    (let [dir (clojure.java.io/file dir)]
      (.mkdirs dir)
      (spit (clojure.java.io/file dir (str (-> (:timestamp change) (t/instant) (t/local-date-time "GMT0") (t/format)) ".md")) (:added change)))))

(defn linkify-issues
  "Convert issue IDs in text to HTML links"
  [text]
  (when text
    (let [issue-pattern #"[A-Z]+-\d+"
          matches (re-seq issue-pattern text)
          parts (str/split text issue-pattern -1)]
      (if (empty? matches)
        text
        (into [:span]
              (interleave
                parts
                (concat
                  (map (fn [issue]
                         [:a.issue-link
                          {:href (str "https://youtrack.jetbrains.com/issue/" issue)
                           :target "_blank"}
                          issue])
                       matches)
                  (repeat ""))))))))

(defn format-report-html
  "Convert dashboard update results to HTML report"
  [results]
  (let [timestamp (t/format "yyyy-MM-dd HH:mm:ss" (t/local-date-time))]
    (html5
      [:head
       [:meta {:charset "UTF-8"}]
       [:title "Dashboard Report - " timestamp]
       [:style
        "body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
         h1 { color: #2c3e50; }
         h2 { color: #34495e; margin-top: 20px; border-bottom: 2px solid #3498db; padding-bottom: 5px; }
         .dashboard { background: white; padding: 15px; margin: 15px 0; border-radius: 5px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
         .dashboard-title { font-size: 1.2em; font-weight: bold; color: #2980b9; margin-bottom: 10px; }
         .diff { margin: 5px 0; padding: 8px; background-color: #ecf0f1; border-left: 3px solid #3498db; }
         .diff-new { color: #27ae60; }
         .diff-old { color: #e74c3c; text-decoration: line-through; }
         .missed { margin: 10px 0; padding: 10px; background-color: #fff3cd; border-left: 3px solid #ffc107; }
         .missed-title { font-weight: bold; color: #856404; }
         .issue-link { color: #3498db; text-decoration: none; }
         .issue-link:hover { text-decoration: underline; }
         .no-changes { color: #7f8c8d; font-style: italic; padding: 10px; }
         .timestamp { color: #95a5a6; font-size: 0.9em; }"]]
      [:body
       [:h1 "Dashboard Update Report"]
       [:p.timestamp "Generated: " timestamp]
       (if (empty? results)
         [:p.no-changes "No changes detected in any dashboard."]
         (for [result results]
           [:div.dashboard
            [:div.dashboard-title
             [:a.issue-link
              {:href (str "https://youtrack.jetbrains.com/articles/" (:id result))
               :target "_blank"}
              (:name result) " (" (:id result) ")"]]
            (if (and (empty? (:diffs result)) (empty? (:missed result)))
              [:p.no-changes "No changes"]
              (list
                (when (not-empty (:diffs result))
                  (list
                    [:h3 "Changes (" (count (:diffs result)) "):"]
                    (for [diff (:diffs result)]
                      [:div.diff
                       (when (:old diff)
                         [:div.diff-old (linkify-issues (:old diff))])
                       (when (:new diff)
                         [:div.diff-new (linkify-issues (:new diff))])])))
                (when (not-empty (:missed result))
                  [:div.missed
                   [:div.missed-title "Missed issues (" (count (:missed result)) "):"]
                   [:ul
                    (for [issue (:missed result)]
                      [:li
                       [:a.issue-link
                        {:href (str "https://youtrack.jetbrains.com/issue/" issue)
                         :target "_blank"}
                        issue]])]])))]))])))

(defn upload-report-to-article
  "Upload HTML report as attachment to YouTrack article"
  [yt-token article-id html-content]
  (let [timestamp (t/format "yyyy-MM-dd-HH-mm-ss" (t/local-date-time))
        filename (str "dashboard-report-" timestamp ".html")]
    (yt-client/upload-attachment {:key yt-token}
                                  (str "articles/" article-id "/attachments")
                                  filename
                                  html-content)))
