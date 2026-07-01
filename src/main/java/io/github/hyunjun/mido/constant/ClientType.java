package io.github.hyunjun.mido.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Underlying HTTP transport used to build a channel's {@link org.springframework.web.client.RestClient}.
 *
 * <p>{@link #SIMPLE} (default) uses {@code SimpleClientHttpRequestFactory}, backed by JDK
 * {@code HttpURLConnection}. Connection reuse goes through the JVM-global keep-alive cache, so all
 * channels effectively share one connection pool regardless of configuration.
 *
 * <p>{@link #JDK} uses {@code JdkClientHttpRequestFactory}, backed by {@code java.net.http.HttpClient}.
 * Each channel/endpoint gets its own {@code HttpClient} — and therefore its own connection pool — plus
 * HTTP/2 support. This is the transport that actually realizes the per-channel connection isolation the
 * library is built around; prefer it when that isolation matters.
 */
@Getter
@RequiredArgsConstructor
public enum ClientType {
    /** {@code SimpleClientHttpRequestFactory} ({@code HttpURLConnection}). Default; JVM-global keep-alive. */
    SIMPLE("simple"),
    /** {@code JdkClientHttpRequestFactory} ({@code java.net.http.HttpClient}). Per-channel pool + HTTP/2. */
    JDK("jdk");

    private final String value;
}
