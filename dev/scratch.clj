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

(def node-2
  (atom (core/build {:http {:port 9092}})))

(do (swap! node-1 component/start-system)
    (swap! node-2 component/start-system))

(do (swap! node-1 component/stop-system)
    (swap! node-2 component/stop-system))

(core/status @node-1)

(core/status @node-2)

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

(client/request {:url (str "http://localhost:" (-> @node-2 :options :http :port))}
                {:op    :register
                 :nodes [{:id   (-> @node-1 :options :id)
                          :host "localhost"
                          :port (-> @node-1 :options :http :port)}]})

(client/request {:url (str "http://localhost:" (-> @node-1 :options :http :port))}
                {:op    :register
                 :nodes [{:id   (-> @node-2 :options :id)
                          :host "localhost"
                          :port (-> @node-2 :options :http :port)}]})

(client/request {:url "http://localhost:9091"}
                {:op :nodes})

(client/request {:url "http://localhost:9091"}
                {:op :unknown})


)
