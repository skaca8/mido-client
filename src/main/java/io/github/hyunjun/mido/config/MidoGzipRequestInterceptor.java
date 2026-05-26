package io.github.hyunjun.mido.config;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

/**
 * Internal. Compresses outgoing request bodies with gzip when {@code gzip.request: true} is set on
 * the endpoint and the body meets the minimum size threshold. Not part of the public API —
 * visibility may be reduced in a future minor release.
 */
@RequiredArgsConstructor
@SuppressWarnings("ClassCanBeRecord")
public class MidoGzipRequestInterceptor implements ClientHttpRequestInterceptor {

    private final int minSize;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte @NonNull [] body, ClientHttpRequestExecution execution) throws IOException {
        if (body.length < minSize) {
            return execution.execute(request, body);
        }

        byte[] compressed = compress(body);
        HttpHeaders headers = request.getHeaders();
        headers.set(HttpHeaders.CONTENT_ENCODING, "gzip");
        headers.remove(HttpHeaders.CONTENT_LENGTH);

        return execution.execute(request, compressed);
    }

    private byte[] compress(byte[] body) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(body);
        }
        return baos.toByteArray();
    }

}