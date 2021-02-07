(ns todoist-sync.texts-handler
  (:require [clojure.string :as str])
  (:import (org.jsoup Jsoup)
           (org.jsoup.nodes Element Node TextNode Attribute)
           (java.util.regex Pattern)))


(def ^Pattern issue-pattern (re-pattern "[A-Z\\-]+-\\d+"))

(def ^String issues-selector (str ":matchesOwn(" (.pattern issue-pattern) ")"))

(defn tag-name [node]
  (when (instance? Element node) (.tagName ^Element node)))

(defn whole-text [node]
  (condp instance? node
    Element (.wholeText ^Element node)
    TextNode (.text ^TextNode node)))

(defn a-surrounding-text [^Element a]
  (let [nexts (->> a (iterate #(.nextSibling %)) (take-while #(#{"a" "span"} (tag-name %))))
        prevs (->> a (iterate #(.previousSibling %)) (drop 1) (take-while some?) (take-while #(nil? (tag-name %))) (reverse))
        aas (concat prevs nexts)]
    (if (-> (count aas) (> 1))
      (str/join (map whole-text aas))
      (.text (.parent a)))))

(defn extract-issues-from-html
  ([html-text {:keys [single-task], :or {single-task false}, :as settings}]
   (let [make-entries-seq (fn [parent-text occ] (map (fn [iss] {:issue iss :input-text parent-text}) (re-seq issue-pattern occ)))]
     (if single-task
       (let [text (.text (Jsoup/parseBodyFragment html-text))]
         (make-entries-seq text text))
       (->> (.select (Jsoup/parseBodyFragment html-text) issues-selector)
           (mapcat (fn [occ] (let [parent-text (or (some-> (filter #(= "a" (tag-name %)) (cons occ (.parents occ)))
                                                           (first)
                                                           (a-surrounding-text))
                                                   (.text (.parent occ)))]
                               (make-entries-seq parent-text (.ownText occ)))))))))
  ([html-text] (extract-issues-from-html html-text {})))

(defn walk-elem [^Node node]
  (condp instance? node
    Element {:tag        (.nodeName ^Element node)
             :attributes (not-empty (->> (.attributes node)
                                         (map (fn [^Attribute attr] [(keyword (.getKey attr)) (.getValue attr)]))
                                         (into {})))
             :content    (if-let [children (not-empty (.childNodes node))]
                           (map walk-elem children))}
    TextNode (.text ^TextNode node)
    :else nil))

(defn parse-html-text [rich-html-text]
  (map walk-elem (.childNodes (.body (Jsoup/parseBodyFragment rich-html-text)))))

(defn add-suffix [s suffix] (if (str/ends-with? s suffix) s (str s suffix)))

(defn to-markdown [node]
  (cond (string? node) node
        (map? node) (let [{:keys [tag attributes content]} node]
                      (cond
                        (= tag "a") (str "[" (to-markdown content) "]" "(" (:href attributes) ")")
                        (= tag "strong") (str "**" (to-markdown content) "**")
                        (= tag "li") (str "\n  *  " (add-suffix (to-markdown content) "\n"))
                        (#{"div" "span" "p"} tag) (str (to-markdown content) "\n")
                        :else (to-markdown content)))
        (seq? node) (str/join (map to-markdown node))
        (nil? node) ""
        :else (throw (IllegalArgumentException. ^String (some-> node (.toString))))))
