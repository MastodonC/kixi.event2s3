(ns kixi.event2s3.shared
  (:require [taoensso.timbre :as timbre]
            [clojure.java.io :as io]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [cognitect.transit :as tr])
  (:import [java.io PrintWriter]
           [java.util.zip GZIPOutputStream]))

(def date-format (f/formatters :basic-date))
(def time-format (f/formatters :time))

(def transit-encoding-level :json-verbose) ;; DO NOT CHANGE
(defn transit-decode-bytes [in]
  (let [reader (tr/reader in transit-encoding-level)]
    (tr/read reader)))

(defn s3-naming-function [event]
  (str "backup/" (f/unparse date-format (t/now)) "/" (f/unparse time-format (t/now)) "-" (:onyx.core/lifecycle-id event) ".gz"))

(defn deserialize-message [bytes]
  (let [result (try
                 (transit-decode-bytes (io/input-stream bytes))
                 (catch Exception e
                   {:parse_error e :original (String. bytes "UTF-8")}))]
    result))

(def gzip-serializer-fn
  (fn [vs]
    (let [output-str (apply str vs)
          out (java.io.ByteArrayOutputStream.)]
      (do (doto (java.io.BufferedOutputStream.
                 (java.util.zip.GZIPOutputStream. out))
            (.write (.getBytes output-str))
            (.close)))
      (.toByteArray out))))

(def logger (agent nil))

(defn log-batch [event lifecycle]
  (let [task-name (:onyx/name (:onyx.core/task-map event))]
    (doseq [m (map :message (mapcat :leaves (:tree (:onyx.core/results event))))]
      (send logger (fn [_] (timbre/debug task-name " segment: " m)))))
  {})

(def log-calls
  {:lifecycle/after-batch log-batch})
