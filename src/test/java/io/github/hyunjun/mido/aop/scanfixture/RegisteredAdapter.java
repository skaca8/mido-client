package io.github.hyunjun.mido.aop.scanfixture;

import io.github.hyunjun.mido.annotation.ChannelAction;
import io.github.hyunjun.mido.annotation.ChannelName;
import io.github.hyunjun.mido.context.ChannelContext;

/** 정상적으로 빈으로 등록되는 어댑터. 경고 대상이 아니어야 한다. */
@ChannelName("payment")
public class RegisteredAdapter {

    @ChannelAction
    public String action() {
        return ChannelContext.getChannelAction();
    }
}
