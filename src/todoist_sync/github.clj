(ns todoist-sync.github
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.java.io :as io])
  (:import (java.io FileInputStream)
           (java.nio.file FileVisitResult Files Paths SimpleFileVisitor)
           (java.util.zip ZipInputStream)))

(defn unzip [zip-file-path ^String output-folder]
  (with-open [zos (ZipInputStream. (io/input-stream zip-file-path))]
    (loop [entry (.getNextEntry zos)]
      (when entry
        (let [file (io/file (str output-folder "/" (.getName entry)))]
          (if (.isDirectory entry)
            (.mkdirs file)
            (with-open [output (io/output-stream file)]
              (io/copy zos output))))
        (recur (.getNextEntry zos))))))

(defn delete-files-recursively [^String dir]
  (let [path-to-delete (Paths/get dir (into-array String []))]
    (Files/walkFileTree
      path-to-delete
      (proxy [java.nio.file.SimpleFileVisitor] []
        (visitFile [file attrs]
          (Files/delete file)
          (FileVisitResult/CONTINUE))
        (postVisitDirectory [dir exc]
          (when exc (throw exc))
          (Files/delete dir)
          (FileVisitResult/CONTINUE))))))

(defn items [rr]
  (->> rr (map #(update % :body json/read-json)) (mapcat #(get-in % [:body :items]))))
(defn items2 [items]
  (->> items (map (fn [item] (let [u (item :html_url)
                                   sp (str/split u #"/")
                                   hash (nth sp 6)]
                               (merge item {:zip-url (str (str/join "/" (take 5 sp)) "/archive/" hash ".zip") :zip (str "repos/" (nth sp 3) "-" (nth sp 4) "-" hash ".zip")}))))))
(defn get-in-zip [items3]
  (doseq [r (->> items3 (map-indexed (fn [i v] (assoc v :i i))) (drop 0))]
    (println r)
    (let [item r
          _ (unzip (:zip item) "unziped")
          unziped-dir (.getParentFile (apply io/file (io/file "unziped") (first (.list (io/file "unziped"))) (str/split (:path item) #"/")))
          target-dir (io/file "folders" (item :name))
          ]
      (.mkdirs target-dir)
      (.renameTo unziped-dir target-dir)
      (delete-files-recursively "unziped")
      )
    )
  )