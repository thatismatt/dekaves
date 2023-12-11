(ns dekaves.worker-test
  (:require [clojure.test :refer [deftest is]]
            [dekaves.worker :as worker])
  (:import [java.util.concurrent LinkedBlockingQueue TimeUnit]))

(deftest offer-test
  (let [queue (LinkedBlockingQueue. 1)]
    (is (= {:result :queued}
           (worker/offer {:options {:queue-offer-timeout 10
                                    :response-timeout    10}
                          :queue   queue}
                         {:command :fake-1
                          :ratify  :queue})))
    (doseq [ratify [:queue :deliver :result]]
      (is (= {:result :error
              :error  :queue-full}
             (worker/offer {:options {:queue-offer-timeout 10
                                      :response-timeout    10}
                            :queue   queue}
                           {:command :fake-2
                            :ratify  ratify}))))
    (is (= [{:params {:command :fake-1
                      :ratify  :queue}}]
           (seq queue))))
  (is (= {:result :error
          :error  :timeout}
         (worker/offer {:options {:queue-offer-timeout 10
                                  :response-timeout    10}
                        :queue   (LinkedBlockingQueue. 1)}
                       {:command :fake
                        :ratify  :result})))
  (let [queue (LinkedBlockingQueue. 1)
        msg-f (future (when-let [message (.poll queue 10 TimeUnit/MILLISECONDS)]
                        (deliver (:promise message) {:result :fake})
                        message))]
    (is (= {:result :fake}
           (worker/offer {:options {:queue-offer-timeout 10
                                    :response-timeout    10}
                          :queue   queue}
                         {:command :fake
                          :ratify  :result})))
    (is (= {:command :fake
            :ratify  :result}
           (:params @msg-f))))
  ;; TODO: message with bad ratify value shouldn't be queued
  ;; (let [queue (LinkedBlockingQueue. 1)]
  ;;   (is (= {:result :error
  ;;           :error  :unknown-ratify
  ;;           :ratify  :bad}
  ;;          (worker/offer {:options {:queue-offer-timeout 10
  ;;                                   :response-timeout    10}
  ;;                         :queue   queue}
  ;;                        {:command :fake
  ;;                         :ratify  :bad})))
  ;;   (is (empty? queue)))
  )
