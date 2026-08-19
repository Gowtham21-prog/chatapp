package com.chatapp.anonymous;

import com.chatapp.IntegrationTestBase;
import com.chatapp.anonymous.dto.AnonymousSessionResponse;
import com.chatapp.anonymous.dto.CreateAnonymousSessionRequest;
import com.chatapp.anonymous.dto.MatchResultResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AnonymousMatchmakingIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void configureRestTemplate() {
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    @Test
    void twoSessions_withNoOneElseWaiting_firstWaitsSecondMatches() {
        AnonymousSessionResponse sessionA = createSession(Set.of("music", "gaming"));
        AnonymousSessionResponse sessionB = createSession(Set.of("gaming", "movies"));
        MatchResultResponse resultA = requestMatch(sessionA.accessToken());
        assertThat(resultA.status()).isEqualTo("WAITING");
        MatchResultResponse resultB = requestMatch(sessionB.accessToken());
        assertThat(resultB.status()).isEqualTo("MATCHED");
        assertThat(resultB.partnerSessionId()).isEqualTo(sessionA.sessionId());
        assertThat(resultB.sharedInterests()).contains("gaming");
        MatchResultResponse pollA = pollMatch(sessionA.accessToken());
        assertThat(pollA.status()).isEqualTo("MATCHED");
        assertThat(pollA.partnerSessionId()).isEqualTo(sessionB.sessionId());
    }

    @Test
    void matchedPair_next_leavesRoomAndReturnsToWaiting() {
        AnonymousSessionResponse sessionA = createSession(Set.of());
        AnonymousSessionResponse sessionB = createSession(Set.of());
        requestMatch(sessionA.accessToken());
        MatchResultResponse matched = requestMatch(sessionB.accessToken());
        assertThat(matched.status()).isEqualTo("MATCHED");
        MatchResultResponse afterNext = next(sessionA.accessToken());
        assertThat(afterNext.status()).isEqualTo("WAITING");
    }

    @Test
    void invalidToken_isRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Anonymous-Token", "not-a-real-token");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/anonymous/match", HttpMethod.POST, entity, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private AnonymousSessionResponse createSession(Set<String> interests) {
        CreateAnonymousSessionRequest request = new CreateAnonymousSessionRequest(interests);
        return restTemplate.postForEntity("/api/anonymous/session", request, AnonymousSessionResponse.class).getBody();
    }

    private MatchResultResponse requestMatch(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Anonymous-Token", token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange("/api/anonymous/match", HttpMethod.POST, entity, MatchResultResponse.class).getBody();
    }

    private MatchResultResponse pollMatch(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Anonymous-Token", token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange("/api/anonymous/match", HttpMethod.GET, entity, MatchResultResponse.class).getBody();
    }

    private MatchResultResponse next(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Anonymous-Token", token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange("/api/anonymous/next", HttpMethod.POST, entity, MatchResultResponse.class).getBody();
    }
}
