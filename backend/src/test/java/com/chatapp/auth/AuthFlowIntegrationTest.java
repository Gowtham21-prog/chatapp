package com.chatapp.auth;

import com.chatapp.IntegrationTestBase;
import com.chatapp.auth.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import static org.assertj.core.api.Assertions.assertThat;

class AuthFlowIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private RegisterRequest validRegisterRequest;

    @BeforeEach
    void configureRestTemplate() {
    restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    @BeforeEach
    void setUp() {
        validRegisterRequest = new RegisterRequest(
                "testuser_" + System.nanoTime() % 1_000_000,
                "user" + System.nanoTime() % 1_000_000 + "@example.com",
                "Password123",
                "Test User"
        );
    }

    @BeforeEach
    void resetRateLimiter() {
        var keys = redisTemplate.keys("ratelimit:auth:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void register_withValidData_createsUserAndReturnsTokens() {
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/api/auth/register", validRegisterRequest, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().refreshToken()).isNotBlank();
        assertThat(response.getBody().user().username())
                .isEqualTo(validRegisterRequest.username());
    }

    @Test
    void register_withDuplicateUsername_returnsConflict() {
        restTemplate.postForEntity(
                "/api/auth/register",
                validRegisterRequest,
                AuthResponse.class);

        RegisterRequest duplicate = new RegisterRequest(
                validRegisterRequest.username(),
                "different" + System.nanoTime() + "@example.com",
                "Password123",
                "Someone Else"
        );

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/register",
                duplicate,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void register_withWeakPassword_returnsBadRequestWithFieldErrors() {
        RegisterRequest weak = new RegisterRequest(
                "weakpwuser" + System.nanoTime() % 100000,
                "weak" + System.nanoTime() + "@example.com",
                "weak",
                "Weak User"
        );

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/register",
                weak,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("fieldErrors");
    }

    @Test
    void login_withCorrectCredentials_returnsTokens() {
        restTemplate.postForEntity(
                "/api/auth/register",
                validRegisterRequest,
                AuthResponse.class);

        LoginRequest login = new LoginRequest(
                validRegisterRequest.username(),
                validRegisterRequest.password());

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/api/auth/login",
                login,
                AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().accessToken()).isNotBlank();
    }

    @Test
    void login_withWrongPassword_returnsUnauthorized() {
        restTemplate.postForEntity(
                "/api/auth/register",
                validRegisterRequest,
                AuthResponse.class);

        LoginRequest login = new LoginRequest(
                validRegisterRequest.username(),
                "WrongPassword1");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/login",
                login,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refresh_withValidToken_rotatesAndReturnsNewTokens() {
        ResponseEntity<AuthResponse> registerResponse = restTemplate.postForEntity(
                "/api/auth/register",
                validRegisterRequest,
                AuthResponse.class);

        String originalRefreshToken = registerResponse.getBody().refreshToken();

        RefreshRequest refreshRequest = new RefreshRequest(originalRefreshToken);

        ResponseEntity<AuthResponse> refreshResponse = restTemplate.postForEntity(
                "/api/auth/refresh",
                refreshRequest,
                AuthResponse.class);

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshResponse.getBody().refreshToken())
                .isNotEqualTo(originalRefreshToken);

        // Reusing the old (now-revoked) refresh token must fail.
        ResponseEntity<String> reuseResponse = restTemplate.postForEntity(
                "/api/auth/refresh",
                refreshRequest,
                String.class);

        assertThat(reuseResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedEndpoint_withoutToken_returnsUnauthorized() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/users/me",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
