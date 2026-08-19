package com.chatapp.websocket;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.web.socket.WebSocketHttpHeaders;
import com.chatapp.IntegrationTestBase;
import com.chatapp.auth.dto.AuthResponse;
import com.chatapp.auth.dto.RegisterRequest;
import com.chatapp.conversation.dto.ConversationResponse;
import com.chatapp.conversation.dto.StartConversationRequest;
import com.chatapp.message.dto.MessageResponse;
import com.chatapp.message.dto.SendMessageRequest;
import com.chatapp.message.entity.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the actual WebSocket/STOMP stack end-to-end: two real users
 * register over REST, open authenticated STOMP sessions, and one sends a
 * message that the other must receive in real time. This is the test that
 * proves JWT WebSocket auth and real-time delivery genuinely work, not
 * just that the classes compile.
 */
class ChatWebSocketIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(newJsonConverter());
    }

    @Test
    void twoUsers_canExchangeRealTimeMessage() throws Exception {
        AuthResponse userA = registerUser("wsuser_a_" + System.nanoTime() % 100000);
        AuthResponse userB = registerUser("wsuser_b_" + System.nanoTime() % 100000);

        ConversationResponse conversation = startConversation(userA, userB.user().id());

        StompSession sessionA = connect(userA.accessToken());
        Thread.sleep(200);
        StompSession sessionB = connect(userB.accessToken());
        Thread.sleep(200);

        BlockingQueue<MessageResponse> userBInbox = new LinkedBlockingQueue<>();
        sessionB.subscribe("/user/queue/messages", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MessageResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                userBInbox.offer((MessageResponse) payload);
            }
        });

        Thread.sleep(300); // allow subscription to register server-side

        SendMessageRequest sendRequest = new SendMessageRequest(
                conversation.id(), "Hello from A", MessageType.TEXT, null, null, null, null);
        sessionA.send("/app/chat.send", sendRequest);

        MessageResponse received = userBInbox.poll(5, TimeUnit.SECONDS);

        assertThat(received).isNotNull();
        assertThat(received.content()).isEqualTo("Hello from A");
        assertThat(received.conversationId()).isEqualTo(conversation.id());
        assertThat(received.senderId()).isEqualTo(userA.user().id());

        sessionA.disconnect();
        sessionB.disconnect();
    }

    @Test
    void connect_withoutToken_isRejected() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(newJsonConverter());

        StompSessionHandler noopHandler = new StompSessionHandlerAdapter() {
        };

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                client.connectAsync("ws://localhost:" + port + "/ws", noopHandler)
                        .get(5, TimeUnit.SECONDS)
        ).isNotNull();
    }

    /**
     * Registers JavaTimeModule so the client can deserialize MessageResponse's
     * Instant fields (createdAt/updatedAt). Without this, Jackson throws
     * during payload conversion on the incoming MESSAGE frame - and because
     * StompSessionHandlerAdapter.handleException() is a no-op by default,
     * that exception is silently swallowed: the frame is logged as
     * "Received", handleFrame() on the subscriber is never called, and the
     * test just times out waiting on an inbox that will never receive
     * anything, with no error anywhere in the logs.
     */
    private MappingJackson2MessageConverter newJsonConverter() {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.getObjectMapper().registerModule(new JavaTimeModule());
        return converter;
    }

    private StompSession connect(String accessToken) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(newJsonConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + accessToken);

        return client
                .connectAsync("ws://localhost:" + port + "/ws-raw", new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {
                            @Override
                            public void handleException(StompSession session, StompCommand command,
                                                         StompHeaders headers, byte[] payload, Throwable exception) {
                                // StompSessionHandlerAdapter swallows exceptions by default
                                // (empty method body), which is exactly what made the original
                                // deserialization failure invisible. Printing here surfaces any
                                // future conversion/handling errors immediately instead of
                                // manifesting only as a mysterious poll() timeout.
                                exception.printStackTrace();
                            }

                            @Override
                            public void handleFrame(StompHeaders headers, Object payload) {
                                // no-op: this handler is only used for the initial CONNECT;
                                // per-destination frame handling is done via the
                                // StompFrameHandler passed to subscribe().
                            }
                        })
                .get(5, TimeUnit.SECONDS);
    }

    private AuthResponse registerUser(String username) {
        RegisterRequest request = new RegisterRequest(
                username, username + "@example.com", "Password123", "Display " + username);
        return restTemplate.postForEntity("/api/auth/register", request, AuthResponse.class).getBody();
    }

    private ConversationResponse startConversation(AuthResponse asUser, java.util.UUID otherUserId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(asUser.accessToken());
        HttpEntity<StartConversationRequest> entity =
                new HttpEntity<>(new StartConversationRequest(otherUserId), headers);

        return restTemplate.exchange("/api/conversations", HttpMethod.POST, entity, ConversationResponse.class)
                .getBody();
    }
}
