(ns todoist-sync.stub-recorder
  (:require [clj-http.client :as client]
            [clojure.data :refer [diff]]
            [clojure.walk :refer [postwalk]]
            [clojure.data.json :as json])
  (:import (java.io InputStream ByteArrayOutputStream ByteArrayInputStream)))

(defn- body-patcher [resp]
  (let [body-is (:body resp)]
    (if (instance? InputStream body-is)
      (let [body-buffer-stream (new ByteArrayOutputStream)
            _ (.transferTo body-is body-buffer-stream)
            bs (.toByteArray body-buffer-stream)]
        (-> resp
            (assoc :body (new ByteArrayInputStream bs))
            (assoc :body-str (String. bs)))))))

(defn wrap-client-to-log [session]
  (fn [client]
    (fn
      ([req]
       (let [resp (body-patcher (client req))]
         (session req (-> resp
                          (assoc :body (:body-str resp))
                          (dissoc :body-str)))
         resp)))))

(defn is-ds [e] ((some-fn sequential? associative? number? string? char? keyword?) e))

(defn no-objects [recs] (postwalk (fn [rec] (when (is-ds rec) rec)) recs))

(defn commonize [recs]
  (let [common (reduce (fn [a b] (last (diff a b))) recs)]
    {:common common
     :items (map (fn [e] (second (diff common e))) recs)}))

(defn json-body [recsno] 
  (map (fn [entry] (update-in entry [:resp :body] json/read-json)) recsno))

(defn- to-fn [sink]
  (condp apply [sink]
    fn? sink
    string? (fn [recorded] (->> recorded (no-objects) (json-body) (commonize) (pr-str) (spit sink)))))

(defn log-clj-http-fn [sink body-fn]
  (let [recorded (atom [])
        session (fn [req resp] (swap! recorded conj {:req req :resp resp}))
        result (client/with-additional-middleware [(wrap-client-to-log session)] (body-fn))]
    ((to-fn sink) @recorded)
    result))

(defmacro log-clj-http [sink & body]
  `(log-clj-http-fn ~sink (fn [] ~@body)))