package io.github.hyunjun.mido.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EndpointType {
    FIRST("first"),
    SECOND("second");

    private final String value;
}