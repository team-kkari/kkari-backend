# kkariMarket 의뢰 인프라 구축·검증 기록

- 작성일: 2026-11-05
- 상태: **철거 완료.** 1차 배포(2026-11-05 09:46~10:06 KST)와 2차 배포(10:18~10:28 KST)를 모두 `terraform destroy`로 지웠고, 현재 AWS 계정에 kkariMarket 리소스는 없다. 다시 필요하면 같은 코드로 재배포한다.
- 목적: Wayco 서비스가 "의뢰받은 AWS 인프라"를 읽고 분석하는 기능을 검증·시연하기 위한 가상 고객사 인프라
- 코드 위치: `infra/kkarimarket/` (Terraform)
- 대상 계정: 개인 계정 (계정 ID는 공개 저장소라 적지 않음, 서울 `ap-northeast-2`). 해커톤 서버 계정이 아니다.

## 1. 컨셉 요약

**kkariMarket**은 블랙 프라이데이를 앞둔 가상의 중소 온라인 쇼핑몰이다. 일부러 약점을 넣었다.

| 약점 | 리소스 |
|---|---|
| 단일 장애점 (서버 1대, 단일 AZ) | `kkarimarket-prod-web-1` |
| DB 단일 AZ, 개인정보 포함 | `kkarimarket-prod-db` |
| 과대 스펙, 평시 거의 유휴 | `kkarimarket-prod-batch-1` |
| 24시간 켜진 개발 서버 | `kkarimarket-dev-app-1` |
| 어디에도 안 붙은 볼륨 | `kkarimarket-prod-orphan-vol` |

이중화, 로드밸런서, 오토스케일링은 **만들지 않았다.** Wayco가 설계안으로 제안할 내용이다.

## 2. 구축했던 리소스 (12개, 현재는 철거됨)

| 리소스 | 사양 | 비고 |
|---|---|---|
| EC2 `kkarimarket-prod-web-1` | `t3.small`, 2a | 부하 약 17%, 태그 `role=web` |
| EC2 `kkarimarket-prod-batch-1` | `m7i-flex.large`, 2a | 평시 약 4%, 새벽 02:00~02:20 KST 약 90% (`batch_nightly_peak=true`) |
| EC2 `kkarimarket-dev-app-1` | `t3.micro`, 2a | 평일 09~18시 KST 약 7%, 그 외 거의 0% |
| RDS `kkarimarket-prod-db` | `db.t3.micro`, PostgreSQL 16.15, gp3 20GiB | 단일 AZ, 비공개, 암호화, 백업 1일, 태그 `do-not-change=true` |
| EBS `kkarimarket-prod-orphan-vol` | gp3 8GiB | 미부착 |
| 보안그룹 3개 | `prod-app`, `dev-app`, `prod-db` | 인바운드는 DB의 5432 (앱 SG에서만) 하나뿐 |
| SG 규칙 2개, DB 서브넷 그룹 1개, `random_password` 1개 | | 비밀번호는 로컬 state에만 존재 |

- 전 리소스 공통: 기본 VPC, **공인 IP 없음, NAT 없음, 키페어 없음, 인스턴스 프로파일 없음**, IMDSv2 필수, EBS·RDS 암호화.
- 접속 수단이 없으므로 서버 내부에는 들어갈 수 없다. 관측은 CloudWatch와 AWS API로만 한다.
- 이름 규칙 `<프로젝트>-<환경>-<리소스>`, 필수 태그 `project`, `env`, `owner`를 지켰다. 값은 `project=kkarimarket`, `owner=whiyoung`.

## 3. 구축 과정

1. 서울 리전의 인스턴스 타입(4개 AZ 모두 제공)과 RDS 옵션(PostgreSQL 16.15 + `db.t3.micro`)을 읽기 전용으로 확인.
2. Terraform 작성: `terraform.tf`, `providers.tf`, `main.tf`, `variables.tf`, `outputs.tf` + `templates/load-generator.sh.tftpl`(CPU 부하 패턴).
3. 서브에이전트 코드 검토 2회:
   - 1차 `CHANGES REQUESTED` (blocker 1: 비용, major 4, minor 다수) → SG 규칙 `env` 태그, systemd `After=` 순환 위험, `batch_peak` 때문에 web·dev가 재생성되는 문제 등을 수정.
   - 2차 코드 품질 `APPROVE`. 비용은 "사용자 승인 후 apply" 조건부.
