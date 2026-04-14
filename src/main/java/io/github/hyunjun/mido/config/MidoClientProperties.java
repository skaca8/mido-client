package io.github.hyunjun.mido.config;

import io.github.hyunjun.mido.constant.LogLevel;
import io.github.hyunjun.mido.constant.TokenType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@ConfigurationProperties(prefix = "mido-client")
public class MidoClientProperties {

    Boolean enabled = false;

    Map<String, ChannelConfig> channels = new HashMap<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ChannelConfig {

        String title;

        String charset = "UTF-8";

        EndpointConfig first;

        EndpointConfig second;

    }

    @Getter
    @Setter
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class EndpointConfig {

        String title;

        String url;

        Long readTimeoutSeconds = 60L;

        Long connectTimeoutSeconds = 3L;

        Authorization authorization;

        List<Header> headers;

        LogLevel log = LogLevel.CONSOLE;

        List<String> interceptors;

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

        String name;

        String value;

    }

    public ChannelConfig getChannelConfig(String channelName) {
        if (channels.containsKey(channelName)) {
            return channels.get(channelName);
        }
        throw new IllegalArgumentException("Unknown Channel: " + channelName);
    }

}