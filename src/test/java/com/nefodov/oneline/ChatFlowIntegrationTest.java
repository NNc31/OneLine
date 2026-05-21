package com.nefodov.oneline;

import com.nefodov.oneline.support.RateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class ChatFlowIntegrationTest {

    private static final String CHAT_TOKEN_HEADER = "X-Chat-Token";

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_LIST = new ParameterizedTypeReference<>() {};

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    RateLimiter rateLimiter;

    @Test
    @DisplayName("Create chat -> join -> send over STOMP -> history round-trips an opaque ciphertext blob")
    void createJoinSendHistory() throws Exception {
        CreatedChat chat = createChat();

        ResponseEntity<Map<String, Object>> joinResp = restTemplate.exchange(
                "/api/chats/" + chat.publicId() + "/join",
                HttpMethod.POST,
                new HttpEntity<>("{\"displayName\":\"Maelle\"}", jsonHeadersWithToken(chat.token())),
                JSON_OBJECT);
        assertEquals(HttpStatus.OK, joinResp.getStatusCode());
        long chatId = ((Number) joinResp.getBody().get("chatId")).longValue();
        String sessionCookie = extractCookie(joinResp.getHeaders().getFirst(HttpHeaders.SET_COOKIE));

        byte[] opaque = new byte[]{0x01, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 99, 99, 99, 99};
        String contentBase64 = Base64.getEncoder().encodeToString(opaque);
        String clientMessageId = UUID.randomUUID().toString();

        StompSession ws = connectStomp(chat.token(), sessionCookie);
        StompHeaders sendHeaders = new StompHeaders();
        sendHeaders.setDestination("/app/chat." + chatId + ".send");
        sendHeaders.setContentType(MimeTypeUtils.APPLICATION_JSON);
        String body = String.format("{\"clientMessageId\":\"%s\",\"content\":\"%s\"}", clientMessageId, contentBase64);
        ws.send(sendHeaders, body.getBytes(StandardCharsets.UTF_8));

        List<Map<String, Object>> history = awaitHistory(chat.publicId(), chat.token(), sessionCookie, 1);
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
                new HttpEntity<>("{\"displayName\":\"Maelle\"}", jsonHeadersWithToken(chat.token())),
                String.class);
        assertEquals(HttpStatus.OK, first.getStatusCode());

        ResponseEntity<String> second = restTemplate.exchange(
                "/api/chats/" + chat.publicId() + "/join",
                HttpMethod.POST,
                new HttpEntity<>("{\"displayName\":\"Maelle\"}", jsonHeadersWithToken(chat.token())),
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
                new HttpEntity<>(null, jsonHeadersWithToken(chat.token())),
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
                new HttpEntity<>("{\"displayName\":\"Maelle\"}", jsonHeadersWithToken("wrong-token")),
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

    private StompSession connectStomp(String chatToken, String sessionCookie) throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new SimpleMessageConverter());
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.add(HttpHeaders.COOKIE, sessionCookie);
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add(CHAT_TOKEN_HEADER, chatToken);
        return stompClient.connectAsync("ws://localhost:" + port + "/ws", handshakeHeaders, connectHeaders, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
    }

    private List<Map<String, Object>> awaitHistory(String publicId, String chatToken, String cookie, int expectedSize) throws InterruptedException {
        List<Map<String, Object>> history = List.of();
        for (int attempt = 0; attempt < 30; attempt++) {
            ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                    "/api/chats/" + publicId + "/messages",
                    HttpMethod.GET,
                    new HttpEntity<>(null, jsonHeadersWithTokenAndCookie(chatToken, cookie)),
                    JSON_LIST);
            history = resp.getBody() == null ? List.of() : resp.getBody();
            if (history.size() >= expectedSize) {
                return history;
            }
            Thread.sleep(100);
        }
        return history;
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private static HttpHeaders jsonHeadersWithToken(String chatToken) {
        HttpHeaders headers = jsonHeaders();
        headers.add(CHAT_TOKEN_HEADER, chatToken);
        return headers;
    }

    private static HttpHeaders jsonHeadersWithTokenAndCookie(String chatToken, String cookie) {
        HttpHeaders headers = jsonHeadersWithToken(chatToken);
        headers.add(HttpHeaders.COOKIE, cookie);
        return headers;
    }

    private static String extractCookie(String setCookieHeader) {
        int semi = setCookieHeader.indexOf(';');
        return semi < 0 ? setCookieHeader : setCookieHeader.substring(0, semi);
    }

    private record CreatedChat(String publicId, String token) {
    }
}
