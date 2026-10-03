(ns todoist-sync.workdash-test
  (:require [clojure.test :refer :all]
            [todoist-sync.utils.utils :as u]
            [todoist-sync.workdash :as wd]
            [todoist-sync.yt-client :as yt-client]))

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

(deftest test-dashboard-emphasized-states
  (let [renderer (wd/wd-conditional-assignee-renderer "Dashboard Owner")]
    (doseq [state ["Answered" "Backporting" "Duplicate" "Fixed in Branch" "Incomplete" "In progress" "No QA" "Obsolete"
                   "Works As Intended"]]
      (testing state
        (is (= (str " **\\[" state "\\]**")
               (:suffix (renderer {:state state :assignee "Dashboard Owner"}))))))))

(deftest test-stale-state-label
  (is (nil? (wd/stale-state-label nil)))
  (is (nil? (wd/stale-state-label 20)))
  (is (= ">20d" (wd/stale-state-label 21)))
  (is (= ">20d" (wd/stale-state-label 30)))
  (is (= ">30d" (wd/stale-state-label 31)))
  (is (= ">40d" (wd/stale-state-label 45))))

(deftest test-dashboard-stale-state-age
  (let [renderer (wd/wd-conditional-assignee-renderer "Dashboard Owner")
        suffix #(:suffix (renderer (merge {:assignee "Dashboard Owner"} %)))]
    (testing "Age is not shown for 20 days and less"
      (is (= " **\\[In progress\\]**" (suffix {:state "In progress" :state-days 20}))))
    (testing "Age is shown in bold rounded down to tens"
      (is (= " **\\[In progress\\]\\[>30d\\]**" (suffix {:state "In progress" :state-days 37})))
      (is (= " [Testing]**\\[>20d\\]**" (suffix {:state "Testing" :state-days 25}))))))

(defn- state-activity [field timestamp added & [removed]]
  {:category  {:id "CustomFieldCategory"}
   :field     {:name field}
   :timestamp timestamp
   :added     (map (fn [n] {:name n}) added)
   :removed   (map (fn [n] {:name n}) removed)})

(defn- tag-activity [timestamp added]
  {:category {:id "TagsCategory"} :field {:name "tag"} :timestamp timestamp :added [{:name added}] :removed []})

(deftest test-state-since
  (let [fixed {:state "Fixed" :qa "QA" :planned-for ["2025.2"] :included-in ["252.1"] :tags [] :created 1}]
    (testing "Raw state is counted from the last change to it"
      (is (= 300 (wd/state-since {:state "In Progress" :created 1}
                                 [(state-activity "State" 100 ["In Progress"] ["Open"])
                                  (state-activity "State" 200 ["Open"] ["In Progress"])
                                  (state-activity "Assignee" 250 ["Someone"])
                                  (state-activity "State" 300 ["In Progress"] ["Open"])]))))
    (testing "Issue created in its state is counted from creation"
      (is (= 1 (wd/state-since {:state "In Progress" :created 1} []))))
    (testing "Testing starts when QA is assigned after Fixed"
      (is (= 200 (wd/state-since fixed [(state-activity "State" 100 ["Fixed"] ["Open"])
                                        (state-activity "QA" 200 ["QA"])]))))
    (testing "Backporting starts when backport becomes necessary, not when issue is fixed"
      (is (= 300 (wd/state-since (assoc fixed :tags ["backport-to-251"])
                                 [(state-activity "State" 100 ["Fixed"] ["Open"])
                                  (state-activity "QA" 200 ["QA"])
                                  (tag-activity 300 "backport-to-251")]))))
    (testing "Testing restarts when Verified is reset"
      (is (= 400 (wd/state-since fixed [(state-activity "State" 100 ["Fixed"] ["Open"])
                                        (state-activity "Verified" 300 ["Yes"])
                                        (state-activity "Verified" 400 [] ["Yes"])]))))
    (testing "Changes made at the same moment are undone together"
      (is (= 100 (wd/state-since fixed [(state-activity "State" 100 ["Fixed"] ["Open"])
                                        (state-activity "QA" 100 ["QA"])
                                        (state-activity "Verified" 200 ["Yes"])
                                        (state-activity "Verified" 200 [] ["Yes"])]))))))

(deftest test-patch-outdated-state-age
  (let [day (* 24 60 60 1000)
        now (System/currentTimeMillis)
        issues {"IJPL-1" {:idReadable "IJPL-1" :id "1-1" :summary "Long in progress" :state "In Progress"
                          :assignee "Dashboard Owner" :created (- now (* 90 day))}
                "IJPL-2" {:idReadable "IJPL-2" :id "1-2" :summary "Long in testing" :state "Fixed" :qa "QA"
                          :verified nil :planned-for [] :assignee "Dashboard Owner" :created (- now (* 45 day))}
                "IJPL-3" {:idReadable "IJPL-3" :id "1-3" :summary "Long open" :state "Open"
                          :assignee "Dashboard Owner" :created (- now (* 90 day))}}
        requested (atom [])]
    (with-redefs [wd/load-issue-data (fn [_ issue] (issues issue))
                  yt-client/get-all-from-yt-lazy
                  (fn [_ path _ _]
                    (swap! requested conj path)
                    (case path
                      "issues/1-1/activities" [(state-activity "State" (- now (* 60 day)) ["In Progress"] ["Open"])
                                               (state-activity "State" (- now (* 50 day)) ["Open"] ["In Progress"])
                                               (state-activity "State" (- now (* 22 day)) ["In Progress"] ["Open"])]
                      []))]
      (is (= "IJPL-1 Long in progress [In Progress]**\\[>20d\\]**\nIJPL-2 Long in testing [Testing]**\\[>40d\\]**\nIJPL-3 Long open"
             (:text (wd/patch-outdated "token" "IJPL-1\nIJPL-2\nIJPL-3"
                                       (wd/wd-conditional-assignee-renderer "Dashboard Owner")
                                       {:state-age? true}))))
      (is (= ["issues/1-1/activities" "issues/1-2/activities"] @requested)))))

(deftest test-enhance-issue-state
  (testing "Fixed state with backport necessary returns 'Backporting'"
    (let [issue-data {:state "Fixed"
                      :tags ["backport-to-251"]
                      :planned-for ["2025.2" "2025.1.X"]
                      :included-in ["252.18515"]
                      :verified nil}]
      (is (= "Backporting" (:state (wd/enhance-issue-state issue-data))))))

  (testing "Fixed state with version mismatch returns 'Backporting'"
    (let [issue-data {:state "Fixed"
                      :tags []
                      :planned-for ["2025.2" "2025.1.X"]
                      :included-in ["252.18515"]
                      :verified nil}]
      (is (= "Backporting" (:state (wd/enhance-issue-state issue-data))))))

  (testing "Fixed state with avaliable in"
    (let [issue-data {:state "Fixed"
                      :tags []
                      :planned-for ["2025.2"]
                      :available-in ["NEXT 2025.3 EAP 1"]
                      :verified "Yes"}]
      (is (= "Backporting" (:state (wd/enhance-issue-state issue-data))))))

  (testing "Fixed state with available in"
    (let [issue-data {:state "Fixed"
                      :tags []
                      :planned-for ["2025.2"]
                      :available-in ["2025.2"]
                      :verified "Yes"}]
      (is (= "Verified" (:state (wd/enhance-issue-state issue-data))))))

  (testing "Fixed mixed include an avaliable in"
    (let [issue-data {:state "Fixed"
                      :tags []
                      :planned-for ["2025.2" "2025.1"]
                      :included-in ["252.18515"]
                      :available-in ["2025.1"]
                      :verified "Yes"
                      :qa nil}]
      (is (= "Verified" (:state (wd/enhance-issue-state issue-data))))))

  (testing "Fixed state with verified field returns 'Verified'"
    (let [issue-data {:state "Fixed"
                      :tags []
                      :planned-for ["2025.2"]
                      :included-in ["252.18515"]
                      :verified "Yes"}]
      (is (= "Verified" (:state (wd/enhance-issue-state issue-data))))))

  (testing "Fixed state with verified 'Not Needed' returns 'Fixed'"
    (let [issue-data {:state "Fixed"
                      :tags []
                      :planned-for ["2025.2"]
                      :included-in ["252.18515"]
                      :verified "Not Needed"}]
      (is (= "Fixed" (:state (wd/enhance-issue-state issue-data))))))

  (testing "Fixed state without backport or verification returns 'Not-verified'"
    (let [issue-data {:state "Fixed"
                      :tags []
                      :planned-for ["2025.2"]
                      :included-in ["252.18515"]
                      :verified nil
                      :qa "Polina"}]
      (is (= "Testing" (:state (wd/enhance-issue-state issue-data))))))

  (testing "Fixed state without backport or verification returns 'Not-verified'"
    (let [issue-data {:state "Fixed"
                      :tags []
                      :planned-for ["2025.2"]
                      :included-in ["252.18515"]
                      :verified nil
                      :qa "Polina"}]
      (is (= "Testing" (:state (wd/enhance-issue-state issue-data))))))

  (testing "Included can be wider than planned for"
    (let [issue-data {:state "Fixed"
                      :tags []
                      :planned-for ["2025.2"]
                      :included-in ["252.18515" "251.15231"]
                      :verified nil}]
      (is (= "No QA" (:state (wd/enhance-issue-state issue-data))))))

  (testing "dotted versions"
    (let [issue-data {:tags        ["blocking-release"],
                      :type        "Bug",
                      :state       "Fixed",
                      :included-in ["252.18515" "252.19874.12" "251.26600"],
                      :resolved    1748468930516,
                      :summary     "JSON. Cannot load the latest OpenAPI Specification schema",
                      :planned-for ["2025.2" "2025.1.3"],
                      :id          "25-6537936",
                      :assignee    "Nicolay Mitropolsky",
                      :qa "Polina"
                      :idReadable  "IJPL-188228"}]
      (is (= "Testing" (:state (wd/enhance-issue-state issue-data))))))

  (testing "Fixed state with empty planned-for returns 'No QA'"
    (let [issue-data {:state        "Fixed"
                      :tags         []
                      :planned-for  []
                      :included-in  ["252.18515"]
                      :verified nil}]
      (is (= "No QA" (:state (wd/enhance-issue-state issue-data))))))

  (testing "Fixed state with 'Requested' in planned-for returns 'No QA'"
    (let [issue-data {:state "Fixed"
                      :tags []
                      :planned-for ["Requested"]
                      :included-in ["252.18515"]
                      :verified nil}]
      (is (= "No QA" (:state (wd/enhance-issue-state issue-data))))))

  (testing "Nil version values are ignored"
    (let [issue-data {:state "Fixed"
                      :tags []
                      :planned-for [nil "2025.2"]
                      :included-in [nil "252.18515"]
                      :available-in [nil]
                      :verified "Yes"}]
      (is (= "Verified" (:state (wd/enhance-issue-state issue-data))))))

  (testing "Triaged 'Escalated' returns 'Escalated' state"
    (let [issue-data {:state "Open"
                      :tags []
                      :planned-for ["2025.2"]
                      :included-in ["252.18515"]
                      :verified nil
                      :triaged "Escalated"}]
      (is (= "Escalated" (:state (wd/enhance-issue-state issue-data))))))

  (testing "Triaged 'Escalated' with Fixed state and verified returns 'Verified' (Fixed logic takes precedence)"
    (let [issue-data {:state "Fixed"
                      :tags []
                      :planned-for ["2025.2"]
                      :included-in ["252.18515"]
                      :verified "Yes"
                      :triaged "Escalated"}]
      (is (= "Verified" (:state (wd/enhance-issue-state issue-data))))))

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
                          :verified nil}]
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
                      :verified nil}
          enhanced (wd/enhance-issue-state issue-data)]
      (is (= "IJPL-123456" (:idReadable enhanced)))
      (is (= "Test issue" (:summary enhanced)))
      (is (= "Test User" (:assignee enhanced)))
      (is (= "No QA" (:state enhanced))))))

