package io.github.hyunjun.mido.aop.scanfixture;

import io.github.hyunjun.mido.annotation.ChannelName;

/**
 * {@code @ChannelName}이 {@code @Inherited}이므로 추상 베이스가 채널을 선언하는 것은 정당한 패턴이다.
 * 추상 클래스는 빈이 될 수 없으니 경고 대상이 되면 오탐이다.
 */
@ChannelName("payment")
public abstract class AbstractBaseAdapter {

    public abstract String describe();
}
