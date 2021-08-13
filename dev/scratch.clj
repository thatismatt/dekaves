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

(def node-1 (atom nil))

(def node-2 (atom nil))

(do

  (if (or (nil? @node-1) (= (:status (core/status @node-1)) :stopped))
    (->> {:http {:port 9091}} core/build component/start-system (reset! node-1))
    (swap! node-1 component/stop-system))

  (if (or (nil? @node-2) (= (:status (core/status @node-2)) :stopped))
    (->> {:http {:port 9092}} core/build component/start-system (reset! node-2))
    (swap! node-2 component/stop-system))

  (map (comp :status core/status) [@node-1 @node-2])

  )

(do (swap! node-1 component/start-system)
    (swap! node-2 component/start-system))

(do (swap! node-1 component/stop-system)
    (swap! node-2 component/stop-system))

(core/status @node-1)

(core/status @node-2)

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:op :ping
                 :ratify :deliver})

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:op    :store
                 :key   :foo
                 :value :bar})

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:op  :retrieve
                 :key :foo})

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:op  :retrieve
                 :key :qux})

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:op :count})

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:op :help})

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:op      :help
                 :command :help})

(client/request {:uri {:scheme "http" :host "localhost" :port (-> @node-2 :options :http :port)}}
                {:op    :register
                 :nodes [{:id   (-> @node-1 :options :id)
                          :uri {:scheme "http"
                                :host "localhost"
                                :port (-> @node-1 :options :http :port)}}]})

(client/request {:uri {:scheme "http" :host "localhost" :port (-> @node-1 :options :http :port)}}
                {:op    :register
                 :nodes [{:id   (-> @node-2 :options :id)
                          :uri {:scheme "http"
                                :host "localhost"
                                :port (-> @node-2 :options :http :port)}}]})

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:op :nodes})

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:op :unknown})

)
