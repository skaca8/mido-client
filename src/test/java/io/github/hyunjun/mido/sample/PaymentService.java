package io.github.hyunjun.mido.sample;

import io.github.hyunjun.mido.api.BaseExternalApi;
import io.github.hyunjun.mido.config.MidoClientFactory;
import io.github.hyunjun.mido.constant.EndpointType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 결제 서비스 샘플 구현
 * Mido Client를 사용하여 결제 API와 통신하는 예제
 */
@Service
public class PaymentService extends BaseExternalApi {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final MidoClientFactory midoClientFactory;

    public PaymentService(MidoClientFactory midoClientFactory) {
        this.midoClientFactory = midoClientFactory;
    }

    @Override
    protected String getChannelName() {
        return "payment";
    }

    /**
     * 결제 상태 조회 (first endpoint 사용)
     */
    public PaymentStatus getPaymentStatus(String paymentId) {
        return withDefaultChannelAction("getPaymentStatus", () -> {
            RestClient client = midoClientFactory.getOrCreateClient("payment");

            return client.get()
                    .uri("/payments/{paymentId}/status", paymentId)
                    .retrieve()
                    .body(PaymentStatus.class);
        });
    }

    /**
     * 결제 처리 (second endpoint 사용 - 결제 처리 전용 서버)
     */
    public PaymentResult processPayment(PaymentRequest request) {
        return withDefaultChannelAction("processPayment", () -> {
            RestClient client = midoClientFactory.getOrCreateClient("payment", EndpointType.SECOND);

            return client.post()
                    .uri("/payments/process")
                    .body(request)
                    .retrieve()
                    .body(PaymentResult.class);
        });
    }

    /**
     * 결제 취소
     */
    public CancellationResult cancelPayment(String paymentId, String reason) {
        return withDefaultChannelAction("cancelPayment", () -> {
            RestClient client = midoClientFactory.getOrCreateClient("payment");

            CancellationRequest cancellationRequest = CancellationRequest.builder()
                    .paymentId(paymentId)
                    .reason(reason)
                    .build();

            return client.post()
                    .uri("/payments/{paymentId}/cancel", paymentId)
                    .body(cancellationRequest)
                    .retrieve()
                    .body(CancellationResult.class);
        });
    }

    /**
     * 결제 리스트 조회
     */
    public PaymentListResponse getPaymentList(PaymentSearchRequest searchRequest) {
        return withDefaultChannelAction("getPaymentList", () -> {
            RestClient client = midoClientFactory.getOrCreateClient("payment");

            return client.get()
                    .uri(uriBuilder -> uriBuilder.path("/payments")
                            .queryParam("userId", searchRequest.getUserId())
                            .queryParam("startDate", searchRequest.getStartDate())
                            .queryParam("endDate", searchRequest.getEndDate())
                            .queryParam("status", searchRequest.getStatus())
                            .build())
                    .retrieve()
                    .body(PaymentListResponse.class);
        });
    }

    // DTO 클래스들
    public static class PaymentStatus {
        private String paymentId;
        private String status;
        private String amount;

        // getters and setters
        public String getPaymentId() { return paymentId; }
        public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getAmount() { return amount; }
        public void setAmount(String amount) { this.amount = amount; }
    }

    public static class PaymentRequest {
        private String userId;
        private String amount;
        private String currency;
        private String cardNumber;

        // getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getAmount() { return amount; }
        public void setAmount(String amount) { this.amount = amount; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public String getCardNumber() { return cardNumber; }
        public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
    }

    public static class PaymentResult {
        private String paymentId;
        private boolean success;
        private String transactionId;

        // getters and setters
        public String getPaymentId() { return paymentId; }
        public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    }

    public static class CancellationRequest {
        private String paymentId;
        private String reason;

        public CancellationRequest() {}

        public static Builder builder() {
            return new Builder();
        }

        public String getPaymentId() { return paymentId; }
        public String getReason() { return reason; }

        public static class Builder {
            private String paymentId;
            private String reason;

            public Builder paymentId(String paymentId) {
                this.paymentId = paymentId;
                return this;
            }

            public Builder reason(String reason) {
                this.reason = reason;
                return this;
            }

            public CancellationRequest build() {
                CancellationRequest request = new CancellationRequest();
                request.paymentId = this.paymentId;
                request.reason = this.reason;
                return request;
            }
        }
    }

    public static class CancellationResult {
        private boolean success;
        private String cancellationId;

        // getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getCancellationId() { return cancellationId; }
        public void setCancellationId(String cancellationId) { this.cancellationId = cancellationId; }
    }

    public static class PaymentSearchRequest {
        private String userId;
        private String startDate;
        private String endDate;
        private String status;

        // getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }
        public String getEndDate() { return endDate; }
        public void setEndDate(String endDate) { this.endDate = endDate; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class PaymentListResponse {
        private java.util.List<PaymentStatus> payments;
        private int totalCount;

        // getters and setters
        public java.util.List<PaymentStatus> getPayments() { return payments; }
        public void setPayments(java.util.List<PaymentStatus> payments) { this.payments = payments; }
        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
    }
}