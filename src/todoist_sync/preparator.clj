(ns todoist-sync.preparator
  (:require [todoist-sync.yt-client :as yt]
            [clojure.set :as set]))

(defn enhance-referenced-issues
  "Enhances the `referencedIssues` field in each commit map with full issue data from YouTrack."
  [conf commits]
  (let [; Collect all unique referenced issue IDs from the commits
        all-issue-ids (->> commits
                           (mapcat :referencedIssues)
                           (remove nil?)
                           set)
        ; Query YouTrack for all these issues
        issues (yt/issues-to-analyse conf (clojure.string/join " or " all-issue-ids))
        ; Build a map from idReadable to issue data
        issue-map (into {} (map (juxt :idReadable identity) issues))]
    ; Enhance each commit
    (mapv (fn [commit]
            (if-let [refs (:referencedIssues commit)]
              (assoc commit :referencedIssues
                            (mapv #(get issue-map % {:idReadable %}) refs))
              commit))
          commits)))