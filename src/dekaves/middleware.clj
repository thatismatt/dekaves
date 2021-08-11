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

(defn assoc-middleware [handler k v]
  (fn [request]
    (-> request (assoc k v) handler)))

(defn debug-middleware [handler id kind]
  (let [ref (str (subs id 0 4) "-" kind)]
    (fn [request]
      (let [_        (log/info ref :request (:params request) "\n")
            response (handler request)
            _        (log/info ref :response response "\n")]
        response))))
