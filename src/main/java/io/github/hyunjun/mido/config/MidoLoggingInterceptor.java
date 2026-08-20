package io.github.hyunjun.mido.config;

import io.github.hyunjun.mido.constant.FailureType;
import io.github.hyunjun.mido.constant.LogLevel;
import io.github.hyunjun.mido.context.ChannelContext;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Internal. Implementation detail of mido-client's interceptor chain; instances are wired by
 * {@link MidoClientFactory} according to the configured {@link io.github.hyunjun.mido.constant.LogLevel}.
 * Not part of the public API — visibility may be reduced in a future minor release.
 */
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("ClassCanBeRecord")
public class MidoLoggingInterceptor implements ClientHttpRequestInterceptor {

    private final LogLevel logLevel;
    private final Charset charset;
    private final boolean logBody;
    private final int maxBodyBytes;

    private static final Logger fileLog = LoggerFactory.getLogger("MidoClientFileLog");

    private static final String OMITTED_BODY = "(omitted)";

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte @NonNull [] body, ClientHttpRequestExecution execution) throws IOException {
        long startTime = System.currentTimeMillis();
        logRequest(request, body, logLevel);

        try {
            ClientHttpResponse response = execution.execute(request, body);
            logResponse(response, System.currentTimeMillis() - startTime, logLevel, charset);
            return response;
        } catch (IOException | RuntimeException e) {
            // 전송 실패(connect/read 타임아웃, DNS, TLS)는 응답 로그가 없어 소요시간이 남지 않는다 — 여기서 남기고 그대로 전파한다.
            logFailure(request, System.currentTimeMillis() - startTime, e, logLevel);
            throw e;
        }
    }

    private void logRequest(HttpRequest request, byte[] body, LogLevel logLevel) {
        LogLevel effectiveLogLevel = LogLevel.resolveEffectiveLogLevel(logLevel);

        if (LogLevel.OFF == effectiveLogLevel) return;

        try {
            String channelAction = getChannelAction();
            String bodyString = resolveRequestBody(request, body);

            String logMessage = "[mido-client request] channelAction: {}, method: {}, url: {}, body: {}";

            emit(effectiveLogLevel, logMessage, channelAction, request.getMethod(), request.getURI(), bodyString);
        } catch (Exception e) {
            log.error("Error logging request: {}", e.getMessage(), e);
        }
    }

    private void logResponse(ClientHttpResponse response, long responseTimeMs, LogLevel logLevel, Charset defaultCharset) {
        LogLevel effectiveLogLevel = LogLevel.resolveEffectiveLogLevel(logLevel);

        if (LogLevel.OFF == effectiveLogLevel) return;

        try {
            HttpStatusCode status = response.getStatusCode();

            StringBuilder logMessage = new StringBuilder("[mido-client response] status: ")
                    .append(status)
                    .append(", responseTimeMs: ")
                    .append(responseTimeMs);

            String channelAction = getChannelAction();
            logMessage.append(", channelAction: ").append(channelAction);

            // logBody=false면 body를 읽지도 않는다 — 마스킹이 아니라 미수집이다.
            logMessage.append(", body: ").append(logBody ? readResponseBody(response, defaultCharset) : OMITTED_BODY);

            emitByStatus(effectiveLogLevel, status, "{}", logMessage);
        } catch (Exception e) {
            log.error("Error logging response: {}", e.getMessage(), e);
        }
    }

    private void logFailure(HttpRequest request, long elapsedMs, Exception cause, LogLevel logLevel) {
        LogLevel effectiveLogLevel = LogLevel.resolveEffectiveLogLevel(logLevel);

        if (LogLevel.OFF == effectiveLogLevel) return;

        try {
            // 스택트레이스는 예외가 호출측으로 전파되며 남는다 — 여기서는 원인 식별에 필요한 타입·메시지만 남긴다.
            FailureType failureType = FailureType.classify(cause);
            emitError(effectiveLogLevel,
                    "[mido-client failure] channelAction: {}, method: {}, url: {}, elapsedMs: {}, failureType: {}, delivery: {}, exception: {}",
                    getChannelAction(), request.getMethod(), request.getURI(), elapsedMs,
                    failureType.getValue(), failureType.getDelivery(), cause.toString());
        } catch (Exception e) {
            log.error("Error logging failure: {}", e.getMessage(), e);
        }
    }

    /**
     * Reads at most {@code maxBodyBytes} of the response for logging. The stream is intentionally
     * not closed — the interceptor must not consume a response that downstream converters still
     * read (the factory wraps every transport in {@code BufferingClientHttpRequestFactory}, so
     * {@code getBody()} hands out a fresh stream over the buffered bytes).
     *
     * <p>A truncation boundary can fall inside a multi-byte character, which decodes to a single
     * replacement character at the end of the logged text. That is accepted: the alternative is
     * materializing the whole body just to align the cut.
     */
    private String readResponseBody(ClientHttpResponse response, Charset defaultCharset) {
        try {
            InputStream body = response.getBody();
            byte[] bytes = maxBodyBytes > 0 ? body.readNBytes(maxBodyBytes) : body.readAllBytes();
            // 남은 바이트는 버리면서 개수만 센다 — 절단된 뒤쪽을 힙에 올리지 않는다.
            long dropped = maxBodyBytes > 0 ? body.transferTo(OutputStream.nullOutputStream()) : 0;

            return new String(bytes, smartDetectCharset(response.getHeaders(), bytes, defaultCharset))
                    + truncationSuffix(dropped);
        } catch (IOException e) {
            log.warn("Could not read response body: {}", e.getMessage());
            return "";
        }
    }

    private Charset getCharsetFromContentType(HttpHeaders headers) {
        return Optional.ofNullable(headers.getContentType())
                .map(MediaType::getCharset)
                .orElse(null);
    }

    private String resolveRequestBody(HttpRequest request, byte[] body) {
        if (!logBody) return OMITTED_BODY;
        if (body == null || body.length == 0) return "";

        int limit = bodyLimit(body.length);
        return new String(body, 0, limit, resolveRequestCharset(request))
                + truncationSuffix(body.length - (long) limit);
    }

    private int bodyLimit(int available) {
        return maxBodyBytes > 0 ? Math.min(maxBodyBytes, available) : available;
    }

    private String truncationSuffix(long droppedBytes) {
        return droppedBytes > 0 ? "...(truncated " + droppedBytes + " bytes)" : "";
    }

    private Charset resolveRequestCharset(HttpRequest request) {
        // Content-Type 헤더의 charset이 가장 정확. 없으면 채널에 선언된 기본 charset.
        Charset headerCharset = getCharsetFromContentType(request.getHeaders());
        return headerCharset != null ? headerCharset : charset;
    }

    private Charset smartDetectCharset(HttpHeaders headers, byte[] bytes, Charset defaultCharset) {
        Charset contentTypeCharset = getCharsetFromContentType(headers);
        if (contentTypeCharset != null) {
            return contentTypeCharset;
        }

        boolean isUtf8Valid = isValidUtf8(bytes);

        if (isUtf8Valid) {
            return StandardCharsets.UTF_8;
        }

        return defaultCharset;
    }

    private void emit(LogLevel effectiveLogLevel, String format, Object... args) {
        forEachTarget(effectiveLogLevel, logger -> logger.info(format, args));
    }

    private void emitError(LogLevel effectiveLogLevel, String format, Object... args) {
        forEachTarget(effectiveLogLevel, logger -> logger.error(format, args));
    }

    /**
     * Severity follows the response status so that failures are reachable from alerting without
     * parsing log text: 5xx as {@code error}, 4xx as {@code warn}, everything else as {@code info}.
     * {@link LogLevel} stays what it has always been — the destination, not the severity.
     */
    private void emitByStatus(LogLevel effectiveLogLevel, HttpStatusCode status, String format, Object... args) {
        // status가 null인 커스텀 응답 구현이라도 로그 라인 자체를 잃지 않도록 info로 떨어뜨린다.
        if (status != null && status.is5xxServerError()) {
            forEachTarget(effectiveLogLevel, logger -> logger.error(format, args));
        } else if (status != null && status.is4xxClientError()) {
            forEachTarget(effectiveLogLevel, logger -> logger.warn(format, args));
        } else {
            forEachTarget(effectiveLogLevel, logger -> logger.info(format, args));
        }
    }

    private void forEachTarget(LogLevel effectiveLogLevel, Consumer<Logger> action) {
        if (LogLevel.ALL == effectiveLogLevel) {
            action.accept(log);
            action.accept(fileLog);
        } else {
            action.accept(getLogger(effectiveLogLevel));
        }
    }

    private Logger getLogger(LogLevel logLevel) {
        return logLevel == LogLevel.FILE ? fileLog : log;
    }

    private String getChannelAction() {
        String channelAction = ChannelContext.getChannelAction();
        return StringUtils.hasText(channelAction) ? channelAction : "unknown";
    }

    private boolean isValidUtf8(byte[] bytes) {
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
            decoder.onMalformedInput(CodingErrorAction.REPORT);
            decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
            decoder.decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
