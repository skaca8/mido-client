package io.github.hyunjun.mido.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(MidoClientProperties.class)
@ConditionalOnProperty(name = "mido-client.enabled", havingValue = "true")
public class MidoClientAutoConfiguration {

    @Bean
    public MidoClientFactory midoClientFactory(MidoClientProperties midoClientProperties) {
        log.info("Mido Client Auto Configuration enabled with {} channels", midoClientProperties.size());
        return new MidoClientFactory(midoClientProperties);
    }

}