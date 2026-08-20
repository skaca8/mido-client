package io.github.hyunjun.mido.aop;

import io.github.hyunjun.mido.annotation.ChannelAction;
import io.github.hyunjun.mido.annotation.ChannelName;
import io.github.hyunjun.mido.context.ChannelContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;

/**
 * Internal. Binds {@link ChannelContext} around every {@link ChannelAction} method, using the
 * declaring class's {@link ChannelName} as the key prefix. Registered by
 * {@code MidoClientAutoConfiguration} only when an AspectJ runtime is present.
 *
 * <p>The pointcut matches on {@link ChannelAction} alone rather than on both annotations. Requiring
 * {@link ChannelName} in the pointcut would make a class that forgot it simply not be advised — the
 * silent {@code "unknown"} this feature exists to eliminate. Matching on the method annotation and
 * then demanding {@link ChannelName} inside the advice turns that mistake into a loud failure.
 */
@Aspect
public class ChannelActionAspect implements Ordered {

    /**
     * Runs outside other ordered advice (notably {@code @Transactional}) so that the action stays
     * bound for everything the method triggers, including transaction commit. Left just short of
     * {@link Ordered#HIGHEST_PRECEDENCE} so a consumer can still wrap this aspect if it needs to.
     */
    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    @Around("@annotation(io.github.hyunjun.mido.annotation.ChannelAction)")
    public Object bindChannelAction(ProceedingJoinPoint joinPoint) throws Throwable {
        Class<?> targetClass = AopUtils.getTargetClass(joinPoint.getTarget());
        Method method = AopUtils.getMostSpecificMethod(
                ((MethodSignature) joinPoint.getSignature()).getMethod(), targetClass);

        ChannelAction channelAction = AnnotatedElementUtils.findMergedAnnotation(method, ChannelAction.class);
        if (channelAction == null) {
            // 포인트컷이 매치했는데 애노테이션을 못 읽는 경우는 없어야 한다. 바인딩만 건너뛰고 호출은 그대로 진행한다.
            return joinPoint.proceed();
        }

        return ChannelContext.callWithChannelAction(
                resolveKey(targetClass, method, channelAction), joinPoint::proceed);
    }

    private String resolveKey(Class<?> targetClass, Method method, ChannelAction channelAction) {
        ChannelName channelName = AnnotatedElementUtils.findMergedAnnotation(targetClass, ChannelName.class);
        if (channelName == null) {
            throw new IllegalStateException("@ChannelAction on " + targetClass.getName() + "#" + method.getName()
                    + " requires @ChannelName on " + targetClass.getSimpleName()
                    + " — annotate the class with the mido-client channel it talks to.");
        }

        String action = channelAction.value().isBlank() ? method.getName() : channelAction.value();
        return channelName.value() + "." + action;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
