package io.github.hyunjun.mido.constant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FailureTypeTest {

    @Test
    void shouldClassifyDnsFailure() {
        assertThat(FailureType.classify(new UnknownHostException("nope.invalid")))
                .isEqualTo(FailureType.DNS)
                .extracting(FailureType::getDelivery)
                .isEqualTo(FailureType.Delivery.NOT_DELIVERED);
    }

    @Test
    void shouldClassifyTlsFailureWithUnknownDelivery() {
        // SSLException은 핸드셰이크(전송 전)뿐 아니라 응답을 읽는 중(전송 후)에도 발생한다.
        // 둘을 구분할 수 없으므로 NOT_DELIVERED로 단정하지 않는다 — 잘못된 단정은 중복 결제로 이어진다.
        assertThat(FailureType.classify(new SSLHandshakeException("bad cert")))
                .isEqualTo(FailureType.TLS)
                .extracting(FailureType::getDelivery)
                .isEqualTo(FailureType.Delivery.UNKNOWN);

        assertThat(FailureType.classify(new SSLException("truncated record while reading response")))
                .isEqualTo(FailureType.TLS)
                .extracting(FailureType::getDelivery)
                .isEqualTo(FailureType.Delivery.UNKNOWN);
    }

    @Test
    void shouldNeverClaimNotDeliveredForAFailureThatCanHappenAfterDelivery() {
        // NOT_DELIVERED는 "전송 전에만 발생 가능한" 실패에만 붙어야 한다.
        assertThat(FailureType.TLS.getDelivery()).isNotEqualTo(FailureType.Delivery.NOT_DELIVERED);
        assertThat(FailureType.TIMEOUT.getDelivery()).isNotEqualTo(FailureType.Delivery.NOT_DELIVERED);
        assertThat(FailureType.UNKNOWN.getDelivery()).isNotEqualTo(FailureType.Delivery.NOT_DELIVERED);
    }

    @Test
    void shouldClassifyConnectRefusedAsNotDelivered() {
        assertThat(FailureType.classify(new ConnectException("Connection refused")))
                .isEqualTo(FailureType.CONNECT)
                .extracting(FailureType::getDelivery)
                .isEqualTo(FailureType.Delivery.NOT_DELIVERED);
    }

    @Test
    void shouldClassifyJdkConnectTimeoutAsConnectNotTimeout() {
        // HttpConnectTimeoutException은 HttpTimeoutException의 하위 타입이다 — 순서가 뒤바뀌면 TIMEOUT으로 잡힌다.
        assertThat(FailureType.classify(new HttpConnectTimeoutException("connect timed out")))
                .isEqualTo(FailureType.CONNECT);
    }

    @Test
    void shouldClassifyJdkResponseTimeoutAsUnknownDelivery() {
        assertThat(FailureType.classify(new HttpTimeoutException("request timed out")))
                .isEqualTo(FailureType.TIMEOUT)
                .extracting(FailureType::getDelivery)
                .isEqualTo(FailureType.Delivery.UNKNOWN);
    }

    @Test
    void shouldClassifySocketTimeoutAsUnknownDelivery() {
        // simple 전송은 connect/read 타임아웃을 구분하지 못하므로 도달 여부를 단정할 수 없다.
        assertThat(FailureType.classify(new SocketTimeoutException("Read timed out")))
                .isEqualTo(FailureType.TIMEOUT)
                .extracting(FailureType::getDelivery)
                .isEqualTo(FailureType.Delivery.UNKNOWN);
    }

    @Test
    void shouldUnwrapSpringResourceAccessException() {
        // Spring은 전송 예외를 ResourceAccessException으로 감싼다 — cause 체인을 따라가야 한다.
        ResourceAccessException wrapped =
                new ResourceAccessException("I/O error", new UnknownHostException("nope.invalid"));

        assertThat(FailureType.classify(wrapped)).isEqualTo(FailureType.DNS);
    }

    @Test
    void shouldClassifyClientErrorResponseAsDelivered() {
        HttpClientErrorException notFound = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

        assertThat(FailureType.classify(notFound))
                .isEqualTo(FailureType.CLIENT_ERROR)
                .extracting(FailureType::getDelivery)
                .isEqualTo(FailureType.Delivery.DELIVERED);
    }

    @Test
    void shouldClassifyServerErrorResponseAsDelivered() {
        HttpServerErrorException serverError = HttpServerErrorException.create(
                HttpStatus.BAD_GATEWAY, "Bad Gateway", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

        assertThat(FailureType.classify(serverError))
                .isEqualTo(FailureType.SERVER_ERROR)
                .extracting(FailureType::getDelivery)
                .isEqualTo(FailureType.Delivery.DELIVERED);
    }

    @Test
    void shouldReturnUnknownForUnrecognizedException() {
        assertThat(FailureType.classify(new IOException("something else")))
                .isEqualTo(FailureType.UNKNOWN)
                .extracting(FailureType::getDelivery)
                .isEqualTo(FailureType.Delivery.UNKNOWN);
    }

    @Test
    void shouldReturnUnknownForNull() {
        assertThat(FailureType.classify(null)).isEqualTo(FailureType.UNKNOWN);
    }

    @Test
    void shouldNotLoopOnSelfReferencingCause() {
        // getCause()가 자기 자신을 가리키는 병리적 예외에도 멈춰야 한다.
        IOException selfReferencing = new IOException("loop") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };

        assertThat(FailureType.classify(selfReferencing)).isEqualTo(FailureType.UNKNOWN);
    }
}
