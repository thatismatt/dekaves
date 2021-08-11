(ns dekaves.worker
  (:require [clojure.tools.logging :as log]
            [dekaves.command :as command]
            [dekaves.middleware :as middleware]
            [com.stuartsierra.component :as component])
  (:import [java.util.concurrent LinkedBlockingQueue TimeUnit]))

(defn offer [worker params]
  (let [p                (promise)
        params+promise   (assoc params ::promise p)
        offer-timeout    (-> worker :options :queue-offer-timeout)
        response-timeout (-> worker :options :response-timeout)
        queued?          (-> worker :queue (.offer params+promise offer-timeout TimeUnit/MILLISECONDS))]
    (if queued?
      (deref p response-timeout
             {:result :error
              :error  "timeout"})
      {:result :error
       :error  "queue full"})))

(defn handler [{:keys [params] :as request}]
  (let [result (command/handle request params)]
    (when (::promise params)
      (deliver (::promise params) result))
    result))

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

(def options-defaults {:queue-size          1
                       :queue-poll-timeout  1000
                       :queue-offer-timeout 1000
                       :response-timeout    1000})

(defrecord Worker [options state queue go? thread]
  component/Lifecycle
  (start [this]
    (let [options (merge options-defaults options)
          queue   (LinkedBlockingQueue. (:queue-size options))
          go?     (atom true)
          thread  (Thread.
                   #(do (while @go?
                          (if-let [params (.poll queue (:queue-poll-timeout options) TimeUnit/MILLISECONDS)]
                            ((app this) {:params params})
                            (log/debug :loop)))
                        (log/info :shutdown)))]
      (.start thread)
      (assoc this
             :options options
             :queue   queue
             :go?     go?
             :thread  thread)))
  (stop [this]
    (reset! go? false)
    this))
