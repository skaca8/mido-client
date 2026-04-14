# Spring Mido Client

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

- **Multi-channel support** — define unlimited external API channels, each with `first` / `second` dual endpoint
- **Automatic client caching** — one `RestClient` instance per channel/endpoint, thread-safe via `ConcurrentHashMap`
- **4-level built-in logging** — `off` / `console` / `file` / `all` (console + file simultaneously), includes body, URL,
  response time
- **Per-endpoint authentication** — Bearer, Basic, API Key
- **Smart charset detection** — Content-Type header → UTF-8 validation → channel default fallback
- **Custom interceptors** — register any `ClientHttpRequestInterceptor` by class name in YAML
- **ChannelContext with MDC** — thread-local channel action tracking, integrated with SLF4J MDC for distributed log
  tracing
- **Zero-code Auto-Configuration** — activated with a single `mido-client.enabled: true` property

## Requirements

| Requirement | Minimum Version |
|-------------|-----------------|
| Java        | 17              |
| Spring Boot | 3.2.0           |
| Gradle      | 8.14.4          |

> Spring Boot 3.2+ is required because `RestClient` was introduced in Spring Framework 6.1 (shipped with Spring Boot
> 3.2).

## Quick Start

### 1. Add Dependency

#### via JitPack (GitHub)

**Gradle**

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.skaca8:mido-client:1.0.3'
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
    <version>1.0.3</version>
</dependency>
```

> To use a specific release, replace `1.0.3` with a tag or a commit hash.

#### via Maven Central (published release)

**Gradle**

```gradle
implementation 'io.github.skaca8:mido-client:1.0.3'
```

**Maven**

```xml

<dependency>
    <groupId>io.github.skaca8</groupId>
    <artifactId>mido-client</artifactId>
    <version>1.0.3</version>
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
      first:
        url: https://api.payment.com
        read-timeout-seconds: 30
        connect-timeout-seconds: 5
        authorization:
          type: bearer
          token: ${PAYMENT_QUERY_TOKEN}
        log: console
      second: # optional: second endpoint for same service
        url: https://process.payment.com
        read-timeout-seconds: 60
        authorization:
          type: bearer
          token: ${PAYMENT_PROCESS_TOKEN}
        log: all
    auth:
      first:
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
        this.processClient = midoClientFactory.getOrCreateClient("payment", EndpointType.SECOND);
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

| Property  | Type   | Default | Description                                  |
|-----------|--------|---------|----------------------------------------------|
| `title`   | String | -       | Channel description (optional)               |
| `charset` | String | `UTF-8` | Default character encoding for response body |

### Endpoint (`first` / `second`)

| Property                  | Type           | Default   | Description                                                   |
|---------------------------|----------------|-----------|---------------------------------------------------------------|
| `url`                     | String         | -         | **Required.** Base URL of the endpoint                        |
| `title`                   | String         | -         | Endpoint description (optional)                               |
| `read-timeout-seconds`    | Long           | `60`      | Read timeout                                                  |
| `connect-timeout-seconds` | Long           | `3`       | Connection timeout                                            |
| `log`                     | LogLevel       | `console` | `off` / `console` / `file` / `all`                            |
| `authorization.type`      | TokenType      | -         | `bearer` / `basic` / `api_key`                                |
| `authorization.token`     | String         | -         | Authentication token value                                    |
| `headers`                 | List           | -         | Static headers to attach to every request                     |
| `interceptors`            | List\<String\> | -         | Fully-qualified class names of `ClientHttpRequestInterceptor` |

### Global

| Property              | Type    | Default | Description                       |
|-----------------------|---------|---------|-----------------------------------|
| `mido-client.enabled` | Boolean | `false` | Enable/disable the entire library |

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

### ChannelContext & MDC

`BaseExternalApi.withDefaultChannelAction()` sets `ChannelContext` automatically. For manual usage:

```java
ChannelContext.setChannelAction("payment.processPayment");
try{
        // your REST call — channelAction appears in all logs via MDC
        }finally{
        ChannelContext.

clear();
}
```

The action key `channelAction` is available in log patterns:

```xml
<!-- logback.xml -->
<pattern>%d [%X{channelAction}] %-5level %msg%n</pattern>
```

## Logging

| Level     | Console | File (`MidoClientFileLog`) |
|-----------|---------|----------------------------|
| `off`     | -       | -                          |
| `console` | Yes     | -                          |
| `file`    | -       | Yes                        |
| `all`     | Yes     | Yes                        |

Each log entry includes: channel action, HTTP method, URL, request/response body, response time, HTTP status.

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
