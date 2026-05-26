package io.github.hyunjun.mido.config;

import io.github.hyunjun.mido.constant.ContentType;
import io.github.hyunjun.mido.constant.EndpointType;
import io.github.hyunjun.mido.constant.LogLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
public class MidoClientFactory {

    private final MidoClientProperties midoClientProperties;

    private final Map<String, RestClient> clientCache = new ConcurrentHashMap<>();

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
                .requestInterceptors(interceptors -> interceptors.addAll(createInterceptors(endpointConfig.getInterceptors(), endpointConfig.getLog(), charset, endpointConfig.getGzip())));
    }

    public RestClient getOrCreateClient(String channelName) {
        String cacheKey = channelName.toLowerCase(Locale.ROOT) + "-primary";
        return clientCache.computeIfAbsent(cacheKey, k -> createClient(channelName, null));
    }

    public RestClient getOrCreateClient(String channelName, EndpointType endpointType) {
        String cacheKey = channelName.toLowerCase(Locale.ROOT) + "-" + endpointType.getValue();
        return clientCache.computeIfAbsent(cacheKey, k -> createClient(channelName, endpointType));
    }

    private RestClient createClient(String channelName, EndpointType endpointType) {
        try {
            MidoClientProperties.ChannelConfig channelConfig = midoClientProperties.getChannelConfig(channelName);
            MidoClientProperties.EndpointConfig endpointConfig = getEndpointConfig(channelConfig, endpointType);

            if (endpointConfig == null || endpointConfig.getUrl() == null || endpointConfig.getUrl().trim().isEmpty()) {
                String configType = endpointType != null ? endpointType.getValue() : "primary";
                throw new IllegalArgumentException("URL is not configured for channel: " + channelName + ", type: " + configType);
            }

            Charset charset = channelConfig.getCharset() != null ?
                Charset.forName(channelConfig.getCharset()) : StandardCharsets.UTF_8;

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

    private List<ClientHttpRequestInterceptor> createInterceptors(List<String> interceptorClassNames, LogLevel logLevel, Charset charset, MidoClientProperties.Gzip gzip) {
        List<ClientHttpRequestInterceptor> interceptorList = new ArrayList<>();

        if (interceptorClassNames != null && !interceptorClassNames.isEmpty()) {
            addCustomInterceptors(interceptorList, interceptorClassNames);
        }

        interceptorList.add(new MidoLoggingInterceptor(logLevel, charset));

        // gzip은 logging 뒤에 등록해야 로깅이 평문 body를 본다 (디버깅 가독성 우선)
        addGzipInterceptors(interceptorList, gzip);

        return interceptorList;
    }

    private void addGzipInterceptors(List<ClientHttpRequestInterceptor> interceptorList, MidoClientProperties.Gzip gzip) {
        if (gzip == null) return;
        if (Boolean.TRUE.equals(gzip.getRequest())) {
            int minSize = gzip.getMinSize() != null ? gzip.getMinSize() : 1024;
            interceptorList.add(new MidoGzipRequestInterceptor(minSize));
        }
        if (Boolean.TRUE.equals(gzip.getResponse())) {
            int maxSize = gzip.getMaxDecompressedSize() != null ? gzip.getMaxDecompressedSize() : 10 * 1024 * 1024;
            interceptorList.add(new MidoGzipResponseInterceptor(maxSize));
        }
    }

    private void addCustomInterceptors(List<ClientHttpRequestInterceptor> interceptorList, List<String> classNames) {
        for (String className : classNames) {
            interceptorList.add(createInterceptor(className));
        }
    }

    private ClientHttpRequestInterceptor createInterceptor(String className) {
        // 설정 실패는 운영 환경에서 silent skip 대신 fail-fast — 외부 catch에서 채널 이름이 함께 보고된다.
        try {
            Class<?> interceptorClass = Class.forName(className);
            Object instance = interceptorClass.getDeclaredConstructor().newInstance();
            if (!(instance instanceof ClientHttpRequestInterceptor interceptor)) {
                throw new IllegalStateException(
                        "Interceptor class does not implement ClientHttpRequestInterceptor: " + className);
            }
            log.debug("Successfully registered interceptor: {}", className);
            return interceptor;
        } catch (IllegalStateException e) {
            throw e;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to instantiate interceptor: " + className, e);
        }
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
        if (customHeaders != null && !customHeaders.isEmpty()) {
            customHeaders.forEach(header -> {
                if (header.getName() != null && header.getValue() != null) {
                    headers.add(header.getName(), header.getValue());
                }
            });
        }
    }

    private void configureMessageConverters(List<org.springframework.http.converter.HttpMessageConverter<?>> converters, Charset charset) {
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

        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();

        converters.clear();
        converters.add(stringConverter);
        converters.add(jacksonConverter);
    }

    private BufferingClientHttpRequestFactory createRequestFactory(Long connectTimeoutSeconds, Long readTimeoutSeconds) {
        SimpleClientHttpRequestFactory simpleFactory = new SimpleClientHttpRequestFactory();
        simpleFactory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds != null ? connectTimeoutSeconds : 3));
        simpleFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds != null ? readTimeoutSeconds : 60));
        return new BufferingClientHttpRequestFactory(simpleFactory);
    }

}