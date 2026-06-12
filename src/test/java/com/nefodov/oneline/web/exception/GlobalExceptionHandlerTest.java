package com.nefodov.oneline.web.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("NotFoundException is 404 with the message in the body")
    void handlesNotFound() {
        ResponseEntity<Map<String, String>> resp = handler.handleNotFound(new NotFoundException("nope"));
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertEquals("nope", bodyError(resp));
    }

    @Test
    @DisplayName("ConflictException is 409")
    void handlesConflict() {
        ResponseEntity<Map<String, String>> resp = handler.handleConflict(new ConflictException("taken"));
        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        assertEquals("taken", bodyError(resp));
    }

    @Test
    @DisplayName("IllegalArgumentException is 400")
    void handlesBadRequest() {
        ResponseEntity<Map<String, String>> resp = handler.handleBadRequest(new IllegalArgumentException("bad input"));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("bad input", bodyError(resp));
    }

    @Test
    @DisplayName("TooManyRequestsException is 429")
    void handlesTooManyRequests() {
        ResponseEntity<Map<String, String>> resp = handler.handleTooManyRequests(new TooManyRequestsException("slow down"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, resp.getStatusCode());
        assertEquals("slow down", bodyError(resp));
    }

    @Test
    @DisplayName("StorageException is 503 with a generic message")
    void handlesStorage() {
        ResponseEntity<Map<String, String>> resp = handler.handleStorage(new StorageException("inner detail"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, resp.getStatusCode());
        assertEquals("Attachment storage is temporarily unavailable", bodyError(resp));
    }

    @Test
    @DisplayName("Validation errors report the first field error")
    void handlesValidationFirstFieldError() {
        BindingResult bindingResult = Mockito.mock(BindingResult.class);
        FieldError fieldError = new FieldError("obj", "displayName", "must not be blank");
        Mockito.when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        MethodArgumentNotValidException ex = Mockito.mock(MethodArgumentNotValidException.class);
        Mockito.when(ex.getBindingResult()).thenReturn(bindingResult);
        ResponseEntity<Map<String, String>> resp = handler.handleValidation(ex);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("displayName must not be blank", bodyError(resp));
    }

    @Test
    @DisplayName("Validation falls back to a generic message without field errors")
    void handlesValidationFallback() {
        BindingResult bindingResult = Mockito.mock(BindingResult.class);
        Mockito.when(bindingResult.getFieldErrors()).thenReturn(List.of());
        MethodArgumentNotValidException ex = Mockito.mock(MethodArgumentNotValidException.class);
        Mockito.when(ex.getBindingResult()).thenReturn(bindingResult);
        ResponseEntity<Map<String, String>> resp = handler.handleValidation(ex);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("Validation failed", bodyError(resp));
    }

    @Test
    @DisplayName("Null exception message falls back to the status reason phrase")
    void handlesNullMessage() {
        ResponseEntity<Map<String, String>> resp = handler.handleNotFound(new NotFoundException(null));
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND.getReasonPhrase(), bodyError(resp));
    }

    private static String bodyError(ResponseEntity<Map<String, String>> resp) {
        Map<String, String> body = resp.getBody();
        assertNotNull(body);
        return body.get("error");
    }
}
