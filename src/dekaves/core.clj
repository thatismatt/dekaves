(ns dekaves.core
  (:require [dekaves.http.server :as server]
            [dekaves.worker :as worker]
            [com.stuartsierra.component :as component])
  (:import [java.util UUID]))

(def options-defaults {:queue-size          1
                       :queue-poll-timeout  1000
                       :queue-offer-timeout 1000
                       :response-timeout    1000
                       :ring-spots          512
                       :ring-redundancy     2})

(defn build [options]
  (let [options (merge {:id (str (UUID/randomUUID))}
                       options-defaults
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
