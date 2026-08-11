(ns todoist-sync.middleware.server-errors-test
  (:require [clojure.test :refer :all]
            [todoist-sync.middleware.server-errors :refer [wrap-server-errors]]))

(deftest logs-5xx-responses
  (let [output (with-out-str
                 ((wrap-server-errors (constantly {:status 503 :body "Unavailable"}))
                  {:request-method :get :uri "/health"}))]
    (is (= "HTTP server error response: GET /health -> 503\n" output))))

(deftest does-not-log-non-5xx-responses
  (let [output (with-out-str
                 ((wrap-server-errors (constantly {:status 404 :body "Not found"}))
                  {:request-method :get :uri "/missing"}))]
    (is (empty? output))))

(deftest logs-and-rethrows-unhandled-errors
  (let [failure (ex-info "database unavailable" {:database "todoist"})
        output (with-out-str
                 (is (identical?
                      failure
                      (try
                        ((wrap-server-errors (fn [_] (throw failure)))
                         {:request-method :post :uri "/json/do-task"})
                        (catch Throwable throwable
                          throwable)))))]
    (is (re-find #"HTTP request failed with an unhandled error: POST /json/do-task" output))
    (is (re-find #"Exception data: \{:database \"todoist\"\}" output))
    (is (re-find #"clojure.lang.ExceptionInfo: database unavailable" output))))

(deftest logs-errors-from-asynchronous-handlers
  (let [failure (RuntimeException. "async failure")
        raised (atom nil)
        output (with-out-str
                 ((wrap-server-errors
                   (fn [_ _respond raise]
                     (raise failure)))
                  {:request-method :get :uri "/async"}
                  (fn [_])
                  #(reset! raised %)))]
    (is (identical? failure @raised))
    (is (re-find #"HTTP request failed with an unhandled error: GET /async" output))
    (is (re-find #"java.lang.RuntimeException: async failure" output))))
