package com.example.fridiet.controller;

import com.example.fridiet.service.ContainerService;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Read-only photo analysis. No inventory updates or inferred unit conversions. */
@RestController
@RequestMapping("/api/containers")
public class ContainerEstimateController {
    private final ContainerService containers;
    private final RestTemplate client;
    private final String endpoint;
    private final String token;
    private final Map<Long, Long> lastRequest = new HashMap<>();

    public ContainerEstimateController(ContainerService containers,
            @Value("${ai.base-url:http://localhost:8000}") String baseUrl,
            @Value("${ai.service-token:${AI_SERVICE_TOKEN:}}") String token) {
        this.containers = containers;
        this.token = token;
        this.endpoint = baseUrl.replaceAll("/+$", "") + "/api/containers/estimate";
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(60000);
        this.client = new RestTemplate(factory);
    }

    private synchronized void reserveRequest(Long userId) {
        long now = System.currentTimeMillis();
        lastRequest.entrySet().removeIf(e -> now - e.getValue() >= 60000);
        if (lastRequest.containsKey(userId))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "사진 분석은 사용자당 1분에 한 번 가능합니다.");
        lastRequest.put(userId, now);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message",
                error.getReason() == null ? "요청 권한 또는 입력을 확인해주세요." : error.getReason()));
    }

    @PostMapping(value = "/estimate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> estimate(@AuthenticationPrincipal Long userId,
            @RequestParam String qrCodeToken, @RequestPart("file") MultipartFile file) throws IOException {
        if (userId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var container = containers.scanContainer(userId, qrCodeToken);
        if (container.getContainerId() == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "먼저 용기를 등록해주세요.");
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진이 비어 있습니다.");
        if (file.getSize() > 10 * 1024 * 1024)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "사진은 10MB 이하여야 합니다.");
        if (!Set.of("image/jpeg", "image/png", "image/webp").contains(
                file.getContentType() == null ? "" : file.getContentType()))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "JPEG, PNG, WebP만 지원합니다.");
        if (token == null || token.length() < 32)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 서버 인증 설정이 필요합니다.");
        reserveRequest(userId);
        var resource = new ByteArrayResource(file.getBytes()) {
            @Override public String getFilename() { return "container-photo"; }
        };
        var partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(file.getContentType()));
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new HttpEntity<>(resource, partHeaders));
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-AI-Service-Token", token);
        try {
            var response = client.postForEntity(endpoint, new HttpEntity<>(form, headers), String.class);
            if (response.getBody() == null)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "분석 응답이 비어 있습니다.");
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response.getBody());
        } catch (HttpStatusCodeException e) {
            if (Set.of(400, 413, 415, 422).contains(e.getStatusCode().value()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진 형식·크기를 확인하고 다시 촬영해주세요.");
            if (e.getStatusCode().value() == 503)
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "사진 분석을 사용할 수 없습니다. 관리자에게 AI 설정·크레딧 확인을 요청해주세요.");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "사진 분석에 실패했습니다.");
        } catch (ResourceAccessException e) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "사진 분석 연결 또는 응답 시간이 초과되었습니다.");
        }
    }
}
