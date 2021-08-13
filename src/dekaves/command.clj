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
              (swap! (:state ctx) #(let [nodes (into (:nodes %) (map (juxt :id identity)) (:nodes params))]
                                     (assoc %
                                            :nodes nodes
                                            :ring  (hash/make-ring (keys nodes) (:ring-spots options) (:ring-redundancy options)))))
              {:result :ok})}
   {:id     :store
    :doc    "Store a `value` at a given `key`."
    :action (fn store-action [{:keys [params options state] :as ctx}]
              (let [k (:key params)
                    nodes (hash/ring-lookup (:ring @state) k (:ring-redundancy options))]
                (doseq [n nodes]
                  (if (= n (:id options))
                    (swap! state assoc-in [:store k] (:value params))
                    (when-not (:store-only params) ;; TODO: avoids cycles of store calls between primary and secondary shards, but feels really hacky
                      (let [response (client/request (->> @state :nodes n) (assoc params
                                                                                  :store-only true
                                                                                  :ratify :queue))]
                        (when-not (= (:result response) :queued)
                          ;; TODO: requeue the request
                          )))))
                {:result :ok}))}
   {:id     :retrieve
    :doc    "Retrieve a value for a given `key`."
    :action (fn retrieve-action [{:keys [params] :as ctx}]
              (let [k (:key params)
                    v (-> ctx :state deref :store (get k))]
                {:result :ok
                 :key    k
                 :value  v}))}
   {:id     :count
    :doc    "Count the total key value pairs stored."
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
    :action (fn help-action [{:keys [params] :as ctx}]
              (let [command-id (:command params)
                    command    (id->command command-id)
                    unknown?   (and command-id (not command))]
                (cond
                  command  {:result  :ok
                            :command command-id
                            :doc     (:doc command)}
                  unknown? {:result :error
                            :error  (str "unknown command " command-id)}
                  :else    {:result   :ok
                            :commands (->> commands (map :id) sort)})))}])

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
