package io.github.hyunjun.mido;

import io.github.hyunjun.mido.config.MidoClientAutoConfiguration;
import io.github.hyunjun.mido.config.MidoClientFactory;
import io.github.hyunjun.mido.config.MidoClientProperties;
import io.github.hyunjun.mido.constant.LogLevel;
import io.github.hyunjun.mido.constant.TokenType;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 간단한 통합 테스트
 * 복잡한 Spring Context 없이 기본 기능만 테스트
 */
class SimpleTest {

    @Test
    void shouldCreateMidoClientProperties() {
        // Given
        MidoClientProperties properties = new MidoClientProperties();
        properties.setEnabled(true);

        // When & Then
        assertThat(properties.getEnabled()).isTrue();
    }

    @Test
    void shouldCreateChannelConfig() {
        // Given
        MidoClientProperties.ChannelConfig config = new MidoClientProperties.ChannelConfig();
        config.setTitle("Test Channel");
        config.setCharset("UTF-8");

        MidoClientProperties.EndpointConfig endpoint = new MidoClientProperties.EndpointConfig();
        endpoint.setUrl("https://api.test.com");
        endpoint.setReadTimeoutSeconds(30L);
        endpoint.setConnectTimeoutSeconds(5L);
        endpoint.setLog(LogLevel.CONSOLE);

        MidoClientProperties.Authorization auth = new MidoClientProperties.Authorization();
        auth.setType(TokenType.BEARER);
        auth.setToken("test-token");
        endpoint.setAuthorization(auth);

        config.setFirst(endpoint);

        // When & Then
        assertThat(config.getTitle()).isEqualTo("Test Channel");
        assertThat(config.getCharset()).isEqualTo("UTF-8");
        assertThat(config.getFirst()).isNotNull();
        assertThat(config.getFirst().getUrl()).isEqualTo("https://api.test.com");
        assertThat(config.getFirst().getAuthorization().getType()).isEqualTo(TokenType.BEARER);
        assertThat(config.getFirst().getAuthorization().getToken()).isEqualTo("test-token");
    }

    @Test
    void shouldCreateMidoClientFactory() {
        // Given
        MidoClientProperties properties = createTestProperties();
        MidoClientFactory factory = new MidoClientFactory(properties);

        // When
        RestClient client = factory.getOrCreateClient("test");

        // Then
        assertThat(client).isNotNull();
    }

    @Test
    void shouldCreateAutoConfiguration() {
        // Given
        MidoClientAutoConfiguration configuration = new MidoClientAutoConfiguration();
        MidoClientProperties properties = createTestProperties();

        // When
        MidoClientFactory factory = configuration.midoClientFactory(properties);

        // Then
        assertThat(factory).isNotNull();
    }

    private MidoClientProperties createTestProperties() {
        MidoClientProperties properties = new MidoClientProperties();
        properties.setEnabled(true);

        MidoClientProperties.ChannelConfig channelConfig = new MidoClientProperties.ChannelConfig();
        channelConfig.setTitle("Test Channel");
        channelConfig.setCharset("UTF-8");

        MidoClientProperties.EndpointConfig firstEndpoint = new MidoClientProperties.EndpointConfig();
        firstEndpoint.setUrl("https://api.test.com");
        firstEndpoint.setReadTimeoutSeconds(30L);
        firstEndpoint.setConnectTimeoutSeconds(5L);
        firstEndpoint.setLog(LogLevel.CONSOLE);

        channelConfig.setFirst(firstEndpoint);
        properties.getChannels().put("test", channelConfig);

        return properties;
    }
}