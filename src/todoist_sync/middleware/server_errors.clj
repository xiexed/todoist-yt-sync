(ns todoist-sync.middleware.server-errors
  (:require [clojure.string :as str])
  (:import (java.io PrintWriter)))

(defn- request-description [request]
  (str (some-> request :request-method name str/upper-case)
       " "
       (or (:uri request) "<unknown URI>")))

(defn- server-error-response? [response]
  (let [status (:status response)]
    (and (integer? status)
         (<= 500 status 599))))

(defn- log-server-error-response! [request response]
  (when (server-error-response? response)
    (println "HTTP server error response:"
             (request-description request)
             "->"
             (:status response))
    (flush))
  response)

(defn- log-unhandled-error! [request ^Throwable throwable]
  (println "HTTP request failed with an unhandled error:"
           (request-description request))
  (when-let [data (ex-data throwable)]
    (println "Exception data:" (pr-str data)))
  (.printStackTrace throwable (PrintWriter. *out*))
  (flush))

(defn wrap-server-errors
  "Log every 5xx Ring response and every unhandled request error to stdout."
  [handler]
  (fn
    ([request]
     (try
       (log-server-error-response! request (handler request))
       (catch Throwable throwable
         (log-unhandled-error! request throwable)
         (throw throwable))))
    ([request respond raise]
     (try
       (handler request
                (fn [response]
                  (respond (log-server-error-response! request response)))
                (fn [throwable]
                  (log-unhandled-error! request throwable)
                  (raise throwable)))
       (catch Throwable throwable
         (log-unhandled-error! request throwable)
         (raise throwable))))))
