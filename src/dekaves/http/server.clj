(ns dekaves.http.server
  (:require [clojure.string :as str]
            [ring.adapter.jetty :as jetty]
            [dekaves.command :as command]
            [dekaves.http.client :as client]
            [dekaves.middleware :as middleware]))

(defn handler [{:keys [params] :as request}]
  (let [result (command/handle request params)]
    {:body (pr-str result) ;; TODO: move pr-str to middleware
     :status 200}))

(defn app [{:keys [state options]}]
  (-> #'handler
      (middleware/debug-middleware (:id options) "http")
      (middleware/assoc-middleware :state state)
      middleware/edn-body-middleware))

(defn start [args]
  (jetty/run-jetty (#'app args)
                   (assoc (-> args :options :http)
                          :join? false)))

(defn stop [server]
  (.stop server))

(defn status [server]
  (if-let [state (some-> server (.getState) str/lower-case keyword)]
    {:status state}
    {:status :built}))
