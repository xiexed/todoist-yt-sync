(ns todoist-sync.workdash-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer :all]
            [todoist-sync.utils.utils :as u]
            [todoist-sync.workdash :as wd]))


(defn use-dumped-load-issue-data [filename]
  (let [store (u/load-edn filename)]
    (fn [_ issue]
      (store issue))))

(deftest test-replace
  (testing "Test replace in wd"
    (with-redefs [wd/load-issue-data (use-dumped-load-issue-data "test/todoist_sync/workdash_data/ws1-issues.edn")]
      (is (= (u/load-edn "test/todoist_sync/workdash_data/ws1-upd.edn")
             (wd/patch-outdated "no-token" (slurp "test/todoist_sync/workdash_data/ws1.md")))))))