# Spring Mido Client

[![Maven Central](https://img.shields.io/maven-central/v/io.github.skaca8/mido-client.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.skaca8/mido-client)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

한국어 | **[English](README.md)**

> Spring Boot 3.2+ 기반의 YAML 설정 멀티채널 RestClient 관리 라이브러리

`mido-client`는 여러 외부 API 채널을 `application.yml`에 선언하는 것만으로 `RestClient`를 자동으로 구성하고 제공합니다. `@Bean` 메서드나 팩토리 클래스 없이, 반복적인
설정 코드 없이 사용할 수 있습니다.

## 왜 mido-client인가?

|                               | RestClient (직접 설정) | OpenFeign          | mido-client |
|-------------------------------|--------------------|--------------------|-------------|
| 설정 방식                         | Java `@Bean`       | Java 인터페이스 + 어노테이션 | YAML만으로 설정  |
| 멀티채널 구성                       | 채널마다 수동 설정         | 인터페이스마다 수동 설정      | 기본 제공       |
| 채널당 이중 엔드포인트                  | 수동                 | 미지원                | 기본 제공       |
| 요청/응답 로깅                      | 인터셉터 직접 구현         | 플러그인 필요            | 기본 제공 (4단계) |
| 클라이언트 인스턴스 캐싱                 | 수동                 | 프레임워크 관리           | 기본 제공       |
| Spring Boot 3.2 RestClient 기반 | Yes                | No (Feign 사용)      | Yes         |

## 주요 기능

- **멀티채널 지원** — 채널 수 제한 없이 정의 가능하며, 각 채널은 `primary` / `secondary` 이중 엔드포인트를 가질 수 있음
- **클라이언트 자동 캐싱** — 채널/엔드포인트 조합별로 `RestClient` 인스턴스를 `ConcurrentHashMap`으로 캐싱, 스레드 안전
- **4단계 로깅** — `off` / `console` / `file` / `all`, 요청/응답 바디, URL, 응답시간 포함
- **엔드포인트별 인증** — Bearer, Basic, API Key 방식 지원
- **스마트 인코딩 감지** — Content-Type 헤더 → UTF-8 유효성 검사 → 채널 기본값 순으로 자동 결정
- **커스텀 인터셉터** — `ClientHttpRequestInterceptor` 구현체를 YAML에 클래스명으로 등록
- **교체 가능한 HTTP 전송** — `simple`(기본, `HttpURLConnection`) / `jdk`(`java.net.http.HttpClient`, 채널별 커넥션 풀 + HTTP/2) 중 전역 또는 엔드포인트 단위로 선택
- **채널별 gzip 압축** — 요청 바디는 `min-size` 임계값 이상일 때만 압축, 응답은 자동 해제 + 압축 폭탄 방어 cap(`max-decompressed-size`)
- **채널별 컨텐트 타입** — `json`(기본) / `xml` 중 채널 단위로 선택, 요청 `Content-Type` 헤더가 자동 설정됨
- **부팅 시 설정 검증** — `@Validated` Bean Validation으로 잘못된 YAML을 시작 시점에 거부, `BindValidationException`에 어떤 필드가 잘못되었는지 명시
- **ChannelContext + MDC 연동** — 스코프 기반(`ScopedValue`) 채널 액션 추적, SLF4J MDC와 통합되어 로그에 자동 포함
- **자동 설정** — `mido-client.enabled: true` 프로퍼티 하나로 활성화

## 요구 사항

| 항목          | 버전                       |
|-------------|--------------------------|
| Java        | 25                       |
| Spring Boot | 3.5.x (3.5.16으로 빌드·검증)   |
| Gradle      | 8.14.4                   |

> `ChannelContext`가 정식 `java.lang.ScopedValue` API(JEP 506, Java 25) 기반이므로 Java 25가 필요합니다.
> 클래스패스 스캐닝(ASM)이 Java 25 바이트코드를 읽을 수 있는 Spring Framework 6.2 릴리즈가 필요합니다(최신 6.2.x 패치,
> Spring Boot 3.2.x는 불가). 이 라이브러리는 Spring Boot 3.5.16으로 빌드·검증되었습니다.

## 빠른 시작

### 1. 의존성 추가

#### JitPack (GitHub)을 통한 방법

**Gradle**

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.skaca8:mido-client:2.0.1'
}
```

**Maven**

```xml

<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.skaca8</groupId>
    <artifactId>mido-client</artifactId>
    <version>2.0.1</version>
</dependency>
```

> 특정 릴리즈를 사용하려면 `2.0.1`을 원하는 태그 또는 커밋 해시로 변경하세요.

#### Maven Central을 통한 방법 (정식 릴리즈)

**Gradle**

```gradle
implementation 'io.github.skaca8:mido-client:2.0.1'
```

**Maven**

```xml

<dependency>
    <groupId>io.github.skaca8</groupId>
    <artifactId>mido-client</artifactId>
    <version>2.0.1</version>
</dependency>
```

### 2. `application.yml` 설정

```yaml
mido-client:
  enabled: true
  channels:
    payment:
      title: "결제 서비스"
      charset: UTF-8
      type: json    # json (기본값) | xml
      primary:
        url: https://api.payment.com
        read-timeout-seconds: 30
        connect-timeout-seconds: 5
        authorization:
          type: bearer
          token: ${PAYMENT_QUERY_TOKEN}
        log: console
      secondary: # 선택사항: 동일 서비스의 두 번째 엔드포인트
        url: https://process.payment.com
        read-timeout-seconds: 60
        authorization:
          type: bearer
          token: ${PAYMENT_PROCESS_TOKEN}
        log: all
    auth:
      primary:
        url: https://auth.example.com
        authorization:
          type: bearer
          token: ${AUTH_TOKEN}
        headers:
          - name: X-API-Version
            value: v1
```

### 3. 서비스에서 사용

```java

@Service
public class PaymentService extends BaseExternalApi {

    private final RestClient queryClient;
    private final RestClient processClient;

    public PaymentService(MidoClientFactory midoClientFactory) {
        this.queryClient = midoClientFactory.getOrCreateClient("payment");
        this.processClient = midoClientFactory.getOrCreateClient("payment", EndpointType.SECONDARY);
    }

    @Override
    protected String getChannelName() {
        return "payment";
    }

    public PaymentStatus getPaymentStatus(String paymentId) {
        return withDefaultChannelAction("getPaymentStatus", () ->
                queryClient.get()
                        .uri("/payments/{id}/status", paymentId)
                        .retrieve()
                        .body(PaymentStatus.class)
        );
    }

    public PaymentResult processPayment(PaymentRequest request) {
        return withDefaultChannelAction("processPayment", () ->
                processClient.post()
                        .uri("/payments/process")
                        .body(request)
                        .retrieve()
                        .body(PaymentResult.class)
        );
    }
}
```

> `BaseExternalApi.withDefaultChannelAction()`은 호출 전후로 `ChannelContext`를 자동으로 설정하고 정리합니다. 예외가 발생해도 반드시 정리됩니다.

## 설정 레퍼런스

### 채널 설정 (`mido-client.channels.<name>`)

| 프로퍼티      | 타입          | 기본값     | 설명                                       |
|-----------|-------------|---------|------------------------------------------|
| `title`   | String      | -       | 채널 설명 (선택사항)                             |
| `charset` | String      | `UTF-8` | 응답 바디 기본 인코딩                             |
| `type`    | ContentType | `json`  | 채널 요청 `Content-Type` — `json` / `xml` 지원 |

### 엔드포인트 설정 (`primary` / `secondary`)

| 프로퍼티                      | 타입             | 기본값       | 설명                                             |
|---------------------------|----------------|-----------|------------------------------------------------|
| `url`                     | String         | -         | **필수.** 엔드포인트 기본 URL                           |
| `title`                   | String         | -         | 엔드포인트 설명 (선택사항)                                |
| `read-timeout-seconds`    | Long           | `60`      | 읽기 타임아웃 (초)                                    |
| `connect-timeout-seconds` | Long           | `3`       | 연결 타임아웃 (초)                                    |
| `log`                     | LogLevel       | `console` | `off` / `console` / `file` / `all`             |
| `client-type`             | ClientType     | (전역값 상속)  | `simple` / `jdk` — 이 엔드포인트의 HTTP 전송, `mido-client.client-type`를 오버라이드 |
| `authorization.type`      | TokenType      | -         | `bearer` / `basic` / `api_key`                 |
| `authorization.token`     | String         | -         | 인증 토큰 값                                        |
| `headers`                 | List           | -         | 모든 요청에 고정으로 추가할 헤더 목록                          |
| `interceptors`            | List\<String\> | -         | `ClientHttpRequestInterceptor` 구현체의 전체 클래스명 목록 |
| `gzip.request`            | Boolean        | `false`   | 요청 바디 gzip 압축 (`Content-Encoding: gzip` 자동 추가) |
| `gzip.response`           | Boolean        | `false`   | `Accept-Encoding: gzip` 강제 후 응답 자동 해제 |
| `gzip.min-size`           | Integer        | `1024`    | 요청 바디가 이 크기 미만이면 압축 skip (bytes) |
| `gzip.max-decompressed-size` | Integer     | `10485760`| 응답 해제 결과가 이 크기를 넘으면 `IOException` (압축 폭탄 방어, bytes) |

### 전역 설정

| 프로퍼티                      | 타입         | 기본값      | 설명                                          |
|---------------------------|------------|----------|---------------------------------------------|
| `mido-client.enabled`     | Boolean    | `false`  | 라이브러리 활성화 여부                                 |
| `mido-client.client-type` | ClientType | `simple` | 모든 채널의 기본 HTTP 전송, 엔드포인트별 `client-type`로 오버라이드 가능 |

### 설정 검증

`mido-client`는 애플리케이션 시작 시점에 `@ConfigurationProperties`를 검증합니다. 잘못된 설정은 `BindValidationException`과 함께 어떤 필드가 잘못되었고 거부된 값이 무엇인지 표시하며 컨텍스트 로드에 실패합니다. 다음 경우 검증에 실패합니다.

- `url`이 비어있거나 `^https?://.+`에 매치되지 않음
- `read-timeout-seconds` 또는 `connect-timeout-seconds`가 0 이하
- `gzip.min-size`가 음수
- `gzip.max-decompressed-size`가 0 이하
- `headers[].name` 또는 `headers[].value`가 비어있음
- 채널에 필수 `primary` 엔드포인트가 없음
- `type`이 명시적으로 `null`로 지정됨 (값은 `json` 또는 `xml`이어야 함 — 그 외 값은 Spring enum 바인더가 시작 시점에 별도로 거부)

## 고급 사용법

### 커스텀 인터셉터

`ClientHttpRequestInterceptor`를 구현하고 YAML에 클래스명으로 등록합니다.

```java

@Component
public class RequestIdInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().add("X-Request-Id", UUID.randomUUID().toString());
        return execution.execute(request, body);
    }
}
```

```yaml
interceptors:
  - "com.example.RequestIdInterceptor"
```

> 커스텀 인터셉터는 no-arg public 생성자(`Class.forName(...).getDeclaredConstructor().newInstance()`)로 인스턴스화됩니다. 이렇게 만들어진 객체는 **Spring이 관리하는 빈이 아니므로**, 생성자 주입은 물론 `@Autowired` 필드 주입도 동작하지 않습니다. 인터셉터에 `@Component`를 함께 붙이더라도 Spring이 만드는 빈은 *별개 인스턴스*이며, `mido-client`는 그 빈을 사용하지 않습니다.
>
> 현실적으로 가능한 두 가지 패턴:
>
> 1. **`static` 필드** — 상태 없는 인터셉터에 가장 깔끔합니다 (권장).
> 2. **`ApplicationContextHolder` 우회** — 시작 시점에 `ApplicationContext`를 정적 필드에 보관해 두고 `intercept(...)` 내부에서 빈을 조회하는 방식. *권장 디자인이 아니며 escape hatch로만 사용*하세요.
>
> "Spring 빈 이름으로 인터셉터 등록" 옵션은 다음 마이너 릴리스의 로드맵에 있습니다.
>
> **Fail-fast 동작**: 클래스 로딩 실패, public no-arg 생성자 부재, `ClientHttpRequestInterceptor` 미구현 등은 `MidoClientFactory.getOrCreateClient(...)`의 첫 호출 시점에 채널 이름과 문제 클래스명을 포함한 `IllegalStateException`이 발생합니다.

### 회복성 (Rate Limiter / Circuit Breaker / Retry)

`mido-client`는 의도적으로 회복성(resilience) 레이어를 **내장하지 않습니다** — Resilience4j, Sentinel, Failsafe, Spring Retry 등 원하는 라이브러리를 `interceptors:` 설정으로 직접 끼우세요. 아래는 Resilience4j 기준 복붙용 레시피입니다.

**1. Resilience4j를 애플리케이션 의존성에 추가** (mido-client 본체가 아니라 사용자 앱에):

```gradle
implementation 'io.github.resilience4j:resilience4j-circuitbreaker:2.2.0'
implementation 'io.github.resilience4j:resilience4j-ratelimiter:2.2.0'
implementation 'io.github.resilience4j:resilience4j-retry:2.2.0'
```

**2. Resilience4j 데코레이터로 감싸는 단일 인터셉터 작성:**

```java
package com.yourapp.interceptor;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.time.Duration;

public class PaymentResilienceInterceptor implements ClientHttpRequestInterceptor {

    private static final RateLimiter RATE_LIMITER = RateLimiter.of("payment",
            RateLimiterConfig.custom()
                    .limitForPeriod(100)
                    .limitRefreshPeriod(Duration.ofSeconds(1))
                    .timeoutDuration(Duration.ofMillis(500))
                    .build());

    private static final CircuitBreaker CIRCUIT_BREAKER = CircuitBreaker.of("payment",
            CircuitBreakerConfig.custom()
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofSeconds(30))
                    .slidingWindowSize(20)
                    .build());

    private static final Retry RETRY = Retry.of("payment",
            RetryConfig.custom()
                    .maxAttempts(3)
                    .waitDuration(Duration.ofMillis(200))
                    .build());

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        try {
            return Decorators.ofCallable(() -> execution.execute(request, body))
                    .withCircuitBreaker(CIRCUIT_BREAKER)
                    .withRateLimiter(RATE_LIMITER)
                    .withRetry(RETRY)
                    .decorate()
                    .call();
        } catch (IOException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
```

**3. YAML에서 채널에 등록:**

```yaml
mido-client:
  channels:
    payment:
      primary:
        url: https://api.payment.com
        interceptors:
          - "com.yourapp.interceptor.PaymentResilienceInterceptor"
```

**Tips**:

- 커스텀 인터셉터는 `mido-client`의 로깅 인터셉터보다 **먼저** 등록되므로, 재시도 시도와 rate-limit 대기가 별도 로그 엔트리로 찍힘 — 연쇄 장애 디버깅에 유용.
- 가능하면 채널당 인터셉터 클래스 1개로 유지하세요. 데코레이터 내부 상태(open/closed 윈도우, 재시도 카운터)는 registry name 기준으로 격리되므로, SLA가 다른 채널끼리 공유하면 cross-talk 발생.
- 재컴파일 없이 YAML로 튜닝하고 싶다면 앱에 `resilience4j-spring-boot3` starter를 추가하고 `application.yml`에 registry 설정. 인터셉터 안에서 `static final` 대신 채널 이름으로 데코레이터를 조회하면 됩니다.
- 3개 중 일부만 필요하면(예: rate limiter만) 안 쓰는 데코레이터는 빼세요. 필요한 것만 체이닝하는 게 스택 트레이스도 얕고 동작도 예측 가능.

### 채널 컨텐트 타입 (JSON / XML)

채널마다 단일 요청 `Content-Type`을 사용합니다. `type`으로 한 번 지정하며, 생략하면 `json`이 적용됩니다.

```yaml
mido-client:
  channels:
    legacySoap:
      type: xml             # 요청 Content-Type: application/xml
      primary:
        url: https://soap.example.com
    modernRest:
      # type 생략 → 기본값 json
      primary:
        url: https://api.example.com
```

**동작**:

- `type: json` (기본값) — 모든 요청에 `Content-Type: application/json` 헤더가 자동 추가되고, POJO 바디는 Jackson으로 직렬화됩니다.
- `type: xml` — 모든 요청에 `Content-Type: application/xml` 헤더가 자동 추가됩니다. 바디는 직렬화된 XML `String`으로 전달하세요 (Jackson XML 마샬링은 기본 번들에 포함되어 있지 않습니다 — POJO ↔ XML 변환이 필요하면 `interceptors`로 직접 컨버터를 등록하세요).

### Gzip 압축

채널별로 HTTP 바디 압축을 opt-in 방식으로 활성화합니다. 송/수신 방향은 독립적으로 설정 가능합니다.

```yaml
mido-client:
  channels:
    payment:
      primary:
        url: https://api.payment.com
        gzip:
          request: true                    # 요청 바디 압축
          response: true                   # 압축 응답 요청 및 자동 해제
          min-size: 1024                   # 작은 바디는 압축 skip
          max-decompressed-size: 10485760  # 10 MB 안전 cap
```

**동작**:

- `request: true` — 바디 크기가 `min-size` 이상이면 gzip 압축 후 전송, `Content-Encoding: gzip` 헤더 자동 추가.
- `response: true` — 요청에 `Accept-Encoding: gzip`을 박고, 서버가 `Content-Encoding: gzip`으로 응답하면 메시지 컨버터가 보기 전에 투명하게 해제.
- `max-decompressed-size`는 압축 폭탄(decompression bomb) 방어 — 해제 결과가 cap을 넘으면 즉시 `IOException`이 발생하며 메모리 사용량은 버퍼 + cap 수준으로 제한됩니다.

인터셉터 등록 순서가 보존되어 로깅에는 항상 평문 바디가 찍히고, 네트워크에는 압축된 바이트가 흘러갑니다.

### HTTP 전송 방식 (`simple` / `jdk`)

하부 request factory를 전역 또는 엔드포인트 단위로 선택합니다. 기본값은 `simple`이라 기존 설정의 동작은 그대로 유지됩니다.

```yaml
mido-client:
  enabled: true
  client-type: jdk               # 모든 채널의 전역 기본값
  channels:
    payment:
      primary:
        url: https://api.payment.com
        # 전역값 상속 -> jdk
      secondary:
        url: https://process.payment.com
        client-type: simple      # 이 엔드포인트만 오버라이드
```

| 값        | 하부 factory                      | 커넥션 재사용                             | HTTP/2 |
|----------|----------------------------------|-------------------------------------|--------|
| `simple` | `SimpleClientHttpRequestFactory` | JVM 전역 `HttpURLConnection` keep-alive | 미지원    |
| `jdk`    | `JdkClientHttpRequestFactory`    | 채널별 `HttpClient` 커넥션 풀               | 지원     |

**`jdk`를 선택하는 이유**: `simple` 클라이언트는 JVM 전역 keep-alive 캐시로 커넥션을 재사용하므로 모든 채널이 사실상 하나의 풀을 공유합니다. `jdk`는 채널/엔드포인트마다 독립된 `HttpClient`(= 독립 커넥션 풀)를 주므로, 이 라이브러리가 지향하는 채널별 커넥션 격리를 실제로 구현합니다. 고throughput 채널이나 HTTP/2가 필요한 경우 권장합니다.

**동작 참고**: `jdk` 전송은 리다이렉트를 따르며(`Redirect.NORMAL` — HTTPS→HTTP 다운그레이드는 거부), `connect-timeout-seconds` / `read-timeout-seconds`를 동일하게 적용합니다. `simple`과 달리 JDK `HttpClient`는 명시적으로 설정하지 않는 한 JVM 기본 프록시 셀렉터를 사용하지 않습니다. 로깅/gzip 인터셉터 동작은 두 전송 모두 동일합니다.

### ChannelContext와 MDC

`BaseExternalApi.withDefaultChannelAction()`을 사용하면 `ChannelContext`가 자동으로 관리됩니다. `ChannelContext`는
`ScopedValue`(Java 25) 기반이라 액션이 호출의 동적 범위 동안만 바인딩되고 정상 반환·예외 모두에서 자동 해제됩니다 — 수동
`set`/`clear`가 없습니다. 직접 사용하는 경우:

```java
// 반환값이 없는 형태
ChannelContext.runWithChannelAction("payment.processPayment", () -> {
    // REST 호출 — MDC를 통해 모든 로그에 channelAction이 포함됨
});

// 반환값이 있는 형태
String status = ChannelContext.callWithChannelAction("payment.processPayment", () ->
        restClient.get().uri("/status").retrieve().body(String.class));
```

`logback.xml` 패턴에서 `channelAction` 키를 사용할 수 있습니다.

```xml
<!-- logback.xml -->
<pattern>%d [%X{channelAction}] %-5level %msg%n</pattern>
```

## 로깅

| 레벨        | 콘솔 출력 | 파일 출력 (`MidoClientFileLog`) |
|-----------|-------|-----------------------------|
| `off`     | -     | -                           |
| `console` | Yes   | -                           |
| `file`    | -     | Yes                         |
| `all`     | Yes   | Yes                         |

각 로그 항목에는 채널 액션, HTTP 메서드, URL, 요청/응답 바디, 응답 시간, HTTP 상태코드가 포함됩니다.

파일 로깅을 사용하려면 `logback.xml`에 `MidoClientFileLog` 로거를 추가합니다.

```xml

<appender name="MIDO_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/mido-client.log</file>
    <!-- rolling policy 설정 -->
</appender>

<logger name="MidoClientFileLog" level="INFO" additivity="false">
<appender-ref ref="MIDO_FILE"/>
</logger>
```

## 라이선스

이 프로젝트는 Apache License 2.0을 따릅니다 — 자세한 내용은 [LICENSE](LICENSE) 파일을 참조하세요.

## 기여

1. 레포지토리를 포크합니다
2. 기능 브랜치를 생성합니다 (`git checkout -b feature/your-feature`)
3. 변경사항을 커밋합니다
4. 브랜치에 푸시합니다
5. Pull Request를 열어주세요
