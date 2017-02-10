#!/usr/bin/env bash

# replace dots with hyphens in APP_NAME
IMAGE_PREFIX=mastodonc
APP_NAME=kixi.event2s3
ENVIRONMENT=$1
INSTANCE_COUNT=$2
TAG=$3
ONYX_BATCH_SIZE=$4
SYSTEM_PROFILE=production
S3_BUCKET=$5

# using deployment service sebastopol
VPC=sandpit
sed -e "s/@@TAG@@/$TAG/" -e "s/@@S3_BUCKET@@/$S3_BUCKET/" -e "s/@@ENVIRONMENT@@/$ENVIRONMENT/" -e "s/@@VPC@@/$VPC/" -e "s/@@CANARY@@/$CANARY/" -e "s/@@APP_NAME@@/$APP_NAME/" -e "s/@@IMAGE_PREFIX@@/$IMAGE_PREFIX/"  -e "s/@@INSTANCE_COUNT@@/$INSTANCE_COUNT/" -e "s/@@SYSTEM_PROFILE@@/$SYSTEM_PROFILE/" -e "s/@@ONYX_BATCH_SIZE@@/$ONYX_BATCH_SIZE/" ./deployment/marathon-config.json.template > $APP_NAME.json

# we want curl to output something we can use to indicate success/failure
STATUS=$(curl -s -w "%{http_code}" -X POST http://$MASTER_URL/marathon/v2/apps/$APP_NAME -H "Content-Type: application/json" --data-binary "@$APP_NAME.json" | sed 's/.*\(...\)/\1/')

echo "HTTP code " $STATUS
if [ $STATUS -eq 201 ]
then exit 0
else exit 1
fi
