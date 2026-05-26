package io.github.hyunjun.mido.context;

import lombok.experimental.UtilityClass;
import org.slf4j.MDC;

/**
 * Thread-local holder for the current channel action.
 *
 * <p>The active action is also mirrored into SLF4J's {@link MDC} under the {@code "channelAction"}
 * key so it can be referenced from log patterns (e.g. {@code %X{channelAction}}). State lives in a
 * {@link ThreadLocal}, so each request thread sees its own value — but the caller is responsible
 * for clearing the context (typically via {@link #clear()} in a {@code finally} block, which
 * {@link io.github.hyunjun.mido.api.BaseExternalApi#withDefaultChannelAction BaseExternalApi}
 * already does).
 *
 * <p>{@link #getChannelAction()} also falls back to reading MDC, which is useful when log lines
 * are emitted from threads other than the one that bound the action (e.g. async logging).
 */
@UtilityClass
public class ChannelContext {
    private static final ThreadLocal<String> CHANNEL_ACTION = new ThreadLocal<>();

    private static final String MDC_CHANNEL_ACTION_KEY = "channelAction";

    /**
     * Binds the action to the current thread and mirrors it into MDC.
     *
     * @param channelAction action identifier, conventionally {@code "<channelName>.<method>"}
     */
    public static void setChannelAction(String channelAction) {
        CHANNEL_ACTION.set(channelAction);
        MDC.put(MDC_CHANNEL_ACTION_KEY, channelAction);
    }

    /**
     * Reads the currently bound action.
     *
     * @return the action bound to the current thread; falls back to the MDC value if the
     *         {@link ThreadLocal} has not been bound (e.g. on a logging thread)
     */
    public static String getChannelAction() {
        String channelAction = CHANNEL_ACTION.get();
        if (channelAction == null) {
            channelAction = MDC.get(MDC_CHANNEL_ACTION_KEY);
        }
        return channelAction;
    }

    /**
     * Removes the action from both the {@link ThreadLocal} and MDC. Always call in a
     * {@code finally} block to avoid leaking state across requests on pooled threads.
     */
    public static void clear() {
        CHANNEL_ACTION.remove();
        MDC.remove(MDC_CHANNEL_ACTION_KEY);
    }

    /**
     * Checks whether the current thread has explicitly set an action via
     * {@link #setChannelAction(String)}.
     *
     * @return {@code true} if a value is currently bound on this thread's {@link ThreadLocal};
     *         this does not consider MDC-only values
     */
    public static boolean isBound() {
        return CHANNEL_ACTION.get() != null;
    }
}