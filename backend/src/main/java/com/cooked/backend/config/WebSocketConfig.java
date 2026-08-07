package com.cooked.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // Allowed origins for WebSocket / SockJS connections.
    // Wildcard '*' is NOT allowed when SockJS sends withCredentials=true.
    private static final String[] ALLOWED_ORIGINS = {
        "https://www.cookedapp.com",
        "https://cookedapp.com",
        "http://localhost:3000",
        "http://localhost:5173",
        "http://localhost:8080",
        "http://127.0.0.1:3000",
        "http://127.0.0.1:5173"
    };

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Clients subscribe to topics under /topic
        config.enableSimpleBroker("/topic");
        // Messages sent from client go to /app prefix
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // SockJS fallback endpoint - frontend connects here
        registry.addEndpoint("/ws")
                .setAllowedOrigins(ALLOWED_ORIGINS)
                .withSockJS()
                .setSessionCookieNeeded(false); // Prevents SockJS from requiring session cookies
    }
}
