package com.nefodov.oneline.attachment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class AttachmentFlowIntegrationTest {

    private static final String CHAT_TOKEN_HEADER = "X-Chat-Token";
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {};

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Container
    static MinIOContainer MINIO = new MinIOContainer("minio/minio");

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("oneline.storage.endpoint", MINIO::getS3URL);
        registry.add("oneline.storage.public-endpoint", MINIO::getS3URL);
        registry.add("oneline.storage.access-key", MINIO::getUserName);
        registry.add("oneline.storage.secret-key", MINIO::getPassword);
    }

    @Autowired
    TestRestTemplate restTemplate;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    @DisplayName("Prepare with presigned PUT and confirm -> presigned GET round-trips ciphertext")
    void uploadAndDownloadRoundTrip() throws Exception {
        Joined chat = createAndJoin();
        byte[] blob = randomBytes(4096);

        Map<String, Object> prepared = prepareUpload(chat, blob.length);
        long attachmentId = ((Number) prepared.get("attachmentId")).longValue();
        String uploadUrl = (String) prepared.get("uploadUrl");

        HttpResponse<Void> put = http.send(
                HttpRequest.newBuilder(URI.create(uploadUrl)).PUT(HttpRequest.BodyPublishers.ofByteArray(blob)).build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(200, put.statusCode());

        ResponseEntity<String> confirm = restTemplate.exchange(
                "/api/chats/" + chat.publicId() + "/attachments/" + attachmentId + "/confirm",
                HttpMethod.POST,
                new HttpEntity<>(null, authedHeaders(chat)),
                String.class);
        assertEquals(HttpStatus.OK, confirm.getStatusCode());

        ResponseEntity<Map<String, Object>> download = restTemplate.exchange(
                "/api/chats/" + chat.publicId() + "/attachments/" + attachmentId,
                HttpMethod.GET,
                new HttpEntity<>(null, authedHeaders(chat)),
                JSON_OBJECT);
        assertEquals(HttpStatus.OK, download.getStatusCode());
        String downloadUrl = (String) download.getBody().get("downloadUrl");

        HttpResponse<byte[]> fetched = http.send(
                HttpRequest.newBuilder(URI.create(downloadUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, fetched.statusCode());
        assertArrayEquals(blob, fetched.body());
    }

    @Test
    @DisplayName("An attachment cannot be downloaded through a different chat session")
    void attachmentIsScopedToItsChat() {
        Joined chatA = createAndJoin();
        Joined chatB = createAndJoin();

        Map<String, Object> prepared = prepareUpload(chatA, 128);
        long attachmentId = ((Number) prepared.get("attachmentId")).longValue();

        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/chats/" + chatB.publicId() + "/attachments/" + attachmentId,
                HttpMethod.GET,
                new HttpEntity<>(null, authedHeaders(chatB)),
                String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    @DisplayName("Confirming before any upload returns 404")
    void confirmWithoutUploadReturns404() {
        Joined chat = createAndJoin();
        Map<String, Object> prepared = prepareUpload(chat, 256);
        long attachmentId = ((Number) prepared.get("attachmentId")).longValue();

        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/chats/" + chat.publicId() + "/attachments/" + attachmentId + "/confirm",
                HttpMethod.POST,
                new HttpEntity<>(null, authedHeaders(chat)),
                String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    private Map<String, Object> prepareUpload(Joined chat, int size) {
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                "/api/chats/" + chat.publicId() + "/attachments",
                HttpMethod.POST,
                new HttpEntity<>("{\"size\":" + size + "}", authedHeaders(chat)),
                JSON_OBJECT);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        return resp.getBody();
    }

    private Joined createAndJoin() {
        String authToken = "tok-" + UUID.randomUUID();
        ResponseEntity<Map<String, Object>> created = restTemplate.exchange(
                "/api/chats",
                HttpMethod.POST,
                new HttpEntity<>("{\"authToken\":\"" + authToken + "\"}", jsonHeaders()),
                JSON_OBJECT);
        assertEquals(HttpStatus.OK, created.getStatusCode());
        String publicId = (String) created.getBody().get("publicId");

        HttpHeaders joinHeaders = jsonHeaders();
        joinHeaders.add(CHAT_TOKEN_HEADER, authToken);
        ResponseEntity<Map<String, Object>> joined = restTemplate.exchange(
                "/api/chats/" + publicId + "/join",
                HttpMethod.POST,
                new HttpEntity<>("{\"displayName\":\"Maelle\"}", joinHeaders),
                JSON_OBJECT);
        assertEquals(HttpStatus.OK, joined.getStatusCode());
        String cookie = extractCookie(joined.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
        return new Joined(publicId, authToken, cookie);
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private static HttpHeaders authedHeaders(Joined chat) {
        HttpHeaders headers = jsonHeaders();
        headers.add(CHAT_TOKEN_HEADER, chat.token());
        headers.add(HttpHeaders.COOKIE, chat.cookie());
        return headers;
    }

    private static String extractCookie(String setCookieHeader) {
        int semi = setCookieHeader.indexOf(';');
        return semi < 0 ? setCookieHeader : setCookieHeader.substring(0, semi);
    }

    private static byte[] randomBytes(int len) {
        byte[] bytes = new byte[len];
        ThreadLocalRandom.current().nextBytes(bytes);
        return bytes;
    }

    private record Joined(String publicId, String token, String cookie) {
    }
}
