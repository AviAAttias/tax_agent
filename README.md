# Tax Assessment Service

A multi-tenant bank statement tax assessment platform built with Spring Boot 3.4.5 and Java 21. The service ingests bank statements, uses an AI-powered Kafka pipeline to categorize transactions and compute tax liabilities, and delivers results via multi-channel notifications.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Key Features](#key-features)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Domain Model](#domain-model)
- [API Reference](#api-reference)
- [Configuration](#configuration)
- [Local Development](#local-development)
- [Running Tests](#running-tests)
- [Helm Chart](#helm-chart)
- [CI/CD Pipeline](#cicd-pipeline)
- [Security](#security)
- [Observability](#observability)

---

## Architecture Overview

```
                        ┌─────────────────────────────────────────────┐
                        │            Keycloak (OAuth2 / JWT)           │
                        └────────────────────┬────────────────────────┘
                                             │ JWT (tid claim)
                        ┌────────────────────▼────────────────────────┐
                        │           Spring Boot API (port 8080)        │
                        │  TenantFilter → SecurityConfig → Controllers │
                        └──────────┬──────────────────────┬───────────┘
                                   │                      │
                    ┌──────────────▼──────┐   ┌──────────▼──────────────┐
                    │  PostgreSQL (RLS)    │   │   Kafka (3 partitions)  │
                    │  Liquibase schema   │   │   statement.ingested     │
                    │  PgBouncer sidecar  │   │   assessment.finalized   │
                    └─────────────────────┘   │   *.dlq (dead-letter)   │
                                              └──────────┬──────────────┘
                                                         │
                        ┌────────────────────────────────▼────────────┐
                        │             Agent Pipeline (Consumer)         │
                        │  1. Transaction Categorization  (OpenAI)     │
                        │  2. Tax Rule Application                     │
                        │  3. Assessment Aggregation                   │
                        │  4. Anomaly Detection                        │
                        │  5. Assessment Finalization                  │
                        └────────────────────┬────────────────────────┘
                                             │
                        ┌────────────────────▼────────────────────────┐
                        │         Notification Dispatcher              │
                        │    Email (SES) · SMS (Twilio) · In-App      │
                        └─────────────────────────────────────────────┘
```

The service follows a **hexagonal / clean architecture** within a single deployable JAR, with strict layer boundaries enforced by ArchUnit at CI time.

### Event Flow

1. Client uploads a bank statement (`POST /api/v1/statements`).
2. The API validates, deduplicates (by file hash), and persists the statement.
3. A `StatementIngestedEvent` is written to the **outbox table** atomically.
4. The **OutboxPoller** (scheduled, 1 s interval) publishes the event to Kafka.
5. `StatementPipelineConsumer` processes the event through the five-stage pipeline.
6. Each stage updates the database; on completion an `AssessmentFinalizedEvent` is published.
7. `NotificationDispatcher` delivers results to the tenant's configured channels.

---

## Key Features

| Feature | Details |
|---|---|
| **Multi-tenancy** | Tenant ID extracted from JWT `tid` claim; PostgreSQL row-level security enforces data isolation |
| **AI Categorization** | OpenAI structured output (JSON Schema strict mode, `gpt-4o`) classifies transactions into 30+ tax categories with confidence scores |
| **Transactional Outbox** | Events are written atomically with business data; a poller publishes to Kafka, preventing dual-write failures |
| **Resilience** | Resilience4j circuit breakers, retries with exponential backoff, and time limiters wrap all external calls |
| **Idempotency** | `ProcessedEventEntity` table prevents duplicate pipeline execution per `(event_id, consumer_group)` |
| **Notifications** | Multi-channel delivery (Email, SMS, In-App) with digest aggregation, retry scheduling, and per-tenant preferences |
| **Digital Signatures** | Finalized assessments are signed to make them immutable artifacts |
| **Zero-downtime deploys** | Kubernetes rolling update with `maxUnavailable=0`; HPA scales 2–20 replicas on CPU/memory |
| **Secrets management** | All credentials are injected at runtime from HashiCorp Vault via the CSI Secrets Store driver |

---

## Technology Stack

### Core

| Layer | Technology |
|---|---|
| Runtime | Java 21 (Eclipse Temurin), Spring Boot 3.4.5 |
| Persistence | PostgreSQL 16, Spring Data JPA / Hibernate, Liquibase, HikariCP |
| Messaging | Apache Kafka 3.9.0 (KRaft), spring-kafka |
| AI | OpenAI Java SDK 0.9.0 (`gpt-4o`, structured output) |
| Security | Spring Security 6.4.10, OAuth2 Resource Server (JWT), HashiCorp Vault CSI |
| Resilience | Resilience4j 2.2.0 (CircuitBreaker, Retry, TimeLimiter) |
| Notifications | Spring Mail (SES-compatible), Twilio 10.6.1, WebSocket |
| Observability | OpenTelemetry 1.43.0, Micrometer, Prometheus, Logstash JSON encoder |

### Infrastructure

| Layer | Technology |
|---|---|
| Container | Docker (multi-stage build, Eclipse Temurin 21 JRE Alpine) |
| Orchestration | Kubernetes + Helm 3 |
| Connection pooling | PgBouncer (session mode, sidecar container) |
| Secrets | HashiCorp Vault + Kubernetes CSI Secrets Store driver |
| Monitoring | Prometheus Operator (`PrometheusRule`), 6 preconfigured alerts |

### Testing

| Tool | Purpose |
|---|---|
| JUnit 5 + Mockito | Unit tests |
| Testcontainers 1.20.3 | Integration tests (PostgreSQL, Kafka) |
| WireMock 2.35.2 | OpenAI API stubbing |
| ArchUnit 1.3.0 | Architecture rule enforcement |
| OWASP Dependency Check 12.1.0 | Vulnerability scanning (CVSS ≥ 7 fails build) |

---

## Project Structure

```
tax-assessment-service/
├── src/
│   ├── main/java/com/abco/taxassessment/
│   │   ├── TaxAssessmentApplication.java       # Entry point
│   │   ├── config/                             # Spring configuration
│   │   │   ├── SecurityConfig.java             # OAuth2 / JWT / method security
│   │   │   ├── KafkaConfig.java                # Topics, DLQ routing, error handling
│   │   │   ├── DataSourceConfig.java           # Multi-tenant datasource + Vault CSI
│   │   │   ├── AsyncConfig.java                # Async executor
│   │   │   ├── WebSocketConfig.java            # SimpMessagingTemplate
│   │   │   ├── OpenApiConfig.java              # Swagger / SpringDoc
│   │   │   └── AppProperties.java              # Validated @ConfigurationProperties records
│   │   ├── tenant/                             # Multi-tenancy infrastructure
│   │   │   ├── TenantContext.java              # ThreadLocal tenant identity
│   │   │   ├── TenantFilter.java               # Servlet filter (Order=1), JWT → context
│   │   │   ├── TenantConnectionPreparer.java   # SET LOCAL rls.tenant_id
│   │   │   └── TenantAwareTaskDecorator.java   # Context propagation to @Async
│   │   ├── domain/
│   │   │   ├── tenant/                         # TenantEntity, TaxProfileEntity
│   │   │   ├── statement/                      # Upload, amendment, duplicate detection
│   │   │   ├── transaction/                    # Parsed transactions, tax categories
│   │   │   ├── assessment/                     # Tax assessments, anomalies, finalization
│   │   │   ├── account/                        # Bank account records
│   │   │   └── notification/                   # Multi-channel dispatcher, channels, preferences
│   │   ├── agent/                              # Kafka pipeline
│   │   │   ├── StatementPipelineConsumer.java  # Orchestrator (manual ack, idempotent)
│   │   │   ├── TransactionCategorizationService.java  # OpenAI AI stage
│   │   │   ├── TaxRuleApplicationService.java  # Deductibility rules, depreciation
│   │   │   ├── AnomalyDetectionService.java    # Statistical + rule-based detection
│   │   │   ├── AbstractAgentService.java       # Base: error handling, metrics
│   │   │   ├── AgentJobEntity.java             # Execution tracking
│   │   │   └── ProcessedEventEntity.java       # Idempotency store
│   │   ├── event/                              # Domain events + outbox pattern
│   │   │   ├── OutboxEventEntity.java
│   │   │   ├── OutboxPoller.java               # Scheduled batch publisher
│   │   │   ├── StatementIngestedEvent.java
│   │   │   └── AssessmentFinalizedEvent.java
│   │   ├── integration/
│   │   │   └── OpenAiJsonClient.java           # Resilience4j-wrapped HTTP client
│   │   └── exception/
│   │       └── GlobalExceptionHandler.java     # RFC 9457 Problem Detail responses
│   ├── main/resources/
│   │   ├── application.yaml                    # Base config
│   │   ├── application-local.yaml              # Local dev overrides
│   │   ├── application-production.yaml         # Production (reads from Vault)
│   │   └── db/changelog/                       # 15 Liquibase changesets + seed data
│   └── test/java/com/abco/taxassessment/
│       ├── ArchitectureTest.java               # ArchUnit layer/naming rules
│       ├── StatementControllerIntegrationTest.java
│       ├── AnomalyDetectionServiceTest.java
│       └── TaxRuleApplicationServiceTest.java
├── helm/                                       # Kubernetes Helm chart
│   ├── Chart.yaml
│   ├── values.yaml                             # Default values
│   ├── values-staging.yaml
│   ├── values-production.yaml
│   └── templates/
│       ├── deployment.yaml                     # App + PgBouncer sidecar
│       ├── service.yaml
│       ├── hpa.yaml                            # autoscaling/v2 with scale-down stabilization
│       ├── serviceaccount.yaml
│       ├── configmap.yaml
│       ├── secret-provider-class.yaml          # Vault CSI object mappings
│       └── prometheus-rules.yaml              # 6 Prometheus alert rules
├── docker/                                     # Keycloak realm import
├── .github/workflows/ci.yaml                  # Full CI/CD pipeline
├── Dockerfile                                  # Multi-stage build
├── docker-compose.yml                          # Local dev stack
├── .trivyignore                                # Trivy CVE suppressions (with justifications)
├── dependency-check-suppressions.xml          # OWASP suppression config
└── pom.xml
```

---

## Domain Model

```
Tenant ─────────── TaxProfile
   │
   ├── BankAccount[]
   ├── Statement[]
   │     └── Transaction[]
   │           └── TaxCategory
   ├── TaxAssessment[]
   │     └── Anomaly[]
   └── NotificationPreference[]
         └── NotificationEvent[]
```

### Database Schema (Liquibase changesets)

| Changeset | Table | Notes |
|---|---|---|
| 001 | `tenants` | Root aggregate, status enum, api_key_hash (unique) |
| 002 | `tax_profiles` | Jurisdiction, filing requirements, deduction limits |
| 003 | `bank_accounts` | Account number stored as hash |
| 004 | `statements` | file_hash for deduplication, status enum |
| 005 | `tax_categories` | 30+ categories seeded (REVENUE_SALES, EXPENSE_MEALS, …) |
| 006 | `transactions` | sign (DEBIT/CREDIT), category FK, confidence_score |
| 007 | `tax_assessments` | total_income, total_deductions, tax_liability, signed_artifact |
| 008 | `anomalies` | type, severity (LOW → CRITICAL), recommendation |
| 009 | `notification_events` | channel, status (PENDING/SENT/FAILED), idempotency_key |
| 010 | `notification_preferences` | Per-tenant per-event-type channel config + digest frequency |
| 011 | `agent_jobs` | Pipeline execution tracking, attempt count |
| 012 | `outbox_events` | Transactional outbox: payload, kafka_topic, published flag |
| 013 | `processed_events` | Idempotency: (event_id, consumer_group) unique constraint |
| 014 | RLS policies | PostgreSQL `rls.tenant_id` session variable, all tables |
| 015 | `custom_category_mappings` | Tenant-specific transaction → category overrides |

---

## API Reference

All endpoints require a valid JWT Bearer token. The tenant is resolved from the `tid` (or `tenant_id`) claim.

Interactive Swagger UI is available at `http://localhost:8080/swagger-ui.html` when running locally.

### Statements

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/statements` | Upload a bank statement (UC-07). Deduplicates by file hash. |
| `POST` | `/api/v1/statements/{id}/amendments` | Submit an amendment (UC-12). |
| `GET` | `/api/v1/statements` | List all statements for the current tenant. |
| `GET` | `/api/v1/statements/{id}` | Get a single statement. |

### Assessments

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/assessments` | List assessments for the current tenant. |
| `GET` | `/api/v1/assessments/{id}` | Get assessment details including tax liability. |
| `POST` | `/api/v1/assessments/{id}/finalize` | Finalize and digitally sign an assessment (UC-24). |
| `GET` | `/api/v1/assessments/{id}/anomalies` | List detected anomalies for an assessment. |

### Error Responses

All errors follow [RFC 9457 Problem Detail](https://www.rfc-editor.org/rfc/rfc9457) and include a `traceId` field for log correlation:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Statement with hash abc123 already exists",
  "traceId": "4bf92f3577b34da6"
}
```

---

## Configuration

### Environment Variables

| Variable | Required | Description |
|---|---|---|
| `JWT_ISSUER_URI` | Yes | Keycloak realm URL, e.g. `https://auth.example.com/realms/tax-assessment` |
| `DB_USERNAME` | Yes | PostgreSQL username |
| `DB_PASSWORD` | Yes | PostgreSQL password |
| `KAFKA_BOOTSTRAP_SERVERS` | Yes | e.g. `kafka:9092` |
| `OPENAI_API_KEY` | Yes | OpenAI API key for transaction categorization |
| `TWILIO_ACCOUNT_SID` | Yes | Twilio account SID for SMS |
| `TWILIO_AUTH_TOKEN` | Yes | Twilio auth token |
| `TWILIO_FROM_NUMBER` | Yes | E.164 sender number |
| `MAIL_HOST` | Yes | SMTP host (SES endpoint in production) |
| `MAIL_PORT` | No | SMTP port (default 587) |
| `NOTIFICATION_FROM_EMAIL` | Yes | Sender address for email notifications |
| `APP_ENVIRONMENT` | No | Profile name: `local`, `staging`, `production` |

In production all secrets are injected from **HashiCorp Vault** at `/vault/secrets/` via the CSI driver — no environment variable configuration needed for credentials.

### Spring Profiles

| Profile | Purpose |
|---|---|
| `local` | All services on localhost, DEBUG logging, 100% trace sampling, Liquibase runs |
| `staging` | Kubernetes, INFO logging, Vault CSI, SASL/SSL Kafka |
| `production` | Kubernetes, WARN root logging, 10% trace sampling, PgBouncer |

### Key Application Properties

```yaml
app:
  kafka:
    topics:
      statement-ingested: statement.ingested
      assessment-finalized: assessment.finalized
    consumer-group: tax-assessment-pipeline
  openai:
    model: gpt-4o
    timeout-seconds: 30
  outbox:
    poll-interval-ms: 1000
    batch-size: 50
```

---

## Local Development

### Prerequisites

- Docker Desktop (or equivalent) with Compose V2
- Java 21 (for running outside Docker)
- Maven 3.9+

### Start the full local stack

```bash
# Copy and configure environment
cp .env.example .env
# Edit .env — set your OPENAI_API_KEY and optionally Twilio credentials

# Start all services (PostgreSQL, Kafka, Keycloak, MailHog, the app)
docker compose up -d

# Tail logs
docker compose logs -f app
```

Add to `/etc/hosts` for Keycloak token validation to work:

```
127.0.0.1  keycloak
```

### Service URLs (local)

| Service | URL |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Actuator health | http://localhost:8080/actuator/health |
| Prometheus metrics | http://localhost:8080/actuator/prometheus |
| Keycloak | http://keycloak:8180 |
| Kafka UI | http://localhost:9080 |
| MailHog | http://localhost:8025 |
| PgAdmin | http://localhost:5050 (start with `--profile tools`) |

### Run outside Docker

```bash
# Start dependencies only
docker compose up -d postgres kafka keycloak mailhog

# Run the application
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

---

## Running Tests

```bash
# Unit tests only
./mvnw test

# Architecture tests (ArchUnit)
./mvnw test -Dtest=ArchitectureTest

# Integration tests (requires Docker for Testcontainers)
./mvnw verify -Pfailsafe

# All tests
./mvnw verify

# OWASP dependency vulnerability scan
./mvnw dependency-check:check
```

Integration tests spin up PostgreSQL and Kafka via Testcontainers automatically. WireMock stubs replace the OpenAI API.

---

## Helm Chart

The Helm chart deploys to Kubernetes and is located in `helm/`.

```bash
# Lint (all value sets)
helm lint helm/
helm lint helm/ -f helm/values-staging.yaml
helm lint helm/ -f helm/values-production.yaml

# Render templates (dry run)
helm template tax-assessment-service helm/ -f helm/values-staging.yaml

# Deploy to staging
helm upgrade --install tax-assessment-service helm/ \
  --namespace tax-assessment \
  --create-namespace \
  -f helm/values-staging.yaml \
  --set image.repository=ghcr.io/abco/tax-assessment-service \
  --set image.tag=sha-<GIT_SHA> \
  --atomic --wait --timeout 5m
```

### Helm Values Reference

| Key | Default | Description |
|---|---|---|
| `image.repository` | `""` | Container image repository |
| `image.tag` | `""` | Image tag (set by CI to `sha-<GIT_SHA>`) |
| `replicaCount` | `2` | Static replicas (ignored when HPA enabled) |
| `autoscaling.enabled` | `true` | Enable HPA |
| `autoscaling.minReplicas` | `2` | HPA minimum |
| `autoscaling.maxReplicas` | `10` | HPA maximum |
| `pgbouncer.enabled` | `true` | Deploy PgBouncer sidecar |
| `pgbouncer.databaseHost` | `""` | PostgreSQL host (injected per environment) |
| `pgbouncer.poolMode` | `session` | Session mode required for RLS |
| `vault.address` | `https://vault.internal:8200` | Vault endpoint |
| `vault.roleName` | `tax-assessment-service` | Kubernetes auth role |

### Prometheus Alerts

| Alert | Condition | Severity |
|---|---|---|
| `AgentDlqDepthHigh` | DLQ consumer lag > 10 for 5m | critical |
| `PipelineProcessingLatencyHigh` | p99 latency > 60 s for 5m | warning |
| `NotificationFailureRateHigh` | Failure rate > 5% for 5m | warning |
| `OpenAiCircuitBreakerOpen` | Circuit breaker open for 1m | critical |
| `OutboxPublishLag` | Unpublished outbox events > 100 for 2m | warning |
| `ApiErrorRateHigh` | Unexpected errors > 0.1/s for 5m | critical |

---

## CI/CD Pipeline

`.github/workflows/ci.yaml` — triggers on push to `main` or `claude/**`, and on PRs to `main`.

```
compile
  └── unit-tests
        └── architecture-check (ArchUnit)
              └── integration-tests
                    └── security-scan (OWASP Dependency Check, CVSS ≥ 7 fails)
                          └── build-image (Docker + Trivy scan)
                                └── helm-lint (default, staging, production values)
                                      └── deploy-staging  (main branch, manual approval)
                                            └── deploy-production (main branch, manual approval)
```

Docker images are published to `ghcr.io` tagged with `sha-<GIT_SHA>` and branch name. Trivy scans the pushed image for CRITICAL and HIGH CVEs; known false-positives are suppressed in `.trivyignore` with documented justifications and review dates.

---

## Security

### Authentication & Authorization

- All API endpoints require a JWT Bearer token issued by Keycloak.
- The `tid` (or `tenant_id`) claim in the JWT establishes tenant context.
- Method-level `@PreAuthorize` annotations enforce fine-grained access control.
- Actuator and Swagger endpoints are permit-all (no auth required).

### Secrets

All runtime secrets are sourced from **HashiCorp Vault** via the Kubernetes CSI Secrets Store driver and mounted read-only at `/vault/secrets/`. The production Spring profile imports them with `spring.config.import=configtree:/vault/secrets/`. No secrets are stored in Kubernetes Secrets or environment variables in production.

Secrets managed:
- Database credentials (`db-username`, `db-password`)
- OpenAI API key
- JWT issuer URI
- Twilio credentials
- Kafka SASL credentials
- Mail credentials

### Container Hardening

- Non-root user (`appuser`, UID 1000)
- Read-only root filesystem (`readOnlyRootFilesystem: true`)
- All Linux capabilities dropped
- `runAsNonRoot: true`, `allowPrivilegeEscalation: false`
- Writable `/tmp` provided by an `emptyDir` volume

### Dependency Scanning

- **OWASP Dependency Check** runs in CI; CVSS score ≥ 7 fails the build.
- **Trivy** scans the final Docker image; CRITICAL and HIGH findings fail the build.
- Security version overrides are declared in `pom.xml` `<dependencyManagement>` with comments referencing the CVE being addressed.

---

## Observability

### Metrics (Prometheus)

Exposed at `/actuator/prometheus`. Custom metrics include:

| Metric | Type | Description |
|---|---|---|
| `pipeline_statements_processing_duration_seconds` | Histogram | End-to-end pipeline latency |
| `kafka_consumer_group_sum_lag` | Gauge | Kafka consumer group lag |
| `outbox_events_published_total` | Counter | Successfully published outbox events |
| `outbox_events_publish_failure_total` | Counter | Failed outbox publishes |
| `notifications_sent_total` | Counter | Notifications delivered |
| `notifications_failed_total` | Counter | Notification delivery failures |

### Distributed Tracing

OpenTelemetry traces are exported via OTLP. The `traceId` is included in all log lines (Logstash JSON format) and in all error responses for correlation.

Sampling rate: 100% (local/staging), 10% (production).

### Health Checks

| Endpoint | Purpose |
|---|---|
| `/actuator/health/liveness` | Kubernetes liveness probe |
| `/actuator/health/readiness` | Kubernetes readiness probe |

The readiness group excludes Kafka so the pod becomes ready regardless of Kafka availability at startup.
