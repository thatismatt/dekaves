(ns dekaves.core
  (:require [dekaves.http.server :as server]
            [dekaves.worker :as worker]
            [com.stuartsierra.component :as component])
  (:import [java.util UUID]))

(defn build [options]
  (let [state  (atom {:store    {}
                      :registry {}})
        id     (str (UUID/randomUUID))]
    (component/system-map
     :options (assoc options :id id)
     :state   state
     :worker  (component/using (worker/map->Worker {})
                               [:options :state])
     :http    (component/using (server/map->HTTPServer {})
                               [:options :state]))))

(defn status [node]
  (let [worker-status (worker/status (:worker node))
        http-status   (server/status (:http node))]
    {:worker worker-status
     :http http-status
     :status (if (= worker-status http-status)
               (:status worker-status)
               :error)}))
