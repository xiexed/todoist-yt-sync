(ns todoist-sync.workflow-test
  (:require [clojure.test :refer :all]
            [todoist-sync.workflow :as wf]
            [todoist-sync.texts-handler :as thd]))

(deftest test-review-preference
  (testing "Testing parsing urls and review filtering"
    (is (= (list {:input-text "IDEA-CR-69986: IDEA-212875 Restrict cases for insertion with indents adjusted and improve it"
                  :issue      "IDEA-CR-69986"
                  :td-type    :review
                  :url        "https://upsource.jetbrains.com/intellij/review/IDEA-CR-69986"})
           (wf/classify-with-review-preference (thd/extract-issues-from-html "<a href=\"https://upsource.jetbrains.com/intellij/review/IDEA-CR-69986\"><font color=\"#0F5B99\"><b><span style=\"font-size: 16px;\">IDEA-CR-69986</span></b><span style=\"font-size: 16px;\">: IDEA-212875 Restrict cases for insertion with indents adjusted and improve it</span></font></a><span style=\"font-size: 16px;\"><font color=\"#0F5B99\"></font><font color=\"#444444\"></font></span>"
                                                                             ))
           ))
    (is (= (list {:input-text "IDEA-CR-69986: IDEA-212875 Restrict cases for insertion with indents adjusted and improve it"
                  :issue      "IDEA-CR-69986"
                  :td-type    :review
                  :url        "https://upsource.jetbrains.com/intellij/review/IDEA-CR-69986"}
                 {:input-text "IJ-CR-4939: JavaEE: remove framework JARs from repository "
                  :issue      "IJ-CR-4939"
                  :td-type    :review
                  :url        "https://jetbrains.team/p/IJ/review/4939/timeline"}
                 {:input-text "KT-44111 Kotlin scripting on Heroku with JVM host"
                  :issue      "KT-44111"
                  :td-type    :ticket
                  :url        "https://youtrack.jetbrains.com/issue/KT-44111"})
           (wf/classify-with-review-preference (thd/extract-issues-from-html "<font color=\"#0F5B99\"><a href=\"https://upsource.jetbrains.com/intellij/review/IDEA-CR-69986\"><b><span style=\"font-size: 16px;\">IDEA-CR-69986</span></b><span style=\"font-size: 16px;\">: IDEA-212875 Restrict cases for insertion with indents adjusted and improve it</span></a></font><span style=\"font-size: 16px;\"><font color=\"#0F5B99\"></font><font color=\"#444444\"></font></span><div><a href=\"https://jetbrains.team/p/ij/review/4939/timeline\" data-toggle=\"tooltip\" title=\"https://jetbrains.team/p/ij/review/4939/timeline\" style=\"font-size: 16px; font-family: -apple-system, BlinkMacSystemFont, &quot;Helvetica Neue&quot;, Arial, sans-serif; color: rgb(85, 121, 167); display: inline;\">IJ-CR-4939: JavaEE: remove framework JARs from repository</a><span style=\"font-size: 16px; caret-color: rgb(12, 12, 13); color: rgb(12, 12, 13); font-family: -apple-system, BlinkMacSystemFont, &quot;Helvetica Neue&quot;, Arial, sans-serif;\">&nbsp;</span><br></div><div><a title=\"https://youtrack.jetbrains.com/issue/KT-44111\" href=\"https://youtrack.jetbrains.com/issue/KT-44111\" data-toggle=\"tooltip\" style=\"font-family: sans-serif; margin-right: 6px; font-size: 15px; color: rgb(20, 102, 198); text-decoration: none; display: inline;\">KT-44111</a><span style=\"font-family: sans-serif; font-size: medium; background-color: rgb(240, 240, 240);\">&nbsp;</span><a title=\"https://youtrack.jetbrains.com/issue/KT-44111\" href=\"https://youtrack.jetbrains.com/issue/KT-44111\" data-toggle=\"tooltip\" style=\"font-family: sans-serif; font-size: 15px; color: rgb(20, 102, 198); text-decoration: none; display: inline;\">Kotlin scripting on Heroku with JVM host</a><span style=\"font-size: 16px; caret-color: rgb(12, 12, 13); color: rgb(12, 12, 13); font-family: -apple-system, BlinkMacSystemFont, &quot;Helvetica Neue&quot;, Arial, sans-serif;\"><br></span></div>"
                                                                             ))
           ))))
