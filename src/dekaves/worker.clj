(ns dekaves.worker
  (:require [clojure.tools.logging :as log]
            [dekaves.command :as command]
            [dekaves.middleware :as middleware])
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

(defn start [args]
  (run-thread (app args)))

(defn stop [worker]
  (-> worker :go? (reset! false))
  nil)

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
