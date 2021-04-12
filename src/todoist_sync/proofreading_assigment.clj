(ns todoist-sync.proofreading-assigment
  (:require [clj-http.client :as client]
            [clojure.string :as str]))


(defn get-raw [my-google-token]
  (client/get "https://sheets.googleapis.com/v4/spreadsheets/1k8mvJfz0Bl-FWf2ndSbrFjEzfKh_3f9_OrI9wwGqShQ"
              {:headers          {"Authorization" (str "Bearer " my-google-token)
                                  "Cache-Control" "no-cache"
                                  "Content-Type"  "application/json"}
               :accept           :json
               :query-params     {:includeGridData true}
               :throw-exceptions false
               :as               :json}))

(defn parse-resp-flat [raw]
  (take 100 (for [sheet (-> raw :body :sheets)
                  data (:data sheet)
                  raw_data_values (map :values (:rowData data))
                  value raw_data_values]
              (:formattedValue value))))

(defn parse-resp [raw]
  (->> (-> raw :body :sheets)
       (map
         (fn [sheet]
           (->> (:data sheet)
                (mapcat (fn [sheet-data] (map :values (:rowData sheet-data))))
                (map (fn [row] (map :formattedValue row))))))))

(defn sheet-table-to-maps [sheet]
  (let [headers (nth sheet 2)]
    (loop [gr nil [head & tail] (drop 2 sheet) accum []]
      (if head
        (let [meaningful-values (filter some? head)
              meaning-count (count meaningful-values)]
          (cond
            (= meaning-count 1) (recur (first meaningful-values) tail accum)
            (> meaning-count 1) (recur gr tail
                                       (conj accum (into {:section gr} 
                                                         (map (fn [header value] [header value]) headers head))))
            :else (recur gr tail accum)))
        accum))))

(defn wrap [prefix suffix value] (when value (str prefix value suffix)))

(defn get-or [m keys]
  (first (keep (fn [key] (get m key)) keys)))

(defn render-line [line]
  (let [assignee (get line "Responsible person")
        assignee-render (wrap " [**" "**]" assignee)
        title (get-or line ["UI Group Name | Element Name" "Path"])]
    (str/replace (str " - [ ] "
                      (str/trim (str/replace title "\n" " "))
                      assignee-render) #"\s+" " ")))

(defn mapd2 [t f seqseq] (map (fn [s] (t f s)) seqseq))

(def our-team #{"Aleksandr Izmaylov"
                "Konstantin Aleev"
                "Michael Golubev"
                "Nicolay Mitropolsky"
                "Nikita Katkov"
                "Sergey Anchipolevsky"
                "Sergey Vasiliev"
                "Yuriy Artamonov"})

(defn our [line]
  (or (= "IDEA - Ultimate" (:section line))
      (some (fn [person] (let [responsible (get line "Responsible person")]
                           (and responsible (str/includes? responsible person)))) our-team)))

(defn render-md-checklist [raw]
  (let [matrix (parse-resp raw)
        sheet-headers (->> raw :body :sheets (map :properties) (map :title))
        all-parsed (map sheet-table-to-maps matrix)
        ultimates (mapd2 filter our all-parsed)
        sheet-renders (map (fn [sheet] (map render-line sheet)) ultimates)]
    (->> (map (fn [shead data] (str "### " shead "\n" (str/join "\n" data))) sheet-headers sheet-renders) (str/join "\n\n"))))
