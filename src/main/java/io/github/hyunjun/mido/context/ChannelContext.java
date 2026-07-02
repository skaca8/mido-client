package io.github.hyunjun.mido.context;

import lombok.experimental.UtilityClass;
import org.slf4j.MDC;

/**
 * Scoped holder for the current channel action, backed by a {@link ScopedValue}.
 *
 * <p>The action is bound only for the dynamic extent of
 * {@link #callWithChannelAction(String, ScopedValue.CallableOp) callWithChannelAction} /
 * {@link #runWithChannelAction(String, Runnable) runWithChannelAction}: it is visible to everything
 * the supplied operation calls (including the mido-client logging interceptor, which runs the HTTP
 * request inside that operation) and is automatically unbound when the operation returns or throws.
 * There is no manual {@code set}/{@code clear} — the binding cannot leak past its scope, so pooled
 * threads never carry a stale value.
 *
 * <p>The active action is also mirrored into SLF4J's {@link MDC} under the {@code "channelAction"}
 * key so it can be referenced from log patterns (e.g. {@code %X{channelAction}}). The MDC value is
 * saved and restored around each scope, so nested calls (one channel action invoked inside another)
 * correctly expose the outer action again once the inner one returns.
 *
 * <p>{@link #getChannelAction()} falls back to reading MDC when the {@link ScopedValue} is not bound,
 * which is useful when a log line is emitted from a thread other than the one that bound the action
 * (e.g. an asynchronous appender).
 */
@UtilityClass
public class ChannelContext {

    private static final ScopedValue<String> CHANNEL_ACTION = ScopedValue.newInstance();

    private static final String MDC_CHANNEL_ACTION_KEY = "channelAction";

    /**
     * Runs {@code op} with the channel action bound for the duration of the call, mirroring it
     * into MDC and restoring the previous MDC value afterwards. The binding is cleared automatically
     * when the call returns or throws. Checked exceptions thrown by {@code op} propagate unchanged
     * ({@code op} is a {@link ScopedValue.CallableOp}, so no wrapping is required).
     *
     * @param channelAction action identifier, conventionally {@code "<channelName>.<method>"}
     * @param op            operation to run while the action is bound
     * @param <T>           return type of {@code op}
     * @param <X>           exception type thrown by {@code op}, if any
     * @return whatever {@code op} returns
     * @throws X whatever {@code op} throws
     */
    public static <T, X extends Throwable> T callWithChannelAction(String channelAction, ScopedValue.CallableOp<T, X> op) throws X {
        return ScopedValue.where(CHANNEL_ACTION, channelAction).call(() -> {
            String previous = MDC.get(MDC_CHANNEL_ACTION_KEY);
            MDC.put(MDC_CHANNEL_ACTION_KEY, channelAction);
            try {
                return op.call();
            } finally {
                // 중첩 호출을 위해 이전 MDC 값을 복원한다(없었으면 제거). ScopedValue는 스코프 종료 시 자동 해제.
                if (previous != null) {
                    MDC.put(MDC_CHANNEL_ACTION_KEY, previous);
                } else {
                    MDC.remove(MDC_CHANNEL_ACTION_KEY);
                }
            }
        });
    }

    /**
     * Void counterpart of {@link #callWithChannelAction(String, ScopedValue.CallableOp)} for
     * operations that don't return a value.
     *
     * @param channelAction action identifier, conventionally {@code "<channelName>.<method>"}
     * @param runnable       operation to run while the action is bound
     */
    public static void runWithChannelAction(String channelAction, Runnable runnable) {
        callWithChannelAction(channelAction, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * Reads the currently bound action.
     *
     * @return the action bound in the current dynamic scope; falls back to the MDC value if the
     *         {@link ScopedValue} is not bound (e.g. on a logging thread)
     */
    public static String getChannelAction() {
        return CHANNEL_ACTION.isBound() ? CHANNEL_ACTION.get() : MDC.get(MDC_CHANNEL_ACTION_KEY);
    }

    /**
     * Checks whether the current thread is executing inside a
     * {@link #callWithChannelAction(String, ScopedValue.CallableOp)} /
     * {@link #runWithChannelAction(String, Runnable)} scope.
     *
     * @return {@code true} if the {@link ScopedValue} is currently bound; this does not consider
     *         MDC-only values
     */
    public static boolean isBound() {
        return CHANNEL_ACTION.isBound();
    }
}
