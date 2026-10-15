package com.example.fridiet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.s3.folder:dev}")
    private String folder;

    /**
     * 사진을 S3에 업로드하고 영구 접근 URL 반환
     */
    public String uploadImage(MultipartFile file, String domainCategory) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 비어있습니다.");
        }

        // 확장자 추출
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // S3 Key 형식 예: dev/meals/550e8400-e29b-41d4-a716-446655440000.jpg
        String s3Key = String.format("%s/%s/%s%s", folder, domainCategory, UUID.randomUUID(), extension);

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            String uploadedUrl = s3Client.utilities().getUrl(GetUrlRequest.builder().bucket(bucket).key(s3Key).build()).toExternalForm();
            log.info("[S3 업로드 성공] Key: {}, URL: {}", s3Key, uploadedUrl);

            return uploadedUrl;

        } catch (IOException e) {
            log.error("[S3 업로드 에러]", e);
            throw new RuntimeException("S3에 이미지 업로드 중 실패했습니다.", e);
        }
    }
}