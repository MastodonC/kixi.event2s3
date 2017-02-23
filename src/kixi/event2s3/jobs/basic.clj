(ns kixi.event2s3.jobs.basic
  (:require [onyx.job :refer [add-task register-job]]
            [onyx.tasks.core-async :as core-async-task]
            [onyx.plugin.kafka]
            [onyx.plugin.s3-output]
            [onyx.plugin.s3-utils :as s3-utils]
            [onyx.tasks.kafka :as kafka-task]
            [onyx.tasks.s3 :as s3]
            [kixi.event2s3.shared :as shared]
            [kixi.event2s3.logstash :as logstash]
            [taoensso.timbre :as timbre]
            [franzy.admin.zookeeper.client :as client]
            [franzy.admin.partitions :as fp]
            [cheshire.core :refer [generate-string]]))

(def logger (agent nil))

(defn log-after-read-batch [event lifecycle]
  (send logger (fn [_]
                 (when-let [batch (:onyx.core/batch event)]
                   (run! (fn [{:keys [message]}]
                           (try
                             (timbre/with-merged-config
                               {:event? true}
                               (-> message
                                   (shared/deserialize-message)
                                   (timbre/info)))
                             (catch Exception e
                               (timbre/error e "original:" (String. message "UTF-8"))))) batch))))
  {})

(def logger-lifecycle
  {:lifecycle/after-read-batch log-after-read-batch})

(defn basic-job
  [kafka-opts s3-opts region]
  (timbre/infof "Setting region: %s " region)
  (let [aws-client (s3-utils/set-region (s3-utils/new-client) region)
        base-job {:workflow [[:in :out]]
                  :catalog []
                  :lifecycles [{:lifecycle/task :in
                                :lifecycle/calls :kixi.event2s3.jobs.basic/logger-lifecycle
                                :onyx/doc "Log incoming messages"}]
                  :windows []
                  :triggers []
                  :flow-conditions []
                  :task-scheduler :onyx.task-scheduler/balanced}]
    (-> base-job
        (add-task (kafka-task/consumer :in kafka-opts))
        (add-task (s3/s3-output :out s3-opts)))))

(defn get-partition-count-for-topic
  [zk-addr topic]
  (let [zk-utils (client/make-zk-utils {:servers zk-addr} false)]
    (try
      (-> zk-utils
          (fp/partitions-for [topic])
          (get (keyword topic))
          (count))
      (catch Exception e
        1)
      (finally
        (.close zk-utils)))))

(defmethod register-job "event-s3-job"
  [job-name config]
  (let [topic (get-in config [:job-config :kafka-topic])
        zk-addr (get-in config [:env-config :zookeeper/address])
        kafka-topic-partitions (get-partition-count-for-topic zk-addr topic)
        _ (timbre/info "Detected" kafka-topic-partitions "Kafka partitions for topic" topic)
        kafka-opts     {:onyx/batch-size (get-in config [:job-config :kafka-task :onyx-batch-size])
                        :onyx/batch-timeout (get-in config [:job-config :kafka-task :onyx-batch-timeout])
                        :onyx/type :input
                        :onyx/medium :kafka
                        :onyx/min-peers kafka-topic-partitions
                        :onyx/max-peers kafka-topic-partitions
                        :kafka/zookeeper zk-addr
                        :kafka/topic topic
                        :kafka/group-id "cds-event-s3"
                        :kafka/offset-reset :earliest
                        :kafka/commit-interval 500
                        :kafka/wrap-with-metadata? false
                        :kafka/force-reset? false
                        :kafka/deserializer-fn :clojure.core/identity
                        :onyx/doc "Reads messages from a Kafka topic"}
        s3-opts    {:s3/bucket (get-in config [:job-config :s3-bucket-name])
                    :s3/serializer-fn :kixi.event2s3.shared/gzip-serializer-fn
                    :s3/key-naming-fn :kixi.event2s3.shared/s3-naming-function
                    :onyx/type :output
                    :onyx/medium :s3
                    :onyx/min-peers 1
                    :onyx/max-peers 1
                    :onyx/batch-size (get-in config [:job-config :s3-task :onyx-batch-size])
                    :onyx/batch-timeout (get-in config [:job-config :s3-task :onyx-batch-timeout])
                    :onyx/doc "Writes segments to s3 files, one file per batch"}]
    (basic-job kafka-opts s3-opts (get-in config [:job-config :aws-region]))))
