# Spring Mido Client

A Spring Boot starter for multi-channel REST client management with dynamic configuration and advanced features.

## Features

- 🚀 **Multi-channel support** with first/second endpoint configuration
- 🔧 **Dynamic RestClient creation** with caching
- 📝 **Advanced logging** (console, file, or both)
- 🔒 **Multiple authentication** methods (Bearer, Basic, API Key)
- 🔌 **Custom interceptors** support
- 🌍 **Charset detection** and encoding support
- ⚙️ **Spring Boot Auto-Configuration**

## Quick Start

### 1. Add Dependency

```gradle
implementation 'io.github.hyunjun:mido-client:1.0.0-SNAPSHOT'
```

### 2. Configuration

```yaml
mido-client:
  enabled: true
  channels:
    auth:
      title: "Authentication Server"
      charset: UTF-8
      first:
        title: "Auth API"
        url: https://auth.example.com
        read-timeout-seconds: 60
        connect-timeout-seconds: 3
        authorization:
          type: bearer
          token: your-token-here
        log: console
    payment:
      title: "Payment Service"
      first:
        title: "Payment Query API"
        url: https://payment.example.com
        log: all
      second:
        title: "Payment Processing API"
        url: https://payment-process.example.com
        read-timeout-seconds: 120
        authorization:
          type: bearer
          token: another-token
        interceptors:
          - com.example.CustomInterceptor
```

### 3. Usage

```java
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final MidoClientFactory midoClientFactory;

    public String queryPayment() {
        RestClient client = midoClientFactory.getOrCreateClient("payment");
        return client.get()
            .uri("/payment/status")
            .retrieve()
            .body(String.class);
    }

    public String processPayment() {
        RestClient client = midoClientFactory.getOrCreateClient("payment", EndpointType.SECOND);
        return client.post()
            .uri("/payment/process")
            .body(paymentRequest)
            .retrieve()
            .body(String.class);
    }
}
```

## Configuration Properties

### Channel Configuration

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | Boolean | `false` | Enable Mido Client |
| `channels.<name>.title` | String | - | Channel description (optional) |
| `channels.<name>.charset` | String | `UTF-8` | Character encoding |

### Endpoint Configuration

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `url` | String | - | **Required** - Endpoint URL |
| `title` | String | - | Endpoint description (optional) |
| `read-timeout-seconds` | Long | `60` | Read timeout in seconds |
| `connect-timeout-seconds` | Long | `3` | Connection timeout in seconds |
| `log` | LogLevel | `console` | Logging level: `off`, `console`, `file`, `all` |

### Authentication

| Property | Type | Description |
|----------|------|-------------|
| `authorization.type` | TokenType | `bearer`, `basic`, `api_key` |
| `authorization.token` | String | Authentication token |

### Headers & Interceptors

```yaml
headers:
  - name: "X-Custom-Header"
    value: "custom-value"

interceptors:
  - "com.example.MyInterceptor"
  - "com.example.AnotherInterceptor"
```

## Advanced Features

### Custom Interceptors

Create a custom interceptor by implementing `ClientHttpRequestInterceptor`:

```java
@Component
public class CustomInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {

        // Add custom logic here
        return execution.execute(request, body);
    }
}
```

### Channel Context

Use `ChannelContext` for tracking requests across threads:

```java
ChannelContext.setChannelAction("payment-query");
try {
    // Your REST call here
} finally {
    ChannelContext.clear();
}
```

## Logging

The library provides comprehensive logging with different levels:

- `off`: No logging
- `console`: Log to console only
- `file`: Log to file only (uses `MidoClientFileLog` logger)
- `all`: Log to both console and file

Log format includes:
- Channel action
- HTTP method and URL
- Request/response body
- Response time
- HTTP status

## Requirements

- Java 17+
- Spring Boot 3.2+ (RestClient minimum version)
- Spring Framework 6.1+ (RestClient support)

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Contributing

1. Fork the repository
2. Create your feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request