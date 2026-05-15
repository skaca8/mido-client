package io.github.hyunjun.mido.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MidoGzipResponseInterceptorTest {

    private HttpRequest request;
    private HttpHeaders requestHeaders;
    private ClientHttpRequestExecution execution;

    @BeforeEach
    void setUp() {
        request = mock(HttpRequest.class);
        requestHeaders = new HttpHeaders();
        execution = mock(ClientHttpRequestExecution.class);
        when(request.getHeaders()).thenReturn(requestHeaders);
    }

    @Test
    void shouldAddAcceptEncodingHeader() throws Exception {
        MidoGzipResponseInterceptor interceptor = new MidoGzipResponseInterceptor(10 * 1024 * 1024);
        ClientHttpResponse plainResponse = stubResponse(new HttpHeaders(), "plain".getBytes(StandardCharsets.UTF_8));
        when(execution.execute(any(HttpRequest.class), any(byte[].class))).thenReturn(plainResponse);

        interceptor.intercept(request, new byte[0], execution);

        assertThat(requestHeaders.getFirst(HttpHeaders.ACCEPT_ENCODING)).isEqualTo("gzip");
    }

    @Test
    void shouldDecompressGzipResponseBody() throws Exception {
        MidoGzipResponseInterceptor interceptor = new MidoGzipResponseInterceptor(10 * 1024 * 1024);
        String payload = "{\"hello\":\"world\"}";
        byte[] compressed = gzip(payload.getBytes(StandardCharsets.UTF_8));
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add(HttpHeaders.CONTENT_ENCODING, "gzip");
        responseHeaders.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(compressed.length));
        ClientHttpResponse gzipResponse = stubResponse(responseHeaders, compressed);
        when(execution.execute(any(HttpRequest.class), any(byte[].class))).thenReturn(gzipResponse);

        ClientHttpResponse wrapped = interceptor.intercept(request, new byte[0], execution);

        byte[] decompressed = wrapped.getBody().readAllBytes();
        assertThat(new String(decompressed, StandardCharsets.UTF_8)).isEqualTo(payload);
        assertThat(wrapped.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING)).isNull();
        assertThat(wrapped.getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH)).isNull();
    }

    @Test
    void shouldPassThroughWhenResponseNotGzipped() throws Exception {
        MidoGzipResponseInterceptor interceptor = new MidoGzipResponseInterceptor(10 * 1024 * 1024);
        String payload = "{\"hello\":\"world\"}";
        HttpHeaders responseHeaders = new HttpHeaders();
        ClientHttpResponse plainResponse = stubResponse(responseHeaders, payload.getBytes(StandardCharsets.UTF_8));
        when(execution.execute(any(HttpRequest.class), any(byte[].class))).thenReturn(plainResponse);

        ClientHttpResponse result = interceptor.intercept(request, new byte[0], execution);

        assertThat(result).isSameAs(plainResponse);
        assertThat(new String(result.getBody().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(payload);
    }

    @Test
    void shouldSupportMultipleGetBodyCallsForBufferedResponse() throws Exception {
        // BufferingClientHttpRequestFactory 계약: getBody()는 여러 번 호출 가능해야 함.
        // 로깅 인터셉터가 1차 소비 → 메시지 컨버터가 2차 소비하는 실제 흐름을 반영한 회귀 방지 테스트.
        MidoGzipResponseInterceptor interceptor = new MidoGzipResponseInterceptor(10 * 1024 * 1024);
        String payload = "{\"hello\":\"world\"}";
        byte[] compressed = gzip(payload.getBytes(StandardCharsets.UTF_8));
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add(HttpHeaders.CONTENT_ENCODING, "gzip");
        ClientHttpResponse gzipResponse = stubResponse(responseHeaders, compressed);
        when(execution.execute(any(HttpRequest.class), any(byte[].class))).thenReturn(gzipResponse);

        ClientHttpResponse wrapped = interceptor.intercept(request, new byte[0], execution);

        String firstRead = new String(wrapped.getBody().readAllBytes(), StandardCharsets.UTF_8);
        String secondRead = new String(wrapped.getBody().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(firstRead).isEqualTo(payload);
        assertThat(secondRead).isEqualTo(payload);
    }

    @Test
    void shouldHandleGzipEncodingCaseInsensitively() throws Exception {
        MidoGzipResponseInterceptor interceptor = new MidoGzipResponseInterceptor(10 * 1024 * 1024);
        byte[] compressed = gzip("data".getBytes(StandardCharsets.UTF_8));
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add(HttpHeaders.CONTENT_ENCODING, "GZIP");
        ClientHttpResponse gzipResponse = stubResponse(responseHeaders, compressed);
        when(execution.execute(any(HttpRequest.class), any(byte[].class))).thenReturn(gzipResponse);

        ClientHttpResponse wrapped = interceptor.intercept(request, new byte[0], execution);

        assertThat(new String(wrapped.getBody().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("data");
    }

    @Test
    void shouldThrowIOExceptionWhenDecompressedSizeExceedsCap() throws Exception {
        // 1MB짜리 반복 패턴을 압축하면 작은 크기로 → 압축 폭탄 시뮬레이션
        int cap = 1024;
        MidoGzipResponseInterceptor interceptor = new MidoGzipResponseInterceptor(cap);
        byte[] largePayload = new byte[cap * 100];  // cap보다 100배 큰 평문
        Arrays.fill(largePayload, (byte) 'A');
        byte[] compressed = gzip(largePayload);

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add(HttpHeaders.CONTENT_ENCODING, "gzip");
        ClientHttpResponse gzipResponse = stubResponse(responseHeaders, compressed);
        when(execution.execute(any(HttpRequest.class), any(byte[].class))).thenReturn(gzipResponse);

        ClientHttpResponse wrapped = interceptor.intercept(request, new byte[0], execution);

        assertThatThrownBy(() -> wrapped.getBody().readAllBytes())
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Decompressed gzip response exceeds maxDecompressedSize");
    }

    @Test
    void shouldDecompressWithinCap() throws Exception {
        int cap = 64 * 1024;
        MidoGzipResponseInterceptor interceptor = new MidoGzipResponseInterceptor(cap);
        String payload = "{\"data\":\"ok\"}";
        byte[] compressed = gzip(payload.getBytes(StandardCharsets.UTF_8));

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add(HttpHeaders.CONTENT_ENCODING, "gzip");
        ClientHttpResponse gzipResponse = stubResponse(responseHeaders, compressed);
        when(execution.execute(any(HttpRequest.class), any(byte[].class))).thenReturn(gzipResponse);

        ClientHttpResponse wrapped = interceptor.intercept(request, new byte[0], execution);

        assertThat(new String(wrapped.getBody().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(payload);
    }

    private ClientHttpResponse stubResponse(HttpHeaders responseHeaders, byte[] body) throws Exception {
        ClientHttpResponse mockResponse = mock(ClientHttpResponse.class);
        when(mockResponse.getHeaders()).thenReturn(responseHeaders);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream(body));
        return mockResponse;
    }

    private byte[] gzip(byte[] body) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(baos)) {
            gz.write(body);
        }
        return baos.toByteArray();
    }

}