package com.fintrack.controller;

import com.fintrack.dto.TransactionDto;
import com.fintrack.entity.User;
import com.fintrack.service.AuthService;
import com.fintrack.service.TransactionService;
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
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Validated
@Tag(name = "Transactions", description = "Manage income and expense transactions")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService transactionService;
    private final AuthService authService;

    @PostMapping
    @Operation(summary = "Create a new transaction")
    public ResponseEntity<TransactionDto.Response> create(
            @Valid @RequestBody TransactionDto.Request request,
            Authentication auth) {
        User user = authService.getCurrentUser(auth.getName());
        return ResponseEntity.ok(transactionService.create(request, user));
    }

    @GetMapping
    @Operation(summary = "Get all transactions for the current user")
    public ResponseEntity<List<TransactionDto.Response>> getAll(Authentication auth) {
        User user = authService.getCurrentUser(auth.getName());
        return ResponseEntity.ok(transactionService.getAll(user.getId()));
    }

    @GetMapping("/month/{year}/{month}")
    @Operation(summary = "Get transactions for a specific month")
    public ResponseEntity<List<TransactionDto.Response>> getByMonth(
            @PathVariable @Min(2000) @Max(2100) int year,
            @PathVariable @Min(1) @Max(12) int month,
            Authentication auth) {
        User user = authService.getCurrentUser(auth.getName());
        return ResponseEntity.ok(transactionService.getByMonth(user.getId(), month, year));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a transaction")
    public ResponseEntity<TransactionDto.Response> update(
            @PathVariable Long id,
            @Valid @RequestBody TransactionDto.Request request,
            Authentication auth) {
        User user = authService.getCurrentUser(auth.getName());
        return ResponseEntity.ok(transactionService.update(id, request, user.getId()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a transaction")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        User user = authService.getCurrentUser(auth.getName());
        transactionService.delete(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
