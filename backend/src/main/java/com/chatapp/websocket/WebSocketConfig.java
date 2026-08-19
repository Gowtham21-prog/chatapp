package com.chatapp.websocket;

import com.chatapp.config.CorsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * We use STOMP over a plain SockJS/WebSocket endpoint with a simple
 * in-memory broker rather than an external broker (RabbitMQ/ActiveMQ).
 * That's a deliberate scope decision: a simple broker is enough for a
 * single backend instance; horizontally scaling this app would mean
 * switching to a STOMP relay (e.g. RabbitMQ) so messages fan out across
 * instances - flagged in the README as a known scaling boundary.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final CorsProperties corsProperties;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(new String[0]))
                .withSockJS();

        // Plain WebSocket endpoint (no SockJS fallback) for clients that
        // don't need IE-era HTTP long-polling fallbacks, e.g. native/mobile.
        // Must be a DISTINCT path from the SockJS endpoint above - registering
        // the same "/ws" path twice causes Spring's WebSocket handler mapping
        // to be ambiguous between the two registrations, which can lead to a
        // connection being accepted by one handler chain while a different
        // (non-interceptor-wired) chain handles it, silently dropping the
        // authenticated Principal set by StompAuthChannelInterceptor.
        registry.addEndpoint("/ws-raw")
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(new String[0]));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // /topic for broadcast-style destinations (presence, typing to a conversation room),
        // /queue for user-specific destinations (delivered via convertAndSendToUser).
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(128 * 1024); // 128KB - text frames only; files go via presigned S3 upload
        registration.setSendTimeLimit(15_000);
        registration.setSendBufferSizeLimit(512 * 1024);
    }

    /**
     * Without registering this, @Valid on @Payload arguments in
     * ChatWebSocketController is silently ignored (unlike @RequestBody in
     * Spring MVC, STOMP does not wire up Bean Validation automatically) -
     * this makes SendMessageRequest's validation constraints actually
     * enforced over the WebSocket path, not just the REST path.
     */
    public Validator getValidator() {
        return new LocalValidatorFactoryBean();
    }
}
