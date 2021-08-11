(ns dekaves.worker
  (:require [clojure.tools.logging :as log]
            [dekaves.command :as command]
            [dekaves.middleware :as middleware]
            [com.stuartsierra.component :as component])
  (:import [java.util.concurrent LinkedBlockingQueue TimeUnit]))

(defn run-thread [f]
  (let [queue  (LinkedBlockingQueue.)
        go?    (atom true)
        thread (Thread.
                #(do (while @go?
                       (if-let [params (.poll queue 500 TimeUnit/MILLISECONDS)]
                         (f {:params params})
                         (log/debug :loop)))
                     (log/info :shutdown)))]
    (.start thread)
    {:queue  queue
     :go?    go?
     :thread thread}))

(defn handler [{:keys [params] :as request}]
  (command/handle request params))

(defn app [{:keys [state options]}]
  (-> #'handler
      (middleware/debug-middleware (:id options) "worker")
      (middleware/assoc-middleware :state state)))

(defn status [worker]
  (let [go?          (some-> worker :go? deref)
        thread-state (some-> worker :thread .getState)
        terminated?  (= thread-state Thread$State/TERMINATED)]
    (cond
      (and (nil? go?) (nil? thread-state)) {:status :built}
      (and go? (not terminated?))          {:status :started}
      (and (not go?) terminated?)          {:status :stopped}
      :else                                {:status :error
                                            :go?    go?
                                            :thread thread-state})))

(defrecord Worker [options state queue go? thread]
  component/Lifecycle
  (start [this]
    (merge this (run-thread (app this))))
  (stop [this]
    (reset! go? false)
    this))
