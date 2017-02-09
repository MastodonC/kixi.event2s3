# kixi.event2s3

## Requirements

There are a number of environment variables that are required before the job is deployed.

### AWS Credentials

```
AWS_ACCESS_KEY_ID=[Insert your access key]
AWS_SECRET_KEY=[Your secret key]
```

You also require the AWS region to be set:

```
AWS_REGION=[eu-central-1 or eu-west-1 for example]
```

If not set AWSS3Client will assume the default profile in the ~/.aws/credentials file.

### S3 Bucket

```
S3_BUCKET_NAME=[The bucket you want to use]
```

### Kafka

```
KAFKA_TOPIC=The topic name you want to store.
KAFKA_TOPIC_PARTITIONS=The number of Kafka partitions for the topic.
```

### Onyx

```
ONYX_BATCH_SIZE=[The number of messages you want to batch before processing/file bundling happens]
```


## Build

```
lein uberjar
```

## Execution (Local)

```
java -cp target/peer.jar kixi.event2s3.core start-peers 10 -c resources/config.edn -p :default
```


The assumption is that Zookeeper is already running so Onyx will NOT create an in memory version for you.
