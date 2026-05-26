package io.github.hyunjun.mido.config;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * Internal. Adds {@code Accept-Encoding: gzip} and transparently decompresses gzipped responses
 * when {@code gzip.response: true} is set on the endpoint. Bounded by
 * {@code gzip.max-decompressed-size} to defend against decompression bombs. Not part of the public
 * API — visibility may be reduced in a future minor release.
 */
@RequiredArgsConstructor
@SuppressWarnings("ClassCanBeRecord")
public class MidoGzipResponseInterceptor implements ClientHttpRequestInterceptor {

    private static final int READ_BUFFER_SIZE = 8192;

    private final int maxDecompressedSize;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte @NonNull [] body, ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().set(HttpHeaders.ACCEPT_ENCODING, "gzip");

        ClientHttpResponse response = execution.execute(request, body);

        String encoding = response.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING);
        if (encoding != null && encoding.toLowerCase(Locale.ROOT).contains("gzip")) {
            return new GzipDecompressingResponse(response, maxDecompressedSize);
        }
        return response;
    }

    private static class GzipDecompressingResponse implements ClientHttpResponse {

        private final ClientHttpResponse delegate;
        private final HttpHeaders headers;
        private final int maxDecompressedSize;
        private byte[] decompressedBytes;

        GzipDecompressingResponse(ClientHttpResponse delegate, int maxDecompressedSize) {
            this.delegate = delegate;
            this.maxDecompressedSize = maxDecompressedSize;
            this.headers = new HttpHeaders();
            this.headers.putAll(delegate.getHeaders());
            this.headers.remove(HttpHeaders.CONTENT_ENCODING);
            this.headers.remove(HttpHeaders.CONTENT_LENGTH);
        }

        @Override
        @NonNull
        public InputStream getBody() throws IOException {
            // BufferingClientHttpRequestFactory 계약상 getBody()는 다중 호출 가능해야 한다.
            // 압축 해제는 1회만 수행하고 매 호출마다 신선한 ByteArrayInputStream을 반환한다.
            // 압축 폭탄(decompression bomb) 방어: maxDecompressedSize 초과 시 IOException.
            if (decompressedBytes == null) {
                decompressedBytes = decompressBounded(delegate.getBody(), maxDecompressedSize);
            }
            return new ByteArrayInputStream(decompressedBytes);
        }

        private static byte[] decompressBounded(InputStream source, int maxSize) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[READ_BUFFER_SIZE];
            int totalRead = 0;
            try (GZIPInputStream gzip = new GZIPInputStream(source)) {
                int n;
                while ((n = gzip.read(buffer)) != -1) {
                    totalRead += n;
                    if (totalRead > maxSize) {
                        throw new IOException("Decompressed gzip response exceeds maxDecompressedSize (" + maxSize + " bytes)");
                    }
                    out.write(buffer, 0, n);
                }
            }
            return out.toByteArray();
        }

        @Override
        @NonNull
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        @NonNull
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        @NonNull
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public void close() {
            delegate.close();
        }

    }

}