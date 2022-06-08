(ns dekaves.command-test
  (:require [clojure.test :refer [deftest is]]
            [dekaves.command :as command]
            [dekaves.status :as status]
            [dekaves.hash :as hash]
            [dekaves.http.client :as client]))

(deftest status-test
  (is (= {:result :ok
          :id     "node-id"
          :status :fake}
         (command/handle {:options {:id "node-id"}
                          :worker  (reify status/Status
                                     (status [_] {:status :fake}))}))))

(deftest ping-test
  (is (= {:result :ok
          :value  :pong}
         (command/handle {:params {:command :ping}}))))

(deftest help-test
  (is (= {:result   :ok
          :commands [:count :help :nodes :ping :register :retrieve :status :store]}
         (command/handle {:params {:command :help}})))
  (is (= {:result  :ok
          :command :help
          :doc     "Show available commands, or show the doc for a given `command`."}
         (command/handle {:params {:command :help
                                   :target  :help}})))
  (is (= {:result  :error
          :error   :unknown-command
          :command :bad}
         (command/handle {:params {:command :help
                                   :target  :bad}}))))

(deftest count-test
  (is (= {:result :ok
          :count  0}
         (command/handle {:state  (atom {:store {}})
                          :params {:command :count}})))
  (is (= {:result :ok
          :count  3}
         (command/handle {:state  (atom {:store {:a 1 :b 2 :c 3}})
                          :params {:command :count}}))))

(deftest nodes-test
  (is (= {:result :ok
          :nodes  [{:id "one"}]}
         (command/handle {:state  (atom {:nodes {"one" {:id "one"}}})
                          :params {:command :nodes}}))))

(deftest register-test
  (let [state (atom {:nodes {"one" {:id "one"}}})]
    (is (= {:result :ok}
           (command/handle {:state   state
                            :options {:ring-redundancy 2
                                      :ring-spots      4}
                            :params  {:command :register
                                      :nodes   [{:id "two"}]}})))
    (is (= @state {:nodes {"one" {:id "one"} "two" {:id "two"}}
                   :ring  (hash/make-ring ["one" "two"] 4 2)}))))

(deftest store-test
  (is (= {:result :error
          :error  :insufficient-nodes}
         (command/handle {:state   (atom {})
                          :options {:ring-redundancy 2}
                          :params  {:command :store
                                    :key     :a
                                    :value   :b}})))
  (with-redefs [client/request (constantly {:result :queued})]
    (let [state  (atom {:nodes {"one" {:id "one"} "two" {:id "two"}}
                        :store {}
                        :ring  (hash/make-ring ["one" "two"] 4 2)})
          result (command/handle {:state   state
                                  :options {:id              "one"
                                            :ring-redundancy 2}
                                  :params  {:command :store
                                            :key     :a
                                            :value   :b}})]
      (is (= (:store @state)
             {:a :b}))
      (is (= {:result  :ok
              :results #{{:node        "one"
                          :destination :local}
                         {:node        "two"
                          :destination :remote
                          :response    {:result :queued}}}}
             (update result :results set))))))

(deftest retrieve-test
  (is (= {:result :error
          :error  :insufficient-nodes}
         (command/handle {:state   (atom {:nodes {}
                                          :store {}})
                          :options {:id              "one"
                                    :ring-redundancy 1}
                          :params  {:command :retrieve
                                    :key     :a}})))
  (let [state (atom {:nodes {"one" {:id "one"} "two" {:id "two"}}
                     :store {:j :k}
                     :ring  (hash/make-ring ["one" "two"] 4 1)})]
    (is (= {:result :ok
            :node   "one"
            :key    :g ;; (hash/ring-lookup ring :g 1) => ("one") i.e. local
            :value  nil}
           (command/handle {:state   state
                            :options {:id              "one"
                                      :ring-redundancy 1}
                            :params  {:command :retrieve
                                      :key     :g}})))
    (is (= {:result :ok
            :node   "one"
            :key    :j ;; (hash/ring-lookup ring :j 1) => ("one") i.e. local
            :value  :k}
           (command/handle {:state   state
                            :options {:id              "one"
                                      :ring-redundancy 1}
                            :params  {:command :retrieve
                                      :key     :j}})))
    (is (= {:result :ok
            :node   "one"
            :key    :g ;; (hash/ring-lookup ring :g 1) => ("one") i.e. local
            :value  nil}
           (command/handle {:state   state
                            :options {:id              "one"
                                      :ring-redundancy 1}
                            :params  {:command :retrieve
                                      :key     :g}})))
    (with-redefs [client/request (constantly {:result :ok
                                              :node   "two"
                                              :key    :a
                                              :value  :b})]
      (is (= {:result :ok
              :node   "two"
              :key    :a ;; (hash/ring-lookup ring :a 1) => ("two") i.e. remote
              :value  :b}
             (command/handle {:state   state
                              :options {:id              "one"
                                        :ring-redundancy 1}
                              :params  {:command :retrieve
                                        :key     :a}}))))))

(comment
()

(group-by #(hash/ring-lookup (hash/make-ring ["one" "two"] 4 1) % 1)
          (map (comp keyword str) "abcdefghijklmnopqrstuvwxyz"))
;; {("two") [:a :b :c :d :e :f :h :i :k :m :n :o :p :q :r :s :t :u :v :w :x :y :z]
;;  ("one") [:g :j :l]}

)
