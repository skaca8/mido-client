package io.github.hyunjun.mido.config;

import ch.qos.logback.classic.Level;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);
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
        MidoLoggingInterceptor interceptor = new MidoLoggingInterceptor(LogLevel.CONSOLE, StandardCharsets.UTF_8, true, 0);
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
        MidoLoggingInterceptor interceptor = new MidoLoggingInterceptor(LogLevel.CONSOLE, Charset.forName("EUC-KR"), true, 0);
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
        MidoLoggingInterceptor interceptor = new MidoLoggingInterceptor(LogLevel.CONSOLE, StandardCharsets.UTF_8, true, 0);
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
        MidoLoggingInterceptor interceptor = new MidoLoggingInterceptor(LogLevel.OFF, StandardCharsets.UTF_8, true, 0);
        HttpRequest request = stubRequest("https://test.example.com/path");

        // When
        interceptor.intercept(request, "ignored".getBytes(StandardCharsets.UTF_8), execution);

        // Then
        assertThat(appender.list).noneMatch(e -> e.getFormattedMessage().contains("[mido-client request]"));
    }

    @Test
    void shouldOmitBodiesWhenLogBodyIsDisabled() throws Exception {
        // Given - logBody=false, 응답 body에 카드번호가 있다고 가정
        MidoLoggingInterceptor interceptor = new MidoLoggingInterceptor(LogLevel.CONSOLE, StandardCharsets.UTF_8, false, 0);
        HttpRequest request = stubRequest("https://card.example.com/pay");
        when(response.getBody()).thenReturn(new ByteArrayInputStream("4111111111111111".getBytes(StandardCharsets.UTF_8)));

        // When
        interceptor.intercept(request, "{\"pan\":\"4111111111111111\"}".getBytes(StandardCharsets.UTF_8), execution);

        // Then - 요청·응답 모두 body는 (omitted), 나머지 메타데이터는 남는다
        assertThat(findLogContaining("[mido-client request]"))
                .contains("body: (omitted)")
                .doesNotContain("4111111111111111")
                .contains("https://card.example.com/pay");
        assertThat(findLogContaining("[mido-client response]"))
                .contains("body: (omitted)")
                .doesNotContain("4111111111111111")
                .contains("responseTimeMs: ");
    }

    @Test
    void shouldNotReadResponseBodyWhenLogBodyIsDisabled() throws Exception {
        // Given - body를 아예 읽지 않아야 한다 (마스킹이 아니라 미수집)
        MidoLoggingInterceptor interceptor = new MidoLoggingInterceptor(LogLevel.CONSOLE, StandardCharsets.UTF_8, false, 0);
        HttpRequest request = stubRequest("https://card.example.com/pay");

        // When
        interceptor.intercept(request, new byte[0], execution);

        // Then
        verify(response, never()).getBody();
    }

    @Test
    void shouldTruncateResponseBodyBeyondMaxBodyBytes() throws Exception {
        // Given - 상한 10바이트, 응답은 30바이트
        MidoLoggingInterceptor interceptor = new MidoLoggingInterceptor(LogLevel.CONSOLE, StandardCharsets.UTF_8, true, 10);
        HttpRequest request = stubRequest("https://big.example.com/list");
        when(response.getBody()).thenReturn(new ByteArrayInputStream("0123456789ABCDEFGHIJKLMNOPQRST".getBytes(StandardCharsets.UTF_8)));

        // When
        interceptor.intercept(request, new byte[0], execution);

        // Then - 앞 10바이트만 남고 버린 바이트 수가 표시된다
        assertThat(findLogContaining("[mido-client response]"))
                .contains("body: 0123456789...(truncated 20 bytes)")
                .doesNotContain("KLMNOPQRST");
    }

    @Test
    void shouldTruncateRequestBodyBeyondMaxBodyBytes() throws Exception {
        // Given
        MidoLoggingInterceptor interceptor = new MidoLoggingInterceptor(LogLevel.CONSOLE, StandardCharsets.UTF_8, true, 5);
        HttpRequest request = stubRequest("https://big.example.com/upload");

        // When
        interceptor.intercept(request, "0123456789".getBytes(StandardCharsets.UTF_8), execution);

        // Then
        assertThat(findLogContaining("[mido-client request]"))
                .contains("body: 01234...(truncated 5 bytes)");
    }

    @Test
    void shouldNotTruncateWhenMaxBodyBytesIsZero() throws Exception {
        // Given - 0은 무제한
        MidoLoggingInterceptor interceptor = new MidoLoggingInterceptor(LogLevel.CONSOLE, StandardCharsets.UTF_8, true, 0);
        HttpRequest request = stubRequest("https://big.example.com/list");
        when(response.getBody()).thenReturn(new ByteArrayInputStream("0123456789ABCDEFGHIJ".getBytes(StandardCharsets.UTF_8)));

        // When
        interceptor.intercept(request, new byte[0], execution);

        // Then
        assertThat(findLogContaining("[mido-client response]"))
                .contains("body: 0123456789ABCDEFGHIJ")
                .doesNotContain("truncated");
    }

    @Test
    void shouldLogServerErrorResponseAtErrorLevel() throws Exception {
        // Given - 5xx는 알림에 걸려야 한다
        MidoLoggingInterceptor interceptor = new MidoLoggingInterceptor(LogLevel.CONSOLE, StandardCharsets.UTF_8, true, 0);
        HttpRequest request = stubRequest("https://fail.example.com/path");
        when(response.getStatusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);

        // When
        interceptor.intercept(request, new byte[0], execution);

        // Then
        assertThat(findEvent("[mido-client response]").getLevel()).isEqualTo(Level.ERROR);
    }

    @Test
    void shouldLogClientErrorResponseAtWarnLevel() throws Exception {
        // Given
        MidoLoggingInterceptor interceptor = new MidoLoggingInterceptor(LogLevel.CONSOLE, StandardCharsets.UTF_8, true, 0);
        HttpRequest request = stubRequest("https://fail.example.com/path");
        when(response.getStatusCode()).thenReturn(HttpStatus.BAD_REQUEST);

        // When
        interceptor.intercept(request, new byte[0], execution);

        // Then
        assertThat(findEvent("[mido-client response]").getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void shouldLogSuccessfulResponseAtInfoLevel() throws Exception {
        // Given
        MidoLoggingInterceptor interceptor = new MidoLoggingInterceptor(LogLevel.CONSOLE, StandardCharsets.UTF_8, true, 0);
        HttpRequest request = stubRequest("https://ok.example.com/path");

        // When
        interceptor.intercept(request, new byte[0], execution);

        // Then
        assertThat(findEvent("[mido-client response]").getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    void shouldLogFailureWithElapsedTimeWhenExecutionThrows() throws Exception {
        // Given - 전송 단계에서 read timeout 발생
        MidoLoggingInterceptor interceptor = new MidoLoggingInterceptor(LogLevel.CONSOLE, StandardCharsets.UTF_8, true, 0);
        HttpRequest request = stubRequest("https://timeout.example.com/path");
        when(execution.execute(any(HttpRequest.class), any(byte[].class)))
                .thenThrow(new SocketTimeoutException("Read timed out"));

        // When & Then - 예외는 그대로 전파된다
        assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], execution))
                .isInstanceOf(SocketTimeoutException.class)
                .hasMessage("Read timed out");

        // Then - 실패 로그에 소요시간과 예외 타입이 남는다
        ILoggingEvent failureEvent = findEvent("[mido-client failure]");
        assertThat(failureEvent.getLevel()).isEqualTo(Level.ERROR);
        assertThat(failureEvent.getFormattedMessage())
                .contains("elapsedMs: ")
                .contains("failureType: timeout")
                .contains("delivery: UNKNOWN")
                .contains("SocketTimeoutException")
                .contains("Read timed out")
                .contains("https://timeout.example.com/path");
    }

    @Test
    void shouldClassifyDnsFailureAsNotDelivered() throws Exception {
        // Given - DNS 실패는 요청이 서버에 도달하지 않았음을 뜻한다 (비멱등 요청도 재시도 가능)
        MidoLoggingInterceptor interceptor = new MidoLoggingInterceptor(LogLevel.CONSOLE, StandardCharsets.UTF_8, true, 0);
        HttpRequest request = stubRequest("https://nope.invalid/path");
        when(execution.execute(any(HttpRequest.class), any(byte[].class)))
                .thenThrow(new UnknownHostException("nope.invalid"));

        // When & Then
        assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], execution))
                .isInstanceOf(UnknownHostException.class);
        assertThat(findLogContaining("[mido-client failure]"))
                .contains("failureType: dns")
                .contains("delivery: NOT_DELIVERED");
    }

    @Test
    void shouldNotLogFailureWhenLogLevelIsOff() throws Exception {
        // Given
        MidoLoggingInterceptor interceptor = new MidoLoggingInterceptor(LogLevel.OFF, StandardCharsets.UTF_8, true, 0);
        HttpRequest request = stubRequest("https://timeout.example.com/path");
        when(execution.execute(any(HttpRequest.class), any(byte[].class)))
                .thenThrow(new SocketTimeoutException("Read timed out"));

        // When & Then - 예외는 전파되지만 로그는 남지 않는다
        assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], execution))
                .isInstanceOf(SocketTimeoutException.class);
        assertThat(appender.list).noneMatch(e -> e.getFormattedMessage().contains("[mido-client failure]"));
    }

    private HttpRequest stubRequest(String url) throws Exception {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getHeaders()).thenReturn(new HttpHeaders());
        when(request.getMethod()).thenReturn(HttpMethod.POST);
        when(request.getURI()).thenReturn(new URI(url));
        return request;
    }

    private String findLogContaining(String marker) {
        return findEvent(marker).getFormattedMessage();
    }

    private ILoggingEvent findEvent(String marker) {
        return appender.list.stream()
                .filter(event -> event.getFormattedMessage().contains(marker))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No log line containing: " + marker));
    }
}