(ns dekaves.worker
  (:require [dekaves.command :as command]
            [dekaves.middleware :as middleware])
  (:import [java.util.concurrent LinkedBlockingQueue TimeUnit]))

(defn run-thread [f]
  (let [queue  (LinkedBlockingQueue.)
        go?    (atom true)
        thread (Thread.
                #(do (while @go?
                       (if-let [params (.poll queue 500 TimeUnit/MILLISECONDS)]
                         (f {:params params})
                         (println :loop!))) ;; TODO: use proper logging
                     (println :goodbye!) ;; TODO: use proper logging
                     ))]
    (.start thread) ;; TODO: separate thread create & start
    {:queue  queue
     :go?    go?
     :thread thread}))

(defn handler [{:keys [params] :as request}]
  (command/handle request params))

(defn app [{:keys [state options]}]
  (-> #'handler
      (middleware/debug-middleware (str (-> options :http :port) "-worker"))
      (middleware/state-middleware state)))

(defn start [args]
  (run-thread (app args)))

(defn stop [worker]
  (-> worker :go? (reset! false))
  nil)

(defn status [worker]
  (let [go?          (-> worker :go? deref)
        thread-state (.getState (:thread worker))
        terminated?  (= thread-state Thread$State/TERMINATED)]
    (cond
      (and go? (not terminated?)) {:status :going}
      (and (not go?) terminated?) {:status :stopped}
      :else                       {:status :error
                                   :go?    go?
                                   :thread thread-state})))
