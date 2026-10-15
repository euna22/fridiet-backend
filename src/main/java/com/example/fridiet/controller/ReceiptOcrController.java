package com.example.fridiet.controller;

import java.io.IOException;
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

/** Analysis only. Inventory is written separately after the user confirms it. */
@RestController
@RequestMapping("/api/ocr")
public class ReceiptOcrController {
    private final RestTemplate client;
    private final String endpoint;

    public ReceiptOcrController(@Value("${ai.base-url:http://localhost:8000}") String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(120000);
        client = new RestTemplate(factory);
        endpoint = baseUrl.replaceAll("/+$", "") + "/api/ocr/receipt";
    }

    @PostMapping(value = "/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> analyze(@AuthenticationPrincipal Long userId,
                                         @RequestPart("file") MultipartFile file) throws IOException {
        if (userId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "영수증 이미지가 비어 있습니다.");
        if (file.getSize() > 10 * 1024 * 1024)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "이미지는 10MB 이하로 보내주세요.");
        if (file.getContentType() == null || !Set.of("image/jpeg", "image/png", "image/webp").contains(file.getContentType()))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "JPEG, PNG, WebP 이미지만 지원합니다.");
        var resource = new ByteArrayResource(file.getBytes()) {
            @Override public String getFilename() { return "receipt"; }
        };
        var partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(file.getContentType()));
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new HttpEntity<>(resource, partHeaders));
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        try {
            var response = client.postForEntity(endpoint, new HttpEntity<>(form, headers), String.class);
            if (response.getBody() == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OCR 응답이 비어 있습니다.");
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response.getBody());
        } catch (HttpStatusCodeException e) {
            if (Set.of(400, 413, 415, 422).contains(e.getStatusCode().value()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지를 분석하지 못했습니다. 선명한 영수증을 다시 선택해주세요.");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 영수증 분석에 실패했습니다.");
        } catch (ResourceAccessException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 연결 또는 분석 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.");
        }
    }
}
