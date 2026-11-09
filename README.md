<h1 align="center">kim-minji-backend</h1>

<p align="center">
  웨이퍼 결함 탐지 시스템의 <strong>Spring Boot API 서버</strong> 레포지토리입니다.
</p>


<p align="center">
<img src="https://img.shields.io/badge/SpringBoot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white"/>
<img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white"/>
<img src="https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white"/>
<img src="https://img.shields.io/badge/MinIO-C72E49?style=for-the-badge&logo=minio&logoColor=white"/>
<img src="https://img.shields.io/badge/Resilience4j-00B38F?style=for-the-badge&logo=java&logoColor=white"/>
<img src="https://img.shields.io/badge/Prometheus-E6522C?style=for-the-badge&logo=prometheus&logoColor=white"/>
</p>



## ▍개요

프론트엔드와 AI 추론 서버 사이에서 **파일 처리, 데이터 저장, 통계 집계, 장애 격리**를 담당하는 핵심 API 서버입니다.

웨이퍼 이미지를 수신해 MinIO에 저장하고 AI 서빙(FastAPI)을 호출한 뒤 추론 결과를 MySQL에 저장합니다. Resilience4j Circuit Breaker로 AI 서버 장애 시 연쇄 장애를 차단하며, Spring Boot Actuator + Micrometer로 Prometheus 메트릭을 노출합니다.

<br>

## ▍관련 레포지토리

| Repository | 설명 |
|------------|------|
| [kim-minji-wiki](https://github.com/msp-architect-2026/kim-minji-wiki) | 프로젝트 메인 (Wiki, 칸반보드) |
| [kim-minji-frontend](https://github.com/msp-architect-2026/kim-minji-frontend) | React 웹 대시보드 |
| [kim-minji-ai](https://github.com/msp-architect-2026/kim-minji-ai) | FastAPI AI 추론 서비스 |
| [kim-minji-helm](https://github.com/msp-architect-2026/kim-minji-helm) | Kubernetes Helm Chart |
| [kim-minji-infra](https://github.com/msp-architect-2026/kim-minji-infra) | k3s 클러스터 및 GitOps 인프라 |

<br>

## ▍추론 처리 흐름

```
POST /ai/predict (multipart/form-data) → InspectionService
└─ 1. 파일 유효성 검사 (ImageFileValidator)
└─ 2. MinIO wafer-images 버킷 업로드 → objectKey 반환
└─ 3. AI 서빙(FastAPI) 호출 → prediction, confidence 반환
       └─ Circuit Breaker (Resilience4j) 보호
└─ 4. MySQL inspection 테이블에 결과·상태 이력 저장 → id 반환
└─ 5. { id, prediction, confidence } 응답
```

<br>

## ▍API 목록

### AI 추론

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/ai/predict` | 웨이퍼 이미지 업로드 및 AI 추론 |

### 추론 결과 조회

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/wafer/records` | 전체 추론 결과 목록 |
| `GET` | `/wafer/records/pages` | 검색 + 페이지네이션 목록 |
| `GET` | `/wafer/records/{id}` | 단건 상세 조회 |
| `GET` | `/wafer/records/export` | 날짜·결함 유형 필터 CSV 내보내기 |

### Lot·검사 (MES)

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/lots` | Lot과 웨이퍼 슬롯 생성 |
| `GET` | `/lots` | Lot 목록 (슬롯 상태별 건수) |
| `GET` | `/lots/{id}` | Lot 상세 (슬롯별 최근 검사) |
| `GET` | `/lots/{id}/route` | 공정 진행 현황과 다음 공정 |
| `POST` | `/lots/{id}/track-in`, `/lots/{id}/track-out` | 설비 이벤트 수신 (공정 순서 검증, 재전송 멱등) |
| `POST` | `/lots/{id}/hold`, `/lots/{id}/release` | 수동 Hold / Release |
| `POST` | `/lots/{id}/wafers/{slot}/inspections` | 웨이퍼 검사. `Idempotency-Key` 헤더 지원 |
| `GET` | `/inspections/pending-reviews` | 리뷰 대기 목록 |
| `POST` | `/inspections/{id}/review` | 엔지니어 최종 판정 (AI 판정과 별도 저장) |
| `POST` | `/inspections/{id}/retry` | 실패한 검사를 재업로드 없이 재시도 |
| `GET` | `/inspections/{id}/events` | 검사 상태 전이 이력 |
| `GET` | `/wafers/{id}/process-history` | 웨이퍼 공정·설비 이력 |
| `POST` | `/dev/simulate` | 시뮬레이션 데이터 생성 (`local` 프로파일 전용) |

```
RECEIVED → UPLOADED → INFERRING → DONE
                          │     → NEED_REVIEW (신뢰도 < inspection.review-confidence-threshold)
                          └─────→ FAILED → (재시도) → INFERRING
```

Lot은 판정 웨이퍼가 `lot.hold.min-judged-wafers`장 이상이고 결함률이 `lot.hold.defect-rate-threshold` 이상이면 자동 Hold됩니다 (Lot당 1회). Hold 중에는 신규 검사와 재시도가 막히고 리뷰만 가능합니다.

### SPC

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/spc/p-chart?days=14&defect=&equipment=` | 일별 결함률 p-chart (3시그마 이탈, 중심선 위 8연속) |
| `GET` | `/spc/commonality?days=14&defect=` | 같은 공정의 설비 간 결함률 비교와 의심 설비 |

### 통계

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/wafer/stats` | 전체 KPI 통계 (최종 판정 기준: 총 건수, 결함 수, 결함률, 평균 신뢰도) |
| `GET` | `/wafer/stats/daily?days=7` | 일별 추론 통계 |
| `GET` | `/wafer/stats/defect-distribution` | 결함 유형별 분포 |

### 헬스체크 및 메트릭

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/actuator/health` | 헬스체크 |
| `GET` | `/actuator/prometheus` | Prometheus 메트릭 수집 엔드포인트 |

> 전체 API 상세 명세: [API Specification](https://github.com/msp-architect-2026/kim-minji-wiki/wiki/API-Specification-%E2%80%90-final)

<br>

## ▍레이어 구조

```
Controller
├─ AiPredictionController   (/ai)
├─ AiRecordController       (/wafer/records)
└─ StatsController          (/wafer/stats)

Service
├─ InspectionService        (검증 → 업로드 → 추론 → 저장 흐름)
├─ InspectionLifecycle      (상태 전이와 이력 기록, 전이별 트랜잭션)
├─ LotService               (Lot·웨이퍼 조회/생성)
├─ AiAnalysisService        (Circuit Breaker 적용, AI 서빙 호출)
└─ StatsService             (통계 집계)

Util
├─ MinioUploader            (MinIO 업로드)
└─ ImageFileValidator       (파일 유효성 검사)

Repository
└─ Lot/Wafer/Inspection 등  (Spring Data JPA)
```

<br>

## ▍DB 스키마

| 테이블 | 용도 |
|--------|------|
| `lot`, `wafer` | Lot과 슬롯(1~25) 현황 |
| `process_step`, `equipment` | 공정·설비 기준정보 (V5 seed) |
| `wafer_process_history` | 웨이퍼별 공정·설비·레시피 이력 |
| `inspection` | 검사 1건의 상태, 판정, 모델 버전, 에러 코드, trace_id, 멱등키 |
| `inspection_event` | 검사 상태 전이 이력 (append-only) |

스키마는 Flyway(`src/main/resources/db/migration`)로 관리하고 `ddl-auto=validate`로 검증합니다. 이미 테이블이 있는 DB는 version 1로 baseline한 뒤 V2부터 적용합니다.

이미지 파일 자체는 MinIO에 저장하고, `inspection.image_key` 컬럼에 MinIO object key를 저장해 두 스토리지를 연결하는 구조입니다.

<br>

## ▍Circuit Breaker (Resilience4j)

AI 서빙 타임아웃 시 백엔드 스레드가 점유되는 연쇄 장애를 차단합니다.

| 설정 | 값 | 설명 |
|------|----|------|
| sliding-window-type | COUNT_BASED | 요청 건수 기반 판단 |
| sliding-window-size | 10 | 최근 10건 기준 |
| failure-rate-threshold | 50% | 실패율 50% 초과 시 OPEN 전환 |
| wait-duration-in-open-state | 30s | OPEN 유지 시간 |
| permitted-calls-in-half-open | 3 | HALF-OPEN 테스트 요청 수 |
| ai.client.connect-timeout | 2s | AI 서버 연결 타임아웃 (RestTemplate) |
| ai.client.read-timeout | 10s | AI 서버 응답 타임아웃 (RestTemplate) |

**상태 전환**

| 상태 | 동작 |
|------|------|
| CLOSED | 정상 요청 처리 |
| OPEN | 즉시 차단, `503` 반환 (30초 후 자동 HALF_OPEN 전환) |
| HALF_OPEN | 테스트 요청 3건 허용 후 상태 결정 |

<br>

## ▍에러 응답

실패 응답은 RFC 9457 ProblemDetail에 `code`와 `traceId`를 담아 반환합니다. `traceId`는 OTel trace_id이고, agent가 없는 환경에서는 서버가 생성해 로그에 함께 남깁니다.

| code | HTTP | 상황 |
|------|------|------|
| `INVALID_FILE` | 400 | 빈 파일, 이미지가 아닌 파일 |
| `INVALID_REQUEST` | 400 | 요청 값 오류 |
| `RECORD_NOT_FOUND`, `LOT_NOT_FOUND`, `WAFER_NOT_FOUND`, `INSPECTION_NOT_FOUND` | 404 | 대상 없음 |
| `LOT_ALREADY_EXISTS`, `LOT_NOT_ACTIVE`, `INSPECTION_IN_PROGRESS`, `INSPECTION_NOT_RETRYABLE`, `INSPECTION_NOT_REVIEWABLE`, `INSPECTION_NEEDS_REVIEW`, `LOT_NOT_ON_HOLD`, `CONCURRENT_UPDATE` | 409 | 현재 상태와 충돌 |
| `STORAGE_UPLOAD_FAILED` | 500 | MinIO 업로드 실패 |
| `AI_SERVER_ERROR` | 502 | AI 서버가 오류 상태 반환 |
| `AI_INVALID_RESPONSE` | 502 | 응답 형식·라벨·confidence 이상 |
| `AI_CIRCUIT_OPEN` | 503 | Circuit Breaker OPEN |
| `AI_UNAVAILABLE` | 503 | AI 서버 연결 실패 |
| `AI_TIMEOUT` | 504 | AI 서버 응답 시간 초과 |
| `INTERNAL_ERROR` | 500 | 그 외 예외 |

<br>

## ▍로컬 실행

```bash
colima start --cpu 4 --memory 6
docker compose up -d --build mysql minio minio-init ai
SPRING_PROFILES_ACTIVE=local DB_URL='jdbc:mysql://127.0.0.1:3306/appdb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \
MINIO_ENDPOINT=http://127.0.0.1:9000 AI_SERVER_URL=http://127.0.0.1:8000 ./gradlew bootRun
```

프론트는 `kim-minji-frontend`에서 `VITE_API_URL=http://localhost:8080 npm run dev`로 띄웁니다. `local` 프로파일에서는 Lots 화면의 Simulate 버튼으로 데모 데이터를 만들 수 있습니다.

`docker-compose.yml`은 로컬 검증 전용입니다. AI 서버는 `FAULT_INJECTION_ENABLED=true`로 떠서 `/admin/fault`로 장애를 재현할 수 있습니다.

통합 테스트는 Testcontainers로 실제 MySQL을 띄우며, Docker에 접속하지 못하면 **건너뛰고 빌드는 성공**합니다. colima에서는 아래처럼 실행하고 결과의 skipped가 0인지 확인합니다.

```bash
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
TESTCONTAINERS_DOCKER_CLIENT_STRATEGY=org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy \
./gradlew test
```

<br>

## ▍환경변수

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://mysql.storage.svc.cluster.local:3306/appdb` | MySQL 접속 URL |
| `SPRING_DATASOURCE_USERNAME` | `appuser` | DB 사용자명 |
| `SPRING_DATASOURCE_PASSWORD` | `apppass123` | DB 비밀번호 |
| `MINIO_ENDPOINT` | `http://minio.storage.svc.cluster.local:9000` | MinIO 엔드포인트 |
| `MINIO_ACCESS_KEY` | `minioadmin` | MinIO 액세스 키 |
| `MINIO_SECRET_KEY` | `minioadmin123` | MinIO 시크릿 키 |
| `MINIO_BUCKET` | `wafer-image-bucket` | 이미지 저장 버킷명 |
| `AI_SERVER_URL` | `http://ai-serving-ai-serving.ai-serving.svc.cluster.local:8000` | AI 서빙 URL |

> 클러스터 배포 시 모든 민감 정보는 **Sealed Secrets**로 암호화 관리합니다. Git에는 암호문만 저장됩니다.

<br>

## ▍Dockerfile (멀티스테이지)

```dockerfile
# Build Stage
FROM eclipse-temurin:17-jdk-jammy AS build
RUN ./gradlew bootJar

# Runtime Stage
FROM eclipse-temurin:17-jre-jammy
RUN adduser appuser
USER appuser
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
```

- non-root 사용자(`appuser`) 실행으로 보안 강화
- JVM 메모리: 컨테이너 메모리의 75% 사용
- `--platform=linux/arm64` (맥북 M5 기반 VM 대응)

<br>

## ▍Helm Chart 스펙

| 항목 | 값 |
|------|----|
| replicaCount | 1 |
| CPU requests / limits | 250m / 1000m |
| Memory requests / limits | 512Mi / 1Gi |
| Readiness Probe | `/actuator/health`, initialDelay 10s, period 10s |
| Liveness Probe | `/actuator/health`, initialDelay 20s, period 20s |
| Secret | Sealed Secrets (`backend-secret`) |

<br>

## ▍CI/CD 파이프라인

```
git push (main 브랜치)
└─ GitLab CI 트리거
   └─ kaniko ARM64 이미지 빌드
      └─ GitLab Registry push (SHA + latest 태그)
         └─ update-helm.sh → kim-minji-helm values.yaml 태그 업데이트
            └─ ArgoCD 감지 → k3s 자동 배포
```

- `develop` 브랜치: build 단계만 실행
- `main` 브랜치: build + update-helm 전체 실행

<br>

## ▍모니터링

Spring Boot Actuator + Micrometer로 `/actuator/prometheus` 엔드포인트를 노출합니다.

Prometheus ServiceMonitor가 30초 간격으로 수집하며 Grafana에서 아래 메트릭을 시각화합니다.

| 메트릭 | PromQL |
|--------|--------|
| 요청 처리율 | `rate(http_server_requests_seconds_count{job="backend-backend"}[1m])` |
| 평균 응답시간 | `rate(..._sum[1m]) / rate(..._count[1m])` |
| 에러율 | `sum(rate(...{status=~"5.."}[1m])) or vector(0)` |

<br>

