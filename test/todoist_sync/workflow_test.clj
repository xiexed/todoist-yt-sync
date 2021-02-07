(ns todoist-sync.workflow-test
  (:require [clojure.test :refer :all]
            [todoist-sync.workflow :as wf]
            [todoist-sync.texts-handler :as thd]))

(deftest test-review-preference
  (testing "Testing parsing urls and review filtering"
    (is (= (list {:input-text "IDEA-CR-69986: IDEA-212875 Restrict cases for insertion with indents adjusted and improve it"
                  :issue      "IDEA-CR-69986"
                  :others     (list {:issue "IDEA-212875"
                                     :url   "https://youtrack.jetbrains.com/issue/IDEA-212875"})
                  :td-type    :review
                  :url        "https://upsource.jetbrains.com/intellij/review/IDEA-CR-69986"})
           (wf/classify-with-review-preference (thd/extract-issues-from-html "<a href=\"https://upsource.jetbrains.com/intellij/review/IDEA-CR-69986\"><font color=\"#0F5B99\"><b><span style=\"font-size: 16px;\">IDEA-CR-69986</span></b><span style=\"font-size: 16px;\">: IDEA-212875 Restrict cases for insertion with indents adjusted and improve it</span></font></a><span style=\"font-size: 16px;\"><font color=\"#0F5B99\"></font><font color=\"#444444\"></font></span>"
                                                                             ))
           ))
    (is (= (list {:input-text "IDEA-CR-69986: IDEA-212875 Restrict cases for insertion with indents adjusted and improve it"
                  :issue      "IDEA-CR-69986"
                  :others     (list {:issue "IDEA-212875"
                                     :url   "https://youtrack.jetbrains.com/issue/IDEA-212875"})
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
           ))
    (is (= (list {:input-text "IDEA-CR-70134: Review of aizmaylov/IDEA-137125-ssl-http-client branch"
                  :issue      "IDEA-CR-70134"
                  :others     (list {:issue "IDEA-137125"
                                     :url   "https://youtrack.jetbrains.com/issue/IDEA-137125"})
                  :td-type    :review
                  :url        "https://upsource.jetbrains.com/intellij/review/IDEA-CR-70134"}
                 {:input-text "IDEA-CR-69986: IDEA-212875 Restrict cases for insertion with indents adjusted and improve it"
                  :issue      "IDEA-CR-69986"
                  :others     (list {:issue "IDEA-212875"
                                     :url   "https://youtrack.jetbrains.com/issue/IDEA-212875"})
                  :td-type    :review
                  :url        "https://upsource.jetbrains.com/intellij/review/IDEA-CR-69986"})
           (wf/classify-with-review-preference (thd/extract-issues-from-html "<font color=\"#0F5B99\"><a href=\"https://upsource.jetbrains.com/intellij/review/IDEA-CR-70134\"><b><span style=\"font-size: 16px;\">IDEA-CR-70134</span></b><span style=\"font-size: 16px;\">: Review of aizmaylov/IDEA-137125-ssl-http-client branch</span></a></font><span style=\"font-size: 16px;\"><font color=\"#0F5B99\"></font><font color=\"#444444\"></font></span><div><a href=\"https://upsource.jetbrains.com/intellij/review/IDEA-CR-69986\"><font color=\"#0F5B99\"><b><span style=\"font-size: 16px;\">IDEA-CR-69986</span></b><span style=\"font-size: 16px;\">: IDEA-212875 Restrict cases for insertion with indents adjusted and improve it</span></font></a><br></div>"
                                                                             ))
           ))))


(deftest test-markdown-and-typw
  (testing "Testing parsing urls and review filtering"

    (is (= (list {:md-string "[IDEA-CR-70134](https://upsource.jetbrains.com/intellij/review/IDEA-CR-70134): Review of aizmaylov/[IDEA-137125](https://youtrack.jetbrains.com/issue/IDEA-137125)-ssl-http-client branch"
                  :td-type   :review}
                 {:md-string "[IDEA-CR-69986](https://upsource.jetbrains.com/intellij/review/IDEA-CR-69986): [IDEA-212875](https://youtrack.jetbrains.com/issue/IDEA-212875) Restrict cases for insertion with indents adjusted and improve it"
                  :td-type   :review})
           (map wf/issue-to-markdown-and-type (wf/classify-with-review-preference (thd/extract-issues-from-html "<font color=\"#0F5B99\"><a href=\"https://upsource.jetbrains.com/intellij/review/IDEA-CR-70134\"><b><span style=\"font-size: 16px;\">IDEA-CR-70134</span></b><span style=\"font-size: 16px;\">: Review of aizmaylov/IDEA-137125-ssl-http-client branch</span></a></font><span style=\"font-size: 16px;\"><font color=\"#0F5B99\"></font><font color=\"#444444\"></font></span><div><a href=\"https://upsource.jetbrains.com/intellij/review/IDEA-CR-69986\"><font color=\"#0F5B99\"><b><span style=\"font-size: 16px;\">IDEA-CR-69986</span></b><span style=\"font-size: 16px;\">: IDEA-212875 Restrict cases for insertion with indents adjusted and improve it</span></font></a><br></div>"
                                                                                                                )))
           ))))

(deftest test-parse-exceptions
  (testing "Testing exceptinos parsed"
    (is (= (list {:md-string "[EA-235440](https://ea.jetbrains.com/browser/ea_problems/235440) - IAE: TextRange.<init> [in-my-plan]"
                  :td-type   :ea})
           (map wf/issue-to-markdown-and-type
                (wf/classify-with-review-preference
                  (thd/extract-issues-from-html "<a class=\"contentLink\" target=\"_blank\" rel=\"noreferrer\" href=\"http://ea.jetbrains.com/browser/ea_problems/235440\" style=\"margin: 0px; padding: 0px; border: 0px; outline: 0px; font-size: 14.000000953674316px; vertical-align: baseline; color: rgb(75, 173, 223); cursor: pointer; font-family: -apple-system, BlinkMacSystemFont, &quot;Segoe UI&quot;, Roboto, Helvetica, Arial, sans-serif, &quot;Apple Color Emoji&quot;, &quot;Segoe UI Emoji&quot;, &quot;Segoe UI Symbol&quot;; white-space: pre-wrap;\">EA-235440</a><span style=\"caret-color: rgb(42, 49, 53); color: rgb(42, 49, 53); font-family: -apple-system, BlinkMacSystemFont, &quot;Segoe UI&quot;, Roboto, Helvetica, Arial, sans-serif, &quot;Apple Color Emoji&quot;, &quot;Segoe UI Emoji&quot;, &quot;Segoe UI Symbol&quot;; font-size: 14.000000953674316px; white-space: pre-wrap;\"> - IAE: TextRange.&lt;init&gt; [in-my-plan]</span>"
                                                )))))
    (is (= (list {:md-string "[EA-235440](https://ea.jetbrains.com/browser/ea_problems/235440) - IAE: TextRange.<init> [in-my-plan]"
                  :td-type   :ea})
           (map wf/issue-to-markdown-and-type
                (wf/classify-with-review-preference
                  (thd/extract-issues-from-html "<span style=\"\">EA-235440 - IAE: TextRange.&lt;init&gt; [in-my-plan]</span>"
                                                )))))))

(deftest test-parse-task-full
  (testing "Testing exceptinos parsed"

    (is (= (list {:md-string "[IDEA-56184](https://youtrack.jetbrains.com/issue/IDEA-56184) HQL syntax highlight inside StringBuffer and StringBuilder [IDEA-55492](https://youtrack.jetbrains.com/issue/IDEA-55492) Broaden types allowed for @Language(\"SQL\") [IDEA-15818](https://youtrack.jetbrains.com/issue/IDEA-15818) Query not validated if obtained through StringBuffer/StringBuilder"
                  :td-type   :ticket})
           (map wf/issue-to-markdown-and-type
                (wf/classify-with-review-preference
                  (thd/extract-issues-from-html "<ul style=\"margin: 15px 0px 0px; padding: 0px; border: 0px; outline: 0px; font-size: 13px; vertical-align: baseline; list-style-position: initial; list-style-image: initial; caret-color: rgb(51, 51, 51); color: rgb(51, 51, 51); font-family: &quot;Helvetica Neue&quot;, Arial, sans-serif;\"><li style=\"margin: 4px 0px 4px 20px; padding: 0px; border: 0px; outline: 0px; vertical-align: baseline;\"><span class=\"name\" data-wfid=\"62a4045382c1\" style=\"margin: 0px; padding: 0px; border: 0px; outline: 0px; vertical-align: baseline; white-space: pre-wrap;\"><a class=\"contentLink\" target=\"_blank\" rel=\"noreferrer\" href=\"https://youtrack.jetbrains.com/issue/IDEA-56184\" style=\"margin: 0px; padding: 0px; border: 0px; outline: 0px; vertical-align: baseline; text-decoration: none; color: inherit; cursor: pointer;\">IDEA-56184</a> HQL syntax highlight inside StringBuffer and StringBuilder</span><ul style=\"margin: 0px; padding: 0px; border: 0px; outline: 0px; vertical-align: baseline; list-style: disc;\"><li style=\"margin: 4px 0px 4px 20px; padding: 0px; border: 0px; outline: 0px; vertical-align: baseline;\"><span class=\"name\" style=\"margin: 0px; padding: 0px; border: 0px; outline: 0px; vertical-align: baseline; white-space: pre-wrap;\"><a class=\"contentLink\" target=\"_blank\" rel=\"noreferrer\" href=\"https://youtrack.jetbrains.com/issue/IDEA-55492\" style=\"margin: 0px; padding: 0px; border: 0px; outline: 0px; vertical-align: baseline; text-decoration: none; color: inherit; cursor: pointer;\">IDEA-55492</a> Broaden types allowed for <span class=\"contentTag\" title=\"Filter @Language\" style=\"margin: 0px; padding: 0px; border: 0px; outline: 0px; vertical-align: baseline;\">@<span class=\"contentTagText\" style=\"margin: 0px; padding: 0px; border: 0px; outline: 0px; vertical-align: baseline;\">Language</span><span class=\"contentTagNub\" style=\"margin: 0px; padding: 0px; border: 0px; outline: 0px; vertical-align: baseline;\"></span></span>(\"SQL\")</span></li><li style=\"margin: 4px 0px 4px 20px; padding: 0px; border: 0px; outline: 0px; vertical-align: baseline;\"><span class=\"name\" style=\"margin: 0px; padding: 0px; border: 0px; outline: 0px; vertical-align: baseline; white-space: pre-wrap;\"><a class=\"contentLink\" target=\"_blank\" rel=\"noreferrer\" href=\"https://youtrack.jetbrains.com/issue/IDEA-15818\" style=\"margin: 0px; padding: 0px; border: 0px; outline: 0px; vertical-align: baseline; text-decoration: none; color: inherit; cursor: pointer;\">IDEA-15818</a> Query not validated if obtained through StringBuffer/StringBuilder</span></li></ul></li></ul>"
                                            {:single-task true}
                                            )))))))
