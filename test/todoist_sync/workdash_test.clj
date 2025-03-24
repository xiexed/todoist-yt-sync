(ns todoist-sync.workdash-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer :all]
            [todoist-sync.utils.utils :as u]
            [todoist-sync.workdash :as wd]))

(deftest test-error-detector
  (testing "Test load1"
    (is (= (list {:assignee  "Nicolay Mitropolsky"
                  :submitted "IJPL-162687"
                  :to-remove "IJPL-178330"}
                 {:assignee "Andrei Ogurtsov"}
                 {:assignee "Petr Galunov"}
                 {:assignee "Vladimir Petrenko"}
                 {:assignee            "Nurullokhon Gulomkodirov"
                  :to-remove           "IJPL-175582 IJPL-152460 IJPL-153445 IJPL-151272"
                  :to-remove-not-fixed "IJPL-175582 IJPL-152460 IJPL-153445 IJPL-151272"}
                 {:assignee "Andrey Belyaev"}
                 {:assignee  "Nikita Katkov"
                  :submitted "IJPL-166355 IJPL-174666 IJPL-63541"
                  :to-remove "IJPL-171311"}
                 {:assignee  "Yuri Trukhin"
                  :submitted "IJPL-158334 IJPL-174847"}
                 {:assignee "Not team members"}
                 {:assignee "Polina Popova"
                  :reassign "IJPL-171685 IJPL-148634"}
                 {:assignee "Alexander Khokhlyavin"}
                 {:assignee "Victoria Miroshnichenko"})
           (wd/to-remove (edn/read-string (slurp "test/todoist_sync/workdash_data/load1.edn")))))))

