(ns dekaves.status
  (:require [com.stuartsierra.component]))

(defprotocol Status
  (status [this]))

(extend-protocol Status
  com.stuartsierra.component.SystemMap
  (status [this]
    (let [status-map (update-vals (filter (fn [[_ v]] (satisfies? Status v)) this)
                                  status)
          statuses (vals status-map)]
      (assoc status-map
             :status (if (and (seq statuses)
                              (apply = statuses))
                       (:status (first statuses))
                       :error)))))
