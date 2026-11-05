#!/usr/bin/env bash
# 서버(~/kkari)에서 실행한다. CD 워크플로가 호출한다.
#   사용법: ./deploy.sh <backend 이미지 전체 이름:태그>
# 동작: 이미지 pull → .env 의 BACKEND_IMAGE 교체 → compose up -d → healthy 대기
#       실패하면 직전 이미지로 되돌린다.
set -euo pipefail

IMAGE="${1:?사용법: deploy.sh <image>}"
cd "$(dirname "$0")"

[ -f .env ] || { echo "::error::.env 가 없다 ($(pwd)/.env)"; exit 1; }

COMPOSE=(docker compose -f compose.prod.yaml)
PREV_IMAGE="$(grep -E '^BACKEND_IMAGE=' .env | tail -n 1 | cut -d= -f2- || true)"

set_image() {
  if grep -q '^BACKEND_IMAGE=' .env; then
    sed -i "s|^BACKEND_IMAGE=.*|BACKEND_IMAGE=$1|" .env
  else
    echo "BACKEND_IMAGE=$1" >> .env
  fi
}

wait_healthy() {
  local cid status
  for _ in $(seq 1 30); do
    cid="$("${COMPOSE[@]}" ps -q backend || true)"
    status="none"
    if [ -n "$cid" ]; then
      status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$cid" 2>/dev/null || echo none)"
    fi
    echo "backend health: $status"
    [ "$status" = "healthy" ] && return 0
    sleep 10
  done
  return 1
}

echo "== pull $IMAGE"
docker pull "$IMAGE"

echo "== up (이전 이미지: ${PREV_IMAGE:-없음})"
set_image "$IMAGE"
"${COMPOSE[@]}" up -d --remove-orphans

if wait_healthy; then
  echo "== 배포 성공"
  docker image prune -a -f --filter "until=72h" > /dev/null
  exit 0
fi

echo "::error::배포 실패 — backend 로그 마지막 50줄"
"${COMPOSE[@]}" logs --tail 50 backend || true

if [ -n "$PREV_IMAGE" ] && [ "$PREV_IMAGE" != "$IMAGE" ]; then
  echo "== 롤백: $PREV_IMAGE"
  set_image "$PREV_IMAGE"
  "${COMPOSE[@]}" up -d
  wait_healthy && echo "== 롤백 완료" || echo "::error::롤백 후에도 healthy 가 아니다"
else
  echo "== 되돌릴 이전 이미지가 없다"
fi
exit 1
