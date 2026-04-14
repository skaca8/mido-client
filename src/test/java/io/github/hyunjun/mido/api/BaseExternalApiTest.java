package io.github.hyunjun.mido.api;

import io.github.hyunjun.mido.context.ChannelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

class BaseExternalApiTest {

    @AfterEach
    void tearDown() {
        ChannelContext.clear();
    }

    @Test
    void shouldSetChannelActionAndExecuteSupplier() {
        // Given
        TestExternalApi api = new TestExternalApi("payment");

        // When
        String result = api.withDefaultChannelAction("processPayment", () -> "success");

        // Then
        assertThat(result).isEqualTo("success");
        assertThat(ChannelContext.getChannelAction()).isNull(); // Should be cleared after execution
    }

    @Test
    void shouldSetChannelActionAndExecuteRunnable() {
        // Given
        TestExternalApi api = new TestExternalApi("auth");
        AtomicBoolean executed = new AtomicBoolean(false);

        // When
        api.withDefaultChannelAction("validateToken", () -> executed.set(true));

        // Then
        assertThat(executed.get()).isTrue();
        assertThat(ChannelContext.getChannelAction()).isNull(); // Should be cleared after execution
    }

    @Test
    void shouldClearChannelActionEvenWhenExceptionOccurs() {
        // Given
        TestExternalApi api = new TestExternalApi("notification");

        // When & Then
        assertThatThrownBy(() ->
            api.withDefaultChannelAction("sendMessage", () -> {
                throw new RuntimeException("Test exception");
            })
        ).isInstanceOf(RuntimeException.class)
         .hasMessage("Test exception");

        assertThat(ChannelContext.getChannelAction()).isNull(); // Should be cleared even after exception
    }

    @Test
    void shouldFormatChannelActionCorrectly() {
        // Given
        TestExternalApi api = new TestExternalApi("external-service");

        // When
        api.withDefaultChannelAction("getData", () -> {
            // Verify channel action is set correctly during execution
            assertThat(ChannelContext.getChannelAction()).isEqualTo("external-service.getData");
        });
    }

    static class TestExternalApi extends BaseExternalApi {
        private final String channelName;

        TestExternalApi(String channelName) {
            this.channelName = channelName;
        }

        @Override
        protected String getChannelName() {
            return channelName;
        }
    }
}