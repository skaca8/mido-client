package io.github.hyunjun.mido.config;

import io.github.hyunjun.mido.constant.ContentType;
import io.github.hyunjun.mido.constant.LogLevel;
import io.github.hyunjun.mido.constant.TokenType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Root type bound from the {@code mido-client.*} property prefix in {@code application.yml}.
 *
 * <p>Validation is applied at bind time ({@link Validated}); a malformed configuration causes the
 * Spring context to fail to start with a {@code BindValidationException} that names the offending
 * field.
 *
 * @see MidoClientAutoConfiguration
 */
@Getter
@Setter
@Validated
@FieldDefaults(level = AccessLevel.PRIVATE)
@ConfigurationProperties(prefix = "mido-client")
public class MidoClientProperties {

    /** Master switch — when {@code false} (default), the auto-configuration is skipped entirely. */
    Boolean enabled = false;

    /** Channel definitions keyed by channel name. Keys are normalized to lowercase at bind time. */
    Map<String, @Valid ChannelConfig> channels = new HashMap<>();

    /**
     * One configured channel. A channel has one mandatory {@link #primary} endpoint and an optional
     * {@link #secondary} endpoint for the same logical service (e.g. read vs. write hosts).
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ChannelConfig {

        /** Optional human-readable description; unused at runtime. */
        String title;

        /** Default charset for response body decoding. Defaults to {@code UTF-8}. */
        String charset = "UTF-8";

        /** Outgoing {@code Content-Type} for every request on this channel. Defaults to JSON. */
        @NotNull
        ContentType type = ContentType.JSON;

        /** Required primary endpoint. */
        @Valid
        @NotNull
        EndpointConfig primary;

        /** Optional secondary endpoint; falls back to {@link #primary} if not configured. */
        @Valid
        EndpointConfig secondary;

    }

    /**
     * One endpoint within a channel ({@code primary} or {@code secondary}). Carries the base URL,
     * timeouts, authentication, custom headers, interceptors, logging level, and gzip options.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class EndpointConfig {

        /** Optional human-readable description; unused at runtime. */
        String title;

        /** Base URL. Must start with {@code http://} or {@code https://}. Required. */
        @NotBlank
        @Pattern(regexp = "^https?://.+", message = "url must start with http:// or https://")
        String url;

        /** Read timeout in seconds. Must be positive. Defaults to {@code 60}. */
        @Positive
        Long readTimeoutSeconds = 60L;

        /** Connect timeout in seconds. Must be positive. Defaults to {@code 3}. */
        @Positive
        Long connectTimeoutSeconds = 3L;

        /** Optional authentication (Bearer / Basic / API key). */
        @Valid
        Authorization authorization;

        /** Static headers attached to every outgoing request on this endpoint. */
        List<@Valid Header> headers;

        /** Logging mode for this endpoint. Defaults to {@link LogLevel#CONSOLE}. */
        LogLevel log = LogLevel.CONSOLE;

        /**
         * Fully-qualified class names of {@code ClientHttpRequestInterceptor} implementations to
         * register. Each class must expose a public no-arg constructor; failure to load or
         * instantiate is reported at first use.
         */
        List<String> interceptors;

        /** Per-endpoint gzip switches. Defaults to both directions off. */
        @Valid
        Gzip gzip = new Gzip();

    }

    /**
     * Per-endpoint gzip configuration. Each direction is independently toggleable.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Gzip {

        /** When {@code true}, compress outgoing bodies larger than {@link #minSize}. */
        @NotNull
        Boolean request = false;

        /**
         * When {@code true}, send {@code Accept-Encoding: gzip} and transparently decompress
         * gzipped responses (subject to {@link #maxDecompressedSize}).
         */
        @NotNull
        Boolean response = false;

        /** Request bodies smaller than this many bytes skip compression. Defaults to {@code 1024}. */
        @PositiveOrZero
        Integer minSize = 1024;

        /**
         * Decompression-bomb cap. If the decompressed response would exceed this many bytes, an
         * {@link java.io.IOException} is thrown immediately. Defaults to {@code 10 * 1024 * 1024}
         * (10 MB).
         */
        @Positive
        Integer maxDecompressedSize = 10 * 1024 * 1024;

    }

    /**
     * Optional authentication. Both {@link #type} and {@link #token} are required when this block
     * is present; the resulting header is {@code Authorization: <prefix> <token>}.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Authorization {

        /** Scheme: {@code bearer}, {@code basic}, or {@code api_key}. */
        @NotNull
        TokenType type;

        /** Token value attached after the scheme prefix. */
        @NotBlank
        String token;

    }

    /**
     * A static header key/value pair to attach to every request on an endpoint.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Header {

        /** Header name. Must be non-blank. */
        @NotBlank
        String name;

        /** Header value. Must be non-blank. */
        @NotBlank
        String value;

    }

    /**
     * Replaces the channel map, normalizing every key to lowercase ({@link Locale#ROOT}) so YAML
     * keys, the cache, and {@link #getChannelConfig(String)} all behave consistently regardless of
     * the casing the user typed.
     *
     * @param channels new map (may be {@code null}, which clears the existing entries)
     */
    public void setChannels(Map<String, ChannelConfig> channels) {
        // YAML 키의 대소문자에 상관없이 일관되게 조회되도록 바인딩 시점에 소문자로 정규화한다.
        if (channels == null) {
            this.channels = new HashMap<>();
            return;
        }
        Map<String, ChannelConfig> normalized = new HashMap<>();
        channels.forEach((key, value) -> normalized.put(normalizeChannelName(key), value));
        this.channels = normalized;
    }

    /**
     * Looks up a channel by name, ignoring case.
     *
     * @param channelName YAML channel key (any casing)
     * @return matching channel configuration
     * @throws IllegalArgumentException if no channel with that name (case-insensitive) is configured
     */
    public ChannelConfig getChannelConfig(String channelName) {
        String normalizedName = normalizeChannelName(channelName);
        ChannelConfig config = channels.get(normalizedName);
        if (config == null) {
            throw new IllegalArgumentException("Unknown Channel: " + channelName);
        }
        return config;
    }

    private static String normalizeChannelName(String channelName) {
        return channelName == null ? null : channelName.toLowerCase(Locale.ROOT);
    }

}
