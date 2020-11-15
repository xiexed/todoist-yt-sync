(ns todoist-sync.core
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :as rur]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.oauth2 :refer [wrap-oauth2]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.logger :as logger]))

(defroutes app-routes
           (GET "/" [] (some-> (rur/resource-response "/index.html" {:root "public"}) (rur/content-type "text/html")))
           (route/resources "/")
           (GET "/hello" [:as request] (str "<h1>Hello World1</h1>" "<div>" (get-in request [:oauth2/access-tokens :todoist]) "</div>"))
           (route/not-found "<h1>Not Found</h1>"))


(def app (-> app-routes
             (wrap-oauth2 {:todoist
                           {:authorize-uri    "https://todoist.com/oauth/authorize"
                            :access-token-uri "https://todoist.com/oauth/access_token"
                            :client-id        "97eb0650ea32417190572e0df5b0ecc7"
                            :client-secret    "c5f9955e9bf34487b7e889dd9de85397"
                            :scopes           ["data:read"]
                            :launch-uri       "/oauth2/todoist"
                            :redirect-uri     "/oauth2/todoist/callback"
                            :landing-uri      "/"}})
             (wrap-session)
             (wrap-cookies)
             (wrap-params)
             (logger/wrap-with-logger)))
