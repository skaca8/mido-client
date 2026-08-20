package io.github.hyunjun.mido.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds {@link io.github.hyunjun.mido.context.ChannelContext} for the duration of the annotated
 * method, so the request/response log lines it triggers carry a {@code channelAction} instead of
 * {@code "unknown"}. The declaring class must carry {@link ChannelName}.
 *
 * <pre>{@code
 * @Service
 * @ChannelName("payment")
 * public class PaymentAdapter {
 *
 *     @ChannelAction                       // -> "payment.getStatus"
 *     public PaymentStatus getStatus(String id) { ... }
 *
 *     @ChannelAction("processPayment")     // -> "payment.processPayment"
 *     public PaymentResult process(PaymentRequest request) { ... }
 * }
 * }</pre>
 *
 * <p>Requires an AOP runtime on the consumer's classpath (for example
 * {@code spring-boot-starter-aop}). Without one the annotation is silently inert — mido-client does
 * not declare aspectjweaver as a dependency.
 *
 * <p><strong>Proxy-based, with the usual limits.</strong> The advice only runs on external calls
 * through the Spring proxy, so it does not apply to a call from another method of the same bean
 * (self-invocation), nor to {@code private} or {@code final} methods, nor to objects that are not
 * Spring beans. In those cases the action is simply not bound and the log line falls back to
 * {@code "unknown"} — the annotation being present is not proof that it took effect. For code paths
 * where that matters, bind explicitly with
 * {@link io.github.hyunjun.mido.context.ChannelContext#callWithChannelAction} or
 * {@code BaseExternalApi.withDefaultChannelAction}.
 *
 * @see ChannelName
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ChannelAction {

    /**
     * Action name appended after the channel. Defaults to the method name when left blank.
     *
     * @return the action name, or {@code ""} to use the method name
     */
    String value() default "";
}
