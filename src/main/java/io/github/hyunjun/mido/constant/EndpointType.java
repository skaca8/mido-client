package io.github.hyunjun.mido.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Identifies which endpoint within a channel to use. A channel may define two endpoints (e.g. a
 * query host and a process host); selecting {@link #SECONDARY} on a channel that has no secondary
 * configuration falls back to {@link #PRIMARY}.
 */
@Getter
@RequiredArgsConstructor
public enum EndpointType {
    /** The channel's primary endpoint. Required for every channel. */
    PRIMARY("primary"),
    /** The channel's secondary endpoint. Optional; falls back to primary if not configured. */
    SECONDARY("secondary");

    private final String value;
}
