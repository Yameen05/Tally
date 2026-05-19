package com.fintrack.controller;

import com.fintrack.dto.BudgetDto;
import com.fintrack.entity.User;
import com.fintrack.service.AuthService;
import com.fintrack.service.BudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
@Validated
@Tag(name = "Budgets", description = "Manage monthly category budgets")
@SecurityRequirement(name = "bearerAuth")
public class BudgetController {

    private final BudgetService budgetService;
    private final AuthService authService;

    @PostMapping
    @Operation(summary = "Create or update a budget for a category")
    public ResponseEntity<BudgetDto.Response> createOrUpdate(
            @Valid @RequestBody BudgetDto.Request request,
            Authentication auth) {
        User user = authService.getCurrentUser(auth.getName());
        return ResponseEntity.ok(budgetService.createOrUpdate(request, user));
    }

    @GetMapping("/month/{year}/{month}")
    @Operation(summary = "Get all budgets for a specific month")
    public ResponseEntity<List<BudgetDto.Response>> getByMonth(
            @PathVariable @Min(2000) @Max(2100) int year,
            @PathVariable @Min(1) @Max(12) int month,
            Authentication auth) {
        User user = authService.getCurrentUser(auth.getName());
        return ResponseEntity.ok(budgetService.getByMonth(user.getId(), month, year));
    }

    @GetMapping("/summary/{year}/{month}")
    @Operation(summary = "Get full monthly financial summary")
    public ResponseEntity<BudgetDto.MonthlySummary> getSummary(
            @PathVariable @Min(2000) @Max(2100) int year,
            @PathVariable @Min(1) @Max(12) int month,
            Authentication auth) {
        User user = authService.getCurrentUser(auth.getName());
        return ResponseEntity.ok(budgetService.getMonthlySummary(user.getId(), month, year));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a budget")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        User user = authService.getCurrentUser(auth.getName());
        budgetService.delete(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
