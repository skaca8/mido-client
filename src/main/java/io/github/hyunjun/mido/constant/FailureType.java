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
 *     log.warn("call failed: {}", FailureType.classify(e));
 *     throw translate(e);
 * }
 * }</pre>
 *
 * <p><strong>{@link Delivery} is an observation, not a retry policy.</strong> It answers "did the
 * request reach the server", which is not the same question as "is re-running this operation safe".
 * The second question is about the operation's own semantics — a non-idempotent call needs an
 * idempotency key regardless of what this classifier says, because
 * {@link Delivery#UNKNOWN} is a frequent and unavoidable answer. Treating
 * {@link Delivery#NOT_DELIVERED} as "safe to retry" is the mistake this class is most likely to
 * invite; it holds only for operations that are already safe to re-run.
 *
 * <p>A connect timeout is reported separately from a response timeout
 * ({@code HttpConnectTimeoutException} vs {@code HttpTimeoutException}), so the former is classified
 * as {@link #CONNECT} while the latter stays {@link #TIMEOUT} with {@link Delivery#UNKNOWN}. Note
 * that {@code read-timeout-seconds} is a whole-exchange deadline, so a {@link #TIMEOUT} routinely
 * means "the server processed the request but the response did not arrive in time".
 *
 * <p><strong>Redirects narrow what {@link Delivery#NOT_DELIVERED} asserts.</strong> The transport
 * follows redirects, and for {@code 307}/{@code 308} it re-sends the original method and body to the
 * new location. So a {@link #DNS} or {@link #CONNECT} failure can occur on a later hop, after an
 * earlier host was already reached. What {@link Delivery#NOT_DELIVERED} means is therefore "the
 * request was not delivered to the host that failed" — every host reached before it answered with a
 * redirect rather than performing the operation. That is the normal case, but it rests on the server
 * behaving correctly: a server that acts on a request and <em>then</em> redirects would make the
 * label wrong. The classifier cannot see the redirect chain, so it cannot detect that.
 */
@Getter
@RequiredArgsConstructor
public enum FailureType {

    /** Host name could not be resolved, so no connection to it was made. */
    DNS("dns", Delivery.NOT_DELIVERED),
    /**
     * A TLS failure — handshake, certificate validation, or a protocol error mid-stream.
     *
     * <p>Delivery is {@link Delivery#UNKNOWN} rather than {@link Delivery#NOT_DELIVERED} because
     * {@code SSLException} also covers failures raised while <em>reading the response</em>, which
     * happen after the request was delivered. Distinguishing the handshake case would buy nothing:
     * a TLS failure does not succeed on retry either way.
     */
    TLS("tls", Delivery.UNKNOWN),
    /** Connection refused, unreachable, or the connect phase timed out, so nothing was sent to it. */
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
     * What the transport observed about whether the request reached the server. This is evidence for
     * a retry decision, never the decision itself — see the note on {@link FailureType}.
     */
    public enum Delivery {
        /** The request is not believed to have reached the server. */
        NOT_DELIVERED,
        /** The server received the request; re-running a non-idempotent call may duplicate it. */
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
