package io.github.hyunjun.mido.api;

import io.github.hyunjun.mido.context.ChannelContext;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

@Slf4j
public abstract class BaseExternalApi {

    protected abstract String getChannelName();

    protected <T> T withDefaultChannelAction(String action, Supplier<T> supplier) {
        String channelAction = getChannelName() + "." + action;
        ChannelContext.setChannelAction(channelAction);

        try {
            log.debug("Starting channel action: {}", channelAction);
            return supplier.get();
        } catch (Exception e) {
            log.error("Failed channel action: {}", channelAction, e);
            throw e;
        } finally {
            ChannelContext.clear();
        }
    }

    protected void withDefaultChannelAction(String action, Runnable runnable) {
        withDefaultChannelAction(action, () -> {
            runnable.run();
            return null;
        });
    }
}