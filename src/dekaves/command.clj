(ns dekaves.command)

(declare id->command)

(def commands
  [{:id     :ping
    :doc    "Ping a node, returns a pong."
    :action (constantly {:result :ok
                         :value  :pong})}
   {:id     :register
    :doc    "Register some nodes with another node."
    :action (fn register-action [request params]
              (swap! (request :state) update :nodes (fn [nodes] (set (concat nodes (:nodes params)))))
              {:result :ok})}
   {:id     :store
    :doc    "Store a `value` at a given `key`."
    :action (fn store-action [request params]
              (swap! (:state request) assoc-in [:store (:key params)] (:value params))
              {:result :ok})}
   {:id     :retrieve
    :doc    "Retrieve a value for a given `key`."
    :action (fn retrieve-action [request params]
              (let [k (:key params)
                    v (-> request :state deref :store (get k))]
                {:result :ok
                 :key    k
                 :value  v}))}
   {:id     :count
    :doc    "Count the total key value pairs stored."
    :action (fn count-action [request params]
              {:result :ok
               :count  (-> request :state deref :store count)})}
   {:id     :nodes
    :doc    "List details about the known nodes in this cluster."
    :action (fn nodes-action [request params]
              {:result :ok
               :nodes  (-> request :state deref :nodes)})}
   {:id     :help
    :doc    "Show available commands, or show the doc for a given `command`."
    :action (fn help-action [request params]
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

(defn handle [request params]
  (if-let [command (id->command (:op params))]
    ((:action command) request params)
    {:error :unknown-op
     :op    (:op params)}))
