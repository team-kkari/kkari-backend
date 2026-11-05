# FE 연동 계약

Swagger의 현재 코드 기준 계약을 정리한다. 입력 통화는 USD/KRW, 비용·비교 결과의 표시 기준은 USD다. 원본 입력 금액·통화와 미수집 상태를 보존한다.

## 계정

- `GET /v1/users/me`: 로그인한 엔지니어·고객의 `{id, email, name, companyName, role}`. 비로그인은 401이며 password는 반환하지 않는다.
- 가입 이메일은 trim·Locale.ROOT 소문자화 후 저장하고 중복 검사한다. 동시 중복 가입은 409 U002다.
- 단일 계정 로그인은 이메일 대소문자를 구분하지 않는다. 기존에 대소문자만 다른 계정들이 공존하면 가입 당시 이메일 표기로 구분한다. 모호한 표기는 임의 계정을 선택하지 않고 401 A001로 처리한다.
- 비밀번호는 영문·숫자를 각각 포함하는 8~64자다. 신규 저장은 PBKDF2, 기존 raw BCrypt 및 `{bcrypt}` 해시는 기존 방식으로 검증한다.

### 비밀번호 저장 컬럼

비밀번호 컬럼은 varchar(255)가 필요하다. 스키마 자동 갱신을 쓰지 않는 환경에서는 새 코드 반영 전에 [컬럼 확장 SQL](migration/2026-11-05-password-hash-storage-v1.sql)을 적용한다. 기존 해시 값은 바꾸지 않는다. 신규 해시가 만들어진 뒤에는 PBKDF2 검증을 지원하는 코드로 운영·롤백해야 한다.

## 상담 및 프로젝트 재진입

- 상담 POST·GET의 `missingFields`는 profile과 같은 camelCase 식별자다. `missing`은 기존 한국어 표시명이며 AI·DB 원본은 유지한다.
- 유효한 `profile.companyName`은 프로젝트 고객사명에 반영한다. null·공백·100자 초과 값은 기존 이름을 지우지 않는다.
- 요구사항은 상담 profile로 자동 확정하지 않는다. 기존 `requirementsDraft`를 사용자가 확인하고 `PUT /v1/projects/{id}/requirements`로 저장하는 흐름을 유지한다.
- 프로젝트의 서로 다른 필드 수정은 변경 컬럼만 갱신하여, 상담 고객사명과 동시 저장한 요구사항이 서로 덮이지 않게 한다.
- 프로젝트 상세에 `terraformUploaded`와 `terraform`이 추가된다. Terraform에는 `fileName`, `size`(byte), `uploadedAt`이 포함된다. 업로드 전에는 false/null이며, 기존 업로드는 true여도 메타데이터만 null일 수 있다.
- Terraform 파일은 파싱만 하며 원본·비밀 속성을 저장하지 않는다. 잘못된 재업로드는 기존 결과와 메타데이터를 유지한다.

## 예산과 비용

- 요구사항의 `monthlyBudget`은 원본 입력 금액, `currency`는 USD 또는 KRW다. 양수 예산에는 통화 선택이 필수다.
- null 예산은 미확인, 0은 예산 상한 없음이다. 이 둘을 같은 값으로 바꾸지 않는다.
- 신규 미지원 통화 또는 양수 예산의 통화 누락은 400 C001이다. 기존 데이터의 미지원 통화·통화 없는 양수 예산은 새 설계 입력 생성 전에 400 AR003으로 처리한다. 해당 요청으로 작업이나 AI 호출을 만들지 않는다.
- 설계 요청에는 저장된 요구사항의 `constraints`와 완전하게 집계된 양수 분석 비용 `current_monthly_usd`를 전달한다. 부분 비용·미수집·0인 현재 비용은 AI 요청에서 생략한다.
- `GET /v1/projects`의 `monthlyCost`는 최근 성공한 ANALYSIS 작업의 월 예상 비용 합(USD)이다. 미수집·불완전 비용은 null이며, 알려진 실제 0은 0으로 유지한다.
- KRW 입력은 원래 값과 통화를 보존한다. USD 표시를 위해 통화 이름만 바꾸지 않는다. 현재 AI 환산 기준은 고정 1,400원/$이며 현재 시장 환율 조회값이 아니다.
- FE 정보 패널·확인 카드의 표시 금액은 USD로 정리해야 한다. 기존 목업의 원화 문자열을 그대로 표시하는 경로와 기획 문서의 KRW 표시 규칙은 후속 정리 대상이다.

## 설계 결과와 최근 작업

`GET /v1/projects/{id}/architectures`의 200 응답:

```json
{
  "jobId": 11,
  "generatedAt": "2026-11-05T18:00:00+09:00",
  "result": {"overall": "이전 성공 결과", "architectures": []},
  "latestJob": {"jobId": 12, "status": "RUNNING", "failureReason": null}
}
```

- `jobId`는 최근 성공 결과, `latestJob`은 가장 최근 설계 작업이다. 두 ID는 다를 수 있다.
- 성공 결과가 아직 없으면 404 AR002에도 `latestJob`을 포함한다. 작업을 시작한 적이 없으면 null이다.
- 새 작업 실패 시 이전 성공 결과를 유지한다. 복원한 ID로 기존 `GET /v1/jobs/{jobId}`를 사용할 수 있다.
- 최신 작업 요약을 엔티티 캐시에 넣지 않고 읽어, 두 조회 사이 작업이 완료돼도 성공 결과를 정상 반환한다.
- 404 오류 Swagger는 중첩되는 오류 구조를 `anyOf`로 표현한다.

## 시각

프로젝트 목록 `updatedAt`, 상세 `createdAt`·`updatedAt`, 설계 `generatedAt`은 ISO 8601 offset을 포함한다. UTC 서버는 Z, KST 서버는 +09:00이다. 기존 DB의 서버 로컬 시각을 해당 서버 시간대로 해석하며 DB 시각 데이터를 이 변경에서 변환하지 않는다.

## 검증 범위

Java 21 전체 build와 회귀 테스트, 로컬 PostgreSQL 17·앱 HTTP·AI 스텁으로 검증한다. 설계 완료/조회 경합은 실제 JPA 두 트랜잭션을 latch로 제어하고, 프로젝트 동시 저장은 두 저장 순서 및 PostgreSQL 잠금 대기로 확인한다. 실제 AI/LLM·AWS·운영 DB 호출은 검증 범위에 포함하지 않는다.
