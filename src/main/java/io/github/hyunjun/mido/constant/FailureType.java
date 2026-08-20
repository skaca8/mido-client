package io.github.hyunjun.mido.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.client.RestClientResponseException;

import javax.net.ssl.SSLException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;

/**
 * Classification of a failed channel call, so callers do not have to walk the {@code cause} chain
 * themselves to answer the only question that usually matters: <em>did the request reach the
 * server?</em>
 *
 * <p>This is a read-only classifier. mido-client never wraps or replaces the exception that Spring
 * and the JDK throw — {@code RestClientException}, {@code SocketTimeoutException},
 * {@code HttpTimeoutException} and friends propagate unchanged, so existing exception handling and
 * Resilience4j exception predicates keep working. Use {@link #classify(Throwable)} on a caught
 * exception when you need the category:
 *
 * <pre>{@code
 * catch (RestClientException e) {
 *     if (FailureType.classify(e).getDelivery() == FailureType.Delivery.NOT_DELIVERED) {
 *         retry();   // 서버에 도달하지 않았으므로 비멱등 요청도 재시도 가능
 *     }
 * }
 * }</pre>
 *
 * <p>A connect timeout is reported separately from a response timeout
 * ({@code HttpConnectTimeoutException} vs {@code HttpTimeoutException}), so the former is classified
 * as {@link #CONNECT} — request not delivered — while the latter stays {@link #TIMEOUT} with
 * {@link Delivery#UNKNOWN}, because the request may already have been sent.
 */
@Getter
@RequiredArgsConstructor
public enum FailureType {

    /** Host name could not be resolved. The request never left the client. */
    DNS("dns", Delivery.NOT_DELIVERED),
    /** TLS handshake or certificate validation failed. No application data was sent. */
    TLS("tls", Delivery.NOT_DELIVERED),
    /** Connection refused, unreachable, or the connect phase timed out. */
    CONNECT("connect", Delivery.NOT_DELIVERED),
    /** Timed out waiting for the response; the request may already have been sent. */
    TIMEOUT("timeout", Delivery.UNKNOWN),
    /** The server answered with 4xx — the request was delivered and understood. */
    CLIENT_ERROR("client-error", Delivery.DELIVERED),
    /** The server answered with 5xx — the request was delivered. */
    SERVER_ERROR("server-error", Delivery.DELIVERED),
    /** Nothing above matched. Treat delivery as unknown. */
    UNKNOWN("unknown", Delivery.UNKNOWN);

    private final String value;
    private final Delivery delivery;

    /**
     * Whether the request is known to have reached the server. Drives the only decision that
     * usually depends on the failure category: whether a non-idempotent call may be retried.
     */
    public enum Delivery {
        /** The request never reached the server; retrying is safe even for non-idempotent calls. */
        NOT_DELIVERED,
        /** The server received the request; retrying a non-idempotent call may duplicate it. */
        DELIVERED,
        /** Cannot be determined from the exception. Treat as possibly delivered. */
        UNKNOWN
    }

    /**
     * Classifies a thrown exception by walking its {@code cause} chain until a known type matches.
     *
     * @param throwable the caught exception; {@code null} yields {@link #UNKNOWN}
     * @return the matching category, or {@link #UNKNOWN} when nothing in the chain is recognized
     */
    public static FailureType classify(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            FailureType matched = classifySingle(current);
            if (matched != UNKNOWN) return matched;
            if (current.getCause() == current) break;
        }
        return UNKNOWN;
    }

    private static FailureType classifySingle(Throwable throwable) {
        // 순서 주의: HttpConnectTimeoutException은 HttpTimeoutException의 하위 타입이므로 먼저 본다.
        if (throwable instanceof UnknownHostException) return DNS;
        if (throwable instanceof SSLException) return TLS;
        if (throwable instanceof HttpConnectTimeoutException
                || throwable instanceof ConnectException
                || throwable instanceof NoRouteToHostException) return CONNECT;
        if (throwable instanceof HttpTimeoutException
                || throwable instanceof SocketTimeoutException
                || throwable instanceof InterruptedIOException) return TIMEOUT;
        return classifyStatus(throwable);
    }

    private static FailureType classifyStatus(Throwable throwable) {
        if (throwable instanceof RestClientResponseException responseException) {
            return responseException.getStatusCode().is4xxClientError() ? CLIENT_ERROR : SERVER_ERROR;
        }
        return UNKNOWN;
    }
}
