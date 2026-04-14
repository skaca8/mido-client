package io.github.hyunjun.mido.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelContextTest {

    @AfterEach
    void tearDown() {
        ChannelContext.clear();
        MDC.clear();
    }

    @Test
    void shouldSetAndGetChannelAction() {
        // Given
        String channelAction = "payment.processPayment";

        // When
        ChannelContext.setChannelAction(channelAction);

        // Then
        assertThat(ChannelContext.getChannelAction()).isEqualTo(channelAction);
        assertThat(ChannelContext.isBound()).isTrue();
    }

    @Test
    void shouldSetMDCWhenSettingChannelAction() {
        // Given
        String channelAction = "auth.validateToken";

        // When
        ChannelContext.setChannelAction(channelAction);

        // Then
        assertThat(MDC.get("channelAction")).isEqualTo(channelAction);
    }

    @Test
    void shouldClearChannelAction() {
        // Given
        ChannelContext.setChannelAction("test.action");

        // When
        ChannelContext.clear();

        // Then
        assertThat(ChannelContext.getChannelAction()).isNull();
        assertThat(ChannelContext.isBound()).isFalse();
        assertThat(MDC.get("channelAction")).isNull();
    }

    @Test
    void shouldReturnMDCValueWhenThreadLocalIsNull() {
        // Given
        String channelAction = "notification.sendMessage";
        MDC.put("channelAction", channelAction);

        // When
        String result = ChannelContext.getChannelAction();

        // Then
        assertThat(result).isEqualTo(channelAction);
        assertThat(ChannelContext.isBound()).isFalse(); // ThreadLocal is not bound
    }

    @Test
    void shouldReturnNullWhenNotSet() {
        // When
        String result = ChannelContext.getChannelAction();

        // Then
        assertThat(result).isNull();
        assertThat(ChannelContext.isBound()).isFalse();
    }
}