(ns todoist-sync.yt-client
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]))

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
  ([conf id] (activities id {}))
  ([conf id {:keys [fields categories] :as params}]
   (get-from-yt conf (str "issues/" id "/activities")
                (merge params {:fields (or fields "author(name),field(name),added(name),removed(name)")
                               :categories (or categories "CustomFieldCategory")}))))

(defn issue-links [conf id]
  (get-from-yt conf (str "issues/" id "/links") {:fields "direction,linkType(name),issues(id,numberInProject,project(shortName),summary)"}))

(defn issues-and-links [conf query]
  (->> (issues conf query {:fields "id,numberInProject,project(shortName),summary,value(name),links(direction,linkType(name),issues(id,numberInProject,project(shortName)))"})
       (map (fn [iss]
              (update iss :links
                      (fn [links]
                        (->> links
                             (filter #(not-empty (:issues %)))
                             (map (fn [lnk]
                                    {:name      (get-in lnk [:linkType :name])
                                     :direction (:direction lnk)
                                     :issues    (map :id (:issues lnk))}))
                             (filter #(not= (:direction %) "INWARD")))))))))