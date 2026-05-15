# Spring Mido Client

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

- **멀티채널 지원** — 채널 수 제한 없이 정의 가능하며, 각 채널은 `first` / `second` 이중 엔드포인트를 가질 수 있음
- **클라이언트 자동 캐싱** — 채널/엔드포인트 조합별로 `RestClient` 인스턴스를 `ConcurrentHashMap`으로 캐싱, 스레드 안전
- **4단계 로깅** — `off` / `console` / `file` / `all`, 요청/응답 바디, URL, 응답시간 포함
- **엔드포인트별 인증** — Bearer, Basic, API Key 방식 지원
- **스마트 인코딩 감지** — Content-Type 헤더 → UTF-8 유효성 검사 → 채널 기본값 순으로 자동 결정
- **커스텀 인터셉터** — `ClientHttpRequestInterceptor` 구현체를 YAML에 클래스명으로 등록
- **채널별 gzip 압축** — 요청 바디는 `min-size` 임계값 이상일 때만 압축, 응답은 자동 해제 + 압축 폭탄 방어 cap(`max-decompressed-size`)
- **부팅 시 설정 검증** — `@Validated` Bean Validation으로 잘못된 YAML을 시작 시점에 거부, `BindValidationException`에 어떤 필드가 잘못되었는지 명시
- **ChannelContext + MDC 연동** — 스레드 로컬 채널 액션 추적, SLF4J MDC와 통합되어 로그에 자동 포함
- **자동 설정** — `mido-client.enabled: true` 프로퍼티 하나로 활성화

## 요구 사항

| 항목          | 최소 버전  |
|-------------|--------|
| Java        | 17     |
| Spring Boot | 3.2.0  |
| Gradle      | 8.14.4 |

> `RestClient`는 Spring Framework 6.1(Spring Boot 3.2)에서 도입되었으므로 Spring Boot 3.2 이상이 필요합니다.

## 빠른 시작

### 1. 의존성 추가

#### JitPack (GitHub)을 통한 방법

**Gradle**

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.skaca8:mido-client:1.0.6'
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
    <version>1.0.6</version>
</dependency>
```

> 특정 릴리즈를 사용하려면 `1.0.6`을 원하는 태그 또는 커밋 해시로 변경하세요.

#### Maven Central을 통한 방법 (정식 릴리즈)

**Gradle**

```gradle
implementation 'io.github.skaca8:mido-client:1.0.6'
```

**Maven**

```xml

<dependency>
    <groupId>io.github.skaca8</groupId>
    <artifactId>mido-client</artifactId>
    <version>1.0.6</version>
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
      first:
        url: https://api.payment.com
        read-timeout-seconds: 30
        connect-timeout-seconds: 5
        authorization:
          type: bearer
          token: ${PAYMENT_QUERY_TOKEN}
        log: console
      second: # 선택사항: 동일 서비스의 두 번째 엔드포인트
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

### 3. 서비스에서 사용

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

> `BaseExternalApi.withDefaultChannelAction()`은 호출 전후로 `ChannelContext`를 자동으로 설정하고 정리합니다. 예외가 발생해도 반드시 정리됩니다.

## 설정 레퍼런스

### 채널 설정 (`mido-client.channels.<name>`)

| 프로퍼티      | 타입     | 기본값     | 설명           |
|-----------|--------|---------|--------------|
| `title`   | String | -       | 채널 설명 (선택사항) |
| `charset` | String | `UTF-8` | 응답 바디 기본 인코딩 |

### 엔드포인트 설정 (`first` / `second`)

| 프로퍼티                      | 타입             | 기본값       | 설명                                             |
|---------------------------|----------------|-----------|------------------------------------------------|
| `url`                     | String         | -         | **필수.** 엔드포인트 기본 URL                           |
| `title`                   | String         | -         | 엔드포인트 설명 (선택사항)                                |
| `read-timeout-seconds`    | Long           | `60`      | 읽기 타임아웃 (초)                                    |
| `connect-timeout-seconds` | Long           | `3`       | 연결 타임아웃 (초)                                    |
| `log`                     | LogLevel       | `console` | `off` / `console` / `file` / `all`             |
| `authorization.type`      | TokenType      | -         | `bearer` / `basic` / `api_key`                 |
| `authorization.token`     | String         | -         | 인증 토큰 값                                        |
| `headers`                 | List           | -         | 모든 요청에 고정으로 추가할 헤더 목록                          |
| `interceptors`            | List\<String\> | -         | `ClientHttpRequestInterceptor` 구현체의 전체 클래스명 목록 |
| `gzip.request`            | Boolean        | `false`   | 요청 바디 gzip 압축 (`Content-Encoding: gzip` 자동 추가) |
| `gzip.response`           | Boolean        | `false`   | `Accept-Encoding: gzip` 강제 후 응답 자동 해제 |
| `gzip.min-size`           | Integer        | `1024`    | 요청 바디가 이 크기 미만이면 압축 skip (bytes) |
| `gzip.max-decompressed-size` | Integer     | `10485760`| 응답 해제 결과가 이 크기를 넘으면 `IOException` (압축 폭탄 방어, bytes) |

### 전역 설정

| 프로퍼티                  | 타입      | 기본값     | 설명           |
|-----------------------|---------|---------|--------------|
| `mido-client.enabled` | Boolean | `false` | 라이브러리 활성화 여부 |

### 설정 검증

`mido-client`는 애플리케이션 시작 시점에 `@ConfigurationProperties`를 검증합니다. 잘못된 설정은 `BindValidationException`과 함께 어떤 필드가 잘못되었고 거부된 값이 무엇인지 표시하며 컨텍스트 로드에 실패합니다. 다음 경우 검증에 실패합니다.

- `url`이 비어있거나 `^https?://.+`에 매치되지 않음
- `read-timeout-seconds` 또는 `connect-timeout-seconds`가 0 이하
- `gzip.min-size`가 음수
- `gzip.max-decompressed-size`가 0 이하
- `headers[].name` 또는 `headers[].value`가 비어있음
- 채널에 필수 `first` 엔드포인트가 없음

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

### Gzip 압축

채널별로 HTTP 바디 압축을 opt-in 방식으로 활성화합니다. 송/수신 방향은 독립적으로 설정 가능합니다.

```yaml
mido-client:
  channels:
    payment:
      first:
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

### ChannelContext와 MDC

`BaseExternalApi.withDefaultChannelAction()`을 사용하면 `ChannelContext`가 자동으로 관리됩니다. 직접 사용하는 경우:

```java
ChannelContext.setChannelAction("payment.processPayment");
try{
        // REST 호출 — MDC를 통해 모든 로그에 channelAction이 포함됨
        }finally{
        ChannelContext.

clear();
}
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
