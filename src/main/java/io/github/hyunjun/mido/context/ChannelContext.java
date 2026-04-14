package io.github.hyunjun.mido.context;

import org.slf4j.MDC;

public class ChannelContext {
    public static final ThreadLocal<String> CHANNEL_ACTION = new ThreadLocal<>();

    private static final String MDC_CHANNEL_ACTION_KEY = "channelAction";

    public static void setChannelAction(String channelAction) {
        CHANNEL_ACTION.set(channelAction);
        MDC.put(MDC_CHANNEL_ACTION_KEY, channelAction);
    }

    public static String getChannelAction() {
        String channelAction = CHANNEL_ACTION.get();
        if (channelAction == null) {
            channelAction = MDC.get(MDC_CHANNEL_ACTION_KEY);
        }
        return channelAction;
    }

    public static void clear() {
        CHANNEL_ACTION.remove();
        MDC.remove(MDC_CHANNEL_ACTION_KEY);
    }

    public static boolean isBound() {
        return CHANNEL_ACTION.get() != null;
    }
}