4. 사용자 승인(플랜 유료·카드 연결, 한도 약 $20, root 변경 허용) 후 `terraform apply`. 시작 2026-11-05 00:46 UTC (09:46 KST), 정상 종료.
5. 검증을 마치고 10:06 KST에 `terraform destroy`가 실행되어 1차 배포가 철거됐다(CloudTrail 기준 10:06:16, 실행 주체는 확인하지 못함). 철거 후 EC2(종료 상태 3대 제외 0), EBS, RDS, SG, ENI가 0개임을 `aws` 조회로 확인.
6. 이어서 **다른 Claude 세션이 같은 디렉터리에서 같은 계획 파일로 `terraform apply`를 시작했다**(10:18:49 KST, 2차 배포). 두 세션이 같은 인프라를 동시에 다루게 되어 사용자가 다른 세션을 종료했고, 인스턴스 ID는 1차와 다르다.
7. 사용자 지시로 2차 배포의 `apply` 완료를 기다린 뒤 10:26 KST에 `terraform destroy`를 실행했다(12개 삭제, 약 2분). 철거 후 EC2, EBS, RDS, ENI, `kkarimarket` SG가 모두 0개이고 state도 비어 있음을 확인했다. RDS 스냅샷 1개가 철거 직후 한 번 조회됐다가 1분 뒤 사라졌다(원인은 확인하지 못함, RDS 삭제 정리 지연으로 추정).

## 4. 검증 결과

아래 검증은 모두 **1차 배포** 기준이다. 2차 배포는 검증 없이 철거했다.

| # | 항목 | 방법 | 결과 |
|---|---|---|---|
| 1 | 구성 일치 | `apply` 직후 `terraform plan` | **통과.** No changes (영구 diff 없음) |
| 2 | 인스턴스 | `describe-instances` | **통과.** 3대 `running`, 모두 `ap-northeast-2a`, 공인 IP 없음, IMDSv2 `required` |
| 3 | 태그 | `describe-instances`, `describe-volumes`, RDS 태그 조회 | **통과.** `project`, `env`, `owner` 전부 존재, 인스턴스 `role` 태그 `web/batch/dev` |
| 4 | 볼륨 | `describe-volumes` | **통과.** 루트 3개 `in-use`, 고아 볼륨만 `available`(미부착), 전부 gp3·암호화 |
| 5 | 네트워크 | SG 규칙 조회 | **통과.** dev SG는 규칙 없음, prod-app은 DB로 나가는 5432만, DB는 앱 SG에서 들어오는 5432만 |
| 6 | RDS | `describe-db-instances` | **통과.** `available`, MultiAZ=false, PubliclyAccessible=false, 암호화, 백업 1일 |
| 7 | 부하 서비스 기동 | 콘솔 출력 | **통과.** `cloud-init finished`, `kkarimarket-load.service` 활성화 확인 |
| 8 | CPU 패턴 (09:54 KST) | CloudWatch `CPUUtilization` 5분 평균 | **통과.** web 17.6%, batch 4.3%, dev 7.8% (평일 근무시간 값) |
| 9 | 재시작 복원 | dev stop → start 후 10분 관찰 | **통과.** 재시작 후 서비스 자동 복구, 10:00 KST 구간 CPU 7.8%, 공인 IP 없음 유지 |
| 10 | RDS 지표 | CloudWatch `AWS/RDS` | **통과.** CPU 5.1% 수집됨 |

### 검증 중 발견한 점

- **재시작 검증 스크립트의 대기 실패**: `start-instances` 직후 `wait instance-running`이 "stopping을 봤다"며 실패했다. 이후 인스턴스는 정상 `running`이었고 서비스도 복구됐다. 원인은 시작 직후 EC2 조회 지연으로 **추정**하며 확정하지 못했다. 인프라 문제는 아니다.
- **CPU 크레딧 잔액이 매우 낮음**: web 0.9, dev 0.5 (10:06 KST 기준). t3를 `standard` 모드로 띄워서 시작 크레딧이 거의 없고 소비가 적립에 근접하기 때문으로 **추정**한다. 평균 사용률이 기준선(t3.small 20%, t3.micro 10%) 아래라 성능 제한은 없을 것으로 보지만, 문서로 확인하지는 못했다. 철거되어 잔액 추이는 확인하지 못했다.
- 서버 안에서 부하 스크립트를 직접 볼 수 없어서(접속 수단 없음), 동작 확인은 CloudWatch 지표와 콘솔 출력으로만 했다.

## 5. 비용

- 단가는 AWS Pricing API로 서울 온디맨드 가격을 조회했다. 스토리지도 조회했다 (EBS gp3 $0.0912/GB-월 × 32GB, RDS gp3 $0.131/GB-월 × 20GB). 사용 시간으로 곱한 **예상값**이며 실제 청구액이 아니다.

| 항목 | 시간당 |
|---|---|
| `m7i-flex.large` | $0.1177 |
| `t3.small` | $0.0260 |
| `t3.micro` | $0.0130 |
| RDS `db.t3.micro` (PostgreSQL, 단일 AZ) | $0.0280 |
| **컴퓨트 합계** | **$0.1847 (일 $4.43)** |

