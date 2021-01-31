(ns todoist-sync.processed-db)

(require '[next.jdbc :as jdbc])

(def ds (jdbc/get-datasource {:dbtype "postgresql" :dbname "todoist-sync" :user "todoist-sync" :password "todoist-sync"} ))

(jdbc/execute! ds ["SELECT 1"])