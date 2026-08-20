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
        // body.length == 0은 minSize와 무관하게 건너뛴다. min-size: 0이면 body 없는 GET에도
        // Content-Encoding: gzip과 20바이트 gzip 헤더가 붙는다.
        if (body.length == 0 || body.length < minSize) {
            return execution.execute(request, body);
        }

        byte[] compressed = compress(body);
        HttpHeaders headers = request.getHeaders();
        headers.set(HttpHeaders.CONTENT_ENCODING, "gzip");
        // 압축 후 길이로 갱신한다. 제거하면 전송 계층이 길이를 모르는 상태가 되어
        // SIMPLE(HttpURLConnection)은 chunked로, JDK는 길이 미지정 BodyPublisher로 전환된다 — 411을 내는 서버가 있다.
        headers.setContentLength(compressed.length);

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