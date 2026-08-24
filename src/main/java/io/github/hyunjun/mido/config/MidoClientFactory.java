package io.github.hyunjun.mido.config;

import io.github.hyunjun.mido.constant.ContentType;
import io.github.hyunjun.mido.constant.EndpointType;
import io.github.hyunjun.mido.constant.LogLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.util.ClassUtils;
import org.springframework.web.client.RestClient;

import java.lang.ref.WeakReference;
import java.net.http.HttpClient;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * Builds and caches {@link RestClient} instances per channel + endpoint configured under
 * {@code mido-client.channels.*}.
 *
 * <p>Each unique {@code (channelName, endpointType)} pair maps to one {@link RestClient}; subsequent
 * lookups return the same cached instance. Channel names are matched case-insensitively (normalized
 * to lowercase via {@link Locale#ROOT}). The cache is backed by a {@link ConcurrentHashMap}, so
 * {@link #getOrCreateClient(String) getOrCreateClient} is safe to call from multiple threads — the
 * underlying {@link RestClient} is itself thread-safe.
 *
 * <p>Each built client is wired with:
 * <ul>
 *   <li>read/connect timeouts and base URL from {@link MidoClientProperties.EndpointConfig}</li>
 *   <li>{@code Authorization} and static headers</li>
 *   <li>a logging interceptor (level controlled by {@code log:} in YAML)</li>
 *   <li>optional gzip request/response interceptors</li>
 *   <li>any user-supplied custom interceptors named in {@code interceptors:}</li>
 * </ul>
 *
 * <p>Interceptor instantiation failure is fail-fast: a class that cannot be loaded, lacks a public
 * no-arg constructor, or does not implement {@link ClientHttpRequestInterceptor} causes the first
 * {@code getOrCreateClient} call for that channel to throw {@link IllegalStateException} naming
 * both the channel and the offending class.
 */
@Slf4j
@RequiredArgsConstructor
public class MidoClientFactory implements InitializingBean, DisposableBean, BeanFactoryAware {

    private static final String MIDO_FILE_LOGGER_NAME = "MidoClientFileLog";

    private static final String LOGBACK_LOGGER_CLASS = "ch.qos.logback.classic.Logger";

    private final MidoClientProperties midoClientProperties;

    /**
     * Set by the container, so it stays {@code null} when this factory is constructed directly in a
     * test or a manual wiring. In that case {@code interceptors:} entries are treated as class names
     * only — the pre-3.3.0 behavior — rather than failing.
     */
    private ListableBeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        // ListableBeanFactory가 아니면 이름 조회만 가능한 컨테이너다 — 그때는 클래스명 해석만 지원한다.
        if (beanFactory instanceof ListableBeanFactory listable) {
            this.beanFactory = listable;
        }
    }

    private final Map<String, RestClient> clientCache = new ConcurrentHashMap<>();

    /**
     * Every {@link HttpClient} built so far — one per channel/endpoint, which is what gives each
     * channel its own connection pool. The instances become unreachable once handed to
     * {@code JdkClientHttpRequestFactory}, so they are tracked here to be shut down in
     * {@link #destroy()}.
     *
     * <p>Tracked <strong>weakly</strong> on purpose. A cached client stays reachable through
     * {@link #clientCache}, so it is still here to be shut down at context close. A one-off client
     * built through {@link #baseRestClient} and then dropped by the caller must stay collectable —
     * holding it strongly would pin its selector thread for the life of the JVM, which is worse than
     * the leak this tracking exists to fix.
     */
    private final List<WeakReference<HttpClient>> httpClients = new CopyOnWriteArrayList<>();

    /**
     * Lower-level builder used internally and exposed for advanced cases where the caller already
     * has an {@link MidoClientProperties.EndpointConfig} in hand (e.g. building a one-off client
     * outside of a configured channel). Most callers should prefer {@link #getOrCreateClient(String)}.
     *
     * @param baseUrl        base URL the resulting {@code RestClient} will resolve relative URIs against
     * @param endpointConfig timeout / auth / headers / interceptors / gzip configuration
     * @param charset        default charset used by the {@code StringHttpMessageConverter} and as a
     *                       fallback for logging
     * @param contentType    outgoing {@code Content-Type} (JSON or XML)
     * @return a pre-configured {@link RestClient.Builder}; callers may further customize and then
     *         {@code .build()}
     */
    public RestClient.Builder baseRestClient(String baseUrl, MidoClientProperties.EndpointConfig endpointConfig, Charset charset, ContentType contentType) {
        BufferingClientHttpRequestFactory requestFactory = createRequestFactory(
                endpointConfig.getConnectTimeoutSeconds(),
                endpointConfig.getReadTimeoutSeconds()
        );

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .messageConverters(converters -> configureMessageConverters(converters, charset))
                .defaultHeaders(headers -> configureHeaders(headers, endpointConfig.getAuthorization(), endpointConfig.getHeaders(), contentType))
                .requestInterceptors(interceptors -> interceptors.addAll(createInterceptors(
                        endpointConfig.getInterceptors(),
                        endpointConfig.getLog(),
                        charset,
                        Boolean.TRUE.equals(endpointConfig.getLogBody()),
                        endpointConfig.getLogMaxBodyBytes(),
                        endpointConfig.getGzip())));
    }

    /**
     * Returns the cached {@link RestClient} for the channel's {@code primary} endpoint, creating it
     * on first access. Channel name lookup is case-insensitive.
     *
     * @param channelName YAML channel key (any casing)
     * @return the channel's primary {@link RestClient}
     * @throws IllegalStateException if the channel is not configured, the URL is missing, or a
     *                               custom interceptor cannot be instantiated. The exception message
     *                               names the channel; the cause carries the original failure.
     */
    public RestClient getOrCreateClient(String channelName) {
        String cacheKey = channelName.toLowerCase(Locale.ROOT) + "-primary";
        return clientCache.computeIfAbsent(cacheKey, k -> createClient(channelName, null));
    }

    /**
     * Returns the cached {@link RestClient} for the requested endpoint type, creating it on first
     * access. If {@link EndpointType#SECONDARY} is requested but the channel has no secondary
     * configuration, the primary endpoint is used as a fallback.
     *
     * <p>The fallback is applied <em>before</em> the cache lookup, so such a channel does not end up
     * with two identically configured clients (and, on the {@code jdk} transport, two connection
     * pools) under the {@code -primary} and {@code -secondary} keys.
     *
     * @param channelName  YAML channel key (any casing)
     * @param endpointType {@link EndpointType#PRIMARY} or {@link EndpointType#SECONDARY}
     * @return the requested {@link RestClient}
     * @throws IllegalStateException if the channel is not configured, the URL is missing, or a
     *                               custom interceptor cannot be instantiated
     */
    public RestClient getOrCreateClient(String channelName, EndpointType endpointType) {
        EndpointType effectiveType = resolveEndpointType(channelName, endpointType);
        String cacheKey = channelName.toLowerCase(Locale.ROOT) + "-" + effectiveType.getValue();
        return clientCache.computeIfAbsent(cacheKey, k -> createClient(channelName, effectiveType));
    }

    private EndpointType resolveEndpointType(String channelName, EndpointType requested) {
        if (requested != EndpointType.SECONDARY) return EndpointType.PRIMARY;

        // secondary가 없으면 primary 설정으로 폴백되므로 캐시 키도 primary로 정규화한다.
        // 알 수 없는 채널은 여기서 판단하지 않는다 — createClient가 채널명을 담은 예외로 보고한다.
        MidoClientProperties.ChannelConfig channelConfig =
                midoClientProperties.getChannels().get(channelName.toLowerCase(Locale.ROOT));
        return channelConfig != null && channelConfig.getSecondary() != null
                ? EndpointType.SECONDARY
                : EndpointType.PRIMARY;
    }

    private RestClient createClient(String channelName, EndpointType endpointType) {
        try {
            MidoClientProperties.ChannelConfig channelConfig = midoClientProperties.getChannelConfig(channelName);
            MidoClientProperties.EndpointConfig endpointConfig = getEndpointConfig(channelConfig, endpointType);

            if (endpointConfig == null || endpointConfig.getUrl() == null || endpointConfig.getUrl().trim().isEmpty()) {
                String configType = endpointType != null ? endpointType.getValue() : "primary";
                throw new IllegalArgumentException("URL is not configured for channel: " + channelName + ", type: " + configType);
            }

            Charset charset = resolveCharset(channelName, channelConfig.getCharset());

            return baseRestClient(
                    endpointConfig.getUrl(),
                    endpointConfig,
                    charset,
                    channelConfig.getType()
            ).build();
        } catch (Exception e) {
            String configType = endpointType != null ? endpointType.getValue() : "primary";
            throw new IllegalStateException("Cannot create RestClient for Channel: " + channelName + ", type: " + configType, e);
        }
    }

    private MidoClientProperties.EndpointConfig getEndpointConfig(MidoClientProperties.ChannelConfig channelConfig, EndpointType endpointType) {
        if (endpointType == null || endpointType == EndpointType.PRIMARY) {
            return channelConfig.getPrimary();
        } else if (endpointType == EndpointType.SECONDARY) {
            MidoClientProperties.EndpointConfig secondaryConfig = channelConfig.getSecondary();
            return secondaryConfig != null ? secondaryConfig : channelConfig.getPrimary();
        }
        throw new IllegalArgumentException("Unsupported EndpointType: " + endpointType);
    }

    private List<ClientHttpRequestInterceptor> createInterceptors(List<String> interceptorClassNames, LogLevel logLevel, Charset charset, boolean logBody, int maxBodyBytes, MidoClientProperties.Gzip gzip) {
        List<ClientHttpRequestInterceptor> interceptorList = new ArrayList<>();

        if (interceptorClassNames != null && !interceptorClassNames.isEmpty()) {
            addCustomInterceptors(interceptorList, interceptorClassNames);
        }

        interceptorList.add(new MidoLoggingInterceptor(logLevel, charset, logBody, maxBodyBytes));

        // gzip은 logging 뒤에 등록해야 로깅이 평문 body를 본다 (디버깅 가독성 우선)
        addGzipInterceptors(interceptorList, gzip);

        return interceptorList;
    }

    private void addGzipInterceptors(List<ClientHttpRequestInterceptor> interceptorList, MidoClientProperties.Gzip gzip) {
        if (gzip == null) return;
        if (Boolean.TRUE.equals(gzip.getRequest())) {
            interceptorList.add(new MidoGzipRequestInterceptor(gzip.getMinSize()));
        }
        if (Boolean.TRUE.equals(gzip.getResponse())) {
            interceptorList.add(new MidoGzipResponseInterceptor(gzip.getMaxDecompressedSize()));
        }
    }

    private void addCustomInterceptors(List<ClientHttpRequestInterceptor> interceptorList, List<String> classNames) {
        for (String className : classNames) {
            interceptorList.add(createInterceptor(className));
        }
    }

    /**
     * Resolves one {@code interceptors:} entry, which may be a Spring bean name or a fully-qualified
     * class name. Resolution order:
     *
     * <ol>
     *   <li>a registered bean of that name — taken from the container</li>
     *   <li>otherwise a loadable class: the sole bean of that type if there is exactly one,
     *       else a reflective instance built from the public no-arg constructor</li>
     *   <li>otherwise {@link IllegalStateException}</li>
     * </ol>
     *
     * <p>Container lookups are wrapped in {@link LazyInterceptorDelegate} so nothing is pulled from
     * the context while a consumer bean is still being constructed. Reflective instances are built
     * immediately, exactly as before — they cannot trigger anything in the container.
     */
    private ClientHttpRequestInterceptor createInterceptor(String reference) {
        if (beanFactory != null && beanFactory.containsBean(reference)) {
            return lazyBeanByName(reference);
        }

        // 설정 실패는 운영 환경에서 silent skip 대신 fail-fast — 외부 catch에서 채널 이름이 함께 보고된다.
        try {
            // Class.forName(String)은 mido-client를 로드한 클래스로더를 쓴다 — devtools의 RestartClassLoader에
            // 올라간 소비 측 인터셉터를 못 본다. 기본 클래스로더(TCCL 우선)로 조회해야 한다.
            Class<?> interceptorClass = ClassUtils.forName(reference, ClassUtils.getDefaultClassLoader());

            String uniqueBeanName = findUniqueBeanNameOfType(interceptorClass);
            if (uniqueBeanName != null) {
                return lazyBeanByName(uniqueBeanName);
            }

            Object instance = interceptorClass.getDeclaredConstructor().newInstance();
            if (!(instance instanceof ClientHttpRequestInterceptor interceptor)) {
                throw new IllegalStateException(
                        "Interceptor class does not implement ClientHttpRequestInterceptor: " + reference);
            }
            log.debug("Successfully registered interceptor: {}", reference);
            return interceptor;
        } catch (IllegalStateException e) {
            throw e;
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Interceptor '" + reference
                    + "' is neither a registered bean name nor a loadable class name", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to instantiate interceptor: " + reference, e);
        }
    }

    private ClientHttpRequestInterceptor lazyBeanByName(String beanName) {
        log.debug("Interceptor '{}' resolves to a Spring bean; it will be fetched on first request", beanName);
        return new LazyInterceptorDelegate(beanName,
                () -> beanFactory.getBean(beanName, ClientHttpRequestInterceptor.class));
    }

    /**
     * The single bean name of {@code type}, or {@code null} when there is none or more than one.
     *
     * <p>More than one deliberately yields {@code null} rather than an error: before bean resolution
     * existed, a class name always meant "instantiate reflectively", and an application that happens
     * to register two beans of that type must keep working. It warns instead, because silently
     * ignoring two candidate beans is worth noticing.
     */
    private String findUniqueBeanNameOfType(Class<?> type) {
        if (beanFactory == null || !ClientHttpRequestInterceptor.class.isAssignableFrom(type)) return null;

        // allowEagerInit=false — 해석 때문에 빈이 조기 초기화되면 안 된다.
        String[] beanNames = beanFactory.getBeanNamesForType(type, true, false);
        if (beanNames.length == 1) return beanNames[0];

        if (beanNames.length > 1) {
            log.warn("Interceptor '{}' matches {} beans {}, so it is instantiated reflectively as before. "
                            + "Reference one of them by bean name to use it.",
                    type.getName(), beanNames.length, Arrays.toString(beanNames));
        }
        return null;
    }

    private void configureHeaders(HttpHeaders headers, MidoClientProperties.Authorization authorization, List<MidoClientProperties.Header> customHeaders, ContentType contentType) {
        headers.add(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);
        headers.add(HttpHeaders.CONTENT_TYPE, contentType.getMediaType().toString());

        addAuthorizationHeader(headers, authorization);
        addCustomHeaders(headers, customHeaders);
    }

    private void addAuthorizationHeader(HttpHeaders headers, MidoClientProperties.Authorization authorization) {
        if (authorization != null && authorization.getType() != null && authorization.getToken() != null) {
            String authorizationValue = authorization.getType().getPrefix() + " " + authorization.getToken();
            headers.add(HttpHeaders.AUTHORIZATION, authorizationValue);
        }
    }

    private void addCustomHeaders(HttpHeaders headers, List<MidoClientProperties.Header> customHeaders) {
        if (customHeaders == null || customHeaders.isEmpty()) return;

        // 같은 이름의 첫 선언은 set(mido가 넣은 Accept/Content-Type/Authorization을 덮는다),
        // 두 번째부터는 add(같은 이름을 의도적으로 여러 번 선언한 경우를 잃지 않는다).
        Set<String> replaced = new HashSet<>();
        customHeaders.forEach(header -> {
            if (header.getName() == null || header.getValue() == null) return;
            if (replaced.add(header.getName().toLowerCase(Locale.ROOT))) {
                headers.set(header.getName(), header.getValue());
            } else {
                headers.add(header.getName(), header.getValue());
            }
        });
    }

    private void configureMessageConverters(List<HttpMessageConverter<?>> converters, Charset charset) {
        StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(charset);
        stringConverter.setWriteAcceptCharset(false);

        // application/json, application/xml 등도 String 으로 처리하도록 설정
        stringConverter.setSupportedMediaTypes(Arrays.asList(
                MediaType.TEXT_PLAIN,
                MediaType.TEXT_HTML,
                MediaType.TEXT_XML,
                MediaType.APPLICATION_JSON,
                MediaType.APPLICATION_XML,
                MediaType.ALL
        ));

        // 기본 String 컨버터만 교체한다. clear()로 전부 비우면 byte[]·Resource·form/multipart 지원까지 사라져
        // 파일 업/다운로드가 불가능해진다. Jackson은 기본 목록에 이미 들어 있어 따로 넣지 않는다.
        converters.removeIf(StringHttpMessageConverter.class::isInstance);
        converters.add(0, stringConverter);
    }

    /**
     * Fails fast at startup on configuration that would otherwise only surface on the first request:
     * an unknown {@code charset}, or an {@code interceptors:} entry that cannot be loaded, does not
     * implement {@link ClientHttpRequestInterceptor}, or has no public no-arg constructor.
     *
     * <p>Interceptor classes are loaded and inspected but <strong>not instantiated</strong> — a
     * constructor with side effects must not run twice.
     *
     * @throws IllegalStateException naming the channel, endpoint, and offending value
     */
    @Override
    public void afterPropertiesSet() {
        midoClientProperties.getChannels().forEach((channelName, channelConfig) -> {
            resolveCharset(channelName, channelConfig.getCharset());
            validateInterceptors(channelName, EndpointType.PRIMARY.getValue(), channelConfig.getPrimary());
            validateInterceptors(channelName, EndpointType.SECONDARY.getValue(), channelConfig.getSecondary());
        });
        warnWhenFileLoggerHasNoAppender();
    }

    /**
     * Warns when an endpoint routes logs to {@code MidoClientFileLog} ({@code log: file} or
     * {@code log: all}) but the host application never configured that logger — in which case the
     * lines silently fall through to root instead of landing in the intended file.
     *
     * <p>The check only runs on Logback. On any other SLF4J binding the appender list is not
     * introspectable through a portable API, so the warning is skipped rather than guessed at.
     */
    private void warnWhenFileLoggerHasNoAppender() {
        boolean fileLoggingConfigured = midoClientProperties.getChannels().values().stream()
                .flatMap(channel -> Stream.of(channel.getPrimary(), channel.getSecondary()))
                .filter(Objects::nonNull)
                .map(endpoint -> LogLevel.resolveEffectiveLogLevel(endpoint.getLog()))
                .anyMatch(level -> level == LogLevel.FILE || level == LogLevel.ALL);

        if (!fileLoggingConfigured) return;
        if (!ClassUtils.isPresent(LOGBACK_LOGGER_CLASS, ClassUtils.getDefaultClassLoader())) return;

        if (!LogbackAppenderCheck.hasAppender(MIDO_FILE_LOGGER_NAME)) {
            log.warn("Channels are configured with log: file/all but no appender is attached to the '{}' logger — "
                            + "those lines will fall through to the root logger. Declare it in logback.xml.",
                    MIDO_FILE_LOGGER_NAME);
        }
    }

    /**
     * Isolates the Logback-specific type reference into its own class so that the reference is only
     * resolved when {@link #warnWhenFileLoggerHasNoAppender()} has already confirmed Logback is on
     * the classpath.
     */
    private static final class LogbackAppenderCheck {

        private LogbackAppenderCheck() {
        }

        static boolean hasAppender(String loggerName) {
            org.slf4j.Logger logger = LoggerFactory.getLogger(loggerName);
            if (logger instanceof ch.qos.logback.classic.Logger logbackLogger) {
                return logbackLogger.iteratorForAppenders().hasNext();
            }
            // Logback이 클래스패스에 있어도 바인딩이 다른 경우가 있다 — 판단하지 않고 경고를 건너뛴다.
            return true;
        }
    }

    /**
     * Releases every channel's connection pool when the context shuts down.
     *
     * <p>{@code shutdown()} is used rather than {@code close()} on purpose: it is non-blocking, so a
     * request still in flight cannot stall context shutdown. In-flight exchanges finish, then the
     * selector and pool threads exit.
     */
    @Override
    public void destroy() {
        // clientCache를 먼저 비우면 캐시된 HttpClient가 도달 불가가 되어 shutdown 대상에서 빠질 수 있다.
        httpClients.forEach(reference -> {
            HttpClient httpClient = reference.get();
            if (httpClient != null) httpClient.shutdown();
        });
        httpClients.clear();
        clientCache.clear();
    }

    private void validateInterceptors(String channelName, String endpointName, MidoClientProperties.EndpointConfig endpointConfig) {
        if (endpointConfig == null || endpointConfig.getInterceptors() == null) return;

        for (String reference : endpointConfig.getInterceptors()) {
            try {
                validateInterceptorReference(reference);
            } catch (IllegalStateException e) {
                throw new IllegalStateException(interceptorFailureMessage(channelName, endpointName), e);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(interceptorFailureMessage(channelName, endpointName),
                        new IllegalStateException("Failed to load interceptor: " + reference, e));
            }
        }
    }

    /**
     * Startup check for one {@code interceptors:} entry. Nothing here instantiates the interceptor:
     * a class name is loaded and inspected, and a bean name is checked through
     * {@code containsBean} / {@code getType}.
     *
     * <p>{@code getType(name)} can initialize a {@code FactoryBean} in order to determine the type it
     * produces. That is accepted: it affects only interceptors declared through a {@code FactoryBean},
     * and the alternative is skipping the check that catches a bean of the wrong type before the first
     * request rather than during it.
     */
    private void validateInterceptorReference(String reference) throws ReflectiveOperationException {
        if (beanFactory != null && beanFactory.containsBean(reference)) {
            Class<?> beanType = beanFactory.getType(reference);
            if (beanType != null && !ClientHttpRequestInterceptor.class.isAssignableFrom(beanType)) {
                throw new IllegalStateException("Interceptor bean '" + reference + "' is a "
                        + beanType.getName() + ", which does not implement ClientHttpRequestInterceptor");
            }
            return;
        }

        Class<?> interceptorClass = ClassUtils.forName(reference, ClassUtils.getDefaultClassLoader());
        if (!ClientHttpRequestInterceptor.class.isAssignableFrom(interceptorClass)) {
            throw new IllegalStateException(
                    "Interceptor class does not implement ClientHttpRequestInterceptor: " + reference);
        }

        // 그 타입의 빈이 유일하게 있으면 그 빈을 쓰게 되므로 무인자 생성자를 요구하지 않는다.
        if (findUniqueBeanNameOfType(interceptorClass) == null) {
            interceptorClass.getDeclaredConstructor();
        }
    }

    private String interceptorFailureMessage(String channelName, String endpointName) {
        return "Invalid interceptor configured for channel: " + channelName + ", type: " + endpointName;
    }

    private Charset resolveCharset(String channelName, String charsetName) {
        if (charsetName == null) return StandardCharsets.UTF_8;
        try {
            return Charset.forName(charsetName);
        } catch (IllegalArgumentException e) {
            // UnsupportedCharsetException / IllegalCharsetNameException 모두 IllegalArgumentException 하위다.
            throw new IllegalStateException("Invalid charset '" + charsetName + "' for channel: " + channelName, e);
        }
    }

    private void track(HttpClient httpClient) {
        // 이미 회수된 one-off 클라이언트의 빈 참조를 걷어내 리스트가 무한히 자라지 않게 한다.
        httpClients.removeIf(reference -> reference.refersTo(null));
        httpClients.add(new WeakReference<>(httpClient));
    }

    /** Visible for tests: how many {@link HttpClient} instances {@link #destroy()} would shut down. */
    int trackedHttpClientCount() {
        return (int) httpClients.stream().filter(reference -> !reference.refersTo(null)).count();
    }

    private BufferingClientHttpRequestFactory createRequestFactory(long connectTimeoutSeconds, long readTimeoutSeconds) {
        // BufferingClientHttpRequestFactory 래핑 유지: 로깅 인터셉터가 응답 body를 재read해야 한다.
        return new BufferingClientHttpRequestFactory(createJdkRequestFactory(connectTimeoutSeconds, readTimeoutSeconds));
    }

    private ClientHttpRequestFactory createJdkRequestFactory(long connectTimeoutSeconds, long readTimeoutSeconds) {
        // connectTimeout은 HttpClient 빌더에, readTimeout은 팩토리에 지정된다.
        // followRedirects(NORMAL) — HttpClient 기본값은 NEVER다. NORMAL은 HTTPS→HTTP 다운그레이드를 거부한다.
        // Executor는 지정하지 않는다 — HttpClient 기본 executor를 쓰고 라이프사이클 소유권을 넘기지 않는다.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        track(httpClient);
        JdkClientHttpRequestFactory jdkFactory = new JdkClientHttpRequestFactory(httpClient);
        jdkFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return jdkFactory;
    }

}