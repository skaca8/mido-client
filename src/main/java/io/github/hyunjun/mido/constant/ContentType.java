package io.github.hyunjun.mido.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;

@Getter
@RequiredArgsConstructor
public enum ContentType {
    JSON("json", MediaType.APPLICATION_JSON),
    XML("xml", MediaType.APPLICATION_XML);

    private final String value;
    private final MediaType mediaType;
}