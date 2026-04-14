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
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mido Client 통합 테스트
 * 실제 Spring 컨텍스트에서 모든 컴포넌트가 올바르게 동작하는지 확인
 */
@SpringBootTest(classes = {
        MidoClientAutoConfiguration.class,
        PaymentService.class,
        AuthService.class,
        NotificationService.class
})
@TestPropertySource(properties = {
        "mido-client.enabled=true",

        // Payment 채널 설정 (dual endpoint)
        "mido-client.channels.payment.title=Payment Service",
        "mido-client.channels.payment.charset=UTF-8",
        "mido-client.channels.payment.first.url=https://api.payment.com",
        "mido-client.channels.payment.first.read-timeout-seconds=30",
        "mido-client.channels.payment.first.connect-timeout-seconds=5",
        "mido-client.channels.payment.first.log=console",
        "mido-client.channels.payment.first.authorization.type=bearer",
        "mido-client.channels.payment.first.authorization.token=payment-query-token",
        "mido-client.channels.payment.second.url=https://process.payment.com",
        "mido-client.channels.payment.second.read-timeout-seconds=60",
        "mido-client.channels.payment.second.connect-timeout-seconds=3",
        "mido-client.channels.payment.second.log=all",
        "mido-client.channels.payment.second.authorization.type=bearer",
        "mido-client.channels.payment.second.authorization.token=payment-process-token",

        // Auth 채널 설정 (single endpoint)
        "mido-client.channels.auth.title=Authentication Service",
        "mido-client.channels.auth.first.url=https://auth.example.com",
        "mido-client.channels.auth.first.authorization.type=bearer",
        "mido-client.channels.auth.first.authorization.token=auth-service-token",
        "mido-client.channels.auth.first.headers[0].name=X-API-Version",
        "mido-client.channels.auth.first.headers[0].value=v1",

        // Notification 채널 설정 (webhook)
        "mido-client.channels.notification.title=Notification Service",
        "mido-client.channels.notification.first.url=https://hooks.slack.com/services/TEST/WEBHOOK/URL",
        "mido-client.channels.notification.first.read-timeout-seconds=10",
        "mido-client.channels.notification.first.connect-timeout-seconds=2",
        "mido-client.channels.notification.first.log=off"
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