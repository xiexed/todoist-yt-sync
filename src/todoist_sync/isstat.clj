(ns todoist-sync.isstat

  (:require [todoist-sync.yt-client :as yt-client]
            [java-time :as t]
            [clojure.string :as str])
  (:import (java.awt Toolkit)
           (java.awt.datatransfer StringSelection)))

(defn get-fixed
  ([yt-token] (get-fixed yt-token "for: me State: Fixed "))
  ([yt-token query]
   (->> (yt-client/issues {:key yt-token} query {:fields "idReadable,resolved"})
        (sort-by :resolved))))

(defn avr-and-adev [my-issues]
  (let [d (->> my-issues
               (sort-by :resolved)
               (map (fn [issue] (-> issue (:resolved) (t/instant) (t/local-date-time "GMT0"))))
               (map (partial t/format "yyyy/MM"))
               (frequencies)
               (into [])
               (sort-by first)
               (map second))
        avr (double (/ (apply + d) (count d)))
        devs (map (fn [v] (Math/pow (- avr v) 2)) d)
        adev (Math/sqrt (/ (apply + devs) (count d)))] [avr adev]))

(defn to-clipboard [content]
  (let [str-to-clipboard (fn [content] (-> (Toolkit/getDefaultToolkit) (.getSystemClipboard) (.setContents (StringSelection. content) nil)))
        item-to-str (fn [item] (cond
                                 (number? item) (str/replace (str item) "." ",")
                                 (string? item) item
                                 (coll? item) (str/join "\t" item)
                                 :else item))]
    (cond
      (string? content) (str-to-clipboard content)
      (coll? content) (str-to-clipboard (->> content (map item-to-str) (str/join "\n")))
      :else (.println System/err (str "can't handle" content)))))