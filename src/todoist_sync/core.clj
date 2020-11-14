(ns todoist-sync.core
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :as rur]))

(defroutes app
           (GET "/" [] (some-> (rur/resource-response "/index.html" {:root "public"}) (rur/content-type "text/html")))
           (route/resources "/")
           (GET "/hello" [] "<h1>Hello World1</h1>")
           (route/not-found "<h1>Not Found</h1>"))
