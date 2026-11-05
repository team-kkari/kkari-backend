# 챗봇 상담 API

## 기준

- [사용자 플로우](https://app.notion.com/p/40e4a7cd859380e182abfd9dac28b6d5) 1-1, 1-2, 1-4
- [기능 명세서](https://app.notion.com/p/e1b4a7cd85938204b7e901a0bcb0d0a9): AI 챗봇 대화·요구사항 수집, 수집 정보 확인
- AI 계약: [kkari-AI main `0eca9ef`](https://github.com/team-kkari/kkari-AI/tree/0eca9ef03382096df2bbd752f8e6f208c7f98948), `server.py`의 `/intake`, `intake.py`의 `Profile`·`Intake.turn`, `errors.py`

명세 확인일: 2026-11-05. 명세의 `engagements`는 현재 백엔드의 `projects`에 해당한다. 아래 대화 API 경로는 프론트와 최종 합의가 필요하다.

## 요청과 복원

`POST /v1/projects/{projectId}/chat/messages`

```json
{"content":"월 예산은 300만 원입니다","clientMessageId":"client-generated-id"}
```

- 담당 프로젝트만 접근할 수 있다. 메시지·선택지·상담 결과를 프로젝트별로 저장한다.
- `{}`로 첫 질문을 받거나 마지막 사용자 메시지의 실패한 AI 응답을 재요청한다. 분석 요약이 바뀌면 `{}`로 추가 확인 질문을 요청할 수 있다.
- 같은 식별자와 같은 내용의 재전송은 저장된 답변을 반환한다. 이후 대화가 진행되어도 성공한 요청의 원래 답변을 반환한다.
- 같은 식별자로 내용을 바꾸면 `409 CH003`. 실패한 옛 메시지 뒤에 다른 대화가 진행되면 `409 CH001`. 동시 전송으로 답변 순번을 확보하지 못하면 `409 CH002`를 반환하며 대화 조회로 현재 상태를 복원한다.
- AI 실패에도 저장된 사용자 메시지는 남는다. 자동으로 AI 호출을 반복하지 않는다.

`GET /v1/projects/{projectId}/chat/messages`는 순서대로 `messages`와 마지막 AI 상담 결과를 반환한다. 전송 응답에는 `userMessage`·`assistantMessage`가 들어간다. 두 응답 모두 다음 상담 결과를 제공한다.

| 필드 | 의미 |
| --- | --- |
| `profile` | AI 프로필을 camelCase로 변환한 값 |
| `missingFields` | `monthly_budget` 등 AI의 필드 식별자 |
| `missing` | `월 예산` 등 AI의 표시 문구. 기존 저장 행에는 null일 수 있음 |
| `ready` | AI가 상담을 마치고 진단 입력을 만들었는지 여부 |

AI에는 매번 전체 대화(`user`/`assistant`), 최근 성공 AWS 분석과 Terraform의 `infra_context`, `session_id=project-{id}`를 보낸다. `required_fields`는 보내지 않아 AI 기본 질문 목록을 사용한다.

## AI 오류

상담 API의 AI 오류 응답은 기존 `timestamp`·`status`·`code`·`message`에 `retryable`을 추가한다. 권한·검증·대화 충돌 등 다른 오류는 기존 공통 오류 형식을 따른다.

| 백엔드 응답 | 조건 | `retryable` |
| --- | --- | --- |
| `503 AI003` | AI 오류 본문의 `retryable=true`, 또는 비 JSON 429/503 | true |
| `504 AI002` | AI 응답 읽기 시간 초과 | true |
| `502 AI001` | AI가 재시도 불가로 응답, 연결 실패, 파싱 실패, 빈 답변 등 | false |

AI 본문의 메시지와 파싱 실패 응답 원문은 로그에 남기지 않는다. 클라이언트는 `retryable`을 참고해 재시도 UI를 제공하고, 충돌 시 대화를 새로 조회한다.

## 최종 확인과 후속 작업

- AI는 사용자가 바로 진단을 원하면 `missingFields`가 남아 있어도 `ready=true`를 반환한다. 두 값을 독립적으로 보존한다. `ready`는 사용자 최종 확인이나 설계 시작 권한을 뜻하지 않는다.
- 미확인 값 `null`과 확인된 값 `monthlyBudget=0`(예산 상한 없음), `containsPersonalData=false`, `frozenResources=[]`를 구분한다.
- AI 전체 응답은 `ai_response` JSON에 보존한다. 새 프로필 필드나 추가 결과도 원본에 남는다. 기존 `requirements`는 덮어쓰지 않는다. 프론트용 기존 프로필 응답은 알려진 필드를 camelCase로 제공한다.
- `recommend_request` 객체는 `constraints`를 포함해 그대로 저장한다. 생략 또는 JSON null은 SQL NULL로 저장한다. AI 원본과 이 값은 현재 프론트 응답에는 노출하지 않는다.
- AI `architecture`는 **현재 구성**이며 요구사항의 `desiredArchitecture`와 같은 필드가 아니다. AI `cost`도 현재 비용 설명이며 월 예산이 아니다.
- [PR 리뷰 결정](https://github.com/team-kkari/kkari-backend/pull/35#pullrequestreview-5779944148)에 따라 `profile → requirements` 자동 변환은 구현하지 않는다. 두 형식은 의미와 필드가 다르므로 AI 원본과 기존 사용자 입력을 각각 보존한다. 담당자·원하는 구조 수집, 사용자 정정, 분석 완료·누락 검증, 최종 확인한 요구사항·분석 버전 고정과 설계 호출은 후속 작업이다. 기존 `PUT /v1/projects/{projectId}/requirements`를 대체하지 않는다.
- 검증 오류의 기존 `errors[].value` 필드는 유지한다. 비밀번호와 상담 입력의 값은 null로 반환하고 원문을 로그에 남기지 않는다.
- #33 통합 시 공통 AI Client·오류 코드·테스트 충돌과 `AnalysisFacade`의 분석 타입 조건을 함께 맞춰야 한다. 동시 상담 시작은 저장 1건을 보장하지만 AI 호출이 여러 번 발생할 수 있다.
