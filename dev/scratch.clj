(ns scratch
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [dekaves.core :as core]
            [dekaves.http.server :as server]
            [dekaves.http.client :as client]
            [dekaves.hash :as hash]
            [dekaves.worker :as worker]
            [dekaves.status :as status])
  (:import [java.util.concurrent LinkedBlockingQueue TimeUnit]))

(defn rand-str [s]
  (str/join (repeatedly s #(rand-nth "abcdefghijklmnopqrstuvwxyz"))))

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

(map (juxt (comp :port :http :options deref)
           (comp :id :options deref)
           (comp :status status/status deref)) nodes)

;; only stop
(map (fn [node]
       (if (= :stopped (:status (status/status @node)))
         :already-stopped
         (node-start-stop node nil))) nodes)

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


(client/request {:uri {:scheme "http" :host "localhost" :port 9091}}
                {:command :status})

(def go (atom true))

(swap! go not)

(future
  (reset! go true)
  (while @go
    (Thread/sleep 500)
    (prn (apply + (map (comp count :store deref :state deref) nodes))))
  (prn :done!))

(let [ns "dekaves"]
  (.setLevel (org.slf4j.LoggerFactory/getLogger ns) ch.qos.logback.classic.Level/WARN))

(let [rs (map
          (fn [c node]
            (merge (client/request {:uri {:scheme "http" :host "localhost" :port (-> @node :options :http :port)}}
                                   c)
                   c))
          (repeatedly 1000 #(do {:command :store
                                 :key   (rand-str 5)
                                 :value (rand-str 5)}))
          (cycle nodes))]
  (future (time (count rs)))
  (def results (concat results rs)))

(->> results (map :result) frequencies)

(->> results (map :key) frequencies (map second) frequencies)

;; reset all nodes stores
(do
  (mapv #(-> % deref :state (swap! assoc :store {})) nodes)
  nil)

(sort-by first
         (map (juxt (comp :id :options deref) (comp count :store deref :state deref)) nodes))

(->> results (mapcat #(->> % :results (map :node))) frequencies (sort-by first))

)
