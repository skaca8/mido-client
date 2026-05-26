package io.github.hyunjun.mido.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Per-endpoint logging mode. {@link #FILE} and {@link #ALL} require a logger named
 * {@code "MidoClientFileLog"} to be configured in the host application's {@code logback.xml}.
 */
@Getter
@RequiredArgsConstructor
public enum LogLevel {
    /** No logging. */
    OFF("off"),
    /** Log to the standard {@code MidoLoggingInterceptor} logger (console by default). */
    CONSOLE("console"),
    /** Log to the dedicated {@code MidoClientFileLog} logger only. */
    FILE("file"),
    /** Log to both the console and the file logger. */
    ALL("all");

    private final String value;

    /**
     * Returns the effective level, defaulting to {@link #CONSOLE} when {@code null} is supplied.
     * Lets callers treat absence as "default" without an explicit null check at each call site.
     *
     * @param logLevel possibly {@code null} level
     * @return non-null level
     */
    public static LogLevel resolveEffectiveLogLevel(LogLevel logLevel) {
        return logLevel != null ? logLevel : CONSOLE;
    }
}