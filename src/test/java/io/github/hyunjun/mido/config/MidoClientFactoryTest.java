package io.github.hyunjun.mido.config;

import io.github.hyunjun.mido.constant.ClientType;
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
    void shouldResolveEndpointClientTypeOverGlobalDefault() {
        // Given - global SIMPLE, endpoint explicitly JDK
        properties.setClientType(ClientType.SIMPLE);
        MidoClientProperties.EndpointConfig endpoint = new MidoClientProperties.EndpointConfig();
        endpoint.setClientType(ClientType.JDK);

        // When & Then - endpoint override wins
        assertThat(factory.resolveClientType(endpoint)).isEqualTo(ClientType.JDK);
    }

    @Test
    void shouldInheritGlobalClientTypeWhenEndpointUnset() {
        // Given - global JDK, endpoint leaves client-type null
        properties.setClientType(ClientType.JDK);
        MidoClientProperties.EndpointConfig endpoint = new MidoClientProperties.EndpointConfig();

        // When & Then - inherits global
        assertThat(endpoint.getClientType()).isNull();
        assertThat(factory.resolveClientType(endpoint)).isEqualTo(ClientType.JDK);
    }

    @Test
    void shouldDefaultToSimpleClientTypeWhenNothingConfigured() {
        // Given - neither global nor endpoint set (global defaults to SIMPLE)
        MidoClientProperties.EndpointConfig endpoint = new MidoClientProperties.EndpointConfig();

        // When & Then
        assertThat(properties.getClientType()).isEqualTo(ClientType.SIMPLE);
        assertThat(factory.resolveClientType(endpoint)).isEqualTo(ClientType.SIMPLE);
    }

    @Test
    void shouldCreateClientWithJdkClientType() {
        // Given - exercises the JdkClientHttpRequestFactory build path
        MidoClientProperties.ChannelConfig channelConfig = new MidoClientProperties.ChannelConfig();
        MidoClientProperties.EndpointConfig endpoint = new MidoClientProperties.EndpointConfig();
        endpoint.setUrl("https://jdk.test.com");
        endpoint.setClientType(ClientType.JDK);
        channelConfig.setPrimary(endpoint);
        properties.getChannels().put("jdkchannel", channelConfig);

        // When
        RestClient client = factory.getOrCreateClient("jdkchannel");

        // Then
        assertThat(client).isNotNull();
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