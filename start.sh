#!/bin/sh
# FORWARD_PROXY_URL 이 있으면 gost 사이드카를 띄워 한국 IP 경유 로컬 프록시(127.0.0.1:18080)를 연다
if [ -n "$FORWARD_PROXY_URL" ]; then
  gost -L 127.0.0.1:18080 -F "$FORWARD_PROXY_URL" &
fi
exec java -jar app.jar
