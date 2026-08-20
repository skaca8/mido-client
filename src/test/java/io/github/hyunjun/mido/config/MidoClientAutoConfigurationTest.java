package io.github.hyunjun.mido.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.hyunjun.mido.constant.ContentType;
import io.github.hyunjun.mido.constant.LogLevel;
import io.github.hyunjun.mido.constant.TokenType;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class MidoClientAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MidoClientAutoConfiguration.class));

    @Test
    void shouldNotAutoConfigureWhenDisabled() {
        contextRunner
                .withPropertyValues("mido-client.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MidoClientFactory.class);
                    assertThat(context).doesNotHaveBean(MidoClientProperties.class);
                });
    }

    @Test
    void shouldNotAutoConfigureWhenPropertyNotSet() {
        contextRunner
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MidoClientFactory.class);
                    assertThat(context).doesNotHaveBean(MidoClientProperties.class);
                });
    }

    @Test
    void shouldAutoConfigureWhenEnabled() {
        contextRunner
                .withPropertyValues("mido-client.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(MidoClientFactory.class);
                    assertThat(context).hasSingleBean(MidoClientProperties.class);
                });
    }

    @Test
    void shouldConfigureChannelsFromProperties() {
        contextRunner
                .withPropertyValues(
                        "mido-client.enabled=true",
                        "mido-client.channels.test.title=Test Channel",
                        "mido-client.channels.test.charset=UTF-8",
                        "mido-client.channels.test.primary.url=https://api.test.com",
                        "mido-client.channels.test.primary.read-timeout-seconds=30",
                        "mido-client.channels.test.primary.connect-timeout-seconds=5",
                        "mido-client.channels.test.primary.log=console",
                        "mido-client.channels.test.primary.authorization.type=bearer",
                        "mido-client.channels.test.primary.authorization.token=test-token",
                        "mido-client.channels.test.primary.headers[0].name=X-Custom-Header",
                        "mido-client.channels.test.primary.headers[0].value=custom-value"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(MidoClientProperties.class);

                    MidoClientProperties properties = context.getBean(MidoClientProperties.class);
                    assertThat(properties.getEnabled()).isTrue();
                    assertThat(properties.getChannels()).containsKey("test");

                    MidoClientProperties.ChannelConfig channelConfig = properties.getChannels().get("test");
                    assertThat(channelConfig.getTitle()).isEqualTo("Test Channel");
                    assertThat(channelConfig.getCharset()).isEqualTo("UTF-8");

                    MidoClientProperties.EndpointConfig primaryEndpoint = channelConfig.getPrimary();
                    assertThat(primaryEndpoint.getUrl()).isEqualTo("https://api.test.com");
                    assertThat(primaryEndpoint.getReadTimeoutSeconds()).isEqualTo(30L);
                    assertThat(primaryEndpoint.getConnectTimeoutSeconds()).isEqualTo(5L);
                    assertThat(primaryEndpoint.getLog()).isEqualTo(LogLevel.CONSOLE);

                    assertThat(primaryEndpoint.getAuthorization()).isNotNull();
                    assertThat(primaryEndpoint.getAuthorization().getType()).isEqualTo(TokenType.BEARER);
                    assertThat(primaryEndpoint.getAuthorization().getToken()).isEqualTo("test-token");

                    assertThat(primaryEndpoint.getHeaders()).hasSize(1);
                    assertThat(primaryEndpoint.getHeaders().get(0).getName()).isEqualTo("X-Custom-Header");
                    assertThat(primaryEndpoint.getHeaders().get(0).getValue()).isEqualTo("custom-value");
                });
    }

    @Test
    void shouldBindGzipPropertiesFromYaml() {
        contextRunner
                .withPropertyValues(
                        "mido-client.enabled=true",
                        "mido-client.channels.test.primary.url=https://api.test.com",
                        "mido-client.channels.test.primary.gzip.request=true",
                        "mido-client.channels.test.primary.gzip.response=true",
                        "mido-client.channels.test.primary.gzip.min-size=2048"
                )
                .run(context -> {
                    MidoClientProperties properties = context.getBean(MidoClientProperties.class);
                    MidoClientProperties.Gzip gzip = properties.getChannels().get("test").getPrimary().getGzip();

                    assertThat(gzip).isNotNull();
                    assertThat(gzip.getRequest()).isTrue();
                    assertThat(gzip.getResponse()).isTrue();
                    assertThat(gzip.getMinSize()).isEqualTo(2048);
                });
    }

    @Test
    void shouldFailValidationWhenUrlIsBlank() {
        contextRunner
                .withPropertyValues(
                        "mido-client.enabled=true",
                        "mido-client.channels.test.primary.url="
                )
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("url"));
    }

    @Test
    void shouldFailValidationWhenUrlSchemeIsInvalid() {
        contextRunner
                .withPropertyValues(
                        "mido-client.enabled=true",
                        "mido-client.channels.test.primary.url=ftp://nope"
                )
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("url"));
    }

    @Test
    void shouldFailValidationWhenTimeoutIsNonPositive() {
        contextRunner
                .withPropertyValues(
                        "mido-client.enabled=true",
                        "mido-client.channels.test.primary.url=https://api.test.com",
                        "mido-client.channels.test.primary.read-timeout-seconds=0"
                )
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("readTimeoutSeconds"));
    }

    @Test
    void shouldFailValidationWhenGzipMaxDecompressedSizeIsZero() {
        contextRunner
                .withPropertyValues(
                        "mido-client.enabled=true",
                        "mido-client.channels.test.primary.url=https://api.test.com",
                        "mido-client.channels.test.primary.gzip.max-decompressed-size=0"
                )
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("maxDecompressedSize"));
    }

    @Test
    void shouldFailValidationWhenHeaderNameIsBlank() {
        contextRunner
                .withPropertyValues(
                        "mido-client.enabled=true",
                        "mido-client.channels.test.primary.url=https://api.test.com",
                        "mido-client.channels.test.primary.headers[0].name=",
                        "mido-client.channels.test.primary.headers[0].value=v"
                )
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("headers[0].name"));
    }

    @Test
    void shouldFailValidationWhenAuthorizationTypeIsMissing() {
        contextRunner
                .withPropertyValues(
                        "mido-client.enabled=true",
                        "mido-client.channels.test.primary.url=https://api.test.com",
                        "mido-client.channels.test.primary.authorization.token=test-token"
                )
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("authorization.type"));
    }

    @Test
    void shouldFailValidationWhenAuthorizationTokenIsBlank() {
        contextRunner
                .withPropertyValues(
                        "mido-client.enabled=true",
                        "mido-client.channels.test.primary.url=https://api.test.com",
                        "mido-client.channels.test.primary.authorization.type=bearer",
                        "mido-client.channels.test.primary.authorization.token="
                )
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("authorization.token"));
    }

    @Test
    void shouldFailValidationWhenChannelPrimaryIsMissing() {
        contextRunner
                .withPropertyValues(
                        "mido-client.enabled=true",
                        "mido-client.channels.test.title=No Primary Endpoint"
                )
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("channels[test].primary"));
    }

    @Test
    void shouldBindMaxDecompressedSizeFromYaml() {
        contextRunner
                .withPropertyValues(
                        "mido-client.enabled=true",
                        "mido-client.channels.test.primary.url=https://api.test.com",
                        "mido-client.channels.test.primary.gzip.max-decompressed-size=20971520"
                )
                .run(context -> {
                    MidoClientProperties properties = context.getBean(MidoClientProperties.class);
                    MidoClientProperties.Gzip gzip = properties.getChannels().get("test").getPrimary().getGzip();
                    assertThat(gzip.getMaxDecompressedSize()).isEqualTo(20 * 1024 * 1024);
                });
    }

    @Test
    void shouldDefaultChannelTypeToJsonWhenNotConfigured() {
        contextRunner
                .withPropertyValues(
                        "mido-client.enabled=true",
                        "mido-client.channels.test.primary.url=https://api.test.com"
                )
                .run(context -> {
                    MidoClientProperties properties = context.getBean(MidoClientProperties.class);
                    MidoClientProperties.ChannelConfig channelConfig = properties.getChannels().get("test");

                    assertThat(channelConfig.getType()).isEqualTo(ContentType.JSON);
                });
    }

    @Test
    void shouldBindChannelTypeXmlFromYaml() {
        contextRunner
                .withPropertyValues(
                        "mido-client.enabled=true",
                        "mido-client.channels.test.type=xml",
                        "mido-client.channels.test.primary.url=https://api.test.com"
                )
                .run(context -> {
                    MidoClientProperties properties = context.getBean(MidoClientProperties.class);
                    MidoClientProperties.ChannelConfig channelConfig = properties.getChannels().get("test");

                    assertThat(channelConfig.getType()).isEqualTo(ContentType.XML);
                });
    }

    @Test
    void shouldBindChannelTypeJsonFromYaml() {
        contextRunner
                .withPropertyValues(
                        "mido-client.enabled=true",
                        "mido-client.channels.test.type=json",
                        "mido-client.channels.test.primary.url=https://api.test.com"
                )
                .run(context -> {
                    MidoClientProperties properties = context.getBean(MidoClientProperties.class);
                    MidoClientProperties.ChannelConfig channelConfig = properties.getChannels().get("test");

                    assertThat(channelConfig.getType()).isEqualTo(ContentType.JSON);
                });
    }

    @Test
    void shouldNormalizeYamlChannelKeyToLowerCase() {
        // Given - YAML 키를 의도적으로 PascalCase로
        contextRunner
                .withPropertyValues(
                        "mido-client.enabled=true",
                        "mido-client.channels.Payment.primary.url=https://api.payment.com"
                )
                .run(context -> {
                    MidoClientProperties properties = context.getBean(MidoClientProperties.class);

                    // setChannels에서 소문자 정규화 → Map에는 "payment" 키로 저장
                    assertThat(properties.getChannels()).containsKey("payment");

                    // 어떤 대소문자로 조회해도 동일하게 매칭
                    assertThat(properties.getChannelConfig("payment")).isNotNull();
                    assertThat(properties.getChannelConfig("Payment")).isNotNull();
                    assertThat(properties.getChannelConfig("PAYMENT")).isNotNull();
                });
    }

    @Test
    void shouldApplyGzipDefaultsWhenNotConfigured() {
        contextRunner
                .withPropertyValues(
                        "mido-client.enabled=true",
                        "mido-client.channels.test.primary.url=https://api.test.com"
                )
                .run(context -> {
                    MidoClientProperties properties = context.getBean(MidoClientProperties.class);
                    MidoClientProperties.Gzip gzip = properties.getChannels().get("test").getPrimary().getGzip();

                    assertThat(gzip).isNotNull();
                    assertThat(gzip.getRequest()).isFalse();
                    assertThat(gzip.getResponse()).isFalse();
                    assertThat(gzip.getMinSize()).isEqualTo(1024);
                    assertThat(gzip.getMaxDecompressedSize()).isEqualTo(10 * 1024 * 1024);
                });
    }

    @Test
    void shouldFailStartupWhenInterceptorClassDoesNotExist() {
        // Given - FQCN 오타. 첫 요청이 아니라 기동 시점에 잡혀야 한다.
        contextRunner
                .withPropertyValues(
                        "mido-client.enabled=true",
                        "mido-client.channels.test.primary.url=https://api.test.com",
                        "mido-client.channels.test.primary.interceptors[0]=com.example.NoSuchInterceptor"
                )
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("channel: test, type: primary")
                        .hasStackTraceContaining("Failed to load interceptor: com.example.NoSuchInterceptor"));
    }

    @Test
    void shouldFailStartupWhenInterceptorDoesNotImplementInterface() {
        contextRunner
                .withPropertyValues(
                        "mido-client.enabled=true",
                        "mido-client.channels.test.secondary.url=https://api2.test.com",
                        "mido-client.channels.test.primary.url=https://api.test.com",
                        "mido-client.channels.test.secondary.interceptors[0]=java.lang.String"
                )
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("channel: test, type: secondary")
                        .hasStackTraceContaining("does not implement ClientHttpRequestInterceptor: java.lang.String"));
    }

    @Test
    void shouldFailStartupWhenCharsetIsUnknown() {
        contextRunner
                .withPropertyValues(
                        "mido-client.enabled=true",
                        "mido-client.channels.test.charset=NO-SUCH-CHARSET",
                        "mido-client.channels.test.primary.url=https://api.test.com"
                )
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("Invalid charset 'NO-SUCH-CHARSET' for channel: test"));
    }

    @Test
    void shouldStartWhenInterceptorIsValid() {
        // Given - 유효한 인터셉터는 기동을 막지 않고, 생성자는 이 시점에 호출되지 않는다
        CountingInterceptor.instantiations = 0;
        contextRunner
                .withPropertyValues(
                        "mido-client.enabled=true",
                        "mido-client.channels.test.primary.url=https://api.test.com",
                        "mido-client.channels.test.primary.interceptors[0]="
                                + CountingInterceptor.class.getName()
                )
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(MidoClientFactory.class);
                    assertThat(CountingInterceptor.instantiations).isZero();
                });
    }

    @Test
    void shouldWarnWhenFileLoggerHasNoAppender() {
        // Given - log: all 인데 MidoClientFileLog 로거에 appender가 없다
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger factoryLogger = (Logger) LoggerFactory.getLogger(MidoClientFactory.class);
        factoryLogger.addAppender(appender);

        try {
            contextRunner
                    .withPropertyValues(
                            "mido-client.enabled=true",
                            "mido-client.channels.test.primary.url=https://api.test.com",
                            "mido-client.channels.test.primary.log=all"
                    )
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(appender.list)
                                .anyMatch(event -> event.getLevel() == Level.WARN
                                        && event.getFormattedMessage().contains("MidoClientFileLog"));
                    });
        } finally {
            factoryLogger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void shouldNotWarnAboutFileLoggerWhenNoEndpointUsesIt() {
        // Given - log: console 만 사용
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger factoryLogger = (Logger) LoggerFactory.getLogger(MidoClientFactory.class);
        factoryLogger.addAppender(appender);

        try {
            contextRunner
                    .withPropertyValues(
                            "mido-client.enabled=true",
                            "mido-client.channels.test.primary.url=https://api.test.com",
                            "mido-client.channels.test.primary.log=console"
                    )
                    .run(context -> assertThat(appender.list)
                            .noneMatch(event -> event.getFormattedMessage().contains("MidoClientFileLog")));
        } finally {
            factoryLogger.detachAppender(appender);
            appender.stop();
        }
    }

    public static class CountingInterceptor implements ClientHttpRequestInterceptor {

        static int instantiations = 0;

        public CountingInterceptor() {
            instantiations++;
        }

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
            return execution.execute(request, body);
        }
    }
}