(deftest test-update-priority-lists-keeps-going-on-issue-error
  (testing "A failed metaissue update is shown in the result and does not stop the rest"
    (let [commands (atom [])]
      (with-redefs [yt-client/issues (fn [_ query params]
                                       (is (= "tag: {Priority-list}" query))
                                       (is (= {:fields "id,idReadable,summary"} params))
                                       [{:id "1-1" :idReadable "IJPL-1" :summary "Forbidden issue"}
                                        {:id "1-2" :idReadable "IJPL-2" :summary "Updated issue"}])
                    wd/update-metaissue-on-server (fn [_ issue-id]
                                                    (if (= "1-1" issue-id)
                                                      (throw (ex-info "clj-http: status 403"
                                                                      {:status 403
                                                                       :reason-phrase "Forbidden"
                                                                       :body "{\"error\":\"Forbidden\"}"}))
                                                      {:id "IJPL-2"
                                                       :name "IJPL-2 Updated issue"
                                                       :missed []
                                                       :detected-from-patched #{"IJPL-3"}
                                                       :diffs [{:new "IJPL-3 Updated"}]}))
                    yt-client/command (fn [_ issue-ids command]
                                        (swap! commands conj {:issue-ids (vec issue-ids)
                                                              :command command}))]
        (is (= [{:id "IJPL-1"
                 :name "IJPL-1 Forbidden issue"
                 :missed []
                 :diffs []
                 :errors [{:message "HTTP 403 Forbidden {\"error\":\"Forbidden\"}"}]}
                {:id "IJPL-2"
                 :name "IJPL-2 Updated issue"
                 :missed []
                 :detected-from-patched #{"IJPL-3"}
                 :diffs [{:new "IJPL-3 Updated"}]}]
               (wd/update-prioirity-lists-on-server "token")))
        (is (= [{:issue-ids ["IJPL-3"]
                 :command "add tag: has-priority-list"}]
               @commands))))))
