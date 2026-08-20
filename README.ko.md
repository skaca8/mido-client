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
- **채널별 커넥션 격리** — 채널/엔드포인트마다 독립된 `java.net.http.HttpClient`를 가지므로 포화된 채널이 다른 채널을 굶기지 않음, HTTP/2 지원
- **채널별 gzip 압축** — 요청 바디는 `min-size` 임계값 이상일 때만 압축, 응답은 자동 해제 + 압축 폭탄 방어 cap(`max-decompressed-size`)
- **채널별 컨텐트 타입** — `json`(기본) / `xml` 중 채널 단위로 선택, 요청 `Content-Type` 헤더가 자동 설정됨
- **부팅 시 설정 검증** — `@Validated` Bean Validation으로 잘못된 YAML을 시작 시점에 거부, `BindValidationException`에 어떤 필드가 잘못되었는지 명시
- **ChannelContext + MDC 연동** — 스코프 기반(`ScopedValue`) 채널 액션 추적, SLF4J MDC와 통합되어 로그에 자동 포함. `@ChannelName` + `@ChannelAction`으로 선언적 바인딩 가능(선택, AOP 런타임 필요)
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
    implementation 'com.github.skaca8:mido-client:3.0.0'
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
    <version>3.0.0</version>
</dependency>
```

> 특정 릴리즈를 사용하려면 위 버전을 원하는 태그 또는 커밋 해시로 변경하세요.

#### Maven Central을 통한 방법 (정식 릴리즈)

**Gradle**

```gradle
implementation 'io.github.skaca8:mido-client:3.0.0'
```

**Maven**

```xml

<dependency>
    <groupId>io.github.skaca8</groupId>
    <artifactId>mido-client</artifactId>
    <version>3.0.0</version>
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
| `log-body`                | Boolean        | `true`    | 로그에 요청·응답 body 포함 여부. PII·카드·토큰이 흐르는 엔드포인트는 `false` |
| `log-max-body-bytes`      | Integer        | `8192`    | 로그 라인당 body 최대 바이트, 초과분은 `...(truncated N bytes)`. `0`은 무제한 |
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

### 설정 검증

`mido-client`는 애플리케이션 시작 시점에 `@ConfigurationProperties`를 검증합니다. 잘못된 설정은 `BindValidationException`과 함께 어떤 필드가 잘못되었고 거부된 값이 무엇인지 표시하며 컨텍스트 로드에 실패합니다. 다음 경우 검증에 실패합니다.

- `url`이 비어있거나 `^https?://.+`에 매치되지 않음
- `read-timeout-seconds` 또는 `connect-timeout-seconds`가 0 이하
- `gzip.min-size`가 음수
- `gzip.max-decompressed-size`가 0 이하
- `headers[].name` 또는 `headers[].value`가 비어있음
- 채널에 필수 `primary` 엔드포인트가 없음
- `type`이 명시적으로 `null`로 지정됨 (값은 `json` 또는 `xml`이어야 함 — 그 외 값은 Spring enum 바인더가 시작 시점에 별도로 거부)

빈 검증 외에, `MidoClientFactory` 빈이 기동 시점에 다음을 확인합니다. 오타가 첫 요청까지 숨어 있지 않습니다.

- `charset`이 알 수 없는 문자셋 → `Invalid charset '<name>' for channel: <channel>`
- `interceptors[]` 항목을 로드할 수 없거나, `ClientHttpRequestInterceptor` 미구현이거나, public 무인자 생성자가 없음 → 메시지에 채널명과 엔드포인트가 함께 표시됨

이 검사에서 인터셉터 클래스는 로드·검사만 하고 **인스턴스는 만들지 않습니다.** 부수효과가 있는 생성자가 두 번 실행되지 않습니다.

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
- `type: xml` — 모든 요청에 `Content-Type: application/xml` 헤더가 자동 추가됩니다. 직렬화된 XML `String` 바디는 항상 동작합니다. POJO ↔ XML은 클래스패스에 따릅니다 — `mido-client`는 `RestClient` 기본 컨버터 목록을 유지하므로(채널 `charset` 적용을 위해 `String` 컨버터만 교체합니다) 애플리케이션에 `jackson-dataformat-xml`을 추가하면 `MappingJackson2XmlHttpMessageConverter`가 활성화됩니다. `mido-client`의 의존성은 아닙니다.

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

- `request: true` — 바디 크기가 `min-size` 이상이면 gzip 압축 후 전송, `Content-Encoding: gzip` 헤더 추가와 함께 `Content-Length`를 압축 후 길이로 갱신합니다. 빈 바디는 `min-size: 0`이어도 압축하지 않으므로 바디 없는 `GET`에 gzip 헤더가 붙지 않습니다.
- `response: true` — 요청에 `Accept-Encoding: gzip`을 박고, 서버가 `Content-Encoding: gzip`으로 응답하면 메시지 컨버터가 보기 전에 투명하게 해제.
- `max-decompressed-size`는 압축 폭탄(decompression bomb) 방어 — 해제 결과가 cap을 넘으면 즉시 `IOException`이 발생하며 메모리 사용량은 버퍼 + cap 수준으로 제한됩니다.

인터셉터 등록 순서가 보존되어 로깅에는 항상 평문 바디가 찍히고, 네트워크에는 압축된 바이트가 흘러갑니다. 전체 체인은 다음과 같습니다.

```
커스텀 인터셉터  →  mido 로깅  →  mido gzip  →  전송
```

⚠️ **커스텀 인터셉터가 최외곽이라 요청 body를 압축 *전* 원문으로 봅니다.** body에 서명·해시를 만드는 인터셉터(HMAC, 체크섬, content-digest 헤더)는 평문 기준으로 계산하는데 서버는 gzip된 바이트를 받으므로 검증이 깨집니다. `gzip.request: true`인 채널에서는 인터셉터 안에서 직접 압축한 바이트로 서명하거나, 해당 채널의 요청 압축을 끄세요.

### HTTP 전송

모든 채널은 `java.net.http.HttpClient` 기반의 `JdkClientHttpRequestFactory`를 사용합니다. 설정할 것은 없습니다 — 요점은 **채널/엔드포인트마다 독립된 `HttpClient`, 즉 독립 커넥션 풀을 갖는다**는 것입니다. 한 채널이 느려지거나 포화되어도 다른 채널의 커넥션을 잡아먹지 않습니다. 이 격리가 이 라이브러리의 존재 이유입니다.

| 항목            | 값                                                        |
|---------------|----------------------------------------------------------|
| Request factory | `JdkClientHttpRequestFactory`                            |
| 커넥션 풀         | 채널/엔드포인트당 1개                                             |
| HTTP/2        | 지원 (협상, HTTP/1.1 폴백)                                     |
| 리다이렉트        | 따라감 (`Redirect.NORMAL` — HTTPS→HTTP 다운그레이드 거부)           |
| 프록시           | `http.proxyHost` / `https.proxyHost` 시스템 프로퍼티를 따름 — mido-client는 `HttpClient.Builder.proxy()`를 호출하지 않고, 호출하지 않은 빌더는 `ProxySelector.getDefault()`를 쓴다고 문서화되어 있음 |

**`read-timeout-seconds`는 소켓 유휴 타임아웃이 아니라 교환 전체 데드라인입니다.** 요청 전송부터 응답 body 소진까지를 덮고 `HttpTimeoutException`으로 만료됩니다. Spring은 이를 `HttpRequest.Builder#timeout` 대신 자체 `TimeoutHandler`로 구현하며([JDK-8258397](https://bugs.openjdk.org/browse/JDK-8258397) 우회), 타이머는 응답 body 스트림이 close될 때 취소됩니다. 따라서 패킷 간격이 아니라 **호출 전체 예상 시간** 기준으로 값을 잡으세요 — 느리지만 꾸준히 흘러오는 응답도 끊깁니다. `mido-client`는 로깅을 위해 응답 전문을 버퍼링하므로 애초에 스트리밍 다운로드용 클라이언트가 아닙니다.

`connect-timeout-seconds`는 `HttpClient.Builder.connectTimeout`에 매핑되며 `HttpConnectTimeoutException`으로 따로 구분됩니다. [`FailureType`](#로깅)이 "서버에 도달하지 못함"과 "이미 전송되었을 수 있음"을 구분할 수 있는 근거입니다.

⚠️ **응답은 스트리밍이 아니라 메모리에 버퍼링됩니다.** 로깅 인터셉터가 body를 재read해야 하므로 모든 전송이 `BufferingClientHttpRequestFactory`로 감싸져 있고, 따라서 `log` / `log-body` 설정과 무관하게 응답 전문이 `byte[]`로 힙에 올라갑니다. `log-max-body-bytes`는 **로그**로 나가는 양의 상한이지 **힙**의 상한이 아닙니다. 힙 크기 대비 유의미하게 큰 응답을 주는 엔드포인트에 채널을 붙이지 마세요 — 파일 다운로드용 클라이언트가 아닙니다.

**라이프사이클**: 모든 클라이언트는 Spring 컨텍스트 종료 시 정리됩니다(`MidoClientFactory`가 `DisposableBean` 구현). `close()` 대신 `HttpClient.shutdown()`을 사용해 진행 중인 요청이 종료를 붙잡지 않게 합니다 — in-flight 교환이 끝나면 selector·풀 스레드가 종료됩니다.

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

#### 선언적 바인딩: `@ChannelName` + `@ChannelAction`

호출마다 람다로 감싸는 건 반복적이고, 하나 빠뜨리면 눈에 보이지 않습니다 — 로그에 `channelAction: unknown`만 찍힙니다. 애노테이션으로 반복을 없앱니다.

```java
@Service
@ChannelName("payment")                  // 클래스 = 채널 (어느 외부 시스템)
public class PaymentAdapter {

    private final RestClient client;

    public PaymentAdapter(MidoClientFactory factory) {
        this.client = factory.getOrCreateClient("payment");
    }

    @ChannelAction                        // -> "payment.getStatus"
    public PaymentStatus getStatus(String id) {
        return client.get().uri("/payments/{id}/status", id).retrieve().body(PaymentStatus.class);
    }

    @ChannelAction("processPayment")      // -> "payment.processPayment"
    public PaymentResult process(PaymentRequest request) {
        return client.post().uri("/payments/process").body(request).retrieve().body(PaymentResult.class);
    }
}
```

두 축을 분리한 것은 의도적입니다. 채널은 클래스의 속성(*어느* 외부 시스템)이고 액션은 메서드의 속성(*무슨* 호출)입니다. 애노테이션 하나가 둘을 겸하면 메서드마다 채널이 갈릴 수 있어 "클래스 = 채널" 불변식이 깨집니다. 진짜 채널 2개를 쓰는 클래스는 둘로 쪼개세요 — 메서드 레벨 채널 오버라이드는 제공하지 않습니다.

**AOP 런타임은 소비 측이 넣어야 합니다.** mido-client는 aspectjweaver를 `compileOnly`로 두므로, 애플리케이션에 `spring-boot-starter-aop`(Boot 3)를 추가해야 애스펙트가 활성화됩니다. 없으면 애노테이션은 무동작이고 나머지는 그대로입니다.

```gradle
implementation 'org.springframework.boot:spring-boot-starter-aop'
```

**`@ChannelName` 누락은 시끄럽게 실패합니다.** `@ChannelName` 없는 클래스에 `@ChannelAction`을 붙이면 클래스명과 메서드명을 담은 `IllegalStateException`이 납니다 — 조용히 `unknown`으로 흘러가지 않습니다. 다만 기동 시점이 아니라 첫 호출에서 드러납니다. 애스펙트는 자신이 호출되지 않은 빈을 알 수 없습니다.

⚠️ **프록시 기반이라 알려진 한계가 있습니다.** 어드바이스는 Spring 프록시를 통한 외부 호출에서만 동작합니다. 다음에는 **적용되지 않습니다.**

- 같은 빈의 다른 메서드에서 호출 (self-invocation)
- `private` 또는 `final` 메서드
- Spring 빈이 아닌 객체

이 경우 액션이 바인딩되지 않고 로그에 `unknown`이 찍힙니다. **애노테이션이 붙어 있다는 게 적용되었다는 증거가 아닙니다** — 선언적 방식이 람다보다 나쁜 유일한 지점입니다. 람다는 빠뜨리면 코드에서 보입니다. 중요한 경로에는 `ChannelContext.callWithChannelAction(...)`이나 `BaseExternalApi`를 계속 쓰세요.

중첩은 안전합니다. `ChannelContext`가 이전 MDC 값을 저장·복원하므로, 이미 바인딩된 액션 안에서 애노테이션 메서드를 호출해도 반환 후 바깥 액션이 정상 복원됩니다.

애스펙트는 `Ordered.HIGHEST_PRECEDENCE + 100`으로 `@Transactional` 바깥에서 동작하므로 트랜잭션 커밋까지 액션이 유지됩니다. 직접 `ChannelActionAspect` 빈을 정의하면 교체됩니다.

#### 어느 쪽을 쓸까

| | `@ChannelAction` | `BaseExternalApi` / `ChannelContext` |
|---|---|---|
| 보일러플레이트 | 없음 | 호출당 람다 하나 |
| `spring-boot-starter-aop` 필요 | 필요 | 불필요 |
| self-invocation, `private`/`final`, 비빈에서 동작 | 안 됨 | 됨 |
| 바인딩 누락이 코드에서 보이는지 | 안 보임 | 보임 |

둘 다 지원되며 함께 쓸 수 있습니다. `BaseExternalApi`는 deprecated가 아닙니다. 평범한 어댑터 빈에는 애노테이션을, 프록시가 닿지 않는 곳이나 호출 지점에서 바인딩을 명시하고 싶은 곳에는 명시적 방식을 쓰세요.

## 로깅

`log`은 **목적지**를 정하며, 심각도는 정하지 않습니다.

| 레벨        | 콘솔 출력 | 파일 출력 (`MidoClientFileLog`) |
|-----------|-------|-----------------------------|
| `off`     | -     | -                           |
| `console` | Yes   | -                           |
| `file`    | -     | Yes                         |
| `all`     | Yes   | Yes                         |

심각도는 호출 결과에 따라 결정됩니다. 로그 본문을 파싱하지 않고 레벨만으로 알림을 걸 수 있습니다.

| 결과                        | 레벨      |
|---------------------------|---------|
| 요청 라인, 2xx / 3xx 응답       | `info`  |
| 4xx 응답                    | `warn`  |
| 5xx 응답                    | `error` |
| 전송 실패 (응답 없음)             | `error` |

각 로그 항목에는 채널 액션, HTTP 메서드, URL, 요청/응답 바디, 응답 시간, HTTP 상태코드가 포함됩니다.

`log: file` 또는 `log: all`을 지정했는데 애플리케이션에 `MidoClientFileLog` 로거가 선언되어 있지 않으면 기동 시 경고가 나갑니다 — 그대로 두면 해당 로그가 조용히 root 로거로 흘러갑니다. 이 검사는 Logback에서만 동작하며, 다른 SLF4J 바인딩에서는 추측하지 않고 건너뜁니다.

응답이 오기 전에 실패한 호출(connect/read 타임아웃, DNS, TLS)은 `[mido-client failure]` 라인으로 **error** 레벨에 별도 기록되며 소요시간, 실패 분류, 예외 타입·메시지를 담습니다. 스택트레이스는 여기서 반복하지 않습니다 — 예외는 호출측으로 그대로 전파됩니다.

```
[mido-client failure] channelAction: payment.pay, method: POST, url: https://api.payment.com/pay,
elapsedMs: 3011, failureType: timeout, delivery: UNKNOWN, exception: java.net.SocketTimeoutException: Read timed out
```

`failureType` / `delivery`는 `FailureType.classify(Throwable)`의 결과이며, `cause` 체인을 직접 파헤치는 대신 이 메서드를 호출하면 됩니다. mido-client는 Spring·JDK가 던지는 예외를 **감싸거나 대체하지 않습니다.** 기존 예외 처리와 Resilience4j 예외 판정이 그대로 동작합니다.

```java
catch (RestClientException e) {
    if (FailureType.classify(e).getDelivery() == FailureType.Delivery.NOT_DELIVERED) {
        retry();   // 서버에 도달하지 않았으므로 비멱등 요청도 재시도 가능
    }
}
```

| `failureType`                     | `delivery`      | 의미                        |
|-----------------------------------|-----------------|---------------------------|
| `dns`                             | `NOT_DELIVERED` | 호스트명 해석 실패                |
| `tls`                             | `NOT_DELIVERED` | 핸드셰이크·인증서 실패              |
| `connect`                         | `NOT_DELIVERED` | 연결 거부·도달 불가·connect 타임아웃  |
| `timeout`                         | `UNKNOWN`       | 타임아웃, 전송 여부 판단 불가         |
| `client-error` / `server-error`   | `DELIVERED`     | 서버가 4xx / 5xx로 응답         |
| `unknown`                         | `UNKNOWN`       | 매칭되는 분류 없음                |

connect 타임아웃은 `HttpConnectTimeoutException`으로 도착해 `connect`(미도달)로 분류되고, 응답 타임아웃은 `HttpTimeoutException`으로 도착해 `timeout`(도달 여부 불명)으로 남습니다 — 요청이 이미 전송되었을 수 있기 때문입니다.

PII·카드·토큰이 흐르는 엔드포인트에서 바디를 로그에 남기지 않으려면 `log-body: false`를 지정합니다. 바디를 아예 읽지 않고(마스킹이 아니라 미수집) `body: (omitted)`로 표기되며, 상태코드·소요시간·채널 액션은 그대로 남습니다.

```yaml
mido-client:
  channels:
    payment:
      primary:
        url: https://api.payment.com
        log: console
        log-body: false        # 카드번호·토큰이 로그로 나가지 않는다
```

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
