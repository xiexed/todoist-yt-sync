(defproject todoist-sync "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [javax.servlet/servlet-api "2.5"]
                 [ring "1.8.2"]
                 [ring/ring-core "1.8.2"]
                 [ring/ring-jetty-adapter "1.8.2"]
                 [compojure "1.6.2"]
                 [ring-oauth2 "0.1.5"]
                 [ring/ring-json "0.5.0"]
                 [clj-http "3.10.0"]
                 [cheshire "5.10.0"]
                 [org.clojure/data.json "1.0.0"]
                 [funcool/promesa "6.0.0"]
                 [com.github.ben-manes.caffeine/caffeine "2.8.6"]
                 [com.typesafe/config "1.4.1"]
                 [org.jsoup/jsoup "1.13.1"]
                 [clojure.java-time "0.3.2"]
                 [seancorfield/next.jdbc "1.1.613"]
                 [honeysql "1.0.461"]
                 [org.postgresql/postgresql "42.2.18"]]
  :plugins [[lein-ring "0.12.5"]]
  :ring {:handler todoist-sync.core/app}
  :main  todoist-sync.core
  :repl-options {:init-ns todoist-sync.core})
