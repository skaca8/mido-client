package io.github.hyunjun.mido.aop;

import io.github.hyunjun.mido.annotation.ChannelAction;
import io.github.hyunjun.mido.annotation.ChannelName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Internal. Checks {@link ChannelAction} usage once all singletons exist, so that an annotation that
 * can never take effect is reported at startup instead of quietly logging {@code channelAction:
 * unknown} forever.
 *
 * <p>Three kinds of mistake are <strong>fatal</strong>, because the advice provably cannot apply:
 * <ul>
 *   <li>{@link ChannelAction} on a class without {@link ChannelName}</li>
 *   <li>{@link ChannelAction} on a {@code private} or {@code static} method — Spring AOP's
 *       {@code @annotation} pointcut never matches those</li>
 *   <li>{@link ChannelAction} on a {@code final} method — CGLIB cannot override it</li>
 * </ul>
 *
 * <p>Self-invocation is reported as a <strong>warning</strong>, not a failure. It is detected by
 * reading the class bytes and looking for calls to an annotated method from an unannotated method of
 * the same class; the bytecode does not prove the receiver is {@code this} rather than another
 * instance of the same type, so a rare false positive is possible and failing startup on it would be
 * wrong. A call from a method that is itself annotated is not reported — the context is already
 * bound, so bypassing the proxy changes nothing.
 *
 * @see ChannelActionAspect
 */
@Slf4j
@RequiredArgsConstructor
public class ChannelActionValidator implements SmartInitializingSingleton {

    private static final String ASPECTJ_MARKER_CLASS = "org.aspectj.weaver.Advice";

    private final ListableBeanFactory beanFactory;

    @Override
    public void afterSingletonsInstantiated() {
        List<Class<?>> annotatedClasses = findClassesWithChannelActions();
        if (annotatedClasses.isEmpty()) return;

        if (!ClassUtils.isPresent(ASPECTJ_MARKER_CLASS, ClassUtils.getDefaultClassLoader())) {
            log.warn("@ChannelAction is used on {} class(es) but no AspectJ runtime is on the classpath, "
                            + "so the annotations do nothing. Add spring-boot-starter-aop, or bind the context "
                            + "explicitly with ChannelContext / BaseExternalApi.",
                    annotatedClasses.size());
            return;
        }

        annotatedClasses.forEach(this::validate);
    }

    private List<Class<?>> findClassesWithChannelActions() {
        Set<Class<?>> classes = new LinkedHashSet<>();
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            Class<?> beanType = resolveBeanType(beanName);
            if (beanType != null && !findChannelActionMethods(beanType).isEmpty()) {
                classes.add(beanType);
            }
        }
        return new ArrayList<>(classes);
    }

    private Class<?> resolveBeanType(String beanName) {
        try {
            Class<?> beanType = beanFactory.getType(beanName);
            return beanType == null ? null : ClassUtils.getUserClass(beanType);
        } catch (Exception e) {
            // 타입 해석에 실패하는 빈(FactoryBean 지연 해석 등)은 검사 대상에서 조용히 제외한다.
            log.trace("Skipping bean '{}' during @ChannelAction validation: {}", beanName, e.getMessage());
            return null;
        }
    }

    private List<Method> findChannelActionMethods(Class<?> beanType) {
        List<Method> methods = new ArrayList<>();
        for (Method method : beanType.getDeclaredMethods()) {
            if (AnnotatedElementUtils.hasAnnotation(method, ChannelAction.class)) {
                methods.add(method);
            }
        }
        return methods;
    }

    private void validate(Class<?> beanType) {
        List<Method> actionMethods = findChannelActionMethods(beanType);

        if (!AnnotatedElementUtils.hasAnnotation(beanType, ChannelName.class)) {
            throw new IllegalStateException("@ChannelAction on " + beanType.getName()
                    + " requires @ChannelName on the class — annotate it with the mido-client channel it talks to. "
                    + "Offending method(s): " + methodNames(actionMethods));
        }

        actionMethods.forEach(method -> requireProxyable(beanType, method));
        SelfInvocationScanner.scan(beanType, actionMethods)
                .forEach(finding -> log.warn("@ChannelAction on {}#{} is bypassed when called from {}#{} — "
                                + "a self-invocation does not go through the Spring proxy, so channelAction "
                                + "will be 'unknown' for that path. Call it through an injected reference, or bind "
                                + "explicitly with ChannelContext.callWithChannelAction(...).",
                        beanType.getSimpleName(), finding.callee(), beanType.getSimpleName(), finding.caller()));
    }

    private void requireProxyable(Class<?> beanType, Method method) {
        String reason = null;
        if (Modifier.isPrivate(method.getModifiers())) {
            reason = "private methods are never matched by Spring AOP's @annotation pointcut";
        } else if (Modifier.isStatic(method.getModifiers())) {
            reason = "static methods cannot be advised";
        } else if (Modifier.isFinal(method.getModifiers())) {
            reason = "final methods cannot be overridden by the CGLIB proxy";
        }

        if (reason != null) {
            throw new IllegalStateException("@ChannelAction on " + beanType.getName() + "#" + method.getName()
                    + " can never take effect: " + reason
                    + ". Remove the annotation and bind explicitly with ChannelContext.callWithChannelAction(...), "
                    + "or change the method so it can be proxied.");
        }
    }

    private String methodNames(List<Method> methods) {
        Set<String> names = new HashSet<>();
        methods.forEach(method -> names.add(method.getName()));
        return String.valueOf(names);
    }
}
