package com.example.fridiet.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ReceiptOcrControllerTest {
    private final ReceiptOcrController controller = new ReceiptOcrController("http://localhost:8000/");

    @Test void requiresAuthenticationBeforeCallingAi() {
        var file = new MockMultipartFile("file", "receipt.png", "image/png", new byte[]{1});
        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(ResponseStatusException.class, () -> controller.analyze(null, file)).getStatusCode());
    }

    @Test void rejectsEmptyAndUnsupportedUploads() {
        var empty = new MockMultipartFile("file", new byte[0]);
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class, () -> controller.analyze(1L, empty)).getStatusCode());
        var text = new MockMultipartFile("file", "x.txt", "text/plain", new byte[]{1});
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                assertThrows(ResponseStatusException.class, () -> controller.analyze(1L, text)).getStatusCode());
    }

    @Test void forwardsMultipartAndPreservesReviewFields() throws Exception {
        var server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(controller, "client")).build();
        String json = "{\"items\":[],\"reviewCandidates\":[{\"rawText\":\"미분류\"}]}";
        server.expect(requestTo("http://localhost:8000/api/ocr/receipt"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        var file = new MockMultipartFile("file", "receipt.webp", "image/webp", new byte[]{1,2,3});
        assertEquals(json, controller.analyze(1L, file).getBody());
        server.verify();
    }

    @Test void hidesUpstreamInternalErrors() {
        var server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(controller, "client")).build();
        server.expect(requestTo("http://localhost:8000/api/ocr/receipt"))
                .andRespond(withServerError().body("private internal details"));
        var file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", new byte[]{1});
        var error = assertThrows(ResponseStatusException.class, () -> controller.analyze(1L, file));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        assertFalse(error.getReason().contains("private"));
        server.verify();
    }
}
