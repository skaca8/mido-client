package io.github.hyunjun.mido.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares which mido-client channel a class talks to, so that {@link ChannelAction} methods on it
 * can build the {@code "<channel>.<action>"} key without repeating the channel name.
 *
 * <p>Deliberately a <strong>type-level</strong> annotation with no method-level counterpart: the
 * channel identifies the external system, which is a property of the class, while the action
 * identifies the call, which is a property of the method. Keeping them on different elements
 * preserves the "one class = one channel" invariant. A class that genuinely talks to two different
 * channels should be split in two rather than overriding the channel per method.
 *
 * <p>The value is the YAML key under {@code mido-client.channels.*}. It is used only to label the
 * {@link io.github.hyunjun.mido.context.ChannelContext} action, so it is not validated against the
 * configured channels.
 *
 * @see ChannelAction
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ChannelName {

    /**
     * Channel key, matching a {@code mido-client.channels.<name>} entry.
     *
     * @return the channel name
     */
    String value();
}
