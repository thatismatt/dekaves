(ns dekaves.worker
  (:require [clojure.tools.logging :as log]
            [dekaves.command :as command]
            [dekaves.middleware :as middleware]
            [com.stuartsierra.component :as component])
  (:import [java.util.concurrent LinkedBlockingQueue TimeUnit]))

(defn offer [worker params]
  (let [ratify           (get params :ratify :result)
        offer-timeout    (-> worker :options :queue-offer-timeout)
        response-timeout (-> worker :options :response-timeout)
        message          (if (= ratify :result)
                           {:params params :promise (promise)}
                           {:params params})
        queued?          (if (= ratify :deliver)
                           (-> worker :queue (.offer message))
                           (-> worker :queue (.offer message offer-timeout TimeUnit/MILLISECONDS)))]
    (cond (not queued?)       {:result :error
                               :error  "queue full"}
          ;; ???: should these be more like {:result :ok :detail :queued}
          (= ratify :queue)   {:result :queued}
          (= ratify :deliver) {:result :queued}
          (= ratify :result)  (deref (:promise message) response-timeout
                                     {:result :error
                                      :error  "timeout"})
          :else               {:result :error
                               :error  (str "Unknown ratify value: " ratify)})))

(defn handler [ctx]
  (let [result (command/handle ctx)]
    (when (:promise ctx)
      (deliver (:promise ctx) result))
    result))

(defn app [{:keys [state options]}]
  (-> #'handler
      (middleware/debug-middleware (:id options) "worker")
      (middleware/assoc-middleware :options options)
      (middleware/assoc-middleware :state state)))

(defn status [worker]
  (when worker
    (let [go?          (some-> worker :go? deref)
          thread-state (some-> worker :thread .getState)
          terminated?  (= thread-state Thread$State/TERMINATED)]
      (cond
        (and (nil? go?) (nil? thread-state)) {:status :built}
        (and go? (not terminated?))          {:status :started}
        (and (not go?) terminated?)          {:status :stopped}
        :else                                {:status :error
                                              :go?    go?
                                              :thread thread-state}))))

(def options-defaults {:queue-size          1
                       :queue-poll-timeout  1000
                       :queue-offer-timeout 1000
                       :response-timeout    1000
                       :ring-spots          512
                       :ring-redundancy     2})

(defrecord Worker [options state queue go? thread]
  component/Lifecycle
  (start [this]
    (let [options (merge options-defaults options)
          queue   (LinkedBlockingQueue. (:queue-size options))
          go?     (atom true)
          thread  (Thread.
                   #(do (log/info :starting)
                        (while @go?
                          (if-let [message (.poll queue (:queue-poll-timeout options) TimeUnit/MILLISECONDS)]
                            ((app {:state state :options options}) message)
                            (log/debug :loop)))
                        (log/info :shutdown)))]
      (.setName thread (str "worker-" (:id options)))
      (.start thread)
      (assoc this
             :options options
             :queue   queue
             :go?     go?
             :thread  thread)))
  (stop [this]
    (reset! go? false)
    this))
