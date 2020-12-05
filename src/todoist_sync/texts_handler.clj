(ns todoist-sync.texts-handler
  (:import (org.jsoup Jsoup)
           (org.jsoup.nodes Element)))


(def issue-pattern (re-pattern "[A-Z]+-\\d+"))

(defn extract-issues-id-from-lis [html-text]
  (let [d (Jsoup/parseBodyFragment html-text)]
    (->> (.select d "li > span")
         (mapcat (fn [^Element li] (re-seq issue-pattern (.text li)))))))