(defproject todoist-sync "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [javax.servlet/servlet-api "2.5"]
                 [ring "1.8.2"]
                 [compojure "1.6.2"]
                 [ring-oauth2 "0.1.5"]
                 [clj-http "3.10.0"]
                 [cheshire "5.10.0"]
                 [org.clojure/data.json "1.0.0"]
                 [funcool/promesa "6.0.0"]
                 [com.github.ben-manes.caffeine/caffeine "2.8.6"]]
  :plugins [[lein-ring "0.12.5"]]
  :ring {:handler todoist-sync.core/app}
  :repl-options {:init-ns todoist-sync.core})
