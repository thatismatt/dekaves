(ns scratch
  (:require [dekaves.core :as core]
            [dekaves.http.server :as server]
            [dekaves.http.client :as client]
            [dekaves.hash :as hash]
            [dekaves.worker :as worker])
  (:import [java.util.concurrent LinkedBlockingQueue TimeUnit]))

(comment ;;
()

(def node-1)

(def node-2)

(when (or (not (bound? #'node-1))
          (and (or (not (:http node-1))
                   (.isStopped (:http node-1)))
               (not (deref (:go? (:worker node-1))))
               ;; TODO: (= (.getState (:thread (:worker node-1))) Thread$State/TERMINATED)
               ))
  (def node-1
    (core/start {:http {:port 9091}})))

(when (or (not (bound? #'node-2)) (.isStopped node-2))
  (def node-2
    (core/start {:http {:port 9092}})))

(core/status node-1)

(core/stop node-1)

(worker/stop (:worker node-1))
(server/stop (:http node-1))

(-> node-2 :worker :go? (reset! false))

(client/request {:url "http://localhost:9091"}
                {:op :ping})

(client/request {:url "http://localhost:9091"}
                {:op    :store
                 :key   :foo
                 :value :bar})

(client/request {:url "http://localhost:9091"}
                {:op  :retrieve
                 :key :foo})

(client/request {:url "http://localhost:9091"}
                {:op  :retrieve
                 :key :qux})

(client/request {:url "http://localhost:9091"}
                {:op :count})

(client/request {:url "http://localhost:9091"}
                {:op :help})

(client/request {:url "http://localhost:9091"}
                {:op      :help
                 :command :help})

(client/request {:url "http://localhost:9091"}
                {:op    :register
                 :nodes [{:host "localhost"
                          :port 9093}]})

(client/request {:url "http://localhost:9091"}
                {:op    :nodes})

(client/request {:url "http://localhost:9091"}
                {:op :unknown})

;; (-> node-1 :worker :queue (.poll 500 TimeUnit/MILLISECONDS))
(-> node-1 :worker :queue (.offer {:op :count}))

(-> node-1 :worker :queue (.offer {:op    :store
                                     :key   :qux
                                     :value :zim}))

(def queue (LinkedBlockingQueue.))

(.offer queue {:op :ping})

(.poll queue 500 TimeUnit/MILLISECONDS)

)

(comment ;; http server
()

(def http-server
  (server/start {:http {:port 9091}}))

(.stop http-server)

)
