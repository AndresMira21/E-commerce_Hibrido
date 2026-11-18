package com.ecommerce.hibrido.dashboard.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.SockJsServiceRegistration;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketConfigurationTest {
    @Test
    void registraEndpointSockJsYBrokerStomp() {
        // Arrange
        var configuration = new WebSocketConfiguration();
        var registry = mock(StompEndpointRegistry.class);
        var endpoint = mock(StompWebSocketEndpointRegistration.class);
        var sockJsRegistration = mock(SockJsServiceRegistration.class);
        var broker = mock(MessageBrokerRegistry.class);
        when(registry.addEndpoint("/ws")).thenReturn(endpoint);
        when(endpoint.setAllowedOriginPatterns("*")).thenReturn(endpoint);
        when(endpoint.withSockJS()).thenReturn(sockJsRegistration);

        // Act
        configuration.registerStompEndpoints(registry);
        configuration.configureMessageBroker(broker);

        // Assert
        verify(registry).addEndpoint("/ws");
        verify(endpoint).withSockJS();
        verify(broker).enableSimpleBroker("/topic");
        verify(broker).setApplicationDestinationPrefixes("/app");
    }
}
