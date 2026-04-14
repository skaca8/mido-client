package io.github.hyunjun.mido.sample;

import io.github.hyunjun.mido.api.BaseExternalApi;
import io.github.hyunjun.mido.config.MidoClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 인증 서비스 샘플 구현
 * 단일 엔드포인트를 사용하는 간단한 인증 API 예제
 */
@Service
public class AuthService extends BaseExternalApi {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final MidoClientFactory midoClientFactory;

    public AuthService(MidoClientFactory midoClientFactory) {
        this.midoClientFactory = midoClientFactory;
    }

    @Override
    protected String getChannelName() {
        return "auth";
    }

    /**
     * 토큰 검증
     */
    public TokenValidationResult validateToken(String token) {
        return withDefaultChannelAction("validateToken", () -> {
            RestClient client = midoClientFactory.getOrCreateClient("auth");

            return client.post()
                    .uri("/auth/validate")
                    .body(new TokenValidationRequest(token))
                    .retrieve()
                    .body(TokenValidationResult.class);
        });
    }

    /**
     * 사용자 로그인
     */
    public LoginResult login(String username, String password) {
        return withDefaultChannelAction("login", () -> {
            RestClient client = midoClientFactory.getOrCreateClient("auth");

            LoginRequest request = new LoginRequest();
            request.setUsername(username);
            request.setPassword(password);

            return client.post()
                    .uri("/auth/login")
                    .body(request)
                    .retrieve()
                    .body(LoginResult.class);
        });
    }

    /**
     * 토큰 갱신
     */
    public RefreshTokenResult refreshToken(String refreshToken) {
        return withDefaultChannelAction("refreshToken", () -> {
            RestClient client = midoClientFactory.getOrCreateClient("auth");

            return client.post()
                    .uri("/auth/refresh")
                    .body(new RefreshTokenRequest(refreshToken))
                    .retrieve()
                    .body(RefreshTokenResult.class);
        });
    }

    /**
     * 사용자 정보 조회
     */
    public UserInfo getUserInfo(String userId) {
        return withDefaultChannelAction("getUserInfo", () -> {
            RestClient client = midoClientFactory.getOrCreateClient("auth");

            return client.get()
                    .uri("/auth/users/{userId}", userId)
                    .retrieve()
                    .body(UserInfo.class);
        });
    }

    // DTO 클래스들
    public static class TokenValidationRequest {
        private String token;

        public TokenValidationRequest() {}
        public TokenValidationRequest(String token) { this.token = token; }

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }

    public static class TokenValidationResult {
        private boolean valid;
        private String userId;
        private long expiresIn;

        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public long getExpiresIn() { return expiresIn; }
        public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }
    }

    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class LoginResult {
        private boolean success;
        private String accessToken;
        private String refreshToken;
        private UserInfo userInfo;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
        public UserInfo getUserInfo() { return userInfo; }
        public void setUserInfo(UserInfo userInfo) { this.userInfo = userInfo; }
    }

    public static class RefreshTokenRequest {
        private String refreshToken;

        public RefreshTokenRequest() {}
        public RefreshTokenRequest(String refreshToken) { this.refreshToken = refreshToken; }

        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    }

    public static class RefreshTokenResult {
        private boolean success;
        private String accessToken;
        private String newRefreshToken;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
        public String getNewRefreshToken() { return newRefreshToken; }
        public void setNewRefreshToken(String newRefreshToken) { this.newRefreshToken = newRefreshToken; }
    }

    public static class UserInfo {
        private String userId;
        private String username;
        private String email;
        private String role;

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }
}