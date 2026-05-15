package io.github.hyunjun.mido.config;

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
import java.util.Map;

@Getter
@Setter
@Validated
@FieldDefaults(level = AccessLevel.PRIVATE)
@ConfigurationProperties(prefix = "mido-client")
public class MidoClientProperties {

    Boolean enabled = false;

    Map<String, @Valid ChannelConfig> channels = new HashMap<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ChannelConfig {

        String title;

        String charset = "UTF-8";

        @Valid
        @NotNull
        EndpointConfig first;

        @Valid
        EndpointConfig second;

    }

    @Getter
    @Setter
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class EndpointConfig {

        String title;

        @NotBlank
        @Pattern(regexp = "^https?://.+", message = "url must start with http:// or https://")
        String url;

        @Positive
        Long readTimeoutSeconds = 60L;

        @Positive
        Long connectTimeoutSeconds = 3L;

        @Valid
        Authorization authorization;

        List<@Valid Header> headers;

        LogLevel log = LogLevel.CONSOLE;

        List<String> interceptors;

        @Valid
        Gzip gzip = new Gzip();

    }

    @Getter
    @Setter
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Gzip {

        @NotNull
        Boolean request = false;

        @NotNull
        Boolean response = false;

        @PositiveOrZero
        Integer minSize = 1024;

        @Positive
        Integer maxDecompressedSize = 10 * 1024 * 1024;

    }

    @Getter
    @Setter
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Authorization {

        TokenType type;

        String token;

    }

    @Getter
    @Setter
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Header {

        @NotBlank
        String name;

        @NotBlank
        String value;

    }

    public ChannelConfig getChannelConfig(String channelName) {
        if (channels.containsKey(channelName)) {
            return channels.get(channelName);
        }
        throw new IllegalArgumentException("Unknown Channel: " + channelName);
    }

}