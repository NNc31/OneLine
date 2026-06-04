package com.nefodov.oneline.stomp;

import com.nefodov.oneline.config.OneLineProperties;
import jakarta.servlet.http.Cookie;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
@AllArgsConstructor
public class SessionCookieHandshakeInterceptor implements HandshakeInterceptor {

    static final String SESSION_TOKEN_ATTRIBUTE = "oneline.sessionToken";

    private final OneLineProperties properties;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            Cookie[] cookies = servletRequest.getServletRequest().getCookies();
            if (cookies != null) {
                String cookieName = properties.session().cookieName();
                for (Cookie cookie : cookies) {
                    if (cookieName.equals(cookie.getName())) {
                        attributes.put(SESSION_TOKEN_ATTRIBUTE, cookie.getValue());
                        break;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.debug("WebSocket handshake completed with error", exception);
        }
    }
}