- 스토리지 포함 **하루 약 $4.62**, 3일 약 $14, 약 4.3일에 $20.
- 계정은 유료 플랜이며 카드가 연결되어 있다. 크레딧 차감 여부는 확인하지 않았다. 실제 청구액은 하루 뒤 Billing에서 확인해야 한다 (비용 데이터는 지연됨).
- 공인 IPv4, NAT, 데이터 전송 비용은 설계상 발생하지 않는다.

## 6. 확인하지 못한 것

철거되어 아래 항목은 확인하지 못했다. 필요하면 재배포 후 해당 시점에 확인한다.

| 항목 | 시점 | 기대 |
|---|---|---|
| batch 새벽 피크 | 재배포 후 첫 02:00~02:20 KST | CPU 약 90% 구간이 보인다 |
| CPU 크레딧 추이 | 재배포 후 하루 뒤 | web·dev 잔액이 0에 고정되거나 성능 제한이 없다 |
| 실제 청구액 | 재배포 후 하루 뒤 | 예측 $4.62/일과 크게 어긋나지 않는다 (이번 배포는 합계 약 40분 가동이라 청구액은 소액일 것으로 예상, 미확인) |
| 평일/주말 패턴 (dev) | 주말 포함 관측 | 주말에는 CPU가 거의 0% |

- 아직 만들지 않은 것: 고객사용 읽기 Role(CloudFormation 템플릿), 서비스용 IAM 사용자(A안), 이 인프라에 대한 AssumeRole·조회 테스트. 이 문서의 범위 밖이다.

## 7. 알려진 한계

- 사용 기간이 짧아서 CloudWatch 이력이 얇다. 서비스의 24×7 사용률 분석 시연에는 더 긴 관측이 필요하다.
- 공인 IP와 인바운드가 없어서 web의 `NetworkIn`이 거의 0이다. 서비스가 "트래픽 없는 서버"로 해석할 수 있으므로 시연에서 설명이 필요하다.
- batch는 새벽 피크가 있어서 "평균이 낮으니 줄인다"는 단순 결론이 되지 않는다. 의도한 장면이지만 팀 확정 전이다 (`batch_nightly_peak`).
- `user_data`를 바꾸면 해당 인스턴스가 재생성되어 CloudWatch 이력이 초기화된다. 이력이 쌓인 뒤에는 템플릿을 수정하지 않는다.
- Terraform state에 DB 비밀번호가 평문으로 들어 있다. 로컬 파일이며 `.gitignore`로 제외했다.
- 이 계정 안에는 Terraform 밖에서 만든 테스트 IAM Role `kkari-dev-readonly-test`가 따로 있다 (신뢰 대상이 해커톤 서버 Role). `terraform destroy`로는 지워지지 않는다.

## 8. 팀 확정이 필요한 항목

- `project` 태그 값을 `kkarimarket`으로 둘지, 서비스명 Wayco로 둘지.
- DB 변경 금지(`do-not-change`) 태그를 유지할지.
- batch 새벽 피크를 유지할지.
- 웹 CPU에 시간대별 변화(저녁 상승 등)를 넣을지.

## 9. 재현·철거 방법

작업 전 확인: `AWS_*` 환경변수가 없어야 하고, `aws sts get-caller-identity --profile personal`의 계정 ID가 의도한 개인 계정이어야 한다. 계정 ID는 저장소에 적지 않고 git에서 제외되는 `terraform.tfvars`에만 넣는다.

```bash
cd infra/kkarimarket
cp terraform.tfvars.example terraform.tfvars   # allowed_account_id 등 실제 값으로 수정 (커밋 금지)
terraform init
terraform plan -out=kkarimarket.tfplan   # 12개 추가 예상
terraform apply kkarimarket.tfplan
```

- 철거: `terraform destroy`. 2차 배포에서는 약 2분 만에 12개가 지워졌다. RDS는 최종 스냅샷과 삭제 보호가 꺼져 있어서 한 번에 지워진다. 철거 후 남은 RDS, 스냅샷, 볼륨, ENI가 없는지 확인하고, 보안그룹 삭제에서 `DependencyViolation`이 나면 한 번 더 실행한다. **현재 철거 완료 상태다.**
- 실행 전 점검: `pgrep -x terraform`으로 다른 Terraform 프로세스가 없는지 확인한다. 같은 디렉터리를 여러 세션이 쓰면 state lock에 막히거나 중복 실행될 수 있다(실제로 철거 직후 다른 세션에서 같은 계획 파일로 `apply`가 시작된 적이 있다).
- `kkari-dev-readonly-test` Role은 사용자 결정(2026-11-05)으로 정리하지 않고 그대로 둔다.
