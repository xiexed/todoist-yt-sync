(ns todoist-sync.workdash-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer :all]
            [todoist-sync.workdash :as wd]))

(deftest test-error-detector
  (testing "Test load1"
    (is (= (list {:assignee    "Nicolay Mitropolsky"
                  :duplicates  "IJPL-173823 IJPL-173823"
                  :not-planned "IJPL-173823 IJPL-173482"
                  :reassign    "IJPL-67420"}
                 {:assignee  "Andrei Ogurtsov"
                  :to-remove ["IJPL-66883"]}
                 {:assignee "Petr Galunov"
                  :reassign "IJPL-68912 IJPL-172289 IJPL-171960 IJPL-68781 IJPL-163578 IJPL-69178 IJPL-68973 IJPL-66930"}
                 {:assignee "Vladimir Petrenko"}
                 {:assignee    "Nurullokhon Gulomkodirov"
                  :duplicates  "IJPL-172787 IJPL-172787"
                  :not-planned "IJPL-166460"
                  :reassign    "IJPL-66042 IJPL-66012 IJPL-66038 IJPL-66011 IJPL-174559 IJPL-159965 IJPL-158872"
                  :to-remove   ["IJPL-166460"]}
                 {:assignee "Andrey Belyaev"
                  :reassign "IJPL-172142"}
                 {:assignee "Nikita Katkov"}
                 {:assignee   "Yuri Trukhin"
                  :duplicates "IJPL-174847 IJPL-174847"}
                 {:assignee "Not team members"
                  :reassign "IJPL-175028"}
                 {:assignee  "Polina Popova"
                  :reassign  "IJPL-154100 IJPL-171685 IJPL-148634 IJPL-66781 IJPL-158178"
                  :to-remove ["IJPL-154100"]})
           (wd/to-remove (edn/read-string (slurp "test/todoist_sync/workdash_data/load1.edn")))))))

(deftest test-parse-articles
  (testing "Testing No members"

    (is (= [{:header   nil
             :idParsed "IJPL-172703"}
            {:header   nil
             :idParsed "IJPL-165755"}
            {:header   nil
             :idParsed "IJPL-66868"}
            {:header   nil
             :idParsed "IJPL-66860"}
            {:header   nil
             :idParsed "IJPL-68955"}
            {:header   nil
             :idParsed "IJPL-63550"}
            {:header   "BDT"
             :idParsed "IJPL-174116"}
            {:header   "BDT"
             :idParsed "IJPL-151004"}
            {:header   "Other"
             :idParsed "RUBY-15482"}
            {:header   "Other"
             :idParsed "IJPL-64883"}]
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
    (is (= [{:header   "Active Backlog"
             :idParsed "IJPL-173714"}
            {:header   "Active Backlog"
             :idParsed "IJPL-172017"}
            {:header   "Active Backlog"
             :idParsed "IJPL-163312"}
            {:header   "Active Backlog"
             :idParsed "IJPL-165572"}
            {:header   "Active Backlog"
             :idParsed "IJPL-165236"}
            {:header   "Active Backlog"
             :idParsed "IJPL-165219"}
            {:header   "To be prioritised"
             :idParsed "IJPL-172213"}
            {:header   "To be prioritised"
             :idParsed "IJPL-172664"}
            {:header   "To be prioritised"
             :idParsed "IJPL-172787"}
            {:header   "To be prioritised"
             :idParsed "IJPL-66016"}
            {:header   "To be prioritised"
             :idParsed "IJPL-159100"}
            {:header   "To be prioritised"
             :idParsed "IJPL-160743"}
            {:header   "To be prioritised"
             :idParsed "IJPL-174559"}
            {:header   "To be prioritised"
             :idParsed "IJPL-171642"}
            {:header   "To be prioritised"
             :idParsed "IJPL-163577"}
            {:header   "To be prioritised"
             :idParsed "IJPL-172984"}
            {:header   "To be prioritised"
             :idParsed "IJPL-172923"}
            {:header   "To be prioritised"
             :idParsed "IJPL-161577"}
            {:header   "To be prioritised"
             :idParsed "IJPL-171910"}
            {:header   "To be prioritised"
             :idParsed "IJPL-171488"}
            {:header   "To be prioritised"
             :idParsed "IJPL-171253"}
            {:header   "To be prioritised"
             :idParsed "IJPL-162957"}
            {:header   "To be prioritised"
             :idParsed "IJPL-151224"}
            {:header   "To be prioritised"
             :idParsed "IJPL-158811"}
            {:header   "To be prioritised"
             :idParsed "IJPL-164416"}
            {:header   "To be prioritised"
             :idParsed "IJPL-161070"}
            {:header   "Planned for current release"
             :idParsed "IJPL-149580"
             :inner    [{:header   "Planned for current release"
                         :idParsed "IJPL-166460"}
                        {:header   "Planned for current release"
                         :idParsed "IJPL-66008"}]}
            {:header   "Planned for current release"
             :idParsed "IJPL-66097"}
            {:header   "Planned for current release"
             :idParsed "IJPL-66037"}
            {:header   "Planned for current release"
             :idParsed "IJPL-66020"}
            {:header   "Planned for current release"
             :idParsed "IJPL-159091"}
            {:header   "Planned for current release"
             :idParsed "IJPL-158416"}
            {:header   "Planned for current release"
             :idParsed "IJPL-172787"}]
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