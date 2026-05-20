package io.github.hyunjun.mido.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EndpointType {
    PRIMARY("primary"),
    SECONDARY("secondary");

    private final String value;
}
