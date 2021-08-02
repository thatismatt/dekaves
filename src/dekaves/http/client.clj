(ns dekaves.http.client
  (:require [clojure.edn :as edn]
            [clj-http.client :as client]))

(defn request [caller body]
  {:pre [(:url caller)]}
  (let [response (try
                   (client/post (:url caller)
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
