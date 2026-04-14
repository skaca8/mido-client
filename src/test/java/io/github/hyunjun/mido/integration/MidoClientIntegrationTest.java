package io.github.hyunjun.mido.integration;

import io.github.hyunjun.mido.config.MidoClientAutoConfiguration;
import io.github.hyunjun.mido.config.MidoClientFactory;
import io.github.hyunjun.mido.constant.EndpointType;
import io.github.hyunjun.mido.sample.AuthService;
import io.github.hyunjun.mido.sample.NotificationService;
import io.github.hyunjun.mido.sample.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mido Client 통합 테스트
 * 실제 Spring 컨텍스트에서 모든 컴포넌트가 올바르게 동작하는지 확인
 * 설정은 src/test/resources/application.yml에서 자동 로드됨
 */
@SpringBootTest(classes = {
        MidoClientAutoConfiguration.class,
        PaymentService.class,
        AuthService.class,
        NotificationService.class
})
class MidoClientIntegrationTest {

    @Autowired
    private MidoClientFactory midoClientFactory;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AuthService authService;

    @Autowired
    private NotificationService notificationService;

    @Test
    void shouldCreateRestClientFactory() {
        assertThat(midoClientFactory).isNotNull();
    }

    @Test
    void shouldCreatePaymentClients() {
        // First endpoint (query)
        RestClient paymentQueryClient = midoClientFactory.getOrCreateClient("payment");
        assertThat(paymentQueryClient).isNotNull();

        // Second endpoint (process)
        RestClient paymentProcessClient = midoClientFactory.getOrCreateClient("payment", EndpointType.SECOND);
        assertThat(paymentProcessClient).isNotNull();

        // Should be different instances
        assertThat(paymentQueryClient).isNotSameAs(paymentProcessClient);
    }

    @Test
    void shouldCreateAuthClient() {
        RestClient authClient = midoClientFactory.getOrCreateClient("auth");
        assertThat(authClient).isNotNull();
    }

    @Test
    void shouldCreateNotificationClient() {
        RestClient notificationClient = midoClientFactory.getOrCreateClient("notification");
        assertThat(notificationClient).isNotNull();
    }

    @Test
    void shouldCacheClients() {
        // Same clients should be cached
        RestClient client1 = midoClientFactory.getOrCreateClient("payment");
        RestClient client2 = midoClientFactory.getOrCreateClient("payment");

        assertThat(client1).isSameAs(client2);
    }

    @Test
    void shouldInjectServicesCorrectly() {
        assertThat(paymentService).isNotNull();
        assertThat(authService).isNotNull();
        assertThat(notificationService).isNotNull();
    }

    // Note: getChannelName() is protected, so we test it indirectly through the service behavior
}