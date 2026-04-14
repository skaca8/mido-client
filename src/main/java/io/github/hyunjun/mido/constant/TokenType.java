package io.github.hyunjun.mido.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TokenType {
    BEARER("Bearer"),
    BASIC("Basic"),
    API_KEY("ApiKey");

    private final String prefix;
}