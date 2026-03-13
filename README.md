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

## ▍추론 처리 흐름

```
POST /ai/predict (multipart/form-data)
└─ 1. 파일 유효성 검사 (ImageFileValidator)
└─ 2. MinIO wafer-images 버킷 업로드 → objectKey 반환
└─ 3. AI 서빙(FastAPI) 호출 → prediction, confidence 반환
       └─ Circuit Breaker (Resilience4j) 보호
└─ 4. MySQL wafer_image 테이블 저장 → id 반환
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

### 통계

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/wafer/stats` | 전체 KPI 통계 (총 건수, 결함 수, 결함률, 평균 신뢰도) |
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
├─ AiAnalysisService        (Circuit Breaker 적용, AI 서빙 호출)
├─ WaferImageService        (DB CRUD)
└─ StatsService             (통계 집계)

Util
├─ MinioUploader            (MinIO 업로드)
└─ ImageFileValidator       (파일 유효성 검사)

Repository
└─ WaferImageRepository     (Spring Data JPA)
```

<br>

## ▍DB 스키마

```sql
CREATE TABLE wafer_image (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    filename   VARCHAR(255),
    filepath   VARCHAR(255),   -- MinIO object key
    prediction VARCHAR(255),
    confidence DOUBLE,
    created_at DATETIME,
    PRIMARY KEY (id)
);
```

이미지 파일 자체는 MinIO에 저장하고, `filepath` 컬럼에 MinIO object key를 저장해 두 스토리지를 연결하는 구조입니다.

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
| timeout-duration | 10s | AI 서버 응답 타임아웃 (기존 60s → 10s) |

**상태 전환**

| 상태 | 동작 |
|------|------|
| CLOSED | 정상 요청 처리 |
| OPEN | 즉시 차단, `503` 반환 (30초 후 자동 HALF_OPEN 전환) |
| HALF_OPEN | 테스트 요청 3건 허용 후 상태 결정 |

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

## ▍관련 레포지토리

| Repository | 설명 |
|------------|------|
| [kim-minji-wiki](https://github.com/msp-architect-2026/kim-minji-wiki) | 프로젝트 메인 (Wiki, 칸반보드) |
| [kim-minji-frontend](https://github.com/msp-architect-2026/kim-minji-frontend) | React 웹 대시보드 |
| [kim-minji-ai](https://github.com/msp-architect-2026/kim-minji-ai) | FastAPI AI 추론 서비스 |
| [kim-minji-helm](https://github.com/msp-architect-2026/kim-minji-helm) | Kubernetes Helm Chart |
| [kim-minji-infra](https://github.com/msp-architect-2026/kim-minji-infra) | k3s 클러스터 및 GitOps 인프라 |
