package io.github.hyunjun.mido.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelContextTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldBindChannelActionWithinScope() {
        // Given
        String channelAction = "payment.processPayment";

        // When / Then - bound only inside the scope
        ChannelContext.runWithChannelAction(channelAction, () -> {
            assertThat(ChannelContext.getChannelAction()).isEqualTo(channelAction);
            assertThat(ChannelContext.isBound()).isTrue();
        });
    }

    @Test
    void shouldMirrorChannelActionToMdcWithinScope() {
        // Given
        String channelAction = "auth.validateToken";

        // When / Then
        ChannelContext.runWithChannelAction(channelAction, () ->
                assertThat(MDC.get("channelAction")).isEqualTo(channelAction));
    }

    @Test
    void shouldUnbindAndClearMdcAfterScope() {
        // When
        ChannelContext.runWithChannelAction("test.action", () -> { /* no-op */ });

        // Then - scope exit auto-unbinds the ScopedValue and clears the mirrored MDC value
        assertThat(ChannelContext.getChannelAction()).isNull();
        assertThat(ChannelContext.isBound()).isFalse();
        assertThat(MDC.get("channelAction")).isNull();
    }

    @Test
    void shouldRestoreOuterActionAfterNestedScope() {
        // When / Then - nested calls must not clobber the outer action once the inner one returns
        ChannelContext.runWithChannelAction("outer.a", () -> {
            assertThat(ChannelContext.getChannelAction()).isEqualTo("outer.a");

            ChannelContext.runWithChannelAction("inner.b", () -> {
                assertThat(ChannelContext.getChannelAction()).isEqualTo("inner.b");
                assertThat(MDC.get("channelAction")).isEqualTo("inner.b");
            });

            // outer action (and its MDC mirror) is restored after the inner scope
            assertThat(ChannelContext.getChannelAction()).isEqualTo("outer.a");
            assertThat(MDC.get("channelAction")).isEqualTo("outer.a");
        });

        assertThat(ChannelContext.isBound()).isFalse();
        assertThat(MDC.get("channelAction")).isNull();
    }

    @Test
    void shouldReturnValueFromCallWithChannelAction() {
        // When
        String result = ChannelContext.callWithChannelAction("service.operation", () -> "result");

        // Then
        assertThat(result).isEqualTo("result");
    }

    @Test
    void shouldPropagateCheckedExceptionAndUnbindScope() {
        // When / Then - checked exception passes through unchanged (no wrapping needed)
        assertThatThrownBy(() ->
                ChannelContext.callWithChannelAction("file.read", () -> {
                    throw new IOException("boom");
                })
        ).isInstanceOf(IOException.class).hasMessage("boom");

        // and the scope is unbound / MDC cleared even on the exception path
        assertThat(ChannelContext.isBound()).isFalse();
        assertThat(MDC.get("channelAction")).isNull();
    }

    @Test
    void shouldReturnMdcValueWhenScopedValueNotBound() {
        // Given - a value is present only in MDC (e.g. on an async logging thread)
        String channelAction = "notification.sendMessage";
        MDC.put("channelAction", channelAction);

        // When
        String result = ChannelContext.getChannelAction();

        // Then
        assertThat(result).isEqualTo(channelAction);
        assertThat(ChannelContext.isBound()).isFalse(); // ScopedValue is not bound
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
