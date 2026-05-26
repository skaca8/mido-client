package io.github.hyunjun.mido.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;

/**
 * Outgoing {@code Content-Type} for a channel. The selected media type is attached to every
 * request automatically. Use {@link #JSON} (the default) for POJO bodies serialized via Jackson;
 * use {@link #XML} when sending pre-serialized XML strings.
 */
@Getter
@RequiredArgsConstructor
public enum ContentType {
    /** {@code application/json}. Default. Jackson handles POJO serialization. */
    JSON("json", MediaType.APPLICATION_JSON),
    /** {@code application/xml}. Caller must provide a pre-serialized XML {@link String} body. */
    XML("xml", MediaType.APPLICATION_XML);

    private final String value;
    private final MediaType mediaType;
}