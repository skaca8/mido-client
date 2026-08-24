package io.github.hyunjun.mido.aop.scanfixture;

import io.github.hyunjun.mido.annotation.ChannelAction;
import io.github.hyunjun.mido.annotation.ChannelName;
import io.github.hyunjun.mido.context.ChannelContext;

/**
 * 애노테이션은 붙었지만 빈으로 등록되지 않는 어댑터. 빈 정의만 훑는 검증에는 아예 잡히지 않으므로
 * 클래스패스 스캔이 경고해야 한다.
 */
@ChannelName("payment")
public class UnregisteredAdapter {

    @ChannelAction
    public String action() {
        return ChannelContext.getChannelAction();
    }
}
