package com.fintrack.service;

import com.fintrack.dto.TransactionDto;
import com.fintrack.entity.Transaction;
import com.fintrack.entity.User;
import com.fintrack.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;

    @Transactional
    public TransactionDto.Response create(TransactionDto.Request request, User user) {
        Transaction transaction = Transaction.builder()
                .description(request.getDescription())
                .amount(request.getAmount())
                .type(request.getType())
                .category(request.getCategory())
                .date(request.getDate())
                .notes(request.getNotes())
                .user(user)
                .build();

        return toResponse(transactionRepository.save(transaction));
    }

    public Page<TransactionDto.Response> getAll(Long userId, int page, int size) {
        return transactionRepository
                .findByUserIdOrderByDateDesc(userId, PageRequest.of(page, size))
                .map(this::toResponse);
    }

    public List<TransactionDto.Response> getByMonth(Long userId, int month, int year) {
        return transactionRepository.findByUserIdAndMonthAndYear(userId, month, year)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public TransactionDto.Response update(Long id, TransactionDto.Request request, Long userId) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        if (!transaction.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        transaction.setDescription(request.getDescription());
        transaction.setAmount(request.getAmount());
        transaction.setType(request.getType());
        transaction.setCategory(request.getCategory());
        transaction.setDate(request.getDate());
        transaction.setNotes(request.getNotes());

        return toResponse(transactionRepository.save(transaction));
    }

    @Transactional
    public void delete(Long id, Long userId) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        if (!transaction.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        transactionRepository.delete(transaction);
    }

    private TransactionDto.Response toResponse(Transaction t) {
        return TransactionDto.Response.builder()
                .id(t.getId())
                .description(t.getDescription())
                .amount(t.getAmount())
                .type(t.getType())
                .category(t.getCategory())
                .date(t.getDate())
                .notes(t.getNotes())
                .createdAt(t.getCreatedAt())
                .merchantName(t.getMerchantName())
                .pending(t.getPending())
                .plaidTransactionId(t.getPlaidTransactionId())
                .plaidAccountId(t.getPlaidAccountId())
                .build();
    }
}
