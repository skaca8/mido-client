package io.github.hyunjun.mido.config;

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

    public RestClient.Builder baseRestClient(String baseUrl, MidoClientProperties.EndpointConfig endpointConfig, Charset charset) {
        BufferingClientHttpRequestFactory requestFactory = createRequestFactory(
                endpointConfig.getConnectTimeoutSeconds(),
                endpointConfig.getReadTimeoutSeconds()
        );

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .messageConverters(converters -> configureMessageConverters(converters, charset))
                .defaultHeaders(headers -> configureHeaders(headers, endpointConfig.getAuthorization(), endpointConfig.getHeaders()))
                .requestInterceptors(interceptors -> interceptors.addAll(createInterceptors(endpointConfig.getInterceptors(), endpointConfig.getLog(), charset)));
    }

    public RestClient getOrCreateClient(String channelName) {
        String cacheKey = channelName.toLowerCase() + "-first";
        return clientCache.computeIfAbsent(cacheKey, k -> createClient(channelName, null));
    }

    public RestClient getOrCreateClient(String channelName, EndpointType endpointType) {
        String cacheKey = channelName.toLowerCase() + "-" + endpointType.getValue();
        return clientCache.computeIfAbsent(cacheKey, k -> createClient(channelName, endpointType));
    }

    private RestClient createClient(String channelName, EndpointType endpointType) {
        try {
            MidoClientProperties.ChannelConfig channelConfig = midoClientProperties.getChannelConfig(channelName);
            MidoClientProperties.EndpointConfig endpointConfig = getEndpointConfig(channelConfig, endpointType);

            if (endpointConfig == null || endpointConfig.getUrl() == null || endpointConfig.getUrl().trim().isEmpty()) {
                String configType = endpointType != null ? endpointType.getValue() : "first";
                throw new IllegalArgumentException("URL is not configured for channel: " + channelName + ", type: " + configType);
            }

            Charset charset = channelConfig.getCharset() != null ?
                Charset.forName(channelConfig.getCharset()) : StandardCharsets.UTF_8;

            return baseRestClient(
                    endpointConfig.getUrl(),
                    endpointConfig,
                    charset
            ).build();
        } catch (Exception e) {
            String configType = endpointType != null ? endpointType.getValue() : "first";
            throw new IllegalStateException("Cannot create RestClient for Channel: " + channelName + ", type: " + configType, e);
        }
    }

    private MidoClientProperties.EndpointConfig getEndpointConfig(MidoClientProperties.ChannelConfig channelConfig, EndpointType endpointType) {
        if (endpointType == null || endpointType == EndpointType.FIRST) {
            return channelConfig.getFirst();
        } else if (endpointType == EndpointType.SECOND) {
            MidoClientProperties.EndpointConfig secondConfig = channelConfig.getSecond();
            return secondConfig != null ? secondConfig : channelConfig.getFirst();
        }
        throw new IllegalArgumentException("Unsupported EndpointType: " + endpointType);
    }

    private List<ClientHttpRequestInterceptor> createInterceptors(List<String> interceptorClassNames, LogLevel logLevel, Charset charset) {
        List<ClientHttpRequestInterceptor> interceptorList = new ArrayList<>();

        if (interceptorClassNames != null && !interceptorClassNames.isEmpty()) {
            addCustomInterceptors(interceptorList, interceptorClassNames);
        }

        interceptorList.add(new MidoLoggingInterceptor(logLevel, charset));
        return interceptorList;
    }

    private void addCustomInterceptors(List<ClientHttpRequestInterceptor> interceptorList, List<String> classNames) {
        for (String className : classNames) {
            createInterceptor(className).ifPresent(interceptorList::add);
        }
    }

    private Optional<ClientHttpRequestInterceptor> createInterceptor(String className) {
        try {
            Class<?> interceptorClass = Class.forName(className);
            ClientHttpRequestInterceptor interceptor = (ClientHttpRequestInterceptor) interceptorClass.getDeclaredConstructor().newInstance();
            log.debug("Successfully registered interceptor: {}", className);
            return Optional.of(interceptor);
        } catch (Exception e) {
            log.warn("Failed to instantiate interceptor: {}, error: {}", className, e.getMessage());
            return Optional.empty();
        }
    }

    private void configureHeaders(HttpHeaders headers, MidoClientProperties.Authorization authorization, List<MidoClientProperties.Header> customHeaders) {
        headers.add(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

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

        // application/json도 String으로 처리하도록 설정
        stringConverter.setSupportedMediaTypes(Arrays.asList(
                MediaType.TEXT_PLAIN,
                MediaType.TEXT_HTML,
                MediaType.APPLICATION_JSON,
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