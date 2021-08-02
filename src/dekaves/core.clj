(ns dekaves.core
  (:require [dekaves.http.server :as server]
            [dekaves.worker :as worker]))

(defn start [options]
  (let [state  (atom {:store    {}
                      :registry {}})
        args   {:options options
                :state   state}
        worker (try (worker/start args) (catch Exception e))
        http   (try (server/start args) (catch Exception e))]
    {:options options
     :state   state
     :worker  worker
     :http    http}))

(defn stop [node]
  {:worker (worker/stop (:worker node))
   :http   (server/stop (:http node))})

(defn status [node]
  {:worker (worker/status (:worker node))
   :http   (server/status (:http node))})
