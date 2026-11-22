package com.ecommerce.hibrido.dashboard.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfiguration implements WebSocketMessageBrokerConfigurer {
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").withSockJS();
        registry.addEndpoint("/ws/operations");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public org.springframework.messaging.Message<?> preSend(org.springframework.messaging.Message<?> message,
                    org.springframework.messaging.MessageChannel channel) {
                var header = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (header == null || header.getCommand() == null) return message;
                if (!(header.getUser() instanceof Authentication staff) || !staff.isAuthenticated()
                        || staff.getAuthorities().stream().noneMatch(role -> role.getAuthority().equals("ROLE_OPERATIONS"))) {
                    throw new AccessDeniedException("Operations role required");
                }
                if (header.getCommand() == StompCommand.SEND
                        || (header.getCommand() == StompCommand.SUBSCRIBE && !"/topic/orders".equals(header.getDestination()))) {
                    throw new AccessDeniedException("Destination not allowed");
                }
                return message;
            }
        });
    }
}
