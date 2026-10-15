package com.example.fridiet.controller;

import com.example.fridiet.dto.ContainerDeductRequest;
import com.example.fridiet.dto.ContainerRegisterRequest;
import com.example.fridiet.dto.ContainerScanResponse;
import com.example.fridiet.dto.ContainerSplitRequest;
import com.example.fridiet.service.ContainerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/containers")
@RequiredArgsConstructor
public class ContainerController {

    private final ContainerService containerService;

    /**
     * GET /api/containers/qr/generate
     * GET /api/containers/qr/generate?token={qrToken}
     * QR 코드 생성 및 다운로드
     */
    @GetMapping(value = "/qr/generate", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> generateQr(
            @RequestParam(required = false) String token,
            @RequestParam(defaultValue = "false") boolean download) throws Exception {

        byte[] qrImage = containerService.generateContainerQrImage(token);

        HttpHeaders headers= new HttpHeaders();
        if(download) {
            headers.setContentDispositionFormData("attachment", "fridiet_container_qr.png");
        }

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.IMAGE_PNG)
                .body(qrImage);
    }

    /**
     * POST /api/containers/register
     * 사용자 자율 QR 스티커 등록 & 재고 연결 API
     */
    @PostMapping("/register")
    public ResponseEntity<ContainerScanResponse> registerContainer(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ContainerRegisterRequest request) {

        ContainerScanResponse response = containerService.registerOrUpdateContainer(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/containers/register-with-split
     * 기존 재고 컨테이너에 분할 저장
     */
    @PostMapping("/register-with-split")
    public ResponseEntity<ContainerScanResponse> registerWithSplit(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ContainerSplitRequest request) {

        ContainerScanResponse response = containerService.registerWithSplit(userId, request);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/containers/scan?qrCodeToken={qrCodeToken}
     * QR 코드 스캔 시 용기에 담긴 재고 상세 반환
     */
    @GetMapping("/scan")
    public ResponseEntity<ContainerScanResponse> scanContainer(@AuthenticationPrincipal Long userId, @RequestParam String qrCodeToken) {
        ContainerScanResponse response = containerService.scanContainer(userId, qrCodeToken);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/containers/deduct?qrCodeToken={qrCodeToken}
     * QR 코드 스캔 후 사용량 차감
     */
    @PostMapping("/deduct")
    public ResponseEntity<ContainerScanResponse> deductQuantity(
            @AuthenticationPrincipal Long userId,
            @RequestParam String qrCodeToken,
            @Valid @RequestBody ContainerDeductRequest request) {

        ContainerScanResponse response = containerService.deductQuantityByQr(userId, qrCodeToken, request);
        return ResponseEntity.ok(response);
    }
}
