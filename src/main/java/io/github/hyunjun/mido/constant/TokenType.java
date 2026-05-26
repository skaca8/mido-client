package io.github.hyunjun.mido.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Authentication scheme used to build the {@code Authorization} header.
 *
 * <p>{@code prefix} is the literal string placed before the token value:
 * {@code "Authorization: <prefix> <token>"}.
 */
@Getter
@RequiredArgsConstructor
public enum TokenType {
    /** {@code Authorization: Bearer <token>}. */
    BEARER("Bearer"),
    /** {@code Authorization: Basic <base64-credentials>}. */
    BASIC("Basic"),
    /** {@code Authorization: ApiKey <token>}. */
    API_KEY("ApiKey");

    private final String prefix;
}