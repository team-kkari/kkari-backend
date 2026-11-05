#!/usr/bin/env bash
# 서버(~/kkari)에서 실행한다. CD 워크플로가 호출한다.
#   사용법: ./deploy.sh <backend 이미지 전체 이름:태그>
# 동작: 이미지 pull → .env 의 BACKEND_IMAGE 교체 → postgres·backend up -d → healthy 대기
#       실패하면 직전 이미지로 되돌린다. 성공하면 web(nginx)을 띄운다 (web 실패는 배포 실패가 아니다).
set -euo pipefail

IMAGE="${1:?사용법: deploy.sh <image>}"
cd "$(dirname "$0")"

[ -f .env ] || { echo "::error::.env 가 없다 ($(pwd)/.env)"; exit 1; }

# 프론트 빌드 결과가 올라갈 폴더. compose 가 만들면 root 소유가 되어 업로드가 막히므로 미리 만든다.
# 프론트가 올라오기 전에도 80 포트가 응답하도록 안내 페이지를 둔다 (프론트가 index.html 을 올리면 대체된다).
mkdir -p frontend
[ -e frontend/index.html ] || printf '%s\n' '<!doctype html><meta charset="utf-8"><title>kkari</title><p>프론트엔드 배포 전입니다.</p>' > frontend/index.html

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

# web(nginx)은 백엔드 배포의 성공·실패와 분리한다. 실패해도 경고만 남기고 백엔드 배포 결과에는 영향을 주지 않는다.
start_web() {
  if "${COMPOSE[@]}" up -d web; then
    # nginx 설정(compose 의 configs)이 바뀐 경우를 위해 끊김 없이 다시 읽는다.
    "${COMPOSE[@]}" exec -T web nginx -s reload > /dev/null 2>&1 \
      || echo "::warning::web(nginx) reload 실패"
  else
    echo "::warning::web(nginx) 기동 실패 — 백엔드 배포에는 영향이 없다"
  fi
}

echo "== pull $IMAGE"
docker pull "$IMAGE"

echo "== up (이전 이미지: ${PREV_IMAGE:-없음})"
set_image "$IMAGE"
"${COMPOSE[@]}" up -d --remove-orphans postgres backend

if wait_healthy; then
  echo "== 배포 성공"
  start_web
  docker image prune -a -f --filter "until=72h" > /dev/null
  exit 0
fi

echo "::error::배포 실패 — backend 로그 마지막 50줄"
"${COMPOSE[@]}" logs --tail 50 backend || true

if [ -n "$PREV_IMAGE" ] && [ "$PREV_IMAGE" != "$IMAGE" ]; then
  echo "== 롤백: $PREV_IMAGE"
  set_image "$PREV_IMAGE"
  "${COMPOSE[@]}" up -d postgres backend
  wait_healthy && echo "== 롤백 완료" || echo "::error::롤백 후에도 healthy 가 아니다"
else
  echo "== 되돌릴 이전 이미지가 없다"
fi
exit 1
