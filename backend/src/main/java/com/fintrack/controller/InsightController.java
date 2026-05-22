package com.fintrack.controller;

import com.fintrack.entity.User;
import com.fintrack.service.AuthService;
import com.fintrack.service.InsightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
@Validated
@Tag(name = "AI Insights", description = "OpenAI-powered spending analysis")
@SecurityRequirement(name = "bearerAuth")
public class InsightController {

    private final InsightService insightService;
    private final AuthService authService;

    @GetMapping("/{year}/{month}")
    @Operation(summary = "Get AI-powered spending insights for a month")
    public ResponseEntity<Map<String, String>> getInsights(
            @PathVariable @Min(2000) @Max(2100) int year,
            @PathVariable @Min(1) @Max(12) int month,
            Authentication auth) {
        User user = authService.getCurrentUser(auth.getName());
        String insights = insightService.getSpendingInsights(user.getId(), month, year);
        return ResponseEntity.ok(Map.of("insights", insights));
    }
}
