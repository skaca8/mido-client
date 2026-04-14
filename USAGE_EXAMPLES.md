# Mido Client Usage Examples

이 문서는 Spring Mido Client의 다양한 사용 사례와 실제 구현 예제를 제공합니다.

## 목차

1. [기본 설정](#기본-설정)
2. [단일 엔드포인트 서비스](#단일-엔드포인트-서비스)
3. [다중 엔드포인트 서비스](#다중-엔드포인트-서비스)
4. [인증이 필요한 API](#인증이-필요한-api)
5. [커스텀 인터셉터](#커스텀-인터셉터)
6. [로깅 설정](#로깅-설정)
7. [에러 처리](#에러-처리)
8. [성능 최적화](#성능-최적화)

## 기본 설정

### application.yml 설정

```yaml
mido-client:
  enabled: true
  channels:
    # 인증 서비스 (단일 엔드포인트)
    auth:
      title: "인증 서버"
      first:
        url: https://auth.example.com
        authorization:
          type: bearer
          token: ${AUTH_SERVICE_TOKEN}
        headers:
          - name: X-API-Version
            value: v2

    # 결제 서비스 (다중 엔드포인트)
    payment:
      title: "결제 서비스"
      first:
        title: "결제 조회"
        url: https://api.payment.com
        read-timeout-seconds: 30
        log: console
        authorization:
          type: bearer
          token: ${PAYMENT_QUERY_TOKEN}
      second:
        title: "결제 처리"
        url: https://process.payment.com
        read-timeout-seconds: 120
        log: all
        authorization:
          type: bearer
          token: ${PAYMENT_PROCESS_TOKEN}

    # 알림 서비스 (웹훅)
    notification:
      title: "알림 서비스"
      first:
        url: https://hooks.slack.com/services/YOUR/SLACK/WEBHOOK
        read-timeout-seconds: 10
        log: off  # 민감한 웹훅 호출은 로깅 비활성화
```

## 단일 엔드포인트 서비스

### 인증 서비스 구현

```java
@Service
@RequiredArgsConstructor
public class AuthService extends BaseExternalApi {

    private final MidoClientFactory midoClientFactory;

    @Override
    protected String getChannelName() {
        return "auth";
    }

    public TokenValidationResult validateToken(String token) {
        return withDefaultChannelAction("validateToken", () -> {
            RestClient client = midoClientFactory.getOrCreateClient("auth");

            return client.post()
                    .uri("/auth/validate")
                    .body(new TokenValidationRequest(token))
                    .retrieve()
                    .body(TokenValidationResult.class);
        });
    }

    public UserInfo getUserInfo(String userId) {
        return withDefaultChannelAction("getUserInfo", () -> {
            RestClient client = midoClientFactory.getOrCreateClient("auth");

            return client.get()
                    .uri("/users/{userId}", userId)
                    .retrieve()
                    .body(UserInfo.class);
        });
    }
}
```

### 사용 예제

```java
@RestController
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @PostMapping("/api/validate-token")
    public ResponseEntity<TokenValidationResult> validateToken(@RequestBody TokenRequest request) {
        try {
            TokenValidationResult result = authService.validateToken(request.getToken());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}
```

## 다중 엔드포인트 서비스

### 결제 서비스 구현

```java
@Service
@RequiredArgsConstructor
public class PaymentService extends BaseExternalApi {

    private final MidoClientFactory midoClientFactory;

    @Override
    protected String getChannelName() {
        return "payment";
    }

    // first 엔드포인트 사용 (조회용)
    public PaymentStatus getPaymentStatus(String paymentId) {
        return withDefaultChannelAction("getPaymentStatus", () -> {
            RestClient client = midoClientFactory.getOrCreateClient("payment");

            return client.get()
                    .uri("/payments/{paymentId}/status", paymentId)
                    .retrieve()
                    .body(PaymentStatus.class);
        });
    }

    // second 엔드포인트 사용 (처리용)
    public PaymentResult processPayment(PaymentRequest request) {
        return withDefaultChannelAction("processPayment", () -> {
            RestClient client = midoClientFactory.getOrCreateClient("payment", EndpointType.SECOND);

            return client.post()
                    .uri("/payments/process")
                    .body(request)
                    .retrieve()
                    .body(PaymentResult.class);
        });
    }
}
```

### 트랜잭션 처리 예제

```java
@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {

    private final PaymentService paymentService;
    private final OrderRepository orderRepository;

    public OrderResult processOrder(OrderRequest request) {
        // 1. 주문 생성 (로컬 DB)
        Order order = createOrder(request);

        // 2. 결제 처리 (외부 API - second endpoint)
        PaymentResult paymentResult = paymentService.processPayment(
            PaymentRequest.builder()
                .orderId(order.getId())
                .amount(order.getAmount())
                .userId(order.getUserId())
                .build()
        );

        if (!paymentResult.isSuccess()) {
            throw new PaymentFailedException("결제 처리 실패");
        }

        // 3. 주문 상태 업데이트
        order.setPaymentId(paymentResult.getPaymentId());
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        return OrderResult.success(order, paymentResult);
    }
}
```

## 인증이 필요한 API

### Bearer Token 설정

```yaml
mido-client:
  channels:
    secure-api:
      first:
        url: https://secure-api.example.com
        authorization:
          type: bearer
          token: ${SECURE_API_TOKEN}
```

### Basic Authentication 설정

```yaml
mido-client:
  channels:
    legacy-api:
      charset: EUC-KR  # 레거시 시스템용 인코딩
      first:
        url: https://legacy.example.com
        authorization:
          type: basic
          token: ${BASIC_AUTH_TOKEN}  # base64 인코딩된 username:password
        headers:
          - name: Content-Type
            value: text/xml; charset=EUC-KR
```

### API Key 설정

```yaml
mido-client:
  channels:
    third-party:
      first:
        url: https://api.thirdparty.com
        authorization:
          type: api_key
          token: ${API_KEY}
        headers:
          - name: X-API-Key
            value: ${API_KEY}
```

## 커스텀 인터셉터

### 인터셉터 구현

```java
@Component
public class CustomSecurityInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {

        // 요청 전처리
        HttpHeaders headers = request.getHeaders();
        headers.add("X-Request-ID", UUID.randomUUID().toString());
        headers.add("X-Timestamp", String.valueOf(System.currentTimeMillis()));

        // 요청 실행
        ClientHttpResponse response = execution.execute(request, body);

        // 응답 후처리
        if (response.getStatusCode().is4xxClientError()) {
            // 클라이언트 에러 로깅
            log.warn("Client error occurred: {} {}",
                request.getMethod(), request.getURI());
        }

        return response;
    }
}
```

### 인터셉터 등록

```yaml
mido-client:
  channels:
    protected-api:
      first:
        url: https://protected-api.example.com
        interceptors:
          - com.example.CustomSecurityInterceptor
          - com.example.AuditInterceptor
```

## 로깅 설정

### 다양한 로깅 레벨

```yaml
mido-client:
  channels:
    # 개발 환경 - 상세 로깅
    dev-api:
      first:
        url: https://dev-api.example.com
        log: all  # 콘솔과 파일 모두

    # 운영 환경 - 파일만 로깅
    prod-api:
      first:
        url: https://prod-api.example.com
        log: file  # 파일만

    # 민감한 API - 로깅 비활성화
    sensitive-api:
      first:
        url: https://sensitive-api.example.com
        log: off  # 로깅 안함

    # 일반 API - 콘솔만
    public-api:
      first:
        url: https://public-api.example.com
        log: console  # 콘솔만 (기본값)
```

### logback-spring.xml 설정

```xml
<configuration>
    <!-- Mido Client 전용 로거 -->
    <logger name="MidoClientFileLog" level="INFO" additivity="false">
        <appender-ref ref="MIDO_CLIENT_FILE"/>
    </logger>

    <appender name="MIDO_CLIENT_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/mido-client.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/mido-client.%d{yyyy-MM-dd}.%i.gz</fileNamePattern>
            <maxFileSize>10MB</maxFileSize>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
</configuration>
```

## 에러 처리

### 전역 에러 처리

```java
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ErrorResponse> handleClientError(HttpClientErrorException ex) {
        log.error("HTTP client error: {}", ex.getMessage());

        return ResponseEntity.status(ex.getStatusCode())
                .body(ErrorResponse.builder()
                    .code("CLIENT_ERROR")
                    .message("외부 서비스 호출 중 오류가 발생했습니다.")
                    .build());
    }

    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<ErrorResponse> handleServerError(HttpServerErrorException ex) {
        log.error("HTTP server error: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.builder()
                    .code("SERVICE_UNAVAILABLE")
                    .message("외부 서비스가 일시적으로 이용할 수 없습니다.")
                    .build());
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ErrorResponse> handleTimeout(ResourceAccessException ex) {
        log.error("Connection timeout: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(ErrorResponse.builder()
                    .code("TIMEOUT")
                    .message("외부 서비스 연결 시간이 초과되었습니다.")
                    .build());
    }
}
```

### 서비스별 에러 처리

```java
@Service
public class RobustPaymentService extends BaseExternalApi {

    private final MidoClientFactory midoClientFactory;

    public PaymentResult processPaymentWithRetry(PaymentRequest request) {
        return withDefaultChannelAction("processPayment", () -> {
            try {
                return callPaymentApi(request);
            } catch (HttpServerErrorException ex) {
                // 서버 오류 시 재시도
                log.warn("Payment API server error, retrying...");
                Thread.sleep(1000);
                return callPaymentApi(request);
            } catch (ResourceAccessException ex) {
                // 타임아웃 시 대체 로직
                log.error("Payment API timeout, using fallback");
                return PaymentResult.fallback(request.getOrderId());
            }
        });
    }

    private PaymentResult callPaymentApi(PaymentRequest request) {
        RestClient client = midoClientFactory.getOrCreateClient("payment", EndpointType.SECOND);

        return client.post()
                .uri("/payments/process")
                .body(request)
                .retrieve()
                .body(PaymentResult.class);
    }
}
```

## 성능 최적화

### 연결 풀 최적화

```yaml
mido-client:
  channels:
    high-volume-api:
      first:
        url: https://high-volume-api.example.com
        connect-timeout-seconds: 2    # 빠른 연결 설정
        read-timeout-seconds: 30      # 적절한 읽기 타임아웃
        log: file                     # 콘솔 로깅 비활성화로 성능 향상
```

### 비동기 처리

```java
@Service
public class AsyncNotificationService extends BaseExternalApi {

    private final MidoClientFactory midoClientFactory;

    @Async
    public CompletableFuture<Void> sendNotificationAsync(String message) {
        return CompletableFuture.runAsync(() -> {
            withDefaultChannelAction("sendNotification", () -> {
                RestClient client = midoClientFactory.getOrCreateClient("notification");

                client.post()
                        .uri("")
                        .body(SlackMessage.of(message))
                        .retrieve()
                        .toBodilessEntity();
            });
        });
    }
}
```

### 캐싱 활용

```java
@Service
public class CachedUserService extends BaseExternalApi {

    private final MidoClientFactory midoClientFactory;

    @Cacheable(value = "userInfo", key = "#userId")
    public UserInfo getUserInfo(String userId) {
        return withDefaultChannelAction("getUserInfo", () -> {
            RestClient client = midoClientFactory.getOrCreateClient("auth");

            return client.get()
                    .uri("/users/{userId}", userId)
                    .retrieve()
                    .body(UserInfo.class);
        });
    }
}
```

## 모니터링 및 헬스체크

### 헬스체크 구현

```java
@Component
public class ExternalApiHealthIndicator implements HealthIndicator {

    private final MidoClientFactory midoClientFactory;

    @Override
    public Health health() {
        try {
            // 각 채널별 헬스체크
            checkChannel("auth");
            checkChannel("payment");

            return Health.up()
                    .withDetail("channels", "All channels are healthy")
                    .build();
        } catch (Exception ex) {
            return Health.down()
                    .withDetail("error", ex.getMessage())
                    .build();
        }
    }

    private void checkChannel(String channelName) {
        RestClient client = midoClientFactory.getOrCreateClient(channelName);

        client.get()
                .uri("/health")
                .retrieve()
                .toBodilessEntity();
    }
}
```

이 예제들을 참고하여 프로젝트에 맞는 Mido Client 사용법을 구현해보세요.