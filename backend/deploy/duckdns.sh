#!/usr/bin/env bash
# Points wukode.duckdns.org at this instance's public IPv4 on boot and clears
# the record on shutdown, so clients fail fast instead of reaching a recycled IP.
# Installed as /usr/local/bin/duckdns.sh and driven by duckdns.service.
# Requires: IMDSv2; instance role with ssm:GetParameter on $TOKEN_PARAM.
set -euo pipefail

REGION=ap-southeast-1
DOMAIN=wukode
TOKEN_PARAM=/leetcode-ted/duckdns-token
IMDS=http://169.254.169.254/latest

duckdns_token() {
  aws ssm get-parameter --name "$TOKEN_PARAM" --with-decryption \
    --query Parameter.Value --output text --region "$REGION"
}

public_ip() {
  local imds_token
  imds_token=$(curl -sf --max-time 5 -X PUT "$IMDS/api/token" \
    -H "X-aws-ec2-metadata-token-ttl-seconds: 60")
  curl -sf --max-time 5 -H "X-aws-ec2-metadata-token: $imds_token" "$IMDS/meta-data/public-ipv4"
}

# Sends one DuckDNS request. The URL (with the token) goes to curl on stdin so it
# never shows up in the process list. Succeeds only on an "OK" body.
call_duckdns() {
  local query=$1 token response
  token=$(duckdns_token) || return 1
  response=$(printf 'url = "https://www.duckdns.org/update?domains=%s&token=%s&%s"\n' \
    "$DOMAIN" "$token" "$query" | curl -sf --max-time 10 -K -) || return 1
  [ "$response" = "OK" ]
}

update() {
  local attempt ip
  for attempt in $(seq 1 10); do
    if ip=$(public_ip) && call_duckdns "ip=$ip"; then
      echo "$DOMAIN.duckdns.org -> $ip"
      return 0
    fi
    echo "DuckDNS update attempt $attempt failed; retrying in 15s" >&2
    sleep 15
  done
  return 1
}

clear_record() {
  local attempt
  for attempt in 1 2 3; do
    if call_duckdns "clear=true"; then
      echo "$DOMAIN.duckdns.org cleared"
      return 0
    fi
    echo "DuckDNS clear attempt $attempt failed" >&2
    sleep 2
  done
  return 1
}

case "${1:-}" in
  update) update ;;
  clear) clear_record ;;
  *) echo "Usage: $0 update|clear" >&2; exit 2 ;;
esac
