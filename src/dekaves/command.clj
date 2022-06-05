(ns dekaves.command
  (:require [clojure.tools.logging :as log]
            [dekaves.http.client :as client]
            [dekaves.hash :as hash]))

(declare id->command)

(def commands
  [{:id     :ping
    :doc    "Ping a node, returns a pong."
    :action (constantly {:result :ok
                         :value  :pong})}
   {:id     :register
    :doc    "Register some nodes with another node."
    :action (fn register-action [{:keys [params options] :as ctx}]
              (swap! (:state ctx) #(let [nodes          (into (:nodes %) (map (juxt :id identity)) (:nodes params))
                                         ring-possible? (hash/ring-possible? (:ring-redundancy options) (keys nodes))]
                                     (cond-> %
                                       :always        (assoc :nodes nodes)
                                       ring-possible? (assoc :ring (hash/make-ring (keys nodes) (:ring-spots options) (:ring-redundancy options))))))
              {:result :ok})}
   {:id     :store
    :doc    "Store a `value` at a given `key`."
    :action (fn store-action [{:keys [params options state] :as _ctx}]
              (let [k     (:key params)
                    nodes (hash/ring-lookup (:ring @state) k (:ring-redundancy options))]
                (doseq [n nodes]
                  (if (= n (:id options))
                    (swap! state assoc-in [:store k] (:value params))
                    (when-not (:store-only params) ;; TODO: avoids cycles of store calls between primary and secondary shards, but feels really hacky
                      (let [response (client/request (-> @state :nodes (get n)) (assoc params
                                                                                       :store-only true
                                                                                       :ratify :queue))]
                        (when-not (= (:result response) :queued)
                          ;; TODO: requeue the request
                          )))))
                {:result :ok}))}
   {:id     :retrieve
    :doc    "Retrieve a value for a given `key`."
    :action (fn retrieve-action [ctx]
              (let [k (-> ctx :params :key)
                    v (-> ctx :state deref :store (get k))]
                {:result :ok
                 :key    k
                 :value  v}))}
   {:id     :count
    :doc    "Count the number of key value pairs stored by this node."
    :action (fn count-action [ctx]
              {:result :ok
               :count  (-> ctx :state deref :store count)})}
   {:id     :nodes
    :doc    "List details about the known nodes in this cluster."
    :action (fn nodes-action [ctx]
              {:result :ok
               :nodes  (-> ctx :state deref :nodes vals)})}
   {:id     :help
    :doc    "Show available commands, or show the doc for a given `command`."
    :action (fn help-action [ctx]
              (let [command-id (-> ctx :params :command)
                    command    (id->command command-id)
                    list-all   (not command-id)]
                (cond
                  command  {:result  :ok
                            :command command-id
                            :doc     (:doc command)}
                  list-all {:result   :ok
                            :commands (->> id->command keys sort)}
                  :else    {:result  :error
                            :error   :unknown-command
                            :command command-id})))}])

(def id->command
  (->> commands (map (juxt :id identity)) (into {})))

(defn handle [ctx]
  (if-let [command (-> ctx :params :op id->command)]
    (try
      ((:action command) ctx)
      (catch Exception e
        (log/warn e "Unhandled exception while executing command" (:params ctx))
        {:result    :error
         :error     :exception
         :exception {:type    (type e)
                     :message (ex-message e)}}))
    {:result :error
     :error  :unknown-op
     :op     (-> ctx :params :op)}))
