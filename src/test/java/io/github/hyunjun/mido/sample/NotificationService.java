package io.github.hyunjun.mido.sample;

import io.github.hyunjun.mido.api.BaseExternalApi;
import io.github.hyunjun.mido.config.MidoClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 알림 서비스 샘플 구현
 * Slack, 이메일, SMS 등의 알림을 전송하는 예제
 */
@Service
public class NotificationService extends BaseExternalApi {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final MidoClientFactory midoClientFactory;

    public NotificationService(MidoClientFactory midoClientFactory) {
        this.midoClientFactory = midoClientFactory;
    }

    @Override
    protected String getChannelName() {
        return "notification";
    }

    /**
     * Slack 메시지 전송
     * 로그 레벨을 OFF로 설정하여 민감한 웹훅 호출은 로깅하지 않음
     */
    public void sendSlackMessage(String message) {
        withDefaultChannelAction("sendSlackMessage", () -> {
            RestClient client = midoClientFactory.getOrCreateClient("notification");

            SlackMessage slackMessage = new SlackMessage();
            slackMessage.setText(message);
            slackMessage.setUsername("Mido Client Bot");
            slackMessage.setIconEmoji(":robot_face:");

            client.post()
                    .uri("")  // URL은 이미 완전한 웹훅 URL
                    .body(slackMessage)
                    .retrieve()
                    .toBodilessEntity();
        });
    }

    /**
     * 이메일 알림 전송
     */
    public EmailSendResult sendEmail(EmailRequest emailRequest) {
        return withDefaultChannelAction("sendEmail", () -> {
            RestClient client = midoClientFactory.getOrCreateClient("notification");

            return client.post()
                    .uri("/email/send")
                    .body(emailRequest)
                    .retrieve()
                    .body(EmailSendResult.class);
        });
    }

    /**
     * SMS 알림 전송
     */
    public SmsSendResult sendSms(SmsRequest smsRequest) {
        return withDefaultChannelAction("sendSms", () -> {
            RestClient client = midoClientFactory.getOrCreateClient("notification");

            return client.post()
                    .uri("/sms/send")
                    .body(smsRequest)
                    .retrieve()
                    .body(SmsSendResult.class);
        });
    }

    /**
     * 푸시 알림 전송
     */
    public PushSendResult sendPushNotification(PushRequest pushRequest) {
        return withDefaultChannelAction("sendPushNotification", () -> {
            RestClient client = midoClientFactory.getOrCreateClient("notification");

            return client.post()
                    .uri("/push/send")
                    .body(pushRequest)
                    .retrieve()
                    .body(PushSendResult.class);
        });
    }

    // DTO 클래스들
    public static class SlackMessage {
        private String text;
        private String username;
        private String iconEmoji;
        private String channel;

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getIconEmoji() { return iconEmoji; }
        public void setIconEmoji(String iconEmoji) { this.iconEmoji = iconEmoji; }
        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }
    }

    public static class EmailRequest {
        private String to;
        private String subject;
        private String body;
        private String from;

        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
    }

    public static class EmailSendResult {
        private boolean success;
        private String messageId;
        private String error;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessageId() { return messageId; }
        public void setMessageId(String messageId) { this.messageId = messageId; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }

    public static class SmsRequest {
        private String to;
        private String message;
        private String from;

        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
    }

    public static class SmsSendResult {
        private boolean success;
        private String messageId;
        private String error;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessageId() { return messageId; }
        public void setMessageId(String messageId) { this.messageId = messageId; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }

    public static class PushRequest {
        private String deviceToken;
        private String title;
        private String body;
        private java.util.Map<String, String> data;

        public String getDeviceToken() { return deviceToken; }
        public void setDeviceToken(String deviceToken) { this.deviceToken = deviceToken; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        public java.util.Map<String, String> getData() { return data; }
        public void setData(java.util.Map<String, String> data) { this.data = data; }
    }

    public static class PushSendResult {
        private boolean success;
        private String messageId;
        private String error;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessageId() { return messageId; }
        public void setMessageId(String messageId) { this.messageId = messageId; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}