package io.github.hyunjun.mido.config;

import io.github.hyunjun.mido.constant.ContentType;
import io.github.hyunjun.mido.constant.EndpointType;
import io.github.hyunjun.mido.constant.LogLevel;
import io.github.hyunjun.mido.constant.TokenType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MidoClientFactoryTest {

    private MidoClientProperties properties;
    private MidoClientFactory factory;

    @BeforeEach
    void setUp() {
        properties = new MidoClientProperties();
        properties.setEnabled(true);

        // Test channel configuration
        MidoClientProperties.ChannelConfig channelConfig = new MidoClientProperties.ChannelConfig();
        channelConfig.setTitle("Test Channel");
        channelConfig.setCharset("UTF-8");

        // Primary endpoint
        MidoClientProperties.EndpointConfig primaryEndpoint = new MidoClientProperties.EndpointConfig();
        primaryEndpoint.setTitle("Primary Endpoint");
        primaryEndpoint.setUrl("https://api.test.com");
        primaryEndpoint.setReadTimeoutSeconds(30L);
        primaryEndpoint.setConnectTimeoutSeconds(5L);
        primaryEndpoint.setLog(LogLevel.CONSOLE);

        // Authorization
        MidoClientProperties.Authorization auth = new MidoClientProperties.Authorization();
        auth.setType(TokenType.BEARER);
        auth.setToken("test-token");
        primaryEndpoint.setAuthorization(auth);

        // Headers
        MidoClientProperties.Header header = new MidoClientProperties.Header();
        header.setName("X-Test-Header");
        header.setValue("test-value");
        primaryEndpoint.setHeaders(List.of(header));

        channelConfig.setPrimary(primaryEndpoint);

        // Secondary endpoint
        MidoClientProperties.EndpointConfig secondaryEndpoint = new MidoClientProperties.EndpointConfig();
        secondaryEndpoint.setTitle("Secondary Endpoint");
        secondaryEndpoint.setUrl("https://api2.test.com");
        secondaryEndpoint.setReadTimeoutSeconds(60L);
        secondaryEndpoint.setConnectTimeoutSeconds(3L);
        secondaryEndpoint.setLog(LogLevel.ALL);

        channelConfig.setSecondary(secondaryEndpoint);

        properties.getChannels().put("test", channelConfig);

        factory = new MidoClientFactory(properties);
    }

    @Test
    void shouldCreatePrimaryEndpointClient() {
        // When
        RestClient client = factory.getOrCreateClient("test");

        // Then
        assertThat(client).isNotNull();
    }

    @Test
    void shouldCreateSecondaryEndpointClient() {
        // When
        RestClient client = factory.getOrCreateClient("test", EndpointType.SECONDARY);

        // Then
        assertThat(client).isNotNull();
    }

    @Test
    void shouldCacheClients() {
        // When
        RestClient client1 = factory.getOrCreateClient("test");
        RestClient client2 = factory.getOrCreateClient("test");

        // Then
        assertThat(client1).isSameAs(client2);
    }

    @Test
    void shouldCreateDifferentClientsForDifferentEndpoints() {
        // When
        RestClient primaryClient = factory.getOrCreateClient("test");
        RestClient secondaryClient = factory.getOrCreateClient("test", EndpointType.SECONDARY);

        // Then
        assertThat(primaryClient).isNotSameAs(secondaryClient);
    }

    @Test
    void shouldThrowExceptionForUnknownChannel() {
        // When & Then
        assertThatThrownBy(() -> factory.getOrCreateClient("unknown"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot create RestClient for Channel: unknown");
    }

    @Test
    void shouldCreatePrimaryEndpointClientWithExplicitEndpointType() {
        // When
        RestClient clientImplicit = factory.getOrCreateClient("test");
        RestClient clientExplicit = factory.getOrCreateClient("test", EndpointType.PRIMARY);

        // Then - same cache key ("test-primary"), so same cached instance
        assertThat(clientExplicit).isNotNull();
        assertThat(clientImplicit).isSameAs(clientExplicit);
    }

    @Test
    void shouldCreateClientWithGzipEnabled() {
        // Given
        MidoClientProperties.ChannelConfig channelConfig = new MidoClientProperties.ChannelConfig();
        MidoClientProperties.EndpointConfig endpoint = new MidoClientProperties.EndpointConfig();
        endpoint.setUrl("https://gzip.test.com");
        MidoClientProperties.Gzip gzip = new MidoClientProperties.Gzip();
        gzip.setRequest(true);
        gzip.setResponse(true);
        gzip.setMinSize(512);
        endpoint.setGzip(gzip);
        channelConfig.setPrimary(endpoint);
        properties.getChannels().put("gzipchannel", channelConfig);

        // When
        RestClient client = factory.getOrCreateClient("gzipchannel");

        // Then
        assertThat(client).isNotNull();
    }

    @Test
    void shouldCreateClientWhenGzipDefaultsApplied() {
        // Given - no explicit gzip config; default object should be used (request=false, response=false)
        MidoClientProperties.ChannelConfig channelConfig = new MidoClientProperties.ChannelConfig();
        MidoClientProperties.EndpointConfig endpoint = new MidoClientProperties.EndpointConfig();
        endpoint.setUrl("https://default-gzip.test.com");
        channelConfig.setPrimary(endpoint);
        properties.getChannels().put("defaultgzip", channelConfig);

        // When
        RestClient client = factory.getOrCreateClient("defaultgzip");

        // Then
        assertThat(client).isNotNull();
        assertThat(endpoint.getGzip()).isNotNull();
        assertThat(endpoint.getGzip().getRequest()).isFalse();
        assertThat(endpoint.getGzip().getResponse()).isFalse();
        assertThat(endpoint.getGzip().getMinSize()).isEqualTo(1024);
        assertThat(endpoint.getGzip().getMaxDecompressedSize()).isEqualTo(10 * 1024 * 1024);
    }

    @Test
    void shouldDefaultToJsonContentTypeWhenNotConfigured() {
        // Given
        MidoClientProperties.ChannelConfig channelConfig = new MidoClientProperties.ChannelConfig();
        MidoClientProperties.EndpointConfig endpoint = new MidoClientProperties.EndpointConfig();
        endpoint.setUrl("https://default-type.test.com");
        channelConfig.setPrimary(endpoint);
        properties.getChannels().put("defaulttype", channelConfig);

        // When
        RestClient client = factory.getOrCreateClient("defaulttype");

        // Then
        assertThat(client).isNotNull();
        assertThat(channelConfig.getType()).isEqualTo(ContentType.JSON);
    }

    @Test
    void shouldCreateClientWithXmlContentType() {
        // Given
        MidoClientProperties.ChannelConfig channelConfig = new MidoClientProperties.ChannelConfig();
        channelConfig.setType(ContentType.XML);
        MidoClientProperties.EndpointConfig endpoint = new MidoClientProperties.EndpointConfig();
        endpoint.setUrl("https://xml.test.com");
        channelConfig.setPrimary(endpoint);
        properties.getChannels().put("xmlchannel", channelConfig);

        // When
        RestClient client = factory.getOrCreateClient("xmlchannel");

        // Then
        assertThat(client).isNotNull();
        assertThat(channelConfig.getType()).isEqualTo(ContentType.XML);
    }

    @Test
    void shouldSendJsonContentTypeHeaderByDefault() {
        // Given
        MidoClientProperties.EndpointConfig endpoint = new MidoClientProperties.EndpointConfig();
        endpoint.setUrl("https://json-hdr.test.com");
        endpoint.setLog(LogLevel.OFF);

        RestClient.Builder builder = factory.baseRestClient(endpoint.getUrl(), endpoint, StandardCharsets.UTF_8, ContentType.JSON);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();

        server.expect(requestTo("https://json-hdr.test.com/test"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess());

        // When
        client.get().uri("/test").retrieve().toBodilessEntity();

        // Then
        server.verify();
    }

    @Test
    void shouldSendXmlContentTypeHeaderWhenContentTypeIsXml() {
        // Given
        MidoClientProperties.EndpointConfig endpoint = new MidoClientProperties.EndpointConfig();
        endpoint.setUrl("https://xml-hdr.test.com");
        endpoint.setLog(LogLevel.OFF);

        RestClient.Builder builder = factory.baseRestClient(endpoint.getUrl(), endpoint, StandardCharsets.UTF_8, ContentType.XML);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();

        server.expect(requestTo("https://xml-hdr.test.com/test"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE))
                .andRespond(withSuccess());

        // When
        client.get().uri("/test").retrieve().toBodilessEntity();

        // Then
        server.verify();
    }

    @Test
    void shouldHandleChannelWithoutSecondaryEndpoint() {
        // Given
        MidoClientProperties.ChannelConfig channelConfig = new MidoClientProperties.ChannelConfig();
        MidoClientProperties.EndpointConfig primaryEndpoint = new MidoClientProperties.EndpointConfig();
        primaryEndpoint.setUrl("https://single.test.com");
        channelConfig.setPrimary(primaryEndpoint);
        properties.getChannels().put("single", channelConfig);

        // When
        RestClient client = factory.getOrCreateClient("single", EndpointType.SECONDARY);

        // Then - Should fallback to primary endpoint
        assertThat(client).isNotNull();
    }

    @Test
    void shouldReturnSameCachedClientRegardlessOfChannelNameCase() {
        // Given - YAML 키는 "test" (소문자, setUp 참조)

        // When - 다양한 대소문자로 호출
        RestClient lower = factory.getOrCreateClient("test");
        RestClient upper = factory.getOrCreateClient("TEST");
        RestClient mixed = factory.getOrCreateClient("Test");

        // Then - 모두 동일한 캐시 인스턴스
        assertThat(lower).isSameAs(upper);
        assertThat(upper).isSameAs(mixed);
    }

    @Test
    void shouldReturnSameCachedClientRegardlessOfCaseForSecondaryEndpoint() {
        // Given - "test" 채널에 secondary 정의됨 (setUp 참조)

        // When
        RestClient upper = factory.getOrCreateClient("TEST", EndpointType.SECONDARY);
        RestClient lower = factory.getOrCreateClient("test", EndpointType.SECONDARY);

        // Then
        assertThat(upper).isSameAs(lower);
    }

    @Test
    void shouldFailFastWhenInterceptorClassNotFound() {
        // Given
        MidoClientProperties.ChannelConfig channelConfig = new MidoClientProperties.ChannelConfig();
        MidoClientProperties.EndpointConfig endpoint = new MidoClientProperties.EndpointConfig();
        endpoint.setUrl("https://bad-interceptor.test.com");
        endpoint.setInterceptors(List.of("com.example.NonExistentInterceptor"));
        channelConfig.setPrimary(endpoint);
        properties.getChannels().put("badinterceptor", channelConfig);

        // When & Then - 채널 이름과 인터셉터 클래스명 모두 메시지에 포함되어야 함
        assertThatThrownBy(() -> factory.getOrCreateClient("badinterceptor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("badinterceptor")
                .hasRootCauseInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void shouldFailFastWhenInterceptorDoesNotImplementInterface() {
        // Given
        MidoClientProperties.ChannelConfig channelConfig = new MidoClientProperties.ChannelConfig();
        MidoClientProperties.EndpointConfig endpoint = new MidoClientProperties.EndpointConfig();
        endpoint.setUrl("https://wrong-type-interceptor.test.com");
        endpoint.setInterceptors(List.of(NotAnInterceptor.class.getName()));
        channelConfig.setPrimary(endpoint);
        properties.getChannels().put("wrongtypeinterceptor", channelConfig);

        // When & Then
        assertThatThrownBy(() -> factory.getOrCreateClient("wrongtypeinterceptor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wrongtypeinterceptor")
                .hasStackTraceContaining("does not implement ClientHttpRequestInterceptor")
                .hasStackTraceContaining(NotAnInterceptor.class.getName());
    }

    @Test
    void shouldFailFastWhenInterceptorHasNoNoArgConstructor() {
        // Given
        MidoClientProperties.ChannelConfig channelConfig = new MidoClientProperties.ChannelConfig();
        MidoClientProperties.EndpointConfig endpoint = new MidoClientProperties.EndpointConfig();
        endpoint.setUrl("https://no-default-ctor-interceptor.test.com");
        endpoint.setInterceptors(List.of(InterceptorWithoutNoArgCtor.class.getName()));
        channelConfig.setPrimary(endpoint);
        properties.getChannels().put("nodefaultctor", channelConfig);

        // When & Then
        assertThatThrownBy(() -> factory.getOrCreateClient("nodefaultctor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nodefaultctor")
                .hasStackTraceContaining("Failed to instantiate interceptor")
                .hasStackTraceContaining(InterceptorWithoutNoArgCtor.class.getName());
    }

    @Test
    void shouldReusePrimaryClientWhenSecondaryFallsBack() {
        // Given - secondary 미설정 채널
        MidoClientProperties.ChannelConfig channelConfig = new MidoClientProperties.ChannelConfig();
        MidoClientProperties.EndpointConfig primary = new MidoClientProperties.EndpointConfig();
        primary.setUrl("https://single.test.com");
        channelConfig.setPrimary(primary);
        properties.getChannels().put("singlefallback", channelConfig);

        // When
        RestClient primaryClient = factory.getOrCreateClient("singlefallback");
        RestClient secondaryClient = factory.getOrCreateClient("singlefallback", EndpointType.SECONDARY);

        // Then - 동일 설정으로 클라이언트(및 jdk 커넥션 풀)를 두 개 만들지 않는다
        assertThat(secondaryClient).isSameAs(primaryClient);
    }

    @Test
    void shouldKeepSeparateClientsWhenSecondaryIsConfigured() {
        // Given - setUp의 "test" 채널은 secondary가 있다
        // When
        RestClient primaryClient = factory.getOrCreateClient("test");
        RestClient secondaryClient = factory.getOrCreateClient("test", EndpointType.SECONDARY);

        // Then - 폴백 정규화가 실제 secondary를 삼키지 않아야 한다
        assertThat(secondaryClient).isNotSameAs(primaryClient);
    }

    @Test
    void shouldNotPinOneOffHttpClientsCreatedThroughBaseRestClient() {
        // Given - baseRestClient는 public API고 "one-off client" 용도로 문서화되어 있다.
        // 여기서 만든 HttpClient를 강참조로 붙잡으면 selector 스레드가 JVM 수명 내내 남는다.
        MidoClientProperties.EndpointConfig endpoint = new MidoClientProperties.EndpointConfig();
        endpoint.setUrl("https://oneoff.test.com");
        endpoint.setLog(LogLevel.OFF);

        for (int i = 0; i < 50; i++) {
            factory.baseRestClient(endpoint.getUrl(), endpoint, StandardCharsets.UTF_8, ContentType.JSON).build();
        }

        // When & Then - 강참조로 붙잡고 있으면 몇 번을 유도해도 절대 줄지 않는다
        assertThat(awaitTrackedCountBelow(50)).isTrue();
    }

    /**
     * {@code System.gc()}는 힌트일 뿐이므로 몇 번 유도하며 기다린다. 강참조 회귀가 들어오면
     * 회수가 아예 일어나지 않아 매번 false가 된다.
     */
    private boolean awaitTrackedCountBelow(int threshold) {
        for (int attempt = 0; attempt < 10; attempt++) {
            System.gc();
            if (factory.trackedHttpClientCount() < threshold) return true;
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    @Test
    void shouldKeepTrackingCachedClientsSoDestroyCanShutThemDown() {
        // Given - 캐시된 클라이언트는 clientCache가 강참조로 붙잡으므로 GC 후에도 남아야 한다
        MidoClientProperties.ChannelConfig channelConfig = new MidoClientProperties.ChannelConfig();
        MidoClientProperties.EndpointConfig endpoint = new MidoClientProperties.EndpointConfig();
        endpoint.setUrl("https://cached.test.com");
        channelConfig.setPrimary(endpoint);
        properties.getChannels().put("cachedchannel", channelConfig);
        factory.getOrCreateClient("cachedchannel");

        // When - GC를 유도해도
        for (int i = 0; i < 3; i++) {
            System.gc();
        }

        // Then - clientCache가 붙잡고 있으므로 destroy()가 shutdown할 대상으로 남아 있어야 한다
        assertThat(factory.trackedHttpClientCount()).isEqualTo(1);
    }

    @Test
    void shouldClearCachedClientsOnDestroy() {
        // Given
        MidoClientProperties.ChannelConfig channelConfig = new MidoClientProperties.ChannelConfig();
        MidoClientProperties.EndpointConfig endpoint = new MidoClientProperties.EndpointConfig();
        endpoint.setUrl("https://destroy.test.com");
        channelConfig.setPrimary(endpoint);
        properties.getChannels().put("destroychannel", channelConfig);
        RestClient before = factory.getOrCreateClient("destroychannel");

        // When
        factory.destroy();

        // Then - 캐시가 비워져 다음 호출은 새 인스턴스를 만든다
        assertThat(factory.getOrCreateClient("destroychannel")).isNotSameAs(before);
    }

    @Test
    void shouldLetCustomHeaderOverrideDefaultAcceptHeader() {
        // Given - 채널이 Accept를 명시하면 mido가 넣은 */*를 대체해야 한다 (두 개가 나가면 서버가 XML을 줄 수 있다)
        MidoClientProperties.EndpointConfig endpoint = new MidoClientProperties.EndpointConfig();
        endpoint.setUrl("https://accept-override.test.com");
        endpoint.setLog(LogLevel.OFF);
        MidoClientProperties.Header accept = new MidoClientProperties.Header();
        accept.setName(HttpHeaders.ACCEPT);
        accept.setValue(MediaType.APPLICATION_JSON_VALUE);
        endpoint.setHeaders(List.of(accept));

        RestClient.Builder builder = factory.baseRestClient(endpoint.getUrl(), endpoint, StandardCharsets.UTF_8, ContentType.JSON);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();

        // header()는 값 목록을 정확히 비교하므로 */*가 남아 있으면 실패한다
        server.expect(requestTo("https://accept-override.test.com/test"))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess());

        // When
        client.get().uri("/test").retrieve().toBodilessEntity();

        // Then
        server.verify();
    }

    @Test
    void shouldKeepEveryValueWhenSameCustomHeaderIsDeclaredTwice() {
        // Given - 같은 이름을 의도적으로 두 번 선언한 경우는 누적되어야 한다
        MidoClientProperties.EndpointConfig endpoint = new MidoClientProperties.EndpointConfig();
        endpoint.setUrl("https://multi-header.test.com");
        endpoint.setLog(LogLevel.OFF);
        MidoClientProperties.Header first = new MidoClientProperties.Header();
        first.setName("X-Trace");
        first.setValue("a");
        MidoClientProperties.Header second = new MidoClientProperties.Header();
        second.setName("X-Trace");
        second.setValue("b");
        endpoint.setHeaders(List.of(first, second));

        RestClient.Builder builder = factory.baseRestClient(endpoint.getUrl(), endpoint, StandardCharsets.UTF_8, ContentType.JSON);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();

        server.expect(requestTo("https://multi-header.test.com/test"))
                .andExpect(header("X-Trace", "a", "b"))
                .andRespond(withSuccess());

        // When
        client.get().uri("/test").retrieve().toBodilessEntity();

        // Then
        server.verify();
    }

    @Test
    void shouldKeepByteArrayConverterForBinaryResponses() {
        // Given - converters.clear()를 하면 byte[] 컨버터가 사라져 파일 다운로드가 불가능해진다
        MidoClientProperties.EndpointConfig endpoint = new MidoClientProperties.EndpointConfig();
        endpoint.setUrl("https://binary.test.com");
        endpoint.setLog(LogLevel.OFF);

        RestClient.Builder builder = factory.baseRestClient(endpoint.getUrl(), endpoint, StandardCharsets.UTF_8, ContentType.JSON);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();

        byte[] payload = {0x00, 0x01, 0x02, (byte) 0xFF};
        server.expect(requestTo("https://binary.test.com/file"))
                .andRespond(withSuccess(payload, MediaType.APPLICATION_OCTET_STREAM));

        // When
        byte[] body = client.get().uri("/file").retrieve().body(byte[].class);

        // Then
        assertThat(body).isEqualTo(payload);
        server.verify();
    }

    @Test
    void shouldStillReadStringBodyWithConfiguredCharset() {
        // Given - String 컨버터는 여전히 최우선이어야 한다 (기본 목록의 것을 교체했으므로)
        MidoClientProperties.EndpointConfig endpoint = new MidoClientProperties.EndpointConfig();
        endpoint.setUrl("https://text.test.com");
        endpoint.setLog(LogLevel.OFF);

        RestClient.Builder builder = factory.baseRestClient(endpoint.getUrl(), endpoint, StandardCharsets.UTF_8, ContentType.JSON);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();

        server.expect(requestTo("https://text.test.com/text"))
                .andRespond(withSuccess("{\"name\":\"한글\"}", MediaType.APPLICATION_JSON));

        // When
        String body = client.get().uri("/text").retrieve().body(String.class);

        // Then
        assertThat(body).isEqualTo("{\"name\":\"한글\"}");
        server.verify();
    }

    @Test
    void shouldListConfiguredChannelsWhenChannelIsUnknown() {
        // Given - setUp에 "test" 채널만 있고, 오타 난 이름으로 조회한다
        // When & Then - 메시지만 보고 오타를 판단할 수 있어야 한다
        assertThatThrownBy(() -> properties.getChannelConfig("tset"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown Channel: tset")
                .hasMessageContaining("Configured channels: [test]");
    }

    @Test
    void shouldGiveEachChannelItsOwnHttpClient() {
        // Given - 채널별 커넥션 풀 격리가 이 라이브러리의 존재 이유다
        MidoClientProperties.ChannelConfig first = new MidoClientProperties.ChannelConfig();
        MidoClientProperties.EndpointConfig firstEndpoint = new MidoClientProperties.EndpointConfig();
        firstEndpoint.setUrl("https://pool-a.test.com");
        first.setPrimary(firstEndpoint);
        properties.getChannels().put("poola", first);

        MidoClientProperties.ChannelConfig second = new MidoClientProperties.ChannelConfig();
        MidoClientProperties.EndpointConfig secondEndpoint = new MidoClientProperties.EndpointConfig();
        secondEndpoint.setUrl("https://pool-b.test.com");
        second.setPrimary(secondEndpoint);
        properties.getChannels().put("poolb", second);

        // When
        factory.getOrCreateClient("poola");
        factory.getOrCreateClient("poolb");

        // Then - destroy가 정리할 HttpClient가 채널 수만큼 추적된다
        assertThat(factory.trackedHttpClientCount()).isEqualTo(2);
    }

    public static class NotAnInterceptor {
        public NotAnInterceptor() {
            // intentionally not implementing ClientHttpRequestInterceptor
        }
    }

    public static class InterceptorWithoutNoArgCtor
            implements org.springframework.http.client.ClientHttpRequestInterceptor {

        @SuppressWarnings("unused")
        private final String required;

        public InterceptorWithoutNoArgCtor(String required) {
            this.required = required;
        }

        @Override
        public org.springframework.http.client.ClientHttpResponse intercept(
                org.springframework.http.HttpRequest request,
                byte[] body,
                org.springframework.http.client.ClientHttpRequestExecution execution) {
            throw new UnsupportedOperationException("test fixture only");
        }
    }

}