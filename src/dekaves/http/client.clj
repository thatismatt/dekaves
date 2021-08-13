(ns dekaves.http.client
  (:require [clojure.edn :as edn]
            [clj-http.client :as client]))

(defn request [node body]
  {:pre [(:url node)]}
  (let [response (try
                   (client/post (:url node)
                                ;; ???: should we be (binding [*print-length* false])?
                                {:body (prn-str body)
                                 :headers {"content-type" "application/edn"}
                                 :throw-exceptions false})
                   (catch Exception e
                     {:type (.getName (type e))
                      :message (ex-message e)
                      ;; DEBUG: :exception e
                      }))]
    (if (-> response :status (= 200))
      (some-> response :body edn/read-string)
      response)))
