(ns todoist-sync.core
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [todoist-sync.todoist :as tdst]
            [todoist-sync.texts-handler :as thd]
            [todoist-sync.workflow :as workflow]
            [ring.util.response :as rur]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.oauth2 :refer [wrap-oauth2]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.session.memory :as ses-mem]
            [promesa.core :as p]
            [clojure.string :as str])
  (:import (com.typesafe.config ConfigFactory)
           (org.joda.time DateTime)
           (java.net URL)
           (java.io File InputStream)))

; a workaround for https://github.com/ring-clojure/ring/issues/184 taken from http://stackoverflow.com/a/35173453/5201186
(defmethod ring.util.response/resource-data :vfs
  [^URL url]
  (let [conn (.openConnection url)
        vfile (.getContent conn)]
    (condp instance? vfile
      File (when-not (.isDirectory ^File vfile)
             {:content        (.getInputStream conn)
              :content-length (.getContentLength conn)
              :last-modified  (-> vfile
                                  .getPhysicalFile
                                  ring.util.io/last-modified-date)})
      InputStream {:content vfile})))

(def last-sent-text (atom nil))

(defn yt-token [request]
  (let [token-info (get-in request [:oauth2/access-tokens :youtrack])
        ^DateTime expires (:expires token-info)]
    (when (and expires (.isAfterNow expires))
      (token-info :token))))

(defn tokens [request]
  {:todoist  (get-in request [:oauth2/access-tokens :todoist :token])
   :youtrack (yt-token request)})

(defroutes json-api
           (-> (context "/json" []
                 (GET "/state" request
                   (rur/response (let [tokens (tokens request)]
                                   (merge (->> tokens (map (fn [[k v]] [k (some? v)])) (into {}))
                                          {:task-options (workflow/handlers-available tokens)}))))
                 (POST "/do-task" request
                   (println "do-task-body" (request :body))
                   (reset! last-sent-text (:text (request :body)))
                   (let [task-id (get-in request [:body :task :value])]
                     (if-let [h (workflow/handler-by-id task-id)]
                       (let [value (h (tokens request) (:body request))]
                         (println "resp = " value)
                         (rur/response value))
                       (rur/bad-request (str "No task " task-id))))))
               (wrap-json-response)
               (wrap-json-body {:keywords? true})))

(defroutes app-routes
           (GET "/" [] (some-> (rur/resource-response "/index.html" {:root "public"}) (rur/content-type "text/html")))
           (route/resources "/")
           json-api
           (POST "/post-task" [text :as request]
             (let [token (get-in request [:oauth2/access-tokens :todoist :token])]
               (println "data = " text "token = " token)
               (println @(tdst/post-task token text))
               (rur/redirect "/" :see-other)))
           (route/not-found "<h1>Not Found</h1>"))

(defonce session-atom (atom {}))



(defn wrap-sync [next-handler sync-handler-to-wrap]
  (let [fix-request (fn [request]
                      (if-let [sctx (:servlet-context-path request)]
                        (-> request
                            (update :uri
                                    (fn [uri pref] (if (str/starts-with? uri pref) (.substring uri (count pref)) uri))
                                    sctx)
                            (assoc :scheme :https))
                        request))]
    (fn
      ([request] ((sync-handler-to-wrap next-handler) (fix-request request)))
      ([request respond raise]
       (let [dr (p/deferred)
             resp ((sync-handler-to-wrap (fn [request] (p/resolve! dr request))) (fix-request request))]
         (if (p/resolved? dr)
           (next-handler @dr respond raise)
           (respond resp)))))))


(def app (let [conf (ConfigFactory/load)]
          (-> app-routes
              (wrap-sync #(wrap-oauth2 % {:todoist
                                          {:authorize-uri    "https://todoist.com/oauth/authorize"
                                           :access-token-uri "https://todoist.com/oauth/access_token"
                                           :client-id        "819888a01ddf4c9ab60d45e60d4a749c"
                                           :client-secret    "d228ac08b0ae4bcba341b1e80e9752b4"
                                           :scopes           ["data:read_write"]
                                           :launch-uri       "/oauth2/todoist"
                                           :redirect-uri     "/oauth2/todoist/callback"
                                           :landing-uri      "/"}
                                          :youtrack
                                          {:authorize-uri    "https://hub.jetbrains.com/api/rest/oauth2/auth"
                                           :access-token-uri "https://hub.jetbrains.com/api/rest/oauth2/token"
                                           :client-id        (.getString conf "oauth.hub.client-id")
                                           :client-secret    (.getString conf "oauth.hub.client-secret")
                                           :scopes           ["YouTrack"]
                                           :launch-uri       "/oauth2/hub"
                                           :redirect-uri     "/oauth2/hub/callback"
                                           :landing-uri      "/"}}))
              (wrap-session {:store (ses-mem/memory-store session-atom)})
              (wrap-cookies)
              (wrap-params))))
