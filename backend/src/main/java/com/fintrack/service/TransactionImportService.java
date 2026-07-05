package com.fintrack.service;

import com.fintrack.entity.Transaction;
import com.fintrack.entity.Transaction.TransactionType;
import com.fintrack.entity.User;
import com.fintrack.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CSV transaction import. Round-trips the app's own export format
 * (Date,Description,Category,Type,Amount,Notes) and tolerates common
 * variations: reordered columns, missing Category/Notes, "$1,234.56" amounts,
 * and US-style dates.
 */
@Service
@RequiredArgsConstructor
public class TransactionImportService {

    private static final int MAX_ROWS = 5000;
    private static final int MAX_REPORTED_ERRORS = 20;
    private static final DateTimeFormatter US_DATE = DateTimeFormatter.ofPattern("M/d/yyyy");

    private final TransactionRepository transactionRepository;

    public record RowError(long line, String message) {
    }

    public record ImportResult(int imported, int duplicates, int failed, List<RowError> errors) {
    }

    @Transactional
    public ImportResult importCsv(InputStream csv, User user) {
        int imported = 0;
        int duplicates = 0;
        int failed = 0;
        List<RowError> errors = new ArrayList<>();

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .build();

        try (CSVParser parser = new CSVParser(new InputStreamReader(csv, StandardCharsets.UTF_8), format)) {
            List<String> headers = parser.getHeaderNames().stream().map(h -> h.toLowerCase(Locale.ROOT)).toList();
            for (String required : List.of("date", "description", "amount", "type")) {
                if (!headers.contains(required)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "CSV must have Date, Description, Amount, and Type columns (got: "
                                    + String.join(", ", parser.getHeaderNames()) + ")");
                }
            }

            for (CSVRecord record : parser) {
                if (record.getRecordNumber() > MAX_ROWS) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "CSV has more than " + MAX_ROWS + " rows; split it into smaller files");
                }
                try {
                    Transaction parsed = parseRow(record, user);
                    if (transactionRepository.existsByUserIdAndDateAndDescriptionAndAmountAndType(
                            user.getId(), parsed.getDate(), parsed.getDescription(),
                            parsed.getAmount(), parsed.getType())) {
                        duplicates++;
                    } else {
                        transactionRepository.save(parsed);
                        imported++;
                    }
                } catch (IllegalArgumentException e) {
                    failed++;
                    if (errors.size() < MAX_REPORTED_ERRORS) {
                        errors.add(new RowError(record.getRecordNumber() + 1, e.getMessage()));
                    }
                }
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the CSV file");
        }

        return new ImportResult(imported, duplicates, failed, errors);
    }

    private Transaction parseRow(CSVRecord record, User user) {
        String description = get(record, "description");
        if (description.isBlank()) throw new IllegalArgumentException("Description is empty");
        if (description.length() > 255) description = description.substring(0, 255);

        LocalDate date = parseDate(get(record, "date"));
        BigDecimal amount = parseAmount(get(record, "amount"));
        TransactionType type = parseType(get(record, "type"));

        String category = get(record, "category");
        if (category.isBlank()) category = "Other";
        if (category.length() > 100) category = category.substring(0, 100);

        String notes = get(record, "notes");
        if (notes.length() > 500) notes = notes.substring(0, 500);

        return Transaction.builder()
                .description(description)
                .amount(amount)
                .type(type)
                .category(category)
                .date(date)
                .notes(notes.isBlank() ? null : notes)
                .pending(false)
                .user(user)
                .build();
    }

    private static String get(CSVRecord record, String column) {
        return record.isMapped(column) && record.isSet(column) ? record.get(column).trim() : "";
    }

    private static LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw); // ISO yyyy-MM-dd (our export format)
        } catch (Exception ignored) {
            try {
                return LocalDate.parse(raw, US_DATE);
            } catch (Exception e) {
                throw new IllegalArgumentException("Unrecognized date \"" + raw + "\" (use YYYY-MM-DD)");
            }
        }
    }

    private static BigDecimal parseAmount(String raw) {
        String cleaned = raw.replace("$", "").replace(",", "").trim();
        try {
            BigDecimal amount = new BigDecimal(cleaned).abs();
            if (amount.signum() == 0) throw new IllegalArgumentException("Amount is zero");
            return amount.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unrecognized amount \"" + raw + "\"");
        }
    }

    private static TransactionType parseType(String raw) {
        try {
            return TransactionType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("Type must be INCOME or EXPENSE, got \"" + raw + "\"");
        }
    }
}
