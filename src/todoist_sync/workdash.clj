(ns todoist-sync.workdash
  (:require [todoist-sync.yt-client :as yt-client]
            [clojure.string :as s]))

(def dashboards
  [{:id "IDEA-A-2100662318" :assignee "Nicolay Mitropolsky"}
   {:id "IDEA-A-2100662338" :assignee "Polina Popova"}
   {:id "IDEA-A-2100662330" :assignee "Not team members"}
   {:id "IDEA-A-2100662325" :assignee "Yuri Trukhin"}
   {:id "IDEA-A-2100662319" :assignee "Andrei Ogurtsov"}
   {:id "IDEA-A-2100662321" :assignee "Vladimir Petrenko"}
   {:id "IDEA-A-2100662323" :assignee "Andrei Beliaev"}
   {:id "IDEA-A-2100662320" :assignee "Petr Galunov"}
   {:id "IDEA-A-2100662322" :assignee "Nurullokhon Gulomkodirov"}
   {:id "IDEA-A-2100662324" :assignee "Nikita Katkov"}])

(defn load-article [yt-token article]
  (yt-client/get-from-yt {:key yt-token} (str "/articles/" article) {:fields "content,summary"}))

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


; (map #(wd/load-issue-data my-yt-token %) (mapcat :issues (wd/parse-md-to-sections text)))
(defn load-issue-data [yt-token issue]
  ;(map (fn [issue] (filter (fn [e] (#{"Assignee" "Planned for"} (:name e))) (yt-client/get-from-yt {:key yt-token} (str "issues/" issue "/customFields") {:fields "id,name,value(name, value, id)"}))))
  (let [custom-fields (yt-client/get-from-yt {:key yt-token} (str "issues/" issue "/customFields") {:fields "id,name,value(name, value, id)"})
        selected_fields (filter (fn [e] (#{"Assignee" "Planned for"} (:name e))) custom-fields)
        assignee-field (first (filter #(= "Assignee" (:name %)) selected_fields))
        planned-for-field (first (filter #(= "Planned for" (:name %)) selected_fields))]
    {:issue       issue
     :assignee    (get-in assignee-field [:value :name])
     :planned-for (map :name (:value planned-for-field))}))

(defn report1 [yt-token]
  (map (fn [d] (let [loaded (load-article yt-token (:id d))
                     _ (println "id" (:id d))]
                 {:assignee (:summary loaded)
                  :issues   (parse-md-to-sections (:content loaded))})) dashboards))

(defn report2 [yt-token report1-result]

  )

