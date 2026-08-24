package io.github.hyunjun.mido.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class InterceptorBeanResolutionTest {

    private static final List<String> CALLS = new ArrayList<>();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MidoClientAutoConfiguration.class))
            .withPropertyValues(
                    "mido-client.enabled=true",
                    "mido-client.channels.test.primary.url=https://api.test.com",
                    "mido-client.channels.test.primary.log=off");

    @BeforeEach
    void setUp() {
        CALLS.clear();
        StatelessFqcnInterceptor.instantiations = 0;
        DependentInterceptor.instantiated = false;
    }

    @Test
    void shouldResolveInterceptorByBeanName() {
        contextRunner
                .withUserConfiguration(HeaderInterceptorConfig.class)
                .withPropertyValues("mido-client.channels.test.primary.interceptors[0]=headerInterceptor")
                .run(context -> {
                    Mocked mocked = mock(context.getBean(MidoClientFactory.class).getOrCreateClient("test"));

                    mocked.server().expect(requestTo("https://api.test.com/ping"))
                            .andExpect(header("X-From-Bean", "yes"))
                            .andRespond(withSuccess());

                    mocked.client().get().uri("/ping").retrieve().toBodilessEntity();

                    mocked.server().verify();
                });
    }

    @Test
    void shouldStillInstantiateFqcnEntriesReflectively() {
        contextRunner
                .withPropertyValues("mido-client.channels.test.primary.interceptors[0]="
                        + StatelessFqcnInterceptor.class.getName())
                .run(context -> {
                    context.getBean(MidoClientFactory.class).getOrCreateClient("test");

                    // 빈이 아닌 클래스명은 종전처럼 즉시 리플렉션 생성된다
                    assertThat(StatelessFqcnInterceptor.instantiations).isEqualTo(1);
                });
    }

    @Test
    void shouldResolveFqcnToTheSoleBeanOfThatType() {
        contextRunner
                .withUserConfiguration(HeaderInterceptorConfig.class)
                .withPropertyValues("mido-client.channels.test.primary.interceptors[0]="
                        + HeaderInterceptor.class.getName())
                .run(context -> {
                    Mocked mocked = mock(context.getBean(MidoClientFactory.class).getOrCreateClient("test"));

                    mocked.server().expect(requestTo("https://api.test.com/ping"))
                            .andExpect(header("X-From-Bean", "yes"))
                            .andRespond(withSuccess());

                    mocked.client().get().uri("/ping").retrieve().toBodilessEntity();

                    mocked.server().verify();
                });
    }

    @Test
    void shouldRunInterceptorsInYamlOrder() {
        contextRunner
                .withUserConfiguration(OrderedInterceptorConfig.class)
                .withPropertyValues(
                        "mido-client.channels.test.primary.interceptors[0]=firstInterceptor",
                        "mido-client.channels.test.primary.interceptors[1]=secondInterceptor")
                .run(context -> {
                    Mocked mocked = mock(context.getBean(MidoClientFactory.class).getOrCreateClient("test"));
                    mocked.server().expect(requestTo("https://api.test.com/ping")).andRespond(withSuccess());

                    mocked.client().get().uri("/ping").retrieve().toBodilessEntity();

                    assertThat(CALLS).containsExactly("first", "second");
                });
    }

    @Test
    void shouldFailStartupWhenBeanIsNotAnInterceptor() {
        contextRunner
                .withUserConfiguration(NotAnInterceptorConfig.class)
                .withPropertyValues("mido-client.channels.test.primary.interceptors[0]=notAnInterceptor")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("channel: test, type: primary")
                        .hasStackTraceContaining("does not implement ClientHttpRequestInterceptor"));
    }

    @Test
    void shouldFailStartupWhenReferenceIsNeitherBeanNorClass() {
        contextRunner
                .withPropertyValues("mido-client.channels.test.primary.interceptors[0]=noSuchThingAnywhere")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("channel: test, type: primary")
                        .hasStackTraceContaining("noSuchThingAnywhere"));
    }

    /**
     * 이 기능의 설계 제약을 증명하는 회귀 테스트. 소비 측은 생성자에서 {@code getOrCreateClient}를 호출하고,
     * 그 채널에는 의존성을 가진 인터셉터 빈이 지정되어 있다. 해석이 지연되지 않으면 인터셉터 빈이 어댑터
     * 생성 중에 강제로 만들어지고, 그 인터셉터가 어댑터를 다시 의존하므로 순환으로 기동이 깨진다.
     */
    @Test
    void shouldNotForceInterceptorBeanWhileConsumerBeanIsBeingConstructed() {
        contextRunner
                .withUserConfiguration(EarlyInitializationConfig.class)
                .withPropertyValues("mido-client.channels.test.primary.interceptors[0]=dependentInterceptor")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    // 어댑터 생성자가 돌던 시점에 인터셉터 빈은 아직 만들어지지 않았어야 한다
                    ConstructorCallingAdapter adapter = context.getBean(ConstructorCallingAdapter.class);
                    assertThat(adapter.interceptorExistedDuringConstruction).isFalse();
                });
    }

    @Test
    void shouldResolveTheBeanOnFirstRequestOnly() {
        contextRunner
                .withUserConfiguration(HeaderInterceptorConfig.class)
                .withPropertyValues("mido-client.channels.test.primary.interceptors[0]=headerInterceptor")
                .run(context -> {
                    Mocked mocked = mock(context.getBean(MidoClientFactory.class).getOrCreateClient("test"));
                    mocked.server().expect(requestTo("https://api.test.com/ping")).andRespond(withSuccess());

                    // 클라이언트를 만들고 mock까지 끼운 뒤에도 아직 빈이 해석되지 않았다
                    assertThat(CALLS).isEmpty();

                    mocked.client().get().uri("/ping").retrieve().toBodilessEntity();

                    assertThat(CALLS).containsExactly("header");
                });
    }

    /** mock 전송을 끼운 클라이언트. bindTo는 빌더의 request factory를 바꾸므로 반드시 그 빌더로 build해야 한다. */
    private record Mocked(RestClient client, MockRestServiceServer server) {
    }

    private Mocked mock(RestClient source) {
        RestClient.Builder builder = source.mutate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Mocked(builder.build(), server);
    }

    @Configuration(proxyBeanMethods = false)
    static class HeaderInterceptorConfig {
        @Bean
        HeaderInterceptor headerInterceptor() {
            return new HeaderInterceptor();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OrderedInterceptorConfig {
        @Bean
        ClientHttpRequestInterceptor firstInterceptor() {
            return recording("first");
        }

        @Bean
        ClientHttpRequestInterceptor secondInterceptor() {
            return recording("second");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class NotAnInterceptorConfig {
        @Bean
        String notAnInterceptor() {
            return "definitely not an interceptor";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class EarlyInitializationConfig {
        @Bean
        ConstructorCallingAdapter constructorCallingAdapter(MidoClientFactory factory) {
            return new ConstructorCallingAdapter(factory);
        }

        @Bean
        DependentInterceptor dependentInterceptor(ConstructorCallingAdapter adapter) {
            return new DependentInterceptor(adapter);
        }
    }

    private static ClientHttpRequestInterceptor recording(String name) {
        return (request, body, execution) -> {
            CALLS.add(name);
            return execution.execute(request, body);
        };
    }

    static class HeaderInterceptor implements ClientHttpRequestInterceptor {
        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            CALLS.add("header");
            request.getHeaders().add("X-From-Bean", "yes");
            return execution.execute(request, body);
        }
    }

    /** 빈이 아닌, 무인자 생성자를 가진 클래스 — 종전 리플렉션 경로. */
    public static class StatelessFqcnInterceptor implements ClientHttpRequestInterceptor {

        static int instantiations = 0;

        public StatelessFqcnInterceptor() {
            instantiations++;
        }

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            return execution.execute(request, body);
        }
    }

    /** 의존성을 가진 인터셉터 — 무인자 생성자가 없으므로 빈으로만 해석될 수 있다. */
    static class DependentInterceptor implements ClientHttpRequestInterceptor {

        static boolean instantiated = false;

        @SuppressWarnings("unused")
        private final ConstructorCallingAdapter adapter;

        DependentInterceptor(ConstructorCallingAdapter adapter) {
            this.adapter = adapter;
            instantiated = true;
        }

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            return execution.execute(request, body);
        }
    }

    /** 실제 소비 패턴: 생성자에서 getOrCreateClient를 호출한다. */
    @Component
    static class ConstructorCallingAdapter {

        final boolean interceptorExistedDuringConstruction;

        @SuppressWarnings("unused")
        private final RestClient client;

        ConstructorCallingAdapter(MidoClientFactory factory) {
            this.client = factory.getOrCreateClient("test");
            this.interceptorExistedDuringConstruction = DependentInterceptor.instantiated;
        }
    }
}
