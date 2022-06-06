(ns scratch
  (:require [com.stuartsierra.component :as component]
            [dekaves.core :as core]
            [dekaves.http.server :as server]
            [dekaves.http.client :as client]
            [dekaves.hash :as hash]
            [dekaves.worker :as worker]
            [dekaves.status :as status])
  (:import [java.util.concurrent LinkedBlockingQueue TimeUnit]))

(defn node-start-stop [node-atom options]
  (if (or (nil? @node-atom) (= (:status (status/status @node-atom)) :stopped))
    (->> options core/build component/start-system (reset! node-atom))
    (swap! node-atom component/stop-system)))

(comment ;;
()

(def nodes (repeatedly 5 #(atom nil)))

(mapv #(node-start-stop %1 {:id %3
                            :http {:port (+ 9091 %2)}})
      nodes
      (range)
      ["kite" "hawk" "rook" "lark" "swan" "crow" "dove" "heron" "raven" "pidgeon" "sparrow" "jackdaw" "swallow" "falcon" "eagle" "vulture" "wagtail"])

(map (comp :status status/status deref) nodes)

(let [ns (map (fn [node]
                {:id  (-> @node :options :id)
                 :uri {:scheme "http" :host "localhost" :port (-> @node :options :http :port)}})
              nodes)]
  (mapv (fn [node]
          (client/request node {:op    :register
                                :nodes (disj (set ns) node)}))
        ns))

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:op    :register
                 :nodes []})

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:op :ping
                 :ratify :deliver})

(client/request {:uri {:scheme "http" :host "localhost" :port 9092}}
                {:op    :store
                 :key   :foo
                 :value :bar})

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:op    :store
                 :key   :foo
                 :value :bar})

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:op  :retrieve
                 :key :foo})

(mapv (fn [node]
        (client/request {:uri {:scheme "http" :host "localhost" :port (-> @node :options :http :port)}}
                        {:op  :retrieve
                         :key :foo}))
      nodes)

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:op  :retrieve
                 :key :qux})

(mapv (fn [node]
        (client/request {:uri {:scheme "http" :host "localhost" :port (-> @node :options :http :port)}}
                        {:op  :count}))
      nodes)

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:op :count})

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:op :help})

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:op      :help
                 :command :help})

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:op :nodes})

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:op :unknown})

(map (comp :store deref :state deref) nodes)

)
