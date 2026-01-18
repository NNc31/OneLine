package com.nefodov.oneline.messaging;

import com.nefodov.oneline.chat.ChatParticipantService;
import com.nefodov.oneline.chat.ChatSession;
import com.nefodov.oneline.security.MagicLinkAuthentication;
import lombok.AllArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.AbstractSubProtocolEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
@AllArgsConstructor
public class ChatSessionLifecycleListener {

    private final ChatParticipantService participantService;

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        touchPrincipal(event);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        touchPrincipal(event);
    }

    private void touchPrincipal(AbstractSubProtocolEvent event) {
        Principal user = event.getUser();
        if (user instanceof MagicLinkAuthentication auth) {
            ChatSession session = auth.session();
            if (session != null && session.participant() != null) {
                participantService.touch(session.participant());
            }
        }
    }
}
