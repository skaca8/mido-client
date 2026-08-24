package io.github.hyunjun.mido.aop;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.hyunjun.mido.annotation.ChannelAction;
import io.github.hyunjun.mido.annotation.ChannelName;
import io.github.hyunjun.mido.aop.scanfixture.AbstractBaseAdapter;
import io.github.hyunjun.mido.aop.scanfixture.RegisteredAdapter;
import io.github.hyunjun.mido.aop.scanfixture.UnregisteredAdapter;
import io.github.hyunjun.mido.config.MidoClientAutoConfiguration;
import io.github.hyunjun.mido.context.ChannelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelActionValidatorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MidoClientAutoConfiguration.class, AopAutoConfiguration.class))
            .withPropertyValues("mido-client.enabled=true");

    private ListAppender<ILoggingEvent> appender;
    private Logger validatorLogger;

    @BeforeEach
    void setUp() {
        appender = new ListAppender<>();
        appender.start();
        validatorLogger = (Logger) LoggerFactory.getLogger(ChannelActionValidator.class);
        validatorLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        validatorLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void shouldFailStartupWhenChannelNameIsMissing() {
        contextRunner.withUserConfiguration(MissingChannelNameConfig.class)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining(MissingChannelNameBean.class.getName())
                        .hasStackTraceContaining("requires @ChannelName on the class")
                        .hasStackTraceContaining("readWithoutChannelName"));
    }

    @Test
    void shouldFailStartupWhenActionMethodIsPrivate() {
        contextRunner.withUserConfiguration(PrivateActionConfig.class)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("#privateAction can never take effect")
                        .hasStackTraceContaining("private methods are never matched"));
    }

    @Test
    void shouldFailStartupWhenActionMethodIsFinal() {
        contextRunner.withUserConfiguration(FinalActionConfig.class)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("#finalAction can never take effect")
                        .hasStackTraceContaining("cannot be overridden by the CGLIB proxy"));
    }

    @Test
    void shouldFailStartupWhenActionMethodIsStatic() {
        contextRunner.withUserConfiguration(StaticActionConfig.class)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("#staticAction can never take effect")
                        .hasStackTraceContaining("static methods cannot be advised"));
    }

    @Test
    void shouldWarnAboutSelfInvocationWithoutFailingStartup() {
        contextRunner.withUserConfiguration(SelfInvokingConfig.class).run(context -> {
            // 확실하지 않은 판정(수신자가 this라고 증명할 수 없음)이므로 기동을 막지는 않는다
            assertThat(context).hasNotFailed();
            assertThat(warnings())
                    .anySatisfy(message -> assertThat(message)
                            .contains("SelfInvokingBean#annotatedAction")
                            .contains("SelfInvokingBean#callsItsOwnAction")
                            .contains("self-invocation does not go through the Spring proxy"));
        });
    }

    @Test
    void shouldNotWarnWhenCallerIsItselfAnnotated() {
        // 호출하는 쪽도 @ChannelAction이면 컨텍스트가 이미 바인딩되어 있어 우회가 무해하다
        contextRunner.withUserConfiguration(AnnotatedCallerConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(warnings()).noneSatisfy(message ->
                    assertThat(message).contains("self-invocation does not go through"));
        });
    }

    @Test
    void shouldNotWarnAboutACleanlyAnnotatedBean() {
        contextRunner.withUserConfiguration(CleanConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(warnings()).isEmpty();
        });
    }

    @Test
    void shouldWarnWhenAnnotationsAreUsedWithoutAnyAopRuntime() {
        contextRunner
                .withUserConfiguration(CleanConfig.class)
                .withClassLoader(new FilteredClassLoader("org.aspectj"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(warnings())
                            .anySatisfy(message -> assertThat(message)
                                    .contains("no AspectJ runtime")
                                    .contains("spring-boot-starter-aop"));
                });
    }

    @Test
    void shouldWarnAboutAnnotatedClassThatIsNotABean() {
        // 빈 정의만 훑으면 이 클래스는 아예 검사 대상에 들어오지 않는다 — 스캔이 잡아야 한다
        contextRunner.withUserConfiguration(ScannedPackageConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(warnings())
                    .anySatisfy(message -> assertThat(message)
                            .contains(UnregisteredAdapter.class.getName())
                            .contains("is not a Spring bean"));
        });
    }

    @Test
    void shouldNotWarnAboutAnnotatedClassThatIsABean() {
        contextRunner.withUserConfiguration(ScannedPackageConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(warnings()).noneSatisfy(message ->
                    assertThat(message).contains(RegisteredAdapter.class.getName()));
        });
    }

    @Test
    void shouldNotWarnAboutAnAbstractAnnotatedBaseClass() {
        // @ChannelName은 @Inherited라 추상 베이스가 채널을 선언하는 건 정당하다. 빈이 될 수 없으니 오탐이면 안 된다.
        contextRunner.withUserConfiguration(ScannedPackageConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(warnings()).noneSatisfy(message ->
                    assertThat(message).contains(AbstractBaseAdapter.class.getName()));
        });
    }

    @Test
    void shouldNotScanWhenApplicationPackagesAreUnknown() {
        // @AutoConfigurationPackage가 없으면(라이브러리 단독 테스트 등) 스캔할 기준 패키지가 없다 — 조용히 건너뛴다
        contextRunner.withUserConfiguration(CleanConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(warnings()).isEmpty();
        });
    }

    @Test
    void shouldStayQuietWhenNoChannelActionIsUsed() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(warnings()).isEmpty();
        });
    }

    @Test
    void shouldNotValidateWhenLibraryIsDisabled() {
        // mido-client.enabled=false면 자동설정 전체가 건너뛰어지므로 검증기도 없다
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MidoClientAutoConfiguration.class))
                .withUserConfiguration(MissingChannelNameConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ChannelActionValidator.class);
                });
    }

    private List<String> warnings() {
        return appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    /** scanfixture 패키지만 스캔 범위로 등록해 다른 테스트의 픽스처가 섞이지 않게 한다. */
    @Configuration(proxyBeanMethods = false)
    @AutoConfigurationPackage(basePackageClasses = RegisteredAdapter.class)
    static class ScannedPackageConfig {
        @Bean
        RegisteredAdapter registeredAdapter() {
            return new RegisteredAdapter();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MissingChannelNameConfig {
        @Bean
        MissingChannelNameBean missingChannelNameBean() {
            return new MissingChannelNameBean();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PrivateActionConfig {
        @Bean
        PrivateActionBean privateActionBean() {
            return new PrivateActionBean();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FinalActionConfig {
        @Bean
        FinalActionBean finalActionBean() {
            return new FinalActionBean();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class StaticActionConfig {
        @Bean
        StaticActionBean staticActionBean() {
            return new StaticActionBean();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SelfInvokingConfig {
        @Bean
        SelfInvokingBean selfInvokingBean() {
            return new SelfInvokingBean();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AnnotatedCallerConfig {
        @Bean
        AnnotatedCallerBean annotatedCallerBean() {
            return new AnnotatedCallerBean();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CleanConfig {
        @Bean
        CleanBean cleanBean() {
            return new CleanBean();
        }
    }

    public static class MissingChannelNameBean {
        @ChannelAction
        public String readWithoutChannelName() {
            return ChannelContext.getChannelAction();
        }
    }

    @ChannelName("payment")
    public static class PrivateActionBean {
        @ChannelAction
        private String privateAction() {
            return ChannelContext.getChannelAction();
        }

        public String callIt() {
            return privateAction();
        }
    }

    @ChannelName("payment")
    public static class FinalActionBean {
        @ChannelAction
        public final String finalAction() {
            return ChannelContext.getChannelAction();
        }
    }

    @ChannelName("payment")
    public static class StaticActionBean {
        @ChannelAction
        public static String staticAction() {
            return ChannelContext.getChannelAction();
        }
    }

    @ChannelName("payment")
    public static class SelfInvokingBean {
        @ChannelAction
        public String annotatedAction() {
            return ChannelContext.getChannelAction();
        }

        /** 애노테이션 없는 메서드에서 내부 호출 — 프록시를 우회한다. */
        public String callsItsOwnAction() {
            return annotatedAction();
        }
    }

    @ChannelName("payment")
    public static class AnnotatedCallerBean {
        @ChannelAction
        public String innerAction() {
            return ChannelContext.getChannelAction();
        }

        @ChannelAction
        public String outerAction() {
            return innerAction();
        }
    }

    @ChannelName("payment")
    public static class CleanBean {
        @ChannelAction
        public String action() {
            return ChannelContext.getChannelAction();
        }
    }
}
