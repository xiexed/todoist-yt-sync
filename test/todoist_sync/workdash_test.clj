(ns todoist-sync.workdash-test
  (:require [clojure.test :refer :all]
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
             (wd/patch-outdated "no-token" (slurp "test/todoist_sync/workdash_data/ws1.md") [:state]))))))

(deftest test-backport-detection-integration
  (testing "Test backport detection in patch-outdated"
    (with-redefs [wd/load-issue-data (use-dumped-load-issue-data "test/todoist_sync/workdash_data/backport-test-issues.edn")]
      (is (= (u/load-edn "test/todoist_sync/workdash_data/backport-test-expected.edn")
             (wd/patch-outdated "no-token" (slurp "test/todoist_sync/workdash_data/backport-test.md") [:state]))))))

(deftest test-enhance-issue-state
  (testing "Fixed state with backport necessary returns 'Backporting'"
    (let [issue-data {:state "Fixed"
                      :tags ["backport-to-251"]
                      :planned-for ["2025.2" "2025.1.X"]
                      :included-in ["252.18515"]
                      :customFields [{:name "Verified" :value nil}]}]
      (is (= "Backporting" (:state (wd/enhance-issue-state issue-data))))))

  (testing "Fixed state with version mismatch returns 'Backporting'"
    (let [issue-data {:state "Fixed"
                      :tags []
                      :planned-for ["2025.2" "2025.1.X"]
                      :included-in ["252.18515"]
                      :customFields [{:name "Verified" :value nil}]}]
      (is (= "Backporting" (:state (wd/enhance-issue-state issue-data))))))

  (testing "Fixed state with verified field returns 'Verified'"
    (let [issue-data {:state "Fixed"
                      :tags []
                      :planned-for ["2025.2"]
                      :included-in ["252.18515"]
                      :customFields [{:name "Verified" :value {:name "Yes"}}]}]
      (is (= "Verified" (:state (wd/enhance-issue-state issue-data))))))

  (testing "Fixed state without backport or verification returns 'Not-verified'"
    (let [issue-data {:state "Fixed"
                      :tags []
                      :planned-for ["2025.2"]
                      :included-in ["252.18515"]
                      :customFields [{:name "Verified" :value nil}]}]
      (is (= "Not-verified" (:state (wd/enhance-issue-state issue-data))))))

  (testing "Included can be wider than planned for"
    (let [issue-data {:state "Fixed"
                      :tags []
                      :planned-for ["2025.2"]
                      :included-in ["252.18515" "251.15231"]
                      :customFields [{:name "Verified" :value nil}]}]
      (is (= "Not-verified" (:state (wd/enhance-issue-state issue-data))))))

  (testing "Fixed state with empty planned-for returns 'Not-verified'"
    (let [issue-data {:state "Fixed"
                      :tags []
                      :planned-for []
                      :included-in ["252.18515"]
                      :customFields [{:name "Verified" :value nil}]}]
      (is (= "Not-verified" (:state (wd/enhance-issue-state issue-data))))))

  (testing "Fixed state with 'Requested' in planned-for returns 'Not-verified'"
    (let [issue-data {:state "Fixed"
                      :tags []
                      :planned-for ["Requested"]
                      :included-in ["252.18515"]
                      :customFields [{:name "Verified" :value nil}]}]
      (is (= "Not-verified" (:state (wd/enhance-issue-state issue-data))))))

  (testing "Non-Fixed states return original state unchanged"
    (let [test-cases [["Open" "Open"]
                      ["In Progress" "In Progress"]
                      ["Reopened" "Reopened"]
                      ["Submitted" "Submitted"]]]
      (doseq [[original-state expected-state] test-cases]
        (let [issue-data {:state original-state
                          :tags []
                          :planned-for ["2025.2"]
                          :included-in ["252.18515"]
                          :customFields [{:name "Verified" :value nil}]}]
          (is (= expected-state (:state (wd/enhance-issue-state issue-data)))
              (str "Expected " original-state " to remain unchanged"))))))

  (testing "Original issue data is preserved except for state"
    (let [issue-data {:idReadable "IJPL-123456"
                      :summary "Test issue"
                      :state "Fixed"
                      :assignee "Test User"
                      :tags []
                      :planned-for ["2025.2"]
                      :included-in ["252.18515"]
                      :customFields [{:name "Verified" :value nil}]}
          enhanced (wd/enhance-issue-state issue-data)]
      (is (= "IJPL-123456" (:idReadable enhanced)))
      (is (= "Test issue" (:summary enhanced)))
      (is (= "Test User" (:assignee enhanced)))
      (is (= "Not-verified" (:state enhanced))))))