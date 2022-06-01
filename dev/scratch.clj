(ns scratch
  (:require [com.stuartsierra.component :as component]
            [dekaves.core :as core]
            [dekaves.http.server :as server]
            [dekaves.http.client :as client]
            [dekaves.hash :as hash]
            [dekaves.worker :as worker])
  (:import [java.util.concurrent LinkedBlockingQueue TimeUnit]))

(defn node-start-stop [node-atom options]
  (if (or (map? @node-atom) (= (:status (core/status @node-atom)) :stopped))
    (->> options core/build component/start-system (reset! node-atom))
    (swap! node-atom component/stop-system)))

(comment ;;
()

(def node-1 (atom nil))

(def node-2 (atom nil))

(def node-3 (atom nil))

(do

  (node-start-stop node-1 {:http {:port 9091}})
  (node-start-stop node-2 {:http {:port 9092}})
  (node-start-stop node-3 {:http {:port 9093}})

  (map (comp :status core/status deref) [node-1 node-2 node-3])

  )

(let [nodes (map (fn [node]
                   {:id  (-> node :options :id)
                    :uri {:scheme "http" :host "localhost" :port (-> node :options :http :port)}})
                 [@node-1 @node-2 @node-3])]
  (mapv (fn [node]
          (client/request node {:op    :register
                                :nodes (disj (set nodes) node)}))
        nodes))

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

(map (comp :store deref :state deref) [node-1 node-2 node-3])

)
