#!/usr/bin/env bash
# Deploy the email consumer Lambda.
# Run from backend/lambda/:  ./deploy.sh
# Requires: AWS CLI configured, ACCOUNT_ID and SES_SENDER_EMAIL set.
set -euo pipefail

FUNCTION_NAME=leetcode-ted-email-consumer
REGION=ap-southeast-1
RUNTIME=nodejs20.x
HANDLER=index.handler
ROLE_NAME=leetcode-ted-email-consumer-role
ACCOUNT_ID=${ACCOUNT_ID:?Set ACCOUNT_ID env var}
SES_SENDER_EMAIL=${SES_SENDER_EMAIL:?Set SES_SENDER_EMAIL env var}
SQS_QUEUE_ARN=arn:aws:sqs:${REGION}:${ACCOUNT_ID}:leetcode-ted-notifications

ZIP=function.zip
zip -j "$ZIP" index.mjs package.json

# ── IAM role (create once, skip if exists) ────────────────────────────────────
if ! aws iam get-role --role-name "$ROLE_NAME" --region "$REGION" &>/dev/null; then
  echo "Creating IAM role $ROLE_NAME..."

  aws iam create-role \
    --role-name "$ROLE_NAME" \
    --assume-role-policy-document '{
      "Version":"2012-10-17",
      "Statement":[{
        "Effect":"Allow",
        "Principal":{"Service":"lambda.amazonaws.com"},
        "Action":"sts:AssumeRole"
      }]
    }'

  aws iam attach-role-policy \
    --role-name "$ROLE_NAME" \
    --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole

  aws iam put-role-policy \
    --role-name "$ROLE_NAME" \
    --policy-name SESAndSQS \
    --policy-document "{
      \"Version\":\"2012-10-17\",
      \"Statement\":[
        {
          \"Effect\":\"Allow\",
          \"Action\":[\"ses:SendEmail\"],
          \"Resource\":\"*\"
        },
        {
          \"Effect\":\"Allow\",
          \"Action\":[\"sqs:ReceiveMessage\",\"sqs:DeleteMessage\",\"sqs:GetQueueAttributes\"],
          \"Resource\":\"$SQS_QUEUE_ARN\"
        }
      ]
    }"

  echo "Waiting for role to propagate..."
  sleep 12
fi

ROLE_ARN=arn:aws:iam::${ACCOUNT_ID}:role/${ROLE_NAME}

# ── Lambda function (create or update) ───────────────────────────────────────
if aws lambda get-function --function-name "$FUNCTION_NAME" --region "$REGION" &>/dev/null; then
  echo "Updating Lambda function code..."
  aws lambda update-function-code \
    --function-name "$FUNCTION_NAME" \
    --zip-file fileb://"$ZIP" \
    --region "$REGION"

  echo "Updating environment variables..."
  aws lambda update-function-configuration \
    --function-name "$FUNCTION_NAME" \
    --environment "Variables={SES_SENDER_EMAIL=$SES_SENDER_EMAIL}" \
    --region "$REGION"
else
  echo "Creating Lambda function..."
  aws lambda create-function \
    --function-name "$FUNCTION_NAME" \
    --runtime "$RUNTIME" \
    --handler "$HANDLER" \
    --role "$ROLE_ARN" \
    --zip-file fileb://"$ZIP" \
    --timeout 30 \
    --memory-size 256 \
    --environment "Variables={SES_SENDER_EMAIL=$SES_SENDER_EMAIL}" \
    --region "$REGION"

  echo "Waiting for function to be active..."
  aws lambda wait function-active \
    --function-name "$FUNCTION_NAME" \
    --region "$REGION"

  # ── SQS trigger ────────────────────────────────────────────────────────────
  echo "Adding SQS trigger..."
  aws lambda create-event-source-mapping \
    --function-name "$FUNCTION_NAME" \
    --event-source-arn "$SQS_QUEUE_ARN" \
    --batch-size 10 \
    --function-response-types ReportBatchItemFailures \
    --region "$REGION"
fi

rm "$ZIP"
echo "Done. Lambda $FUNCTION_NAME deployed."
