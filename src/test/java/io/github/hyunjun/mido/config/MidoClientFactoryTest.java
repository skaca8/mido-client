package io.github.hyunjun.mido.config;

import io.github.hyunjun.mido.constant.EndpointType;
import io.github.hyunjun.mido.constant.LogLevel;
import io.github.hyunjun.mido.constant.TokenType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class MidoClientFactoryTest {

    private MidoClientProperties properties;
    private MidoClientFactory factory;

    @BeforeEach
    void setUp() {
        properties = new MidoClientProperties();
        properties.setEnabled(true);

        // Test channel configuration
        MidoClientProperties.ChannelConfig channelConfig = new MidoClientProperties.ChannelConfig();
        channelConfig.setTitle("Test Channel");
        channelConfig.setCharset("UTF-8");

        // First endpoint
        MidoClientProperties.EndpointConfig firstEndpoint = new MidoClientProperties.EndpointConfig();
        firstEndpoint.setTitle("First Endpoint");
        firstEndpoint.setUrl("https://api.test.com");
        firstEndpoint.setReadTimeoutSeconds(30L);
        firstEndpoint.setConnectTimeoutSeconds(5L);
        firstEndpoint.setLog(LogLevel.CONSOLE);

        // Authorization
        MidoClientProperties.Authorization auth = new MidoClientProperties.Authorization();
        auth.setType(TokenType.BEARER);
        auth.setToken("test-token");
        firstEndpoint.setAuthorization(auth);

        // Headers
        MidoClientProperties.Header header = new MidoClientProperties.Header();
        header.setName("X-Test-Header");
        header.setValue("test-value");
        firstEndpoint.setHeaders(List.of(header));

        channelConfig.setFirst(firstEndpoint);

        // Second endpoint
        MidoClientProperties.EndpointConfig secondEndpoint = new MidoClientProperties.EndpointConfig();
        secondEndpoint.setTitle("Second Endpoint");
        secondEndpoint.setUrl("https://api2.test.com");
        secondEndpoint.setReadTimeoutSeconds(60L);
        secondEndpoint.setConnectTimeoutSeconds(3L);
        secondEndpoint.setLog(LogLevel.ALL);

        channelConfig.setSecond(secondEndpoint);

        properties.getChannels().put("test", channelConfig);

        factory = new MidoClientFactory(properties);
    }

    @Test
    void shouldCreateFirstEndpointClient() {
        // When
        RestClient client = factory.getOrCreateClient("test");

        // Then
        assertThat(client).isNotNull();
    }

    @Test
    void shouldCreateSecondEndpointClient() {
        // When
        RestClient client = factory.getOrCreateClient("test", EndpointType.SECOND);

        // Then
        assertThat(client).isNotNull();
    }

    @Test
    void shouldCacheClients() {
        // When
        RestClient client1 = factory.getOrCreateClient("test");
        RestClient client2 = factory.getOrCreateClient("test");

        // Then
        assertThat(client1).isSameAs(client2);
    }

    @Test
    void shouldCreateDifferentClientsForDifferentEndpoints() {
        // When
        RestClient firstClient = factory.getOrCreateClient("test");
        RestClient secondClient = factory.getOrCreateClient("test", EndpointType.SECOND);

        // Then
        assertThat(firstClient).isNotSameAs(secondClient);
    }

    @Test
    void shouldThrowExceptionForUnknownChannel() {
        // When & Then
        assertThatThrownBy(() -> factory.getOrCreateClient("unknown"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot create RestClient for Channel: unknown");
    }

    @Test
    void shouldThrowExceptionForUnsupportedEndpointType() {
        // When & Then
        assertThatThrownBy(() -> factory.getOrCreateClient("test", EndpointType.FIRST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only SECOND EndpointType is supported for multi-endpoint channels");
    }

    @Test
    void shouldHandleChannelWithoutSecondEndpoint() {
        // Given
        MidoClientProperties.ChannelConfig channelConfig = new MidoClientProperties.ChannelConfig();
        MidoClientProperties.EndpointConfig firstEndpoint = new MidoClientProperties.EndpointConfig();
        firstEndpoint.setUrl("https://single.test.com");
        channelConfig.setFirst(firstEndpoint);
        properties.getChannels().put("single", channelConfig);

        // When
        RestClient client = factory.getOrCreateClient("single", EndpointType.SECOND);

        // Then - Should fallback to first endpoint
        assertThat(client).isNotNull();
    }

}