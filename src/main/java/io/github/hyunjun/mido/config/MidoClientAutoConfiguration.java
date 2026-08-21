package io.github.hyunjun.mido.config;

import io.github.hyunjun.mido.aop.ChannelActionAspect;
import io.github.hyunjun.mido.aop.ChannelActionValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    /**
     * Reports {@code @ChannelAction} usage that cannot take effect. Registered unconditionally — a
     * missing AOP runtime is itself one of the things worth warning about, so this must not be gated
     * on AspectJ being present.
     *
     * @param beanFactory factory whose bean definitions are inspected after startup
     * @return the validator bean
     */
    @Bean
    @ConditionalOnMissingBean
    public ChannelActionValidator midoChannelActionValidator(ListableBeanFactory beanFactory) {
        return new ChannelActionValidator(beanFactory);
    }

    /**
     * Registers {@link ChannelActionAspect} only when an AspectJ runtime is on the classpath (for
     * example via {@code spring-boot-starter-aop}). mido-client declares aspectjweaver as
     * {@code compileOnly}, so a consumer that does not want AOP is not forced to pull it in — the
     * {@code @ChannelAction} annotation is then simply inert and the explicit
     * {@code ChannelContext} / {@code BaseExternalApi} paths keep working unchanged.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.aspectj.weaver.Advice")
    public static class ChannelActionAspectConfiguration {

        /**
         * @return the aspect that binds {@code ChannelContext} around {@code @ChannelAction} methods
         */
        @Bean
        @ConditionalOnMissingBean
        public ChannelActionAspect midoChannelActionAspect() {
            return new ChannelActionAspect();
        }
    }

}