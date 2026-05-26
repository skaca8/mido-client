package io.github.hyunjun.mido.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.hyunjun.mido.constant.LogLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MidoLoggingInterceptorTest {

    private static final String KOREAN_BODY = "안녕하세요";

    private ListAppender<ILoggingEvent> appender;
    private Logger interceptorLogger;
    private ClientHttpRequestExecution execution;
    private ClientHttpResponse response;

    @BeforeEach
    void setUp() throws Exception {
        appender = new ListAppender<>();
        appender.start();
        interceptorLogger = (Logger) LoggerFactory.getLogger(MidoLoggingInterceptor.class);
        interceptorLogger.addAppender(appender);

        execution = mock(ClientHttpRequestExecution.class);
        response = mock(ClientHttpResponse.class);
        when(response.getHeaders()).thenReturn(new HttpHeaders());
        when(response.getBody()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(execution.execute(any(HttpRequest.class), any(byte[].class))).thenReturn(response);
    }

    @AfterEach
    void tearDown() {
        interceptorLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void shouldDecodeRequestBodyUsingHeaderCharsetWhenPresent() throws Exception {
        // Given - 채널 기본 charset은 UTF-8, 요청 헤더는 EUC-KR
        MidoLoggingInterceptor interceptor = new MidoLoggingInterceptor(LogLevel.CONSOLE, StandardCharsets.UTF_8);
        HttpRequest request = stubRequest("https://test.example.com/path");
        request.getHeaders().set(HttpHeaders.CONTENT_TYPE, "text/plain; charset=EUC-KR");
        byte[] body = KOREAN_BODY.getBytes(Charset.forName("EUC-KR"));

        // When
        interceptor.intercept(request, body, execution);

        // Then - 헤더 charset이 적용되어 한글이 올바르게 디코딩됨
        String requestLog = findLogContaining("[mido-client request]");
        assertThat(requestLog).contains(KOREAN_BODY);
    }

    @Test
    void shouldDecodeRequestBodyUsingChannelCharsetWhenHeaderHasNoCharset() throws Exception {
        // Given - 채널 charset은 EUC-KR, 요청 헤더에는 charset 없음
        MidoLoggingInterceptor interceptor = new MidoLoggingInterceptor(LogLevel.CONSOLE, Charset.forName("EUC-KR"));
        HttpRequest request = stubRequest("https://legacy.example.com/path");
        byte[] body = KOREAN_BODY.getBytes(Charset.forName("EUC-KR"));

        // When
        interceptor.intercept(request, body, execution);

        // Then - 채널 charset이 fallback으로 적용됨
        String requestLog = findLogContaining("[mido-client request]");
        assertThat(requestLog).contains(KOREAN_BODY);
    }

    @Test
    void shouldDecodeRequestBodyAsUtf8ByDefault() throws Exception {
        // Given - charset 기본값 UTF-8
        MidoLoggingInterceptor interceptor = new MidoLoggingInterceptor(LogLevel.CONSOLE, StandardCharsets.UTF_8);
        HttpRequest request = stubRequest("https://test.example.com/path");
        byte[] body = KOREAN_BODY.getBytes(StandardCharsets.UTF_8);

        // When
        interceptor.intercept(request, body, execution);

        // Then
        String requestLog = findLogContaining("[mido-client request]");
        assertThat(requestLog).contains(KOREAN_BODY);
    }

    @Test
    void shouldNotLogRequestBodyWhenLogLevelIsOff() throws Exception {
        // Given
        MidoLoggingInterceptor interceptor = new MidoLoggingInterceptor(LogLevel.OFF, StandardCharsets.UTF_8);
        HttpRequest request = stubRequest("https://test.example.com/path");

        // When
        interceptor.intercept(request, "ignored".getBytes(StandardCharsets.UTF_8), execution);

        // Then
        assertThat(appender.list).noneMatch(e -> e.getFormattedMessage().contains("[mido-client request]"));
    }

    private HttpRequest stubRequest(String url) throws Exception {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getHeaders()).thenReturn(new HttpHeaders());
        when(request.getMethod()).thenReturn(HttpMethod.POST);
        when(request.getURI()).thenReturn(new URI(url));
        return request;
    }

    private String findLogContaining(String marker) {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(msg -> msg.contains(marker))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No log line containing: " + marker));
    }
}