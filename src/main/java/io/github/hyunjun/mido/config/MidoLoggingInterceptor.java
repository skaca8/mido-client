package io.github.hyunjun.mido.config;

import io.github.hyunjun.mido.constant.LogLevel;
import io.github.hyunjun.mido.context.ChannelContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("ClassCanBeRecord")
public class MidoLoggingInterceptor implements ClientHttpRequestInterceptor {

    private final LogLevel logLevel;
    private final Charset charset;

    private static final Logger fileLog = LoggerFactory.getLogger("MidoClientFileLog");

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        long startTime = System.currentTimeMillis();
        logRequest(request, body, logLevel);

        ClientHttpResponse response = execution.execute(request, body);

        long responseTime = System.currentTimeMillis() - startTime;
        logResponse(response, responseTime, logLevel, charset);

        return response;
    }

    private void logRequest(HttpRequest request, byte[] body, LogLevel logLevel) {
        LogLevel effectiveLogLevel = LogLevel.resolveEffectiveLogLevel(logLevel);

        if (LogLevel.OFF == effectiveLogLevel) return;

        try {
            String channelAction = ChannelContext.isBound()
                    ? ChannelContext.getChannelAction()
                    : "unknown";
            String bodyString = body != null && body.length > 0 ? new String(body, StandardCharsets.UTF_8) : "";

            String logMessage = "[mido-client request] channelAction: {}, method: {}, url: {}, body: {}";

            if (LogLevel.ALL == effectiveLogLevel) {
                log.info(logMessage, channelAction, request.getMethod(), request.getURI(), bodyString);
                fileLog.info(logMessage, channelAction, request.getMethod(), request.getURI(), bodyString);
            } else {
                Logger logger = getLogger(effectiveLogLevel);
                logger.info(logMessage, channelAction, request.getMethod(), request.getURI(), bodyString);
            }
        } catch (Exception e) {
            log.error("Error logging request: {}", e.getMessage(), e);
        }
    }

    private void logResponse(ClientHttpResponse response, long responseTimeMs, LogLevel logLevel, Charset defaultCharset) {
        LogLevel effectiveLogLevel = LogLevel.resolveEffectiveLogLevel(logLevel);

        if (LogLevel.OFF == effectiveLogLevel) return;

        try {
            StringBuilder logMessage = new StringBuilder("[mido-client response] status: ")
                    .append(response.getStatusCode())
                    .append(", responseTimeMs: ")
                    .append(responseTimeMs);

            String channelAction = ChannelContext.isBound()
                    ? ChannelContext.getChannelAction()
                    : "unknown";
            if (channelAction != null && !channelAction.trim().isEmpty()) {
                logMessage.append(", channelAction: ").append(channelAction);
            }

            logMessage.append(", body: ").append(readResponseBody(response, defaultCharset));

            if (LogLevel.ALL == effectiveLogLevel) {
                log.info("{}", logMessage);
                fileLog.info("{}", logMessage);
            } else {
                Logger logger = getLogger(effectiveLogLevel);
                logger.info("{}", logMessage);
            }
        } catch (Exception e) {
            log.error("Error logging response: {}", e.getMessage(), e);
        }
    }

    private String readResponseBody(ClientHttpResponse response, Charset defaultCharset) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            response.getBody().transferTo(outputStream);
            byte[] bytes = outputStream.toByteArray();

            return new String(bytes, smartDetectCharset(response.getHeaders(), bytes, defaultCharset));
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

    private Logger getLogger(LogLevel logLevel) {
        return logLevel == LogLevel.FILE ? fileLog : log;
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