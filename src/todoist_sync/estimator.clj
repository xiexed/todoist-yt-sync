(ns todoist-sync.estimator
  (:require [clojure.string :as str]
            [java-time :as t]))


(defn string-flatten [coll]
  (when (seq coll)
    (let [[strings remaining] (split-with string? coll)]
      (cond
        (seq strings) (cons (str/join strings) (lazy-seq (string-flatten remaining)))
        (sequential? (first remaining)) (concat (string-flatten (first remaining)) (lazy-seq (string-flatten (rest remaining))))
        (nil? (first remaining)) (lazy-seq (string-flatten (rest remaining)))
        :else (cons (first remaining) (lazy-seq (string-flatten (rest remaining))))))))


(defn extract-tasks-tree [prsd]
  (->> prsd
       ((fn wlk [e]
          (cond
            (map? e) (let [tag (:tag e)
                           content (:content e)
                           content-handled (if (string? content) content (string-flatten (map wlk content)))
                           title (str/join " " (filter string? content-handled))
                           members (not-empty (remove string? content-handled))]
                       (cond
                         (= "li" tag) (if members {:title   title
                                                   :members members
                                                   :weight  (apply + 1 (map #(:weight % 1) members))}
                                                  {:title title})
                         :else content-handled))
            (coll? e) (map wlk e)
            :else e)))
       (string-flatten)
       (remove string?)))

(defn accumulate
  "(-> (slurp \"last-sent-text.html\") (thd/parse-html-text) (est/extract-tasks-tree) (est/accumulate 10.17))"
  ([lst sp-per-month] (accumulate lst sp-per-month 0 (t/local-date)))
  ([lst sp-per-month init-sp begin]
   (when (seq lst)
     (let [[head & tail] lst
           sp-to-burn (+ init-sp (:weight head 0))
           sp-per-day (/ sp-per-month 30)
           days-to-burn (Math/round (double (/ sp-to-burn sp-per-day)))
           estimate (t/format "yyyy-MM-dd" (t/plus begin (t/days days-to-burn)))]
       (cons {:title  (:title head)
              :weight (:weight head)
              :task-days (Math/round (double (/ (:weight head 0) sp-per-day)))
              :estimate estimate}
             (lazy-seq (accumulate tail sp-per-month sp-to-burn begin)))))))