(deftest test-parse-articles
  (testing "Testing No members"

    (is (= (list {:idParsed "IJPL-172703"
                  :text     "IJPL-172703 Provide the Kubernetes file schema to Fus for the File Types in Project counter"}
                 {:idParsed "IJPL-165755"
                  :text     "IJPL-165755 Kubernetes. Logs recent search selection pastes values incorrectly in split mode"}
                 {:idParsed "IJPL-66868"
                  :text     "IJPL-66868 The ability to perform common actions for the technology at the level of the Technology Node in Service View"}
                 {:idParsed "IJPL-66860"
                  :text     "IJPL-66860 Kubernetes: can't close log tab in services view"}
                 {:idParsed "IJPL-68955"
                  :text     "IJPL-68955 Too much space for names in Kubernetes \"Information\""}
                 {:idParsed "IJPL-63550"
                  :text     "IJPL-63550 WebSymbols: provide JSON schema for .ws-context file"}
                 {:header   "BDT"
                  :idParsed "IJPL-174116"
                  :text     "IJPL-174116 Intellij keeps crashing on MAC Apple M3 Pro"}
                 {:header   "BDT"
                  :idParsed "IJPL-151004"
                  :text     "IJPL-151004 Plugins not updated together with Pycharm update"}
                 {:header   "Other"
                  :idParsed "RUBY-15482"
                  :text     "RUBY-15482 Better clouds integration"}
                 {:header   "Other"
                  :idParsed "IJPL-64883"
                  :text     "IJPL-64883 Visualise newlines in long json strings"})
           (wd/parse-md-to-sections "
           IJPL-172703 Provide the Kubernetes file schema to Fus for the File Types in Project counter
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
           )))

  (testing "Testing Nested"
    (is (= (list {:header   "Active Backlog"
                  :idParsed "IJPL-173714"
                  :text     "IJPL-173714 Revise and fix BDT unit tests in master"}
                 {:header   "Active Backlog"
                  :idParsed "IJPL-172017"
                  :text     "IJPL-172017 Terraform run configurations are displayed as broken"}
                 {:header   "Active Backlog"
                  :idParsed "IJPL-163312"
                  :text     "IJPL-163312 Terraform. Settings. Install: the old 1.8 version is downloaded instead of the new one"}
                 {:header   "Active Backlog"
                  :idParsed "IJPL-165572"
                  :text     "IJPL-165572 Provide meaningful names to all PSI elements in Terraform file"}
                 {:header   "Active Backlog"
                  :idParsed "IJPL-165236"
                  :text     "IJPL-165236 Terraform. New run configuration is not shown in the gutter until the file is reopened"}
                 {:header   "Active Backlog"
                  :idParsed "IJPL-165219"
                  :text     "IJPL-165219 Terraform. Commands in the gutter are duplicated after the first execution"}
                 {:header   "To be prioritised"
                  :idParsed "IJPL-172213"
                  :text     "IJPL-172213 Unable to Access Parquet File"}
                 {:header   "To be prioritised"
                  :idParsed "IJPL-172664"
                  :text     "IJPL-172664 Terraform. Global options must be placed before the command"}
                 {:header   "To be prioritised"
                  :idParsed "IJPL-172787"
                  :text     "IJPL-172787 Big Data Tools (Kafka, RFS etc) – macOS 15 workaround"}
                 {:header   "To be prioritised"
                  :idParsed "IJPL-66016"
                  :text     "IJPL-66016 Terraform. HCL. Red code, no completion for a set of objects domain_validation_options"}
                 {:header   "To be prioritised"
                  :idParsed "IJPL-159100"
                  :text     "IJPL-159100 Terraform HCL code completion not valid for variable reference"}
                 {:header   "To be prioritised"
                  :idParsed "IJPL-160743"
                  :text     "IJPL-160743 Terraform plugin - format action fails when (regression from 2024.1.x)"}
                 {:header   "To be prioritised"
                  :idParsed "IJPL-174559"
                  :text     "IJPL-174559 Using Terraform to reformat deletes all code."}
                 {:header   "To be prioritised"
                  :idParsed "IJPL-171642"
                  :text     "IJPL-171642 Add the terraform show action to the gutter icon"}
                 {:header   "To be prioritised"
                  :idParsed "IJPL-163577"
                  :text     "IJPL-163577 Can't use OAUTHBEARER authentication with custom sasl.login.callback.handler.class"}
                 {:header   "To be prioritised"
                  :idParsed "IJPL-172984"
                  :text     "IJPL-172984 Get rid of the storeData method in Kafka Producer/Consumer editors"}
                 {:header   "To be prioritised"
                  :idParsed "IJPL-172923"
                  :text     "IJPL-172923 Make more visible 'Producer/Consumer' actions in Kafka"}
                 {:header   "To be prioritised"
                  :idParsed "IJPL-161577"
                  :text     "IJPL-161577 Users that have a bucket shared with them cannot list it on IDE"}
                 {:header   "To be prioritised"
                  :idParsed "IJPL-171910"
                  :text     "IJPL-171910 Parquet viewer plugin opens file twice, once with an error"}
                 {:header   "To be prioritised"
                  :idParsed "IJPL-171488"
                  :text     "IJPL-171488 Repository Creation Template not supported"}
                 {:header   "To be prioritised"
                  :idParsed "IJPL-171253"
                  :text     "IJPL-171253 Azure CLI auth do not work in PyCharm Big Data Tools plugin on MacOS"}
                 {:header   "To be prioritised"
                  :idParsed "IJPL-162957"
                  :text     "IJPL-162957 Unable to resolve protobuf imports from Kafka Schema Registry when consuming topics"}
                 {:header   "To be prioritised"
                  :idParsed "IJPL-151224"
                  :text     "IJPL-151224 Trying to make a connection to a Google Cloud Storage (GCS) bucket using the Big Data Tool plugin but its failing to authenticate, using the GCloud account option. It works for BigQuery connection but not GCS."}
                 {:header   "To be prioritised"
                  :idParsed "IJPL-158811"
                  :text     "IJPL-158811 Big Data Tools: Content-Type not preserved when updating S3 object"}
                 {:header   "To be prioritised"
                  :idParsed "IJPL-164416"
                  :text     "IJPL-164416 Move, Delete, Rename options are not available for the S3-compatible storage"}
                 {:header   "To be prioritised"
                  :idParsed "IJPL-161070"
                  :text     "IJPL-161070 Big Data Tools Hive Metastore refresh error when network changed"}
                 {:header   "Planned for current release"
                  :idParsed "IJPL-149580"
                  :inner    (list {:idParsed "IJPL-166460"
                                   :text     "IJPL-166460 Terraform and HCL plugin does not know provider function syntax"}
                                  {:idParsed "IJPL-66008"
                                   :text     "IJPL-66008 Unresolved reference on member attribute"})
                  :text     "IJPL-149580 Terraform : syntax highlight for provider-defined functions"}
                 {:header   "Planned for current release"
                  :idParsed "IJPL-66097"
                  :text     "IJPL-66097 Terraform: .tfvars and .tfvars bugs"}
                 {:header   "Planned for current release"
                  :idParsed "IJPL-66037"
                  :text     "IJPL-66037 Terraform: support for variables.tf.json"}
                 {:header   "Planned for current release"
                  :idParsed "IJPL-66020"
                  :text     "IJPL-66020 Terraform: automatically determine if terraform init required and suggest it"}
                 {:header   "Planned for current release"
                  :idParsed "IJPL-159091"
                  :text     "IJPL-159091 Terraform. Refactoring. Unify Terraform classes prefix"}
                 {:header   "Planned for current release"
                  :idParsed "IJPL-158416"
                  :text     "IJPL-158416 Meta issue for Terraform formatting problems"}
                 {:header   "Planned for current release"
                  :idParsed "IJPL-172787"
                  :text     "IJPL-172787 Big Data Tools (Kafka, RFS etc) – macOS 15 workaround"})
           (wd/parse-md-to-sections "# Active Backlog

           IJPL-173714 Revise and fix BDT unit tests in master
           IJPL-172017 Terraform run configurations are displayed as broken
           IJPL-163312 Terraform. Settings. Install: the old 1.8 version is downloaded instead of the new one
           IJPL-165572 Provide meaningful names to all PSI elements in Terraform file
           IJPL-165236 Terraform. New run configuration is not shown in the gutter until the file is reopened
           IJPL-165219 Terraform. Commands in the gutter are duplicated after the first execution

           # To be prioritised

           IJPL-172213 Unable to Access Parquet File
           IJPL-172664 Terraform. Global options must be placed before the command
           IJPL-172787 Big Data Tools (Kafka, RFS etc) – macOS 15 workaround
           IJPL-66016 Terraform. HCL. Red code, no completion for a set of objects domain_validation_options
           IJPL-159100 Terraform HCL code completion not valid for variable reference
           IJPL-160743 Terraform plugin - format action fails when (regression from 2024.1.x)
           IJPL-174559 Using Terraform to reformat deletes all code.
           IJPL-171642 Add the terraform show action to the gutter icon
           IJPL-163577 Can't use OAUTHBEARER authentication with custom sasl.login.callback.handler.class
           IJPL-172984 Get rid of the storeData method in Kafka Producer/Consumer editors
           IJPL-172923 Make more visible 'Producer/Consumer' actions in Kafka
           IJPL-161577 Users that have a bucket shared with them cannot list it on IDE
           IJPL-171910 Parquet viewer plugin opens file twice, once with an error
           IJPL-171488 Repository Creation Template not supported
           IJPL-171253 Azure CLI auth do not work in PyCharm Big Data Tools plugin on MacOS
           IJPL-162957 Unable to resolve protobuf imports from Kafka Schema Registry when consuming topics
           IJPL-151224 Trying to make a connection to a Google Cloud Storage (GCS) bucket using the Big Data Tool plugin but its failing to authenticate, using the GCloud account option. It works for BigQuery connection but not GCS.
           IJPL-158811 Big Data Tools: Content-Type not preserved when updating S3 object
           IJPL-164416 Move, Delete, Rename options are not available for the S3-compatible storage
           IJPL-161070 Big Data Tools Hive Metastore refresh error when network changed

           # Planned for current release

           IJPL-149580 Terraform : syntax highlight for provider-defined functions

           * IJPL-166460 Terraform and HCL plugin does not know provider function syntax
             IJPL-66008 Unresolved reference on member attribute

           IJPL-66097 Terraform: .tfvars and .tfvars bugs
           IJPL-66037 Terraform: support for variables.tf.json

           IJPL-66020 Terraform: automatically determine if terraform init required and suggest it
           IJPL-159091 Terraform. Refactoring. Unify Terraform classes prefix
           IJPL-158416 Meta issue for Terraform formatting problems

           IJPL-172787 Big Data Tools (Kafka, RFS etc) – macOS 15 workaround")
           ))))

(defn use-dumped-load-issue-data [filename]
  (let [store (u/load-edn filename)]
    (fn [_ issue]
      (store issue))))

(deftest test-replace
  (testing "Test replace in wd"
    (with-redefs [wd/load-issue-data (use-dumped-load-issue-data "test/todoist_sync/workdash_data/ws1-issues.edn")]
      (is (= (slurp "test/todoist_sync/workdash_data/ws1-upd.md")
             (wd/patch-outdated "no-token" (slurp "test/todoist_sync/workdash_data/ws1.md")))))))