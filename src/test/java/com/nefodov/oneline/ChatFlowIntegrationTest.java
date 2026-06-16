package com.nefodov.oneline;

import com.nefodov.oneline.ratelimit.RateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.messaging.converter.SimpleMessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class ChatFlowIntegrationTest extends AbstractWebIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_LIST = new ParameterizedTypeReference<>() {};

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @LocalServerPort
    int port;

    @Autowired
    RateLimiter rateLimiter;

    @Test
    @DisplayName("Create chat -> join -> send over STOMP -> history round-trips an opaque ciphertext blob")
    void createJoinSendHistory() throws Exception {
        CreatedChat chat = createChat();

        ResponseEntity<Map<String, Object>> joinResp = restTemplate.exchange(
                "/api/chats/" + chat.publicId() + "/join",
                HttpMethod.POST,
                new HttpEntity<>("{\"displayName\":\"Maelle\"}", jsonHeadersWithChatToken(chat.token())),
                JSON_OBJECT);
        assertEquals(HttpStatus.OK, joinResp.getStatusCode());
        long chatId = ((Number) joinResp.getBody().get("chatId")).longValue();
        String sessionToken = (String) joinResp.getBody().get("sessionToken");

        byte[] opaque = new byte[]{0x01, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 99, 99, 99, 99};
        String contentBase64 = Base64.getEncoder().encodeToString(opaque);
        String clientMessageId = UUID.randomUUID().toString();

        StompSession ws = connectStomp(chat.token(), sessionToken);
        StompHeaders sendHeaders = new StompHeaders();
        sendHeaders.setDestination("/app/chat." + chatId + ".send");
        sendHeaders.setContentType(MimeTypeUtils.APPLICATION_JSON);
        String body = String.format("{\"clientMessageId\":\"%s\",\"content\":\"%s\"}", clientMessageId, contentBase64);
        ws.send(sendHeaders, body.getBytes(StandardCharsets.UTF_8));

        List<Map<String, Object>> history = awaitHistory(chat.publicId(), chat.token(), sessionToken, 1);
        ws.disconnect();

        assertEquals(1, history.size());
        assertEquals(contentBase64, history.getFirst().get("content"));
        assertEquals("Maelle", history.getFirst().get("displayName"));
    }

    @Test
    @DisplayName("Joining with an already taken name returns 409")
    void joinWithTakenNameReturns409() {
        CreatedChat chat = createChat();

        ResponseEntity<String> first = restTemplate.exchange(
                "/api/chats/" + chat.publicId() + "/join",
                HttpMethod.POST,
                new HttpEntity<>("{\"displayName\":\"Maelle\"}", jsonHeadersWithChatToken(chat.token())),
                String.class);
        assertEquals(HttpStatus.OK, first.getStatusCode());

        ResponseEntity<String> second = restTemplate.exchange(
                "/api/chats/" + chat.publicId() + "/join",
                HttpMethod.POST,
                new HttpEntity<>("{\"displayName\":\"Maelle\"}", jsonHeadersWithChatToken(chat.token())),
                String.class);
        assertEquals(HttpStatus.CONFLICT, second.getStatusCode());
    }

    @Test
    @DisplayName("History without a valid session returns 401")
    void historyWithoutSessionIsUnauthorized() {
        CreatedChat chat = createChat();

        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/chats/" + chat.publicId() + "/messages",
                HttpMethod.GET,
                new HttpEntity<>(null, jsonHeadersWithChatToken(chat.token())),
                String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    @DisplayName("Join with a wrong chat token returns 404")
    void joinWithWrongTokenReturns404() {
        CreatedChat chat = createChat();

        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/chats/" + chat.publicId() + "/join",
                HttpMethod.POST,
                new HttpEntity<>("{\"displayName\":\"Maelle\"}", jsonHeadersWithChatToken("wrong-token")),
                String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    @DisplayName("Message rate limiter rejects a burst beyond bucket capacity")
    void messageRateLimiterRejectsBurst() {
        String key = "test-" + UUID.randomUUID();
        int allowed = 0;
        for (int i = 0; i < 40; i++) {
            if (rateLimiter.tryAcquire("message", key)) {
                allowed++;
            }
        }
        assertTrue(allowed >= 25);
        assertTrue(allowed < 40);
    }

    private CreatedChat createChat() {
        String authToken = "tok-" + UUID.randomUUID();
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                "/api/chats",
                HttpMethod.POST,
                new HttpEntity<>("{\"authToken\":\"" + authToken + "\"}", jsonHeaders()),
                JSON_OBJECT);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        String publicId = (String) resp.getBody().get("publicId");
        assertNotNull(publicId);
        return new CreatedChat(publicId, authToken);
    }

    private StompSession connectStomp(String chatToken, String sessionToken) throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new SimpleMessageConverter());
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add(CHAT_TOKEN_HEADER, chatToken);
        connectHeaders.add(SESSION_TOKEN_HEADER, sessionToken);
        return stompClient.connectAsync("ws://localhost:" + port + "/ws", handshakeHeaders, connectHeaders, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
    }

    private List<Map<String, Object>> awaitHistory(String publicId, String chatToken, String sessionToken, int expectedSize) {
        AtomicReference<List<Map<String, Object>>> last = new AtomicReference<>(List.of());
        await().atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> {
                    ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                            "/api/chats/" + publicId + "/messages",
                            HttpMethod.GET,
                            new HttpEntity<>(null, jsonHeadersWithChatTokenAndSession(chatToken, sessionToken)),
                            JSON_LIST);
                    last.set(resp.getBody() == null ? List.of() : resp.getBody());
                    return last.get().size() >= expectedSize;
                });
        return last.get();
    }

    private record CreatedChat(String publicId, String token) {
    }
}
