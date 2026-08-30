# groovy-common

Groovy MSA 백엔드 6개 서비스 레포에 **byte-identical 로 복붙되어 있던 공통 코드**를 관심사별
모듈로 분리한 레포. 각 서비스는 필요한 모듈만 골라 의존한다.

> 배포 방식: **바이너리 아티팩트 + GitHub Packages (Maven)**. `com.groovy.backend:<module>:<version>`
> frontend 는 대상이 아니다 (독립적인 React 앱).

---

## 모듈

| 모듈 | 아티팩트 | 내용 |
|---|---|---|
| `event-contract` | `com.groovy.backend:event-contract` | Kafka 이벤트 봉투(`EventEnvelope`) + payload record (`NotificationPayload`, `UserDeletedPayload`). 프레임워크 의존 0 |
| `observability` | `com.groovy.backend:observability` | 로그 포맷(`logback-json.xml`, `TraceIdTurboFilter`, `LogFields`), `HttpStatusObservationFilter`, 분산 트레이싱 조립(`TracingConfig`) |
| `web-common` | `com.groovy.backend:web-common` | `ApiResponse`, `BaseTimeEntity`, `GlobalExceptionHandler`, `ForbiddenException`, `JwtAuthenticationEntryPoint` (+ 자동설정) |
| `security-common` | `com.groovy.backend:security-common` | JWT **검증**용 `JwtAuthenticationFilter` / `JwksKeyLocator` / `TokenProvider` (+ 자동설정). 발급자 identity-service 는 미사용 |
| `client-common` | `com.groovy.backend:client-common` | 서비스 간 동기 호출 + Resilience4j 실행기(`ResilientCallExecutor`, `UserServiceClient`) (+ 자동설정). → `web-common` 의존 |
| `outbox` | `com.groovy.backend:outbox` | Transactional Outbox (`OutboxEvent`(@Entity) / `OutboxEventRepository` / `OutboxEventWriter` / `OutboxRelay` / `SchedulingConfig`). → `web-common`, `event-contract` 의존 |

### 모듈 ↔ 서비스 매트릭스

| 모듈 | gateway | identity | study | calendar | content | notification |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| observability | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| event-contract | | ✅ | ✅ | ✅ | ✅ | ✅ |
| web-common | | ✅ | ✅ | ✅ | ✅ | ✅ |
| security-common | | | ✅ | ✅ | ✅ | ✅ |
| client-common | | | ✅ | ✅ | ✅ | |
| outbox | | | ✅ | ✅ | ✅ | |

---

## 소비 설정 (서비스 레포)

`build.gradle`:
```groovy
repositories {
    mavenCentral()
    maven {
        url = uri('https://maven.pkg.github.com/KDT-Team26/groovy-common')
        credentials {
            username = System.getenv('GITHUB_ACTOR') ?: findProperty('gpr.user')
            password = System.getenv('GPR_TOKEN')   ?: findProperty('gpr.key')
        }
    }
}

dependencies {
    implementation 'com.groovy.backend:observability:0.1.0'
    implementation 'com.groovy.backend:web-common:0.1.0'
    // ... 서비스별 매트릭스대로
}
```

- **CI**: 조직 시크릿 `GPR_TOKEN`(= `read:packages` PAT) 을 Gradle 스텝 env 로 주입
  (`GITHUB_ACTOR: ${{ github.actor }}`, `GPR_TOKEN: ${{ secrets.GPR_TOKEN }}`).
- **로컬**: `~/.gradle/gradle.properties` 에
  ```properties
  gpr.user=<본인 GitHub username>
  gpr.key=<read:packages PAT>
  ```
- GitHub Packages Maven 은 public 이어도 익명 read 불가 → 소비자마다 토큰 필요.

`settings.gradle` 에서 기존 `include 'libs:*'` 는 전부 삭제.

---

## 게시 (릴리스)

```bash
git tag v0.1.0 && git push origin v0.1.0
```
→ `.github/workflows/publish.yml` 이 6개 모듈을 `0.1.0` 으로 GitHub Packages 에 게시
(`GITHUB_TOKEN` + `packages: write`, 별도 PAT 불필요).

로컬 수동 게시(디버깅용): `~/.gradle/gradle.properties` 에 `gpr.user` / `gpr.key`(`write:packages` PAT) 두고
```bash
./gradlew publish -PreleaseVersion=0.1.0
```

버전 규칙: 소비 측은 항상 정확한 버전 핀(`0.1.0`), `SNAPSHOT` 금지.

---

## outbox 배선 (study / calendar / content)

`outbox` 모듈의 JPA 클래스는 서비스 base 패키지(`com.groovy.backend.<svc>`) **밖**이라
기본 스캔에 안 잡힌다. 소비 서비스의 `@SpringBootApplication` 클래스에:

```java
@SpringBootApplication(scanBasePackages = {"com.groovy.backend.<svc>", "com.groovy.backend.outbox"})
@EntityScan(basePackages          = {"com.groovy.backend.<svc>", "com.groovy.backend.outbox"})
@EnableJpaRepositories(basePackages = {"com.groovy.backend.<svc>", "com.groovy.backend.outbox"})
```

`observability` 의 `TracingConfig` 도 같은 이유로 `@Import(com.groovy.backend.observability.TracingConfig.class)`
또는 `scanBasePackages` 에 `com.groovy.backend.observability` 추가가 필요하다.
(→ 정확한 세트는 study-service 파일럿에서 확정. 후속으로 `@AutoConfiguration` 전환 검토 — 이슈 #4.)

`outbox_events` 테이블 DDL(Flyway `V*__*.sql`)은 계속 각 서비스 레포 로컬에 둔다 (common 으로 옮기지 않는다).

---

## 로컬 빌드

```bash
./gradlew build              # 6개 모듈 컴파일 + 계약 테스트
./gradlew publishToMavenLocal # ~/.m2 에 게시 (다른 프로젝트에서 로컬 검증용)
```
