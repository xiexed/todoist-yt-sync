(ns todoist-sync.utils.utils
  (:refer-clojure :exclude [when-some])
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.io PushbackReader)))

(defn map-vals [f m]
  (zipmap (keys m) (map f (vals m))))

(defn keep-vals [f m]
  (->> m (keep (fn [[k v]] (some->> (f v) (conj [k])))) (into {})))

(defn log
  [& values] (apply println values) (last values))

(defn take-if [pred e] (when (and e (pred e)) e))

(defn str-spaced [& args] (str/join " " (filter some? args)))

(defn println-err [& args]
  (binding [*out* *err*] (apply println args)))

(defn load-edn
  "Load edn from an io/reader source (filename or io/resource)."
  [source]
  (with-open [r (io/reader source)]
    (edn/read (PushbackReader. r))))

(defn trim-ext [^String filename]
  (let [i (.lastIndexOf filename ".")]
    (if (> i 0) (.substring filename 0 i) filename)))

(defmacro enhance-error [message operation]
  `(try ~operation
        (catch Exception e# (throw (new RuntimeException ~message e#)))
        (catch AssertionError e# (throw (new AssertionError ~message e#)))))

(defmacro check [pred message operation]
  `(let [r# ~operation]
     (if (~pred r#) r# (throw (new IllegalStateException ~message)))))

(defmacro when-some
  "bindings => binding-form test, binding-form test, ...

   When every test is not nil, evaluates body with each binding-form bound to the
   value of its corresponding test"
  [bindings & body]
  (assert (vector? bindings) "a vector for its binding")
  (assert (even? (count bindings)) "exactly even forms in binding vector")
  (if (== 2 (count bindings))
    `(let [temp# ~(second bindings)]
       (if (nil? temp#)
         nil
         (let [~(first bindings) temp#]
           ~@body)))
    (if (seq bindings)
      `(when-some [~(first bindings) ~(second bindings)]
         (when-some ~(vec (rest (rest bindings))) ~@body))
      `(do ~@body))))

(defn non-zero-map [& pairs]
  (if (and (= 1 (bounded-count 2 pairs)) (associative? (first pairs)))
    (apply non-zero-map (mapcat identity (first pairs)))
    (->> pairs (partition 2) (keep (fn [[k v]] (when (some? v) [k v]))) (into {}))))

(defn single-or-vec [s] (if (> (bounded-count 2 s) 1) s (first s)))

(defmacro fn->> [& body] `(fn [arg#] (->> arg# ~@body)))
(defmacro fn-> [& body] `(fn [arg#] (-> arg# ~@body)))

(defmacro ssel [skey & body] (if (empty? body)
                               `(fn [arg#] (arg# ~skey))
                               (let [[h & tail] body]
                                 `(fn [arg#] (~h (arg# ~skey) ~@tail)))
                               ))

(defn avr [& args] (/ (reduce + args) (count args)))

(defn abs "(abs n) is the absolute value of n" [n]
  (cond
    (not (number? n)) (throw (IllegalArgumentException.
                               "abs requires a number"))
    (neg? n) (- n)
    :else n))