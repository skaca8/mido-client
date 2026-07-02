package io.github.hyunjun.mido.api;

import io.github.hyunjun.mido.context.ChannelContext;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * Convenience base class for services that talk to a single mido-client channel.
 *
 * <p>Subclasses provide {@link #getChannelName()} once, then wrap each external call in
 * {@link #withDefaultChannelAction(String, Supplier) withDefaultChannelAction(...)} so the
 * {@link ChannelContext} (and SLF4J MDC) is populated for the duration of the call. The action key
 * is formatted as {@code "<channelName>.<action>"} and is automatically unbound on both normal
 * return and exception.
 *
 * <p>Typical usage:
 * <pre>{@code
 * @Service
 * public class PaymentService extends BaseExternalApi {
 *     @Override protected String getChannelName() { return "payment"; }
 *
 *     public PaymentStatus getStatus(String id) {
 *         return withDefaultChannelAction("getStatus", () ->
 *             client.get().uri("/payments/{id}", id).retrieve().body(PaymentStatus.class));
 *     }
 * }
 * }</pre>
 */
@Slf4j
public abstract class BaseExternalApi {

    /**
     * Channel key that identifies the {@code mido-client.channels.<name>} configuration this
     * service talks to. Returned value is used as the prefix of the {@link ChannelContext} action.
     *
     * @return non-null channel name (any casing; matched case-insensitively by the factory)
     */
    protected abstract String getChannelName();

    /**
     * Runs {@code supplier} with the channel action {@code "<channelName>.<action>"} bound via
     * {@link ChannelContext#callWithChannelAction}; the binding is scoped to the call and
     * automatically released on both success and exception paths.
     *
     * @param action   short action name (e.g. {@code "processPayment"})
     * @param supplier the call to make while the context is bound
     * @param <T>      return type of {@code supplier}
     * @return whatever {@code supplier} returns
     */
    protected <T> T withDefaultChannelAction(String action, Supplier<T> supplier) {
        String channelAction = getChannelName() + "." + action;
        return ChannelContext.callWithChannelAction(channelAction, () -> {
            try {
                log.debug("Starting channel action: {}", channelAction);
                return supplier.get();
            } catch (Exception e) {
                log.error("Failed channel action: {}", channelAction, e);
                throw e;
            }
        });
    }

    /**
     * Void overload of {@link #withDefaultChannelAction(String, Supplier)} for callers that don't
     * need a return value.
     *
     * @param action   short action name
     * @param runnable the call to make while the context is bound
     */
    protected void withDefaultChannelAction(String action, Runnable runnable) {
        withDefaultChannelAction(action, () -> {
            runnable.run();
            return null;
        });
    }
}