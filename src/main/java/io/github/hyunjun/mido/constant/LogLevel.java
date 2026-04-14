package io.github.hyunjun.mido.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LogLevel {
    OFF("off"),
    CONSOLE("console"),
    FILE("file"),
    ALL("all");

    private final String value;

    public static LogLevel resolveEffectiveLogLevel(LogLevel logLevel) {
        return logLevel != null ? logLevel : CONSOLE;
    }
}