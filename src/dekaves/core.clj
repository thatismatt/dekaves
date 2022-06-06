(ns dekaves.core
  (:require [dekaves.http.server :as server]
            [dekaves.worker :as worker]
            [com.stuartsierra.component :as component])
  (:import [java.util UUID]))

(defn build [options]
  (let [options (merge {:id (str (UUID/randomUUID))}
                       options)
        id (:id options)
        state (atom {:store {}
                     :nodes {id {:id id :me? true}}})]
    (component/system-map
     :options options
     :state   state
     :worker  (component/using (worker/map->Worker {})
                               [:options :state])
     :http    (component/using (server/map->HTTPServer {})
                               [:options :worker]))))

(defn status [node]
  (let [worker-status (worker/status (:worker node))
        http-status   (server/status (:http node))]
    {:worker worker-status
     :http   http-status
     :status (if (= worker-status http-status)
               (:status worker-status)
               :error)}))
