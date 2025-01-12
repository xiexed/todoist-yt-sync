(ns todoist-sync.workdash-test
  (:require [clojure.test :refer :all]
            [todoist-sync.workdash :as wd]))

(deftest test-parse-articles
  (testing "Testing No members"

    (is (= [{:header nil, :issues ["IJPL-172703" "IJPL-165755" "IJPL-66868" "IJPL-66860" "IJPL-68955" "IJPL-63550"]}
            {:header "BDT", :issues ["IJPL-174116" "IJPL-151004"]}
            {:header "Other", :issues ["RUBY-15482" "IJPL-64883"]}]
           (wd/parse-md-to-sections "IJPL-172703 Provide the Kubernetes file schema to Fus for the File Types in Project counter
           IJPL-165755 Kubernetes. Logs recent search selection pastes values incorrectly in split mode
           IJPL-66868 The ability to perform common actions for the technology at the level of the Technology Node in Service View
           IJPL-66860 Kubernetes: can't close log tab in services view
           IJPL-68955 Too much space for names in Kubernetes \"Information\"
           IJPL-63550 WebSymbols: provide JSON schema for .ws-context file

           # BDT

           IJPL-174116 Intellij keeps crashing on MAC Apple M3 Pro
           IJPL-151004 Plugins not updated together with Pycharm update

           # Other

           RUBY-15482 Better clouds integration
           IJPL-64883 Visualise newlines in long json strings

           ")
           ))))