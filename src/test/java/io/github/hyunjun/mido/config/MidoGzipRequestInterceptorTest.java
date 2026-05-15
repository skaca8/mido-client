package io.github.hyunjun.mido.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MidoGzipRequestInterceptorTest {

    private HttpRequest request;
    private HttpHeaders headers;
    private ClientHttpRequestExecution execution;
    private ClientHttpResponse response;

    @BeforeEach
    void setUp() {
        request = mock(HttpRequest.class);
        headers = new HttpHeaders();
        execution = mock(ClientHttpRequestExecution.class);
        response = mock(ClientHttpResponse.class);

        when(request.getHeaders()).thenReturn(headers);
    }

    @Test
    void shouldSkipCompressionWhenBodySmallerThanMinSize() throws Exception {
        MidoGzipRequestInterceptor interceptor = new MidoGzipRequestInterceptor(1024);
        byte[] body = "small".getBytes(StandardCharsets.UTF_8);
        when(execution.execute(eq(request), any(byte[].class))).thenReturn(response);

        interceptor.intercept(request, body, execution);

        ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(execution).execute(eq(request), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).isEqualTo(body);
        assertThat(headers.getFirst(HttpHeaders.CONTENT_ENCODING)).isNull();
    }

    @Test
    void shouldSkipCompressionWhenBodyIsEmpty() throws Exception {
        MidoGzipRequestInterceptor interceptor = new MidoGzipRequestInterceptor(1024);
        byte[] body = new byte[0];
        when(execution.execute(eq(request), any(byte[].class))).thenReturn(response);

        interceptor.intercept(request, body, execution);

        verify(execution).execute(eq(request), eq(body));
        assertThat(headers.getFirst(HttpHeaders.CONTENT_ENCODING)).isNull();
    }

    @Test
    void shouldCompressWhenBodyAtLeastMinSize() throws Exception {
        MidoGzipRequestInterceptor interceptor = new MidoGzipRequestInterceptor(10);
        byte[] body = "0123456789ABCDEFGHIJ".getBytes(StandardCharsets.UTF_8);
        headers.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length));
        when(execution.execute(eq(request), any(byte[].class))).thenReturn(response);

        interceptor.intercept(request, body, execution);

        ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(execution).execute(eq(request), bodyCaptor.capture());

        byte[] compressed = bodyCaptor.getValue();
        assertThat(compressed).isNotEqualTo(body);
        assertThat(decompress(compressed)).isEqualTo(body);
        assertThat(headers.getFirst(HttpHeaders.CONTENT_ENCODING)).isEqualTo("gzip");
        assertThat(headers.getFirst(HttpHeaders.CONTENT_LENGTH)).isNull();
    }

    @Test
    void shouldCompressWhenBodyExactlyAtMinSize() throws Exception {
        MidoGzipRequestInterceptor interceptor = new MidoGzipRequestInterceptor(5);
        byte[] body = "12345".getBytes(StandardCharsets.UTF_8);
        when(execution.execute(eq(request), any(byte[].class))).thenReturn(response);

        interceptor.intercept(request, body, execution);

        assertThat(headers.getFirst(HttpHeaders.CONTENT_ENCODING)).isEqualTo("gzip");
    }

    private byte[] decompress(byte[] compressed) throws Exception {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return gzip.readAllBytes();
        }
    }

}