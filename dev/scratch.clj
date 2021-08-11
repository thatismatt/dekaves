(ns scratch
  (:require [com.stuartsierra.component :as component]
            [dekaves.core :as core]
            [dekaves.http.server :as server]
            [dekaves.http.client :as client]
            [dekaves.hash :as hash]
            [dekaves.worker :as worker])
  (:import [java.util.concurrent LinkedBlockingQueue TimeUnit]))

(comment ;;
()

(def node-1
  (atom (core/build {:http {:port 9091}})))

(swap! node-1 component/start-system)

(swap! node-1 component/stop-system)

(core/status @node-1)

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
                {:op :nodes})

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
