(ns todoist-sync.proofreading-assigment
  (:require [clj-http.client :as client]))

;(map (fn [data-vector] (map (fn [rawData-vector] (map type (map :values rawData-vector))) (map :rowData data-vector))) (map :data (-> raw :body :sheets)))

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
                (map (fn [row] (map :formattedValue row))))
           ))
       ))

(defn- sheet-table-to-maps [sheet]
  (let [headers (nth sheet 2)]
    (loop [gr nil [head & tail] (drop 2 sheet) accum {}]
      (if head
        (let [meaningful-values (filter some? head)
              meaning-count (count meaningful-values)]
          (cond
            (= meaning-count 1) (recur (first meaningful-values) tail accum)
            (> meaning-count 1) (recur gr tail 
                                       (update accum gr
                                               (fn [prev] 
                                                 (conj (or prev [])
                                                       (into {} (map (fn [header value] [header value]) headers head)))
                                                 )))
            :else (recur gr tail accum)
            ))
        accum)
      )
    )
  )

(defn pretty-map [matrix]
  (map sheet-table-to-maps matrix))

;(map (fn [sheet] (map (fn [line] (str (get line "UI Group Name | Element Name") " [" (get line "Responsible person") "]")) sheet )) (take 2 (map #(get % "IDEA - Ultimate") (pretty-map (parse-resp raw0)))))