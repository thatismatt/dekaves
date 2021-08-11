(ns dekaves.middleware
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]))

(defn read-edn [in]
  (if (string? in)
    (edn/read-string in)
    (edn/read (java.io.PushbackReader. (io/reader in)))))

(defn edn-request? [request]
  (re-find #"^application/edn" (get-in request [:headers "content-type"] "")))

(defn edn-body-middleware [handler]
  (fn [request]
    (let [edn-params (when (edn-request? request) (some-> request :body read-edn))]
      (-> request (update :params merge edn-params) handler))))

(defn state-middleware [handler state]
  (fn [request]
    (-> request (assoc :state state) handler)))

(defn debug-middleware [handler id]
  (fn [request]
    (let [_        (log/info :request id (:params request))
          response (handler request)
          _        (log/info :response id response)]
      response)))
