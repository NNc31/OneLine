package com.nefodov.oneline.config;

import com.nefodov.oneline.stomp.OneLineStompErrorHandler;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.messaging.StompSubProtocolHandler;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;

@Component
@AllArgsConstructor
public class StompErrorHandlerInstaller {

    private final ApplicationContext applicationContext;
    private final OneLineStompErrorHandler errorHandler;

    @EventListener(ApplicationReadyEvent.class)
    public void install() {
        WebSocketHandler handler = applicationContext.getBean("subProtocolWebSocketHandler", WebSocketHandler.class);
        if (handler instanceof SubProtocolWebSocketHandler dispatcher) {
            for (var protocolHandler : dispatcher.getProtocolHandlers()) {
                if (protocolHandler instanceof StompSubProtocolHandler stomp) {
                    stomp.setErrorHandler(errorHandler);
                }
            }
        }
    }
}
