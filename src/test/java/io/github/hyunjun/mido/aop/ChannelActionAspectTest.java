package io.github.hyunjun.mido.aop;

import io.github.hyunjun.mido.annotation.ChannelAction;
import io.github.hyunjun.mido.annotation.ChannelName;
import io.github.hyunjun.mido.config.MidoClientAutoConfiguration;
import io.github.hyunjun.mido.context.ChannelContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChannelActionAspectTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MidoClientAutoConfiguration.class, AopAutoConfiguration.class))
            .withPropertyValues("mido-client.enabled=true");

    @Test
    void shouldBindChannelActionUsingMethodNameByDefault() {
        contextRunner.withUserConfiguration(AnnotatedBeanConfig.class).run(context -> {
            AnnotatedBean bean = context.getBean(AnnotatedBean.class);

            assertThat(bean.readWithDefaultName()).isEqualTo("payment.readWithDefaultName");
        });
    }

    @Test
    void shouldBindExplicitActionNameWhenProvided() {
        contextRunner.withUserConfiguration(AnnotatedBeanConfig.class).run(context -> {
            AnnotatedBean bean = context.getBean(AnnotatedBean.class);

            assertThat(bean.readWithExplicitName()).isEqualTo("payment.explicitAction");
        });
    }

    @Test
    void shouldUnbindAfterMethodReturns() {
        contextRunner.withUserConfiguration(AnnotatedBeanConfig.class).run(context -> {
            AnnotatedBean bean = context.getBean(AnnotatedBean.class);
            bean.readWithDefaultName();

            // ScopedValue는 스코프를 벗어나면 자동 해제된다
            assertThat(ChannelContext.isBound()).isFalse();
        });
    }

    @Test
    void shouldUnbindWhenMethodThrows() {
        contextRunner.withUserConfiguration(AnnotatedBeanConfig.class).run(context -> {
            AnnotatedBean bean = context.getBean(AnnotatedBean.class);

            assertThatThrownBy(bean::failWithCheckedException)
                    .isInstanceOf(IOException.class)
                    .hasMessage("boom");
            assertThat(ChannelContext.isBound()).isFalse();
        });
    }

    @Test
    void shouldPropagateCheckedExceptionUnchanged() {
        // CallableOp 덕분에 검사 예외가 래핑 없이 그대로 나온다 — 이게 깨지면 소비 측 catch가 전부 무력화된다
        contextRunner.withUserConfiguration(AnnotatedBeanConfig.class).run(context -> {
            AnnotatedBean bean = context.getBean(AnnotatedBean.class);

            assertThatThrownBy(bean::failWithCheckedException).isExactlyInstanceOf(IOException.class);
        });
    }

    @Test
    void shouldRestoreOuterActionAfterNestedCall() {
        contextRunner.withUserConfiguration(AnnotatedBeanConfig.class).run(context -> {
            AnnotatedBean bean = context.getBean(AnnotatedBean.class);

            // 바깥 액션 안에서 애스펙트가 감싼 메서드를 또 호출해도 바깥 액션이 복원되어야 한다
            String observed = ChannelContext.callWithChannelAction("outer.action", () -> {
                bean.readWithDefaultName();
                return ChannelContext.getChannelAction();
            });

            assertThat(observed).isEqualTo("outer.action");
        });
    }

    @Test
    void shouldFailLoudlyWhenChannelNameIsMissing() throws Exception {
        // ChannelActionValidator가 기동 시점에 잡으므로, 어드바이스의 런타임 가드는 컨텍스트 없이 직접 검증한다.
        // 가드를 두 겹으로 두는 이유: 검증기는 빈 정의만 볼 수 있어서 프로그래매틱하게 프록시된 비빈 객체는 못 본다.
        MissingChannelNameBean target = new MissingChannelNameBean();
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(MissingChannelNameBean.class.getMethod("readWithoutChannelName"));

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getSignature()).thenReturn(signature);

        assertThatThrownBy(() -> new ChannelActionAspect().bindChannelAction(joinPoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(MissingChannelNameBean.class.getName() + "#readWithoutChannelName")
                .hasMessageContaining("requires @ChannelName");
    }

    @Test
    void shouldNotBindOnSelfInvocation() {
        // 프록시 기반의 알려진 한계를 명시적으로 잠근다 — 문서화된 동작이 조용히 바뀌면 여기서 깨진다
        contextRunner.withUserConfiguration(AnnotatedBeanConfig.class).run(context -> {
            AnnotatedBean bean = context.getBean(AnnotatedBean.class);

            assertThat(bean.callAnnotatedMethodInternally()).isNull();
        });
    }

    @Test
    void shouldRegisterAspectOutsideOtherOrderedAdvice() {
        contextRunner.run(context -> assertThat(context.getBean(ChannelActionAspect.class).getOrder())
                .isEqualTo(Ordered.HIGHEST_PRECEDENCE + 100));
    }

    @Test
    void shouldNotRegisterAspectWhenAspectJIsAbsent() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("org.aspectj.weaver"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ChannelActionAspect.class);
                });
    }

    @Test
    void shouldBackOffWhenConsumerDefinesOwnAspect() {
        contextRunner.withUserConfiguration(CustomAspectConfig.class).run(context -> {
            assertThat(context).hasSingleBean(ChannelActionAspect.class);
            assertThat(context.getBean(ChannelActionAspect.class)).isSameAs(CustomAspectConfig.INSTANCE);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class AnnotatedBeanConfig {
        @Bean
        AnnotatedBean annotatedBean() {
            return new AnnotatedBean();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomAspectConfig {
        static final ChannelActionAspect INSTANCE = new ChannelActionAspect();

        @Bean
        ChannelActionAspect midoChannelActionAspect() {
            return INSTANCE;
        }
    }

    @ChannelName("payment")
    public static class AnnotatedBean {

        @ChannelAction
        public String readWithDefaultName() {
            return ChannelContext.getChannelAction();
        }

        @ChannelAction("explicitAction")
        public String readWithExplicitName() {
            return ChannelContext.getChannelAction();
        }

        @ChannelAction
        public String failWithCheckedException() throws IOException {
            throw new IOException("boom");
        }

        /** 프록시를 통하지 않는 내부 호출 — 애스펙트가 걸리지 않는다. */
        public String callAnnotatedMethodInternally() {
            return readWithDefaultName();
        }
    }

    /** {@code @ChannelName} 없이 {@code @ChannelAction}만 붙은, 잘못 설정된 클래스. */
    public static class MissingChannelNameBean {

        @ChannelAction
        public String readWithoutChannelName() {
            return ChannelContext.getChannelAction();
        }
    }
}
