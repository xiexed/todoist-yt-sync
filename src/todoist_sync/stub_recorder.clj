(ns todoist-sync.stub-recorder
  (:require [clj-http.client :as client])
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

(defn log-clj-http-fn [file body-fn]
  (let [recorded (atom [])
        session (fn [req resp] (swap! recorded conj {:req req :resp resp}))
        result (client/with-additional-middleware [(wrap-client-to-log session)] (body-fn))]
    (spit file (pr-str @recorded))
    result))

(defmacro log-clj-http [file & body]
  `(log-clj-http-fn ~file (fn [] ~@body)))