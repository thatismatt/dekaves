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
          (client/request node {:command :register
                                :nodes   (disj (set ns) node)}))
        ns))

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:command :register
                 :nodes   []})

(for [ratify [:queue :deliver :result]]
  (client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                  {:command :ping
                   :ratify  ratify}))

(client/request {:uri {:scheme "http" :host "localhost" :port 9092}}
                {:command :store
                 :key     :foo
                 :value   :bar})

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:command :store
                 :key     :foo
                 :value   :bar})

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:command :retrieve
                 :key     :foo})

(mapv (fn [node]
        (client/request {:uri {:scheme "http" :host "localhost" :port (-> @node :options :http :port)}}
                        {:command :retrieve
                         :key     :foo}))
      nodes)

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:command :retrieve
                 :key     :qux})

(mapv (fn [node]
        (client/request {:uri {:scheme "http" :host "localhost" :port (-> @node :options :http :port)}}
                        {:command :count}))
      nodes)

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:command :count})

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:command :help})

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:command :help
                 :target  :help})

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:command :nodes})

(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:command :unknown})

(map (comp :store deref :state deref) nodes)

)
