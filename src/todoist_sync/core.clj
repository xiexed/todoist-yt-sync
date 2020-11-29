(ns todoist-sync.core
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [todoist-sync.todoist :as tdst]
            [ring.util.response :as rur]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.oauth2 :refer [wrap-oauth2]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.session.memory :as ses-mem]
            [ring.logger :as logger]))

(defroutes app-routes
           (GET "/" [] (some-> (rur/resource-response "/index.html" {:root "public"}) (rur/content-type "text/html")))
           (route/resources "/")
           (POST "/post-task" [text :as request]
             (let [token (get-in request [:oauth2/access-tokens :todoist :token])]
               (println "data = " text "token = " token)
               (println @(tdst/post-task token text))
             (rur/redirect "/" :see-other)))
           (route/not-found "<h1>Not Found</h1>"))

(defonce session-atom (atom {}))

(def app (-> app-routes
             (wrap-oauth2 {:todoist
                           {:authorize-uri    "https://todoist.com/oauth/authorize"
                            :access-token-uri "https://todoist.com/oauth/access_token"
                            :client-id        "819888a01ddf4c9ab60d45e60d4a749c"
                            :client-secret    "d228ac08b0ae4bcba341b1e80e9752b4"
                            :scopes           ["data:read_write"]
                            :launch-uri       "/oauth2/todoist"
                            :redirect-uri     "/oauth2/todoist/callback"
                            :landing-uri      "/"}})
             (wrap-session {:store (ses-mem/memory-store session-atom)})
             (wrap-cookies)
             (wrap-params)
             (logger/wrap-with-logger)))
