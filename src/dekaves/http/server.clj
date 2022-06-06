(ns dekaves.http.server
  (:require [clojure.string :as str]
            [ring.adapter.jetty :as jetty]
            [com.stuartsierra.component :as component]
            [dekaves.worker :as worker]
            [dekaves.middleware :as middleware]
            [dekaves.status :as status]))

(defn handler [{:keys [worker params] :as _request}]
  (let [result (worker/offer worker params)]
    {:body (pr-str ;; TODO: move pr-str to middleware
            result)
     :status 200}))

(defn app [{:keys [options worker]}]
  (-> #'handler
      (middleware/debug-middleware (:id options) "http")
      (middleware/assoc-middleware :worker worker)
      middleware/edn-body-middleware))

(defrecord HTTPServer [options worker jetty]

  component/Lifecycle
  (start [this]
    (assoc this
           :jetty (jetty/run-jetty (#'app this)
                                    (assoc (:http options)
                                           :join? false))))
  (stop [this]
    (.stop jetty)
    this)

  status/Status
  (status [_]
    (if-let [state (some-> jetty (.getState) str/lower-case keyword)]
      {:status state}
      {:status :built})))
