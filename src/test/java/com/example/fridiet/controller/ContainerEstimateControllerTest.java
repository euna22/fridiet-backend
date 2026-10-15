package com.example.fridiet.controller;

import com.example.fridiet.dto.ContainerScanResponse;
import com.example.fridiet.service.ContainerService;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ContainerEstimateControllerTest {
    private final ContainerService containers = mock(ContainerService.class);
    private final String token = "x".repeat(48);
    private final ContainerEstimateController controller = new ContainerEstimateController(containers, "http://ai", token);
    private final MockMultipartFile photo = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2});

    private void registered() {
        when(containers.scanContainer(1L, "qr")).thenReturn(ContainerScanResponse.builder().containerId(2L).build());
    }

    @Test void requiresLogin() {
        assertEquals(401, assertThrows(ResponseStatusException.class,
            () -> controller.estimate(null, "qr", photo)).getStatusCode().value());
        verifyNoInteractions(containers);
    }

    @Test void requiresRegisteredAccessibleContainer() {
        when(containers.scanContainer(1L, "qr")).thenReturn(ContainerScanResponse.builder().build());
        assertEquals(404, assertThrows(ResponseStatusException.class,
            () -> controller.estimate(1L, "qr", photo)).getStatusCode().value());
        when(containers.scanContainer(1L, "qr")).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertEquals(403, assertThrows(ResponseStatusException.class,
            () -> controller.estimate(1L, "qr", photo)).getStatusCode().value());
    }

    @Test void rejectsInvalidImageType() {
        registered();
        assertEquals(415, assertThrows(ResponseStatusException.class,
            () -> controller.estimate(1L, "qr", new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{1})))
            .getStatusCode().value());
    }

    @Test void forwardsSecretServerSideAndLimitsRepeat() throws Exception {
        registered();
        var client = (RestTemplate) ReflectionTestUtils.getField(controller, "client");
        var server = MockRestServiceServer.bindTo(client).build();
        String json = "{\"status\":\"estimated\",\"remainingPercent\":30}";
        server.expect(requestTo("http://ai/api/containers/estimate"))
            .andExpect(header("X-AI-Service-Token", token))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        assertEquals(json, controller.estimate(1L, "qr", photo).getBody());
        assertEquals(429, assertThrows(ResponseStatusException.class,
            () -> controller.estimate(1L, "qr", photo)).getStatusCode().value());
        server.verify();
        verify(containers, times(2)).scanContainer(1L, "qr");
        verifyNoMoreInteractions(containers);
    }

    @Test void hidesUpstreamErrorBody() {
        registered();
        var server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(controller, "client")).build();
        server.expect(requestTo("http://ai/api/containers/estimate"))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("secret-details"));
        var error = assertThrows(ResponseStatusException.class, () -> controller.estimate(1L, "qr", photo));
        assertEquals(503, error.getStatusCode().value());
        assertFalse(error.getReason().contains("secret-details"));
        server.verify();
    }
}
