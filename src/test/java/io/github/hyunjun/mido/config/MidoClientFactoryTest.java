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

        // First endpoint
        MidoClientProperties.EndpointConfig firstEndpoint = new MidoClientProperties.EndpointConfig();
        firstEndpoint.setTitle("First Endpoint");
        firstEndpoint.setUrl("https://api.test.com");
        firstEndpoint.setReadTimeoutSeconds(30L);
        firstEndpoint.setConnectTimeoutSeconds(5L);
        firstEndpoint.setLog(LogLevel.CONSOLE);

        // Authorization
        MidoClientProperties.Authorization auth = new MidoClientProperties.Authorization();
        auth.setType(TokenType.BEARER);
        auth.setToken("test-token");
        firstEndpoint.setAuthorization(auth);

        // Headers
        MidoClientProperties.Header header = new MidoClientProperties.Header();
        header.setName("X-Test-Header");
        header.setValue("test-value");
        firstEndpoint.setHeaders(List.of(header));

        channelConfig.setFirst(firstEndpoint);

        // Second endpoint
        MidoClientProperties.EndpointConfig secondEndpoint = new MidoClientProperties.EndpointConfig();
        secondEndpoint.setTitle("Second Endpoint");
        secondEndpoint.setUrl("https://api2.test.com");
        secondEndpoint.setReadTimeoutSeconds(60L);
        secondEndpoint.setConnectTimeoutSeconds(3L);
        secondEndpoint.setLog(LogLevel.ALL);

        channelConfig.setSecond(secondEndpoint);

        properties.getChannels().put("test", channelConfig);

        factory = new MidoClientFactory(properties);
    }

    @Test
    void shouldCreateFirstEndpointClient() {
        // When
        RestClient client = factory.getOrCreateClient("test");

        // Then
        assertThat(client).isNotNull();
    }

    @Test
    void shouldCreateSecondEndpointClient() {
        // When
        RestClient client = factory.getOrCreateClient("test", EndpointType.SECOND);

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
        RestClient firstClient = factory.getOrCreateClient("test");
        RestClient secondClient = factory.getOrCreateClient("test", EndpointType.SECOND);

        // Then
        assertThat(firstClient).isNotSameAs(secondClient);
    }

    @Test
    void shouldThrowExceptionForUnknownChannel() {
        // When & Then
        assertThatThrownBy(() -> factory.getOrCreateClient("unknown"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot create RestClient for Channel: unknown");
    }

    @Test
    void shouldCreateFirstEndpointClientWithExplicitEndpointType() {
        // When
        RestClient clientImplicit = factory.getOrCreateClient("test");
        RestClient clientExplicit = factory.getOrCreateClient("test", EndpointType.FIRST);

        // Then - same cache key ("test-first"), so same cached instance
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
        channelConfig.setFirst(endpoint);
        properties.getChannels().put("gzipChannel", channelConfig);

        // When
        RestClient client = factory.getOrCreateClient("gzipChannel");

        // Then
        assertThat(client).isNotNull();
    }

    @Test
    void shouldCreateClientWhenGzipDefaultsApplied() {
        // Given - no explicit gzip config; default object should be used (request=false, response=false)
        MidoClientProperties.ChannelConfig channelConfig = new MidoClientProperties.ChannelConfig();
        MidoClientProperties.EndpointConfig endpoint = new MidoClientProperties.EndpointConfig();
        endpoint.setUrl("https://default-gzip.test.com");
        channelConfig.setFirst(endpoint);
        properties.getChannels().put("defaultGzip", channelConfig);

        // When
        RestClient client = factory.getOrCreateClient("defaultGzip");

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
        channelConfig.setFirst(endpoint);
        properties.getChannels().put("defaultType", channelConfig);

        // When
        RestClient client = factory.getOrCreateClient("defaultType");

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
        channelConfig.setFirst(endpoint);
        properties.getChannels().put("xmlChannel", channelConfig);

        // When
        RestClient client = factory.getOrCreateClient("xmlChannel");

        // Then
        assertThat(client).isNotNull();
        assertThat(channelConfig.getType()).isEqualTo(ContentType.XML);
    }

    @Test
    void shouldBuildClientViaThreeArgBaseRestClientOverload() {
        // Given - back-compat 3-arg overload contract
        MidoClientProperties.EndpointConfig endpoint = new MidoClientProperties.EndpointConfig();
        endpoint.setUrl("https://overload.test.com");
        endpoint.setLog(LogLevel.OFF);

        // When
        RestClient.Builder builder = factory.baseRestClient(endpoint.getUrl(), endpoint, StandardCharsets.UTF_8);

        // Then
        assertThat(builder).isNotNull();
        assertThat(builder.build()).isNotNull();
    }

    @Test
    void shouldSendJsonContentTypeHeaderByDefault() {
        // Given
        MidoClientProperties.EndpointConfig endpoint = new MidoClientProperties.EndpointConfig();
        endpoint.setUrl("https://json-hdr.test.com");
        endpoint.setLog(LogLevel.OFF);

        RestClient.Builder builder = factory.baseRestClient(endpoint.getUrl(), endpoint, StandardCharsets.UTF_8);
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
    void shouldHandleChannelWithoutSecondEndpoint() {
        // Given
        MidoClientProperties.ChannelConfig channelConfig = new MidoClientProperties.ChannelConfig();
        MidoClientProperties.EndpointConfig firstEndpoint = new MidoClientProperties.EndpointConfig();
        firstEndpoint.setUrl("https://single.test.com");
        channelConfig.setFirst(firstEndpoint);
        properties.getChannels().put("single", channelConfig);

        // When
        RestClient client = factory.getOrCreateClient("single", EndpointType.SECOND);

        // Then - Should fallback to first endpoint
        assertThat(client).isNotNull();
    }

}