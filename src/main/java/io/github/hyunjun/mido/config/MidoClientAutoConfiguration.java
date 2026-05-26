package io.github.hyunjun.mido.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration entry point for {@code mido-client}.
 *
 * <p>Activated only when {@code mido-client.enabled=true} is present in the environment. When active,
 * binds {@link MidoClientProperties} from the {@code mido-client.*} prefix and exposes a single
 * {@link MidoClientFactory} bean.
 *
 * <p>Wired into Spring Boot via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(MidoClientProperties.class)
@ConditionalOnProperty(name = "mido-client.enabled", havingValue = "true")
public class MidoClientAutoConfiguration {

    /**
     * Builds the singleton {@link MidoClientFactory} used to create and cache {@code RestClient}
     * instances per channel/endpoint.
     *
     * @param midoClientProperties bound configuration tree
     * @return the factory bean
     */
    @Bean
    public MidoClientFactory midoClientFactory(MidoClientProperties midoClientProperties) {
        log.info("Mido Client Auto Configuration enabled with {} channels", midoClientProperties.getChannels().size());
        return new MidoClientFactory(midoClientProperties);
    }

}