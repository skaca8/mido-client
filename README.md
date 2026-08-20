# Spring Mido Client

[![Maven Central](https://img.shields.io/maven-central/v/io.github.skaca8/mido-client.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.skaca8/mido-client)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**[한국어](README.ko.md)** | English

> YAML-driven multi-channel RestClient management for Spring Boot 3.2+

`mido-client` eliminates boilerplate `RestClient` configuration by letting you define multiple external API channels —
each with its own URL, auth, timeout, logging, and interceptors — entirely in `application.yml`. No `@Bean` methods, no
factory classes, no repeated setup code.

## Why mido-client?

|                                     | RestClient (vanilla) | OpenFeign                    | mido-client         |
|-------------------------------------|----------------------|------------------------------|---------------------|
| Configuration style                 | Java `@Bean`         | Java interface + annotations | YAML only           |
| Multi-channel setup                 | Manual per bean      | Manual per interface         | Built-in            |
| Dual endpoint per service           | Manual               | Not supported                | Built-in            |
| Request/response logging            | Manual interceptor   | Plugin required              | Built-in (4 levels) |
| Client instance caching             | Manual               | Managed by framework         | Built-in            |
| Based on Spring Boot 3.2 RestClient | Yes                  | No (uses Feign)              | Yes                 |

## Features

- **Multi-channel support** — define unlimited external API channels, each with `primary` / `secondary` dual endpoint
- **Automatic client caching** — one `RestClient` instance per channel/endpoint, thread-safe via `ConcurrentHashMap`
- **4-level built-in logging** — `off` / `console` / `file` / `all` (console + file simultaneously), includes body, URL,
  response time
- **Per-endpoint authentication** — Bearer, Basic, API Key
- **Smart charset detection** — Content-Type header → UTF-8 validation → channel default fallback
- **Custom interceptors** — register any `ClientHttpRequestInterceptor` by class name in YAML
- **Per-channel connection isolation** — every channel/endpoint gets its own `java.net.http.HttpClient`, so one saturated channel cannot starve the others; HTTP/2 included
- **Per-channel gzip** — opt-in request compression with `min-size` skip threshold; response auto-decompression with decompression-bomb defense cap (`max-decompressed-size`)
- **Per-channel content type** — pick `json` (default) or `xml` per channel; the request `Content-Type` header is set automatically
- **Fail-fast configuration validation** — `@Validated` Bean Validation rejects malformed YAML at startup with a `BindValidationException` indicating the offending field
- **ChannelContext with MDC** — scoped (`ScopedValue`) channel action tracking, integrated with SLF4J MDC for
  distributed log tracing
- **Zero-code Auto-Configuration** — activated with a single `mido-client.enabled: true` property

## Requirements

| Requirement | Version                            |
|-------------|------------------------------------|
| Java        | 25                                 |
| Spring Boot | 3.5.x (built & tested with 3.5.16) |
| Gradle      | 8.14.4                             |

> Java 25 is required because `ChannelContext` is built on the final `java.lang.ScopedValue` API (JEP 506, Java 25).
> Consumers need a Spring Framework 6.2 release whose bundled ASM can read Java 25 bytecode (recent 6.2.x patches;
> Spring Boot 3.2.x cannot). This library is built and tested against Spring Boot 3.5.16.

## Quick Start

### 1. Add Dependency

#### via JitPack (GitHub)

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

> To use a specific release, replace the version above with a tag or a commit hash.

#### via Maven Central (published release)

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

### 2. Configure `application.yml`

```yaml
mido-client:
  enabled: true
  channels:
    payment:
      title: "Payment Service"
      charset: UTF-8
      type: json    # json (default) | xml
      primary:
        url: https://api.payment.com
        read-timeout-seconds: 30
        connect-timeout-seconds: 5
        authorization:
          type: bearer
          token: ${PAYMENT_QUERY_TOKEN}
        log: console
      secondary: # optional: secondary endpoint for the same service
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

### 3. Use in Your Service

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

> `BaseExternalApi.withDefaultChannelAction()` automatically sets and clears `ChannelContext` around each call,
> including on exception.

## Configuration Reference

### Channel (`mido-client.channels.<name>`)

| Property  | Type        | Default | Description                                              |
|-----------|-------------|---------|----------------------------------------------------------|
| `title`   | String      | -       | Channel description (optional)                           |
| `charset` | String      | `UTF-8` | Default character encoding for response body             |
| `type`    | ContentType | `json`  | Request `Content-Type` for the channel — `json` / `xml` |

### Endpoint (`primary` / `secondary`)

| Property                  | Type           | Default   | Description                                                   |
|---------------------------|----------------|-----------|---------------------------------------------------------------|
| `url`                     | String         | -         | **Required.** Base URL of the endpoint                        |
| `title`                   | String         | -         | Endpoint description (optional)                               |
| `read-timeout-seconds`    | Long           | `60`      | Read timeout                                                  |
| `connect-timeout-seconds` | Long           | `3`       | Connection timeout                                            |
| `log`                     | LogLevel       | `console` | `off` / `console` / `file` / `all`                            |
| `log-body`                | Boolean        | `true`    | Include request/response bodies in the log lines. Set `false` on endpoints carrying PII, card, or token data |
| `log-max-body-bytes`      | Integer        | `8192`    | Max body bytes per log line; the rest becomes `...(truncated N bytes)`. `0` = no limit |
| `authorization.type`      | TokenType      | -         | `bearer` / `basic` / `api_key`                                |
| `authorization.token`     | String         | -         | Authentication token value                                    |
| `headers`                 | List           | -         | Static headers to attach to every request                     |
| `interceptors`            | List\<String\> | -         | Fully-qualified class names of `ClientHttpRequestInterceptor` |
| `gzip.request`            | Boolean        | `false`   | Compress outgoing request body (`Content-Encoding: gzip`)     |
| `gzip.response`           | Boolean        | `false`   | Force `Accept-Encoding: gzip` and auto-decompress response    |
| `gzip.min-size`           | Integer        | `1024`    | Skip request compression when body is smaller than this (bytes) |
| `gzip.max-decompressed-size` | Integer     | `10485760` | Throw `IOException` if decompressed response exceeds this (bytes — decompression-bomb defense) |

### Global

| Property                  | Type       | Default  | Description                                                        |
|---------------------------|------------|----------|--------------------------------------------------------------------|
| `mido-client.enabled`     | Boolean    | `false`  | Enable/disable the entire library                                  |

### Configuration Validation

`mido-client` validates `@ConfigurationProperties` at application startup. Misconfiguration causes the context to fail to start with a `BindValidationException` that indicates the offending field and the rejected value. Examples that fail validation:

- `url` is blank or doesn't match `^https?://.+`
- `read-timeout-seconds` or `connect-timeout-seconds` is zero or negative
- `gzip.min-size` is negative
- `gzip.max-decompressed-size` is zero or negative
- `headers[].name` or `headers[].value` is blank
- A channel is missing its required `primary` endpoint
- `type` is explicitly set to `null` (must be `json` or `xml`; unknown values are rejected separately by Spring's enum binder at startup)

Beyond bean validation, the `MidoClientFactory` bean checks the following at startup, so a typo does not wait for the first request to surface:

- `charset` names an unknown charset → `Invalid charset '<name>' for channel: <channel>`
- an `interceptors[]` entry cannot be loaded, does not implement `ClientHttpRequestInterceptor`, or has no public no-arg constructor → the message names the channel and endpoint

Interceptor classes are loaded and inspected but **not instantiated** during this check, so a constructor with side effects does not run twice.

## Advanced Usage

### Custom Interceptors

Implement `ClientHttpRequestInterceptor` and register by class name in YAML:

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

> Custom interceptors are instantiated via the no-arg public constructor
> (`Class.forName(...).getDeclaredConstructor().newInstance()`). The resulting instance is **not** a Spring-managed bean,
> so neither constructor injection nor `@Autowired` field injection works — even if the class is also annotated
> `@Component`, the bean Spring manages is a *separate* instance that `mido-client` never sees.
>
> Two patterns are practical today:
>
> 1. **`static` fields** — best for stateless interceptors (preferred).
> 2. **`ApplicationContextHolder` escape hatch** — store the `ApplicationContext` in a `static` field at startup and
>    look up beans inside `intercept(...)`. Treat as an escape hatch, not recommended design.
>
> A first-class "register interceptor by Spring bean name" option is on the roadmap for the next minor release.
>
> **Fail-fast behavior**: if the class cannot be loaded, has no public no-arg constructor, or does not implement
> `ClientHttpRequestInterceptor`, the first call to `MidoClientFactory.getOrCreateClient(...)` throws
> `IllegalStateException` naming the channel and the offending class.

### Resilience (Rate Limiter / Circuit Breaker / Retry)

`mido-client` intentionally does **not** bundle a resilience layer — bring your own (Resilience4j, Sentinel, Failsafe, Spring Retry, …) via the `interceptors:` config. Below is a copy-paste-ready recipe with Resilience4j.

**1. Add Resilience4j to your application's dependencies** (NOT to `mido-client` itself):

```gradle
implementation 'io.github.resilience4j:resilience4j-circuitbreaker:2.2.0'
implementation 'io.github.resilience4j:resilience4j-ratelimiter:2.2.0'
implementation 'io.github.resilience4j:resilience4j-retry:2.2.0'
```

**2. Write a single interceptor that wraps the call with Resilience4j decorators:**

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

**3. Register on the channel via YAML:**

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

- Custom interceptors are registered **before** `mido-client`'s logging interceptor, so retry attempts and rate-limit waits show up as separate log entries — useful for debugging cascading failures.
- Prefer one interceptor class per channel — the decorators' state (open/closed window, retry counters) is keyed by the registry name, so sharing across channels with different SLAs causes cross-talk.
- For YAML-driven tuning without recompiling, add the `resilience4j-spring-boot3` starter to your app and configure registries in `application.yml`; then look up decorators by channel name inside the interceptor instead of building them as `static final` fields.
- If you only need one of the three (e.g. rate limiting), drop the unused decorators — chaining only what you need keeps stack traces shallow and behavior predictable.

### Channel Content Type (JSON / XML)

Each channel sends requests with a single default `Content-Type`. Pick it once per channel via `type`; if omitted, `json` is used.

```yaml
mido-client:
  channels:
    legacySoap:
      type: xml             # outgoing Content-Type: application/xml
      primary:
        url: https://soap.example.com
    modernRest:
      # type omitted → defaults to json
      primary:
        url: https://api.example.com
```

**Behavior**:

- `type: json` (default) — `Content-Type: application/json` is attached to every request; POJO bodies are serialized via Jackson.
- `type: xml` — `Content-Type: application/xml` is attached to every request. A pre-serialized XML `String` body always works. POJO ↔ XML depends on your classpath: `mido-client` keeps `RestClient`'s default converter list (only the `String` converter is replaced, to apply the channel `charset`), so adding `jackson-dataformat-xml` to your application enables `MappingJackson2XmlHttpMessageConverter`. It is not a `mido-client` dependency.

### Gzip Compression

Per-channel opt-in HTTP body compression. Each direction is independently toggleable.

```yaml
mido-client:
  channels:
    payment:
      primary:
        url: https://api.payment.com
        gzip:
          request: true                    # compress outgoing body
          response: true                   # request gzipped response and auto-decompress
          min-size: 1024                   # skip compression for small bodies
          max-decompressed-size: 10485760  # 10 MB safety cap
```

**Behavior**:

- `request: true` — bodies ≥ `min-size` bytes are gzipped before sending; `Content-Encoding: gzip` is added and `Content-Length` is updated to the compressed length. An empty body is never compressed, even with `min-size: 0`, so a bodyless `GET` does not pick up a gzip header.
- `response: true` — `Accept-Encoding: gzip` is sent; if the server replies with `Content-Encoding: gzip`, the body is transparently decompressed before reaching your message converters.
- `max-decompressed-size` defends against decompression bombs — if the decompressed response exceeds the cap, an `IOException` is thrown immediately and memory stays bounded to roughly buffer + cap.

Interceptors are ordered so that logging always sees plain-text bodies while the network carries compressed bytes. The full chain is:

```
your custom interceptors  →  mido logging  →  mido gzip  →  transport
```

⚠️ **Custom interceptors run outermost, so they see the request body *before* compression.** An interceptor that signs or hashes the body (HMAC, checksum, content-digest header) computes over the plain bytes while the server receives the gzipped ones — the check fails on the server side. On a channel with `gzip.request: true`, either compute the signature over the compressed body yourself inside the interceptor, or leave request compression off for that channel.

### HTTP Transport

Every channel is backed by `JdkClientHttpRequestFactory` over `java.net.http.HttpClient`. There is nothing to configure — the point is that **each channel/endpoint gets its own `HttpClient`, and therefore its own connection pool.** A slow or saturated channel cannot starve the others, which is the isolation this library exists to provide.

| Property        | Value                                                    |
|-----------------|----------------------------------------------------------|
| Request factory | `JdkClientHttpRequestFactory`                            |
| Connection pool | One per channel/endpoint                                 |
| HTTP/2          | Yes (negotiated, with HTTP/1.1 fallback)                  |
| Redirects       | Followed (`Redirect.NORMAL`, refuses HTTPS→HTTP downgrade) |
| Proxy           | `http.proxyHost` / `https.proxyHost` system properties honored — mido-client never calls `HttpClient.Builder.proxy()`, and a builder that does not is documented to use `ProxySelector.getDefault()` |

**`read-timeout-seconds` is a whole-exchange deadline, not a socket idle timeout.** It covers everything from request send to the response body being fully consumed, and expires as `HttpTimeoutException`. Spring implements it with its own `TimeoutHandler` rather than `HttpRequest.Builder#timeout` (a workaround for [JDK-8258397](https://bugs.openjdk.org/browse/JDK-8258397)); the timer is cancelled when the response body stream is closed. So size the value against **total expected call time**, not per-packet gaps — a response that trickles in slowly but steadily will still be cut off. `mido-client` buffers the whole response for logging anyway, so this is not a streaming-download client.

`connect-timeout-seconds` maps to `HttpClient.Builder.connectTimeout` and surfaces separately as `HttpConnectTimeoutException`, which is why [`FailureType`](#logging) can tell "never reached the server" from "may already have been sent".

⚠️ **Responses are buffered in memory, not streamed.** Every transport is wrapped in `BufferingClientHttpRequestFactory` so the logging interceptor can re-read the body, which means the full response is held as a `byte[]` regardless of your `log` / `log-body` settings. `log-max-body-bytes` bounds what reaches the *log*, not what reaches the *heap*. Do not point a channel at an endpoint that returns responses large enough to matter against your heap — this is not a file-download client.

**Lifecycle**: every client is shut down when the Spring context closes (`MidoClientFactory` implements `DisposableBean`). `HttpClient.shutdown()` is used rather than `close()` so that a request still in flight cannot stall shutdown — in-flight exchanges finish and the selector/pool threads then exit.

### ChannelContext & MDC

`BaseExternalApi.withDefaultChannelAction()` binds `ChannelContext` automatically. `ChannelContext` is
backed by a `ScopedValue` (Java 25): the action is bound only for the dynamic extent of the call and
is auto-unbound on both return and exception — there is no manual `set`/`clear`. For direct usage:

```java
// void form
ChannelContext.runWithChannelAction("payment.processPayment", () -> {
    // your REST call — channelAction appears in all logs via MDC
});

// value-returning form
String status = ChannelContext.callWithChannelAction("payment.processPayment", () ->
        restClient.get().uri("/status").retrieve().body(String.class));
```

The action key `channelAction` is available in log patterns:

```xml
<!-- logback.xml -->
<pattern>%d [%X{channelAction}] %-5level %msg%n</pattern>
```

## Logging

`log` picks the **destination**, not the severity:

| Level     | Console | File (`MidoClientFileLog`) |
|-----------|---------|----------------------------|
| `off`     | -       | -                          |
| `console` | Yes     | -                          |
| `file`    | -       | Yes                        |
| `all`     | Yes     | Yes                        |

Severity follows the outcome, so alerting can key on the level instead of parsing log text:

| Outcome                                      | Level   |
|----------------------------------------------|---------|
| Request line, 2xx / 3xx response             | `info`  |
| 4xx response                                 | `warn`  |
| 5xx response                                 | `error` |
| Transport failure (no response)              | `error` |

Each log entry includes: channel action, HTTP method, URL, request/response body, response time, HTTP status.

If `log: file` or `log: all` is configured but the host application never declared a `MidoClientFileLog` logger, a warning is emitted at startup — otherwise those lines would silently fall through to the root logger. The check runs on Logback only; on other SLF4J bindings it is skipped rather than guessed at.

A call that fails before a response arrives (connect / read timeout, DNS, TLS) is logged as a separate `[mido-client failure]` line at **error** level, carrying the elapsed time, the failure classification, and the exception type/message. The stack trace is not repeated there — the exception propagates to the caller unchanged.

```
[mido-client failure] channelAction: payment.pay, method: POST, url: https://api.payment.com/pay,
elapsedMs: 3011, failureType: timeout, delivery: UNKNOWN, exception: java.net.SocketTimeoutException: Read timed out
```

`failureType` / `delivery` come from `FailureType.classify(Throwable)`, which you can call yourself instead of walking the `cause` chain. mido-client **never wraps or replaces** the exception Spring and the JDK throw, so existing handlers and Resilience4j exception predicates keep working:

```java
catch (RestClientException e) {
    if (FailureType.classify(e).getDelivery() == FailureType.Delivery.NOT_DELIVERED) {
        retry();   // the request never reached the server — safe even when not idempotent
    }
}
```

| `failureType`                     | `delivery`      | Meaning                                       |
|-----------------------------------|-----------------|-----------------------------------------------|
| `dns`                             | `NOT_DELIVERED` | Host name did not resolve                     |
| `tls`                             | `NOT_DELIVERED` | Handshake / certificate failure                |
| `connect`                         | `NOT_DELIVERED` | Refused, unreachable, or connect timeout      |
| `timeout`                         | `UNKNOWN`       | Timed out; may or may not have been sent      |
| `client-error` / `server-error`   | `DELIVERED`     | Server answered 4xx / 5xx                     |
| `unknown`                         | `UNKNOWN`       | Nothing matched                               |

A connect timeout arrives as `HttpConnectTimeoutException` and is classified as `connect` (not delivered); a response timeout arrives as `HttpTimeoutException` and stays `timeout` (delivery unknown), because the request may already have been sent.

To keep bodies out of the logs on an endpoint carrying PII, card, or token data, set `log-body: false`. The body is then not read at all (this is omission, not masking) and the line shows `body: (omitted)`; status, elapsed time, and channel action are still logged.

```yaml
mido-client:
  channels:
    payment:
      primary:
        url: https://api.payment.com
        log: console
        log-body: false        # card numbers and tokens never reach the log
```

To enable file logging, add a logger named `MidoClientFileLog` in your `logback.xml`:

```xml

<appender name="MIDO_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/mido-client.log</file>
    <!-- rolling policy -->
</appender>

<logger name="MidoClientFileLog" level="INFO" additivity="false">
<appender-ref ref="MIDO_FILE"/>
</logger>
```

## License

This project is licensed under the Apache License 2.0 — see the [LICENSE](LICENSE) file for details.

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes
4. Push to the branch
5. Open a Pull Request
