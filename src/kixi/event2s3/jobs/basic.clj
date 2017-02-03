(ns kixi.event2s3.jobs.basic
  (:require [onyx.job :refer [add-task register-job]]
            [onyx.tasks.core-async :as core-async-task]
            [environ.core :refer [env]]
            [onyx.plugin.kafka]
            [onyx.plugin.s3-output]
            [onyx.plugin.s3-utils :as s3-utils]
            [onyx.tasks.kafka :as kafka-task]
            [onyx.tasks.s3 :as s3]))

(defn basic-job
  [kafka-opts s3-opts]
  (let [aws-client (s3-utils/set-region (s3-utils/new-client) (env :aws-region))
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

(defmethod register-job "basic-job"
  [job-name config]
  (let [kafka-opts     {:onyx/batch-size (env :onyx-batch-size)
                        :onyx/batch-timeout 1000
                        :onyx/min-peers (env :kafka-topic-partitions)
                        :onyx/max-peers (env :kafka-topic-partitions)
                        :kafka/zookeeper (get-in config [:env-config :zookeeper/address])
                        :kafka/topic (env :kafka-topic)
                        :kafka/group-id "onyx-event-backup"
                        :kafka/fetch-size 307200
                        :kafka/chan-capacity 1000
                        :kafka/offset-reset :smallest
                        :kafka/force-reset? true
                        :kafka/empty-read-back-off 500
                        :kafka/commit-interval 500
                        :kafka/deserializer-fn :kixi.event2s3.shared/deserialize-message
                        :onyx/doc "Reads messages from a Kafka topic"}
        s3-opts    {:s3/bucket (env :s3-bucket-name)
                    :s3/serializer-fn :kixi.event2s3.shared/gzip-serializer-fn
                    :s3/key-naming-fn :kixi.event2s3.shared/s3-naming-function
                    :onyx/type :output
                    :onyx/medium :s3
                    :onyx/min-peers 1
                    :onyx/max-peers 1
                    :onyx/batch-size (env :onyx-batch-size)
                    :onyx/batch-timeout 1000
                    :onyx/doc "Writes segments to s3 files, one file per batch"}]
    (basic-job kafka-opts s3-opts)))
