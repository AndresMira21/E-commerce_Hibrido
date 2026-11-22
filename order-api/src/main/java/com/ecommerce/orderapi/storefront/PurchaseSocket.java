package com.ecommerce.orderapi.storefront;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import jakarta.servlet.http.HttpSession;

@Configuration
@EnableWebSocket
@Profile("storefront")
class PurchaseSocket implements WebSocketConfigurer {
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;

    PurchaseSocket(ObjectMapper mapper) {this.mapper = mapper;}

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new TextWebSocketHandler() {
            @Override public void afterConnectionEstablished(WebSocketSession socket) {
                var session = (HttpSession) socket.getAttributes().get("httpSession");
                var owner = owner(session);
                if (owner != null) {
                    connections.put(socket.getId(), new Connection(new ConcurrentWebSocketSessionDecorator(socket, 5000, 65536), session));
                } else close(socket);
            }

            @Override public void afterConnectionClosed(WebSocketSession socket, CloseStatus status) {
                connections.remove(socket.getId());
            }

            @Override protected void handleTextMessage(WebSocketSession socket, TextMessage message) {
                close(socket);
            }
        }, "/ws/purchases").addInterceptors(new HandshakeInterceptor() {
            @Override public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                                      WebSocketHandler handler, Map<String, Object> attributes) {
                if (!(request instanceof ServletServerHttpRequest servlet)) return false;
                var http = servlet.getServletRequest();
                var session = http.getSession(false);
                var origin = request.getHeaders().getOrigin();
                if (origin == null || origin.equals("null") || owner(session) == null) return false;
                try {
                    var local = URI.create(http.getScheme() + "://" + http.getServerName() + ":" + http.getServerPort());
                    var remote = URI.create(origin);
                    var port = remote.getPort() == -1 ? ("https".equals(remote.getScheme()) ? 443 : 80) : remote.getPort();
                    if (!local.getScheme().equals(remote.getScheme()) || !local.getHost().equalsIgnoreCase(remote.getHost())
                            || local.getPort() != port) return false;
                } catch (IllegalArgumentException invalid) { return false; }
                attributes.put("httpSession", session);
                return true;
            }
            @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                                 WebSocketHandler handler, Exception exception) {}
        });
    }

    @TransactionalEventListener
    public void publish(Update update) {
        for (var connection : connections.values()) {
            var owner = owner(connection.session());
            if (owner == null) { close(connection.socket()); continue; }
            if (!update.owner().equals(owner)) continue;
            try {
                var payload = update.purchaseId() == null ? Map.of("type", "notifications") :
                        Map.of("type", "purchase", "purchaseId", update.purchaseId());
                connection.socket().sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
            } catch (Exception failure) { close(connection.socket()); }
        }
    }

    void closeSession(HttpSession session) {
        if (session == null) return;
        for (var connection : connections.values()) {
            if (connection.session().getId().equals(session.getId())) close(connection.socket());
        }
    }

    private UUID owner(HttpSession session) {
        if (session == null) return null;
        try {
            var stored = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (stored instanceof SecurityContext context) {
                Authentication auth = context.getAuthentication();
                if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AccountController.Customer customer) {
                    return customer.id();
                }
            }
        } catch (IllegalStateException expired) { return null; }
        return null;
    }

    private void close(WebSocketSession socket) {
        connections.remove(socket.getId());
        try { socket.close(CloseStatus.POLICY_VIOLATION); }
        catch (java.io.IOException ignored) {}
    }

    private record Connection(WebSocketSession socket, HttpSession session) {}
    record Update(UUID owner, UUID purchaseId) {}
}
