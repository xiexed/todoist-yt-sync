(ns todoist-sync.texts-handler-test
  (:require [clojure.test :refer :all]
            [todoist-sync.texts-handler :refer :all]))

(deftest test-refs-parsed
  (testing "some refs parse"
    (is (= (list {:attributes {:href  "https://upsource.jetbrains.com/intellij/review/IDEA-CR-69547"
                          :style "color: rgb(15, 91, 153);"}
             :content    (list {:attributes nil
                                :content    (list "IDEA-CR-69547")
                                :tag        "strong"}
                               ": Review of dt/open-in-browser-intention branch")
             :tag        "a"})
           (parse-html-text "<a href=\"https://upsource.jetbrains.com/intellij/review/IDEA-CR-69547\" style=\"color: rgb(15, 91, 153);\"><strong>IDEA-CR-69547</strong>: Review of dt/open-in-browser-intention branch</a>")))
    (is (= (list "aaa")
           (parse-html-text "aaa")))
    ))

(deftest test-to-md
  (testing "pasing li"
    (is (= "\n  *  [IDEA-244537](https://youtrack.jetbrains.com/issue/IDEA-244537) Axios: template literal interpolations in urls are not supported\n\n  *  [IDEA-254105](https://youtrack.jetbrains.com/issue/IDEA-254105) Frameworks: http:// part of the path parsed differently in yaml with http path injection and in swagger specification\n\n  *  [IDEA-254646](https://youtrack.jetbrains.com/issue/IDEA-254646) Spring MVC: if the constant reference is used as mapping url value, completion/navigation/rename is not available for the path variable used in this url\n"
           (to-markdown (parse-html-text "<pre style=\"color: rgb(8, 8, 8); font-family: &quot;JetBrains Mono&quot;, monospace; font-size: 9.8pt;\"><ul style=\"white-space: normal; margin: 15px 0px 0px; padding: 0px; border: 0px; outline: 0px; font-size: 13px; vertical-align: baseline; list-style-position: initial; list-style-image: initial; caret-color: rgb(51, 51, 51); color: rgb(51, 51, 51); font-family: &quot;Helvetica Neue&quot;, Arial, sans-serif;\"><li style=\"margin: 4px 0px 4px 20px; padding: 0px; border: 0px; outline: 0px; vertical-align: baseline;\"><span class=\"name\" data-wfid=\"2d34d97c6d99\" style=\"margin: 0px; padding: 0px; border: 0px; outline: 0px; vertical-align: baseline; white-space: pre-wrap;\"><a class=\"contentLink\" target=\"_blank\" rel=\"noreferrer\" href=\"https://youtrack.jetbrains.com/issue/IDEA-244537\" style=\"margin: 0px; padding: 0px; border: 0px; outline: 0px; vertical-align: baseline; text-decoration: none; color: inherit; cursor: pointer;\">IDEA-244537</a>&nbsp;Axios: template literal interpolations in urls are not supported</span></li><li style=\"margin: 4px 0px 4px 20px; padding: 0px; border: 0px; outline: 0px; vertical-align: baseline;\"><span class=\"name\" data-wfid=\"3333ec44b1ca\" style=\"margin: 0px; padding: 0px; border: 0px; outline: 0px; vertical-align: baseline; white-space: pre-wrap;\"><a class=\"contentLink\" target=\"_blank\" rel=\"noreferrer\" href=\"https://youtrack.jetbrains.com/issue/IDEA-254105\" style=\"margin: 0px; padding: 0px; border: 0px; outline: 0px; vertical-align: baseline; text-decoration: none; color: inherit; cursor: pointer;\">IDEA-254105</a>&nbsp;Frameworks: http:// part of the path parsed differently in yaml with http path injection and in swagger specification</span></li><li style=\"margin: 4px 0px 4px 20px; padding: 0px; border: 0px; outline: 0px; vertical-align: baseline;\"><span class=\"name\" data-wfid=\"0d61397a90b6\" style=\"margin: 0px; padding: 0px; border: 0px; outline: 0px; vertical-align: baseline; white-space: pre-wrap;\"><a class=\"contentLink\" target=\"_blank\" rel=\"noreferrer\" href=\"https://youtrack.jetbrains.com/issue/IDEA-254646\" style=\"margin: 0px; padding: 0px; border: 0px; outline: 0px; vertical-align: baseline; text-decoration: none; color: inherit; cursor: pointer;\">IDEA-254646</a>&nbsp;Spring MVC: if the constant reference is used as mapping url value, completion/navigation/rename is not available for the path variable used in this url</span></li></ul></pre>"))))
    (is (= "[LAB-87](https://youtrack.jetbrains.com/issue/LAB-87) \n[Microservices Debugger ](https://youtrack.jetbrains.com/issue/LAB-87)[KT-41279](https://youtrack.jetbrains.com/issue/KT-41279) \n[calls to functions with reified parameters don't resolve ](https://youtrack.jetbrains.com/issue/KT-41279)\n"
           (to-markdown (parse-html-text "<a class=\"yt-issue-title yt-issues-issue__id\" yt-focus-if=\"false\" yt-issue-href=\"$ctrl.issue\" href=\"https://youtrack.jetbrains.com/issue/LAB-87\" style=\"outline-offset: -1px; font-size: 13px; display: inline-flex; margin-right: 6px; text-decoration: none; font-family: system-ui, -apple-system, &quot;Segoe UI&quot;, Roboto, &quot;Noto Sans&quot;, Ubuntu, Cantarell, &quot;Helvetica Neue&quot;, Arial, sans-serif;\"><yt-issue-id class=\"yt-dark-text yt-issue-id js-issue-id\" issue=\"$ctrl.issue\" data-test=\"issueId\" style=\"color: var(--ring-text-color); font-variant-ligatures: normal; font-variant-numeric: tabular-nums; font-variant-alternates: normal; font-variant-position: normal; font-variant-east-asian: normal;\">LAB-87</yt-issue-id></a><span style=\"caret-color: rgb(31, 35, 38); color: rgb(31, 35, 38); font-family: system-ui, -apple-system, &quot;Segoe UI&quot;, Roboto, &quot;Noto Sans&quot;, Ubuntu, Cantarell, &quot;Helvetica Neue&quot;, Arial, sans-serif; font-size: 13px; background-color: rgb(235, 246, 255);\">&nbsp;</span><a class=\"ring-link ring-link_pseudo yt-issue-title yt-issues-issue__summary\" yt-issue-href=\"::$ctrl.issue\" data-test=\"issueSummaryLink\" href=\"https://youtrack.jetbrains.com/issue/LAB-87\" style=\"cursor: pointer; transition: color var(--ring-fast-ease); text-decoration: none; color: var(--ring-link-color); outline-offset: -1px; font-family: system-ui, -apple-system, &quot;Segoe UI&quot;, Roboto, &quot;Noto Sans&quot;, Ubuntu, Cantarell, &quot;Helvetica Neue&quot;, Arial, sans-serif;\"><yt-issue-summary issue=\"$ctrl.issue\" class=\"yt-issue-summary\" data-test=\"issueSummary\">Microservices Debugger</yt-issue-summary>&nbsp;</a><div><a class=\"yt-issue-title yt-issues-issue__id\" yt-focus-if=\"false\" yt-issue-href=\"$ctrl.issue\" href=\"https://youtrack.jetbrains.com/issue/KT-41279\" style=\"outline-offset: -1px; font-size: 13px; display: inline-flex; margin-right: 6px; text-decoration: none; font-family: system-ui, -apple-system, &quot;Segoe UI&quot;, Roboto, &quot;Noto Sans&quot;, Ubuntu, Cantarell, &quot;Helvetica Neue&quot;, Arial, sans-serif;\"><yt-issue-id class=\"yt-dark-text yt-issue-id js-issue-id\" issue=\"$ctrl.issue\" data-test=\"issueId\" style=\"color: var(--ring-text-color); font-variant-ligatures: normal; font-variant-numeric: tabular-nums; font-variant-alternates: normal; font-variant-position: normal; font-variant-east-asian: normal;\">KT-41279</yt-issue-id></a><span style=\"caret-color: rgb(31, 35, 38); color: rgb(31, 35, 38); font-family: system-ui, -apple-system, &quot;Segoe UI&quot;, Roboto, &quot;Noto Sans&quot;, Ubuntu, Cantarell, &quot;Helvetica Neue&quot;, Arial, sans-serif; font-size: 13px; background-color: rgb(235, 246, 255);\">&nbsp;</span><a class=\"ring-link ring-link_pseudo yt-issue-title yt-issues-issue__summary\" yt-issue-href=\"::$ctrl.issue\" data-test=\"issueSummaryLink\" href=\"https://youtrack.jetbrains.com/issue/KT-41279\" style=\"cursor: pointer; transition: color var(--ring-fast-ease); text-decoration: none; color: var(--ring-link-color); outline-offset: -1px; font-family: system-ui, -apple-system, &quot;Segoe UI&quot;, Roboto, &quot;Noto Sans&quot;, Ubuntu, Cantarell, &quot;Helvetica Neue&quot;, Arial, sans-serif;\"><yt-issue-summary issue=\"$ctrl.issue\" class=\"yt-issue-summary\" data-test=\"issueSummary\">calls to functions with reified parameters don't resolve</yt-issue-summary>&nbsp;</a><br></div>"
                                         ))))
    )
  )
