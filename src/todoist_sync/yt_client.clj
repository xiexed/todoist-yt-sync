(ns todoist-sync.yt-client
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [todoist-sync.utils.utils :as u]))

(defn mk-url [conf path] (str (conf :host "https://youtrack.jetbrains.com/api/") path))

(defn yt-request
  ([conf method url params]
   (-> (client/request
         (merge {:url          (mk-url conf url)
                 :method       method
                 :content-type :json
                 :headers      {"Authorization" (str "Bearer " (conf :key))
                                "Cache-Control" "no-cache"
                                "Content-Type"  "application/json"}
                 :accept       :json} params))
       (update :body json/read-json)))
  ([conf url] (yt-request conf :get url {})))

(defn get-from-yt
  ([conf path query]
   (get-from-yt conf path query {}))
  ([conf path query params]
   (:body (yt-request conf :get path (assoc params :query-params query)))))

(defn update-on-yt
  ([conf path data]
   (update-on-yt conf path data {}))
  ([conf path data params]
   (:body (yt-request conf :post path (assoc params :body (json/write-str data))))))


(defn projects [conf] (get-from-yt conf "admin/projects" {:fields ["id,name,shortName,createdBy(login,name,id),leader(login,name,id)"]}))

(def ^:dynamic *default-issue-fields* "id,numberInProject,project(shortName),summary,value(name),tags(name)")

(def default-query "assigned to: me State: Unresolved, -Shelved  tag: -{Waiting for review}")

(defn issues
  ([conf] (issues conf default-query))
  ([conf query] (issues conf query {}))
  ([conf query y-params]
   (issues conf query y-params {}))
  ([conf query {:keys [fields]} req-params]
   ((fn cons-load [skip]
      (let [chunk-size 500
            chunk (get-from-yt conf "issues" {:fields (or fields *default-issue-fields*) :$skip skip :$top chunk-size :query query} req-params)]
        (if (< (count chunk) chunk-size)
          chunk
          (concat chunk (lazy-seq (cons-load (+ skip chunk-size)))))))
    0)))

(defn issue
  ([conf id] (issue conf id {}))
  ([conf id y-params]
   (issue conf id y-params {}))
  ([conf id {:keys [fields]} params]
   (get-from-yt conf (str "issues/" id) {:fields (or fields *default-issue-fields*)} params)))

(defn activities
  ([conf id] (activities conf id {}))
  ([conf id {:keys [fields categories] :as params}]
   (get-from-yt conf (str "issues/" id "/activities")
                (merge params {:fields     (or fields "author(name),field(name,value),added(name,value),removed(name,value),timestamp")
                               :categories (or categories "CustomFieldCategory")}))))

(defn clean-up-activity [act]
  (let [simplify (fn [ar]
                   (if (sequential? ar)
                     (not-empty (map :name ar))
                     ar)
                   )]
    (u/non-zero-map
      :field (:name (:field act))
      :author (:name (:author act))
      :timestamp (:timestamp act)
      :removed (simplify (:removed act))
      :added (simplify (:added act)))))

(defn me [yt-token]
  (get-from-yt {:key yt-token} "/users/me" {:fields ["id,login,ringId"]}))

(defn issue-links [conf id]
  (get-from-yt conf (str "issues/" id "/links") {:fields "direction,linkType(name),issues(id,numberInProject,project(shortName),summary)"}))

(defn custom-field [issue-data name]
  (->> (:customFields issue-data)
       (filter #(= name (:name %)))
       (first)
       :value))

(defn clean-up [iss]
  (dissoc (merge iss
                 (u/non-zero-map
                   :links (->> (:links iss)
                               (filter #(not-empty (:issues %)))
                               (map (fn [lnk]
                                      {:name      (get-in lnk [:linkType :name])
                                       :direction (:direction lnk)
                                       :issues    (map :idReadable (:issues lnk))}))
                               (filter #(not= (:direction %) "INWARD"))
                               (group-by :name)
                               (u/map-vals (fn [e] (mapcat :issues e))))
                   :tags (some->> (:tags iss) (map :name))
                   :activities (some->> (:comments iss)
                                        (map (fn [comment]
                                               {
                                                :author    (:name (:author comment))
                                                :timestamp (:created comment)
                                                :text      (:text comment)
                                                }
                                               ))
                                        (concat (:activities iss))
                                        (sort-by :timestamp)
                                        )
                   :state (:name (custom-field iss "State"))
                   :reporter (:email (:reporter iss))
                   :priority (:name (custom-field iss "Priority"))
                   :type (:name (custom-field iss "Type"))
                   :subsystem (:name (custom-field iss "Subsystem"))
                   :assignee (:name (custom-field iss "Assignee"))
                   :state (:name (custom-field iss "State"))
                   :type (:name (custom-field iss "Type"))
                   :planned-for (:name (custom-field iss "Planned for"))
                   )) :customFields :comments))

(defn issues-and-links [conf query]
  (->> (issues conf query {:fields "id,numberInProject,project(shortName),summary,value(name),links(direction,linkType(name),issues(idReadable,numberInProject,project(shortName)))"})
       (map clean-up)))

(defn issues-to-analyse [conf query]
  (->> (issues conf query {:fields "idReadable,summary,description,reporter(email),value(name),tags(name),votes,resolved,updated,customFields(id,name,value(name, value, id)),links(direction,linkType(name),issues(idReadable,numberInProject,project(shortName))),comments(text,created,author(name))"})
       (take 1000)
       (map (fn [issue]
              (assoc issue :activities (map clean-up-activity (activities conf (:idReadable issue))))))
       (map clean-up)))

(defn command [conf source-issue-id cmd]
  (let [source-ids (if (seqable? source-issue-id) source-issue-id [source-issue-id])
        command-data {:query  cmd
                      :issues (mapv (fn [id] {:idReadable id}) source-ids)}]
    (when (not-empty source-ids)
      (update-on-yt conf "commands" command-data))))

(defn add-issue-link [conf source-issue-id target-issue-id link-type]
  (command conf source-issue-id (str link-type " " target-issue-id)))

