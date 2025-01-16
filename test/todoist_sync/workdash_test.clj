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
           )))

  (testing "Testing Nested"
    (is (= [{:header "Active Backlog"
             :issues ["IJPL-173714"
                      "IJPL-172017"
                      "IJPL-163312"
                      "IJPL-165572"
                      "IJPL-165236"
                      "IJPL-165219"]}
            {:header "To be prioritised"
             :issues ["IJPL-172213"
                      "IJPL-172664"
                      "IJPL-172787"
                      "IJPL-66016"
                      "IJPL-159100"
                      "IJPL-160743"
                      "IJPL-174559"
                      "IJPL-171642"
                      "IJPL-163577"
                      "IJPL-172984"
                      "IJPL-172923"
                      "IJPL-161577"
                      "IJPL-171910"
                      "IJPL-171488"
                      "IJPL-171253"
                      "IJPL-162957"
                      "IJPL-151224"
                      "IJPL-158811"
                      "IJPL-164416"
                      "IJPL-161070"]}
            {:header "Planned for current release"
             :issues ["IJPL-149580"
                      "IJPL-166460"
                      "IJPL-66008"
                      "IJPL-66097"
                      "IJPL-66037"
                      "IJPL-66020"
                      "IJPL-159091"
                      "IJPL-158416"
                      "IJPL-172787"]}]
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