package com.example.fridiet.controller;

import com.example.fridiet.dto.MonthlyReportResponse;
import com.example.fridiet.service.MonthlyReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class MonthlyReportController {
    private final MonthlyReportService service;

    @GetMapping("/monthly")
    public MonthlyReportResponse monthly(@AuthenticationPrincipal Long userId,
                                         @RequestParam(required = false) String month) {
        return service.getReport(userId, month);
    }
}
