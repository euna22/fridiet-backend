package com.example.fridiet.controller;

import com.example.fridiet.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;

    /**
     * 식사 사진 업로드
     * POST /api/upload/meal-image
     */
    @PostMapping("/meal-image")
    public ResponseEntity<Map<String, String>> uploadMealImage(@RequestParam("file") MultipartFile file) {
        String imageUrl = fileUploadService.uploadImage(file, "meals");
        return ResponseEntity.ok(Map.of("imageUrl", imageUrl));
    }
}