#!/usr/bin/env bash
# Redeploys the notification container on the EC2 host.
# Run as root on the instance (CD sends it through SSM Run Command).
# Requires: IMAGE_TAG env var; instance role with ECR read + SSM GetParameter.
set -euo pipefail

REGION=ap-southeast-1
ACCOUNT_ID=529088301474
REGISTRY="$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com"
IMAGE="$REGISTRY/leetcode-ted-notification:${IMAGE_TAG:?Set IMAGE_TAG}"
NAME=notification

param() {
  aws ssm get-parameter --name "$1" --with-decryption --query Parameter.Value --output text --region "$REGION"
}

start() {
  docker run -d \
    --name "$NAME" \
    --restart unless-stopped \
    -e AWS_REGION="$REGION" \
    -e MONGODB_URI="$(param /leetcode-ted/mongodb-uri)" \
    -e SQS_QUEUE_URL="$(param /leetcode-ted/sqs-queue-url)" \
    -e ENABLE_SQS_NOTIFICATIONS=true \
    -e SOLVE_POLL_INTERVAL_MS=10000 \
    "$1" >/dev/null
}

wait_started() {
  for _ in $(seq 1 24); do
    if docker logs "$NAME" 2>&1 | grep -q "Started NotificationApplication"; then
      return 0
    fi
    if [ "$(docker inspect -f '{{.State.Running}}' "$NAME" 2>/dev/null)" != "true" ]; then
      return 1
    fi
    sleep 5
  done
  return 1
}

aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY" >/dev/null
docker pull -q "$IMAGE"

PREVIOUS=$(docker inspect -f '{{.Image}}' "$NAME" 2>/dev/null || true)

# t4g.nano has 512 MB: the old container must stop before the new JVM starts.
docker rm -f "$NAME" >/dev/null 2>&1 || true
start "$IMAGE"

if wait_started; then
  echo "Deployed $IMAGE"
  docker image prune -af --filter "until=72h" >/dev/null || true
  exit 0
fi

echo "New container did not start; last logs:" >&2
docker logs --tail 40 "$NAME" >&2 || true
if [ -n "$PREVIOUS" ]; then
  echo "Rolling back to $PREVIOUS" >&2
  docker rm -f "$NAME" >/dev/null 2>&1 || true
  start "$PREVIOUS"
fi
exit 1
