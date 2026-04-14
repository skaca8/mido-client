package io.github.hyunjun.mido.config;

import io.github.hyunjun.mido.constant.LogLevel;
import io.github.hyunjun.mido.constant.TokenType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MidoClientAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MidoClientAutoConfiguration.class));

    @Test
    void shouldNotAutoConfigureWhenDisabled() {
        contextRunner
                .withPropertyValues("mido-client.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MidoClientFactory.class);
                    assertThat(context).doesNotHaveBean(MidoClientProperties.class);
                });
    }

    @Test
    void shouldNotAutoConfigureWhenPropertyNotSet() {
        contextRunner
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MidoClientFactory.class);
                    assertThat(context).doesNotHaveBean(MidoClientProperties.class);
                });
    }

    @Test
    void shouldAutoConfigureWhenEnabled() {
        contextRunner
                .withPropertyValues("mido-client.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(MidoClientFactory.class);
                    assertThat(context).hasSingleBean(MidoClientProperties.class);
                });
    }

    @Test
    void shouldConfigureChannelsFromProperties() {
        contextRunner
                .withPropertyValues(
                        "mido-client.enabled=true",
                        "mido-client.channels.test.title=Test Channel",
                        "mido-client.channels.test.charset=UTF-8",
                        "mido-client.channels.test.first.url=https://api.test.com",
                        "mido-client.channels.test.first.read-timeout-seconds=30",
                        "mido-client.channels.test.first.connect-timeout-seconds=5",
                        "mido-client.channels.test.first.log=console",
                        "mido-client.channels.test.first.authorization.type=bearer",
                        "mido-client.channels.test.first.authorization.token=test-token",
                        "mido-client.channels.test.first.headers[0].name=X-Custom-Header",
                        "mido-client.channels.test.first.headers[0].value=custom-value"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(MidoClientProperties.class);

                    MidoClientProperties properties = context.getBean(MidoClientProperties.class);
                    assertThat(properties.getEnabled()).isTrue();
                    assertThat(properties.getChannels()).containsKey("test");

                    MidoClientProperties.ChannelConfig channelConfig = properties.getChannels().get("test");
                    assertThat(channelConfig.getTitle()).isEqualTo("Test Channel");
                    assertThat(channelConfig.getCharset()).isEqualTo("UTF-8");

                    MidoClientProperties.EndpointConfig firstEndpoint = channelConfig.getFirst();
                    assertThat(firstEndpoint.getUrl()).isEqualTo("https://api.test.com");
                    assertThat(firstEndpoint.getReadTimeoutSeconds()).isEqualTo(30L);
                    assertThat(firstEndpoint.getConnectTimeoutSeconds()).isEqualTo(5L);
                    assertThat(firstEndpoint.getLog()).isEqualTo(LogLevel.CONSOLE);

                    assertThat(firstEndpoint.getAuthorization()).isNotNull();
                    assertThat(firstEndpoint.getAuthorization().getType()).isEqualTo(TokenType.BEARER);
                    assertThat(firstEndpoint.getAuthorization().getToken()).isEqualTo("test-token");

                    assertThat(firstEndpoint.getHeaders()).hasSize(1);
                    assertThat(firstEndpoint.getHeaders().get(0).getName()).isEqualTo("X-Custom-Header");
                    assertThat(firstEndpoint.getHeaders().get(0).getValue()).isEqualTo("custom-value");
                });
    }
}