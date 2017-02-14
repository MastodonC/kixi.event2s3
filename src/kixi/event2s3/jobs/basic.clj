(ns kixi.event2s3.jobs.basic
  (:require [onyx.job :refer [add-task register-job]]
            [onyx.tasks.core-async :as core-async-task]
            [onyx.plugin.kafka]
            [onyx.plugin.s3-output]
            [onyx.plugin.s3-utils :as s3-utils]
            [onyx.tasks.kafka :as kafka-task]
            [onyx.tasks.s3 :as s3]
            [kixi.event2s3.shared]
            [taoensso.timbre :as timbre]))

(defn basic-job
  [kafka-opts s3-opts region]
  (timbre/infof "Setting region: %s " region)
  (let [aws-client (s3-utils/set-region (s3-utils/new-client) region)
        base-job {:workflow [[:in :out]]
                  :catalog []
                  :lifecycles []
                  :windows []
                  :triggers []
                  :flow-conditions []
                  :task-scheduler :onyx.task-scheduler/balanced}]
    (-> base-job
        (add-task (kafka-task/consumer :in kafka-opts))
        (add-task (s3/s3-output :out s3-opts)))))

(defmethod register-job "event-s3-job"
  [job-name config]

  (let [onyx-batch-size (get-in config [:job-config :onyx-batch-size])
        kafka-topic-partitions (get-in config [:job-config :kafka-topic-partitions])
        kafka-opts     {:onyx/batch-size onyx-batch-size
                        :onyx/batch-timeout 1000
                        :onyx/type :input
                        :onyx/medium :kafka
                        :onyx/min-peers kafka-topic-partitions
                        :onyx/max-peers kafka-topic-partitions
                        :kafka/zookeeper (get-in config [:env-config :zookeeper/address])
                        :kafka/topic (get-in config [:job-config :kafka-topic])
                        :kafka/group-id "cds-event-s3"
                        :kafka/offset-reset :smallest
                        :kafka/commit-interval 500
                        :kafka/wrap-with-metadata? false
                        :kafka/force-reset? false
                        :kafka/deserializer-fn :kixi.event2s3.shared/deserialize-message
                        :onyx/doc "Reads messages from a Kafka topic"}
        s3-opts    {:s3/bucket (get-in config [:job-config :s3-bucket-name])
                    :s3/serializer-fn :kixi.event2s3.shared/gzip-serializer-fn
                    :s3/key-naming-fn :kixi.event2s3.shared/s3-naming-function
                    :onyx/type :output
                    :onyx/medium :s3
                    :onyx/min-peers 1
                    :onyx/max-peers 1
                    :onyx/batch-size onyx-batch-size
                    :onyx/batch-timeout (get-in config [:job-config :onyx-batch-timeout])
                    :onyx/doc "Writes segments to s3 files, one file per batch"}]
    (basic-job kafka-opts s3-opts (get-in config [:job-config :aws-region]))))
