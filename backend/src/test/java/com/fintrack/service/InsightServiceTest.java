package com.fintrack.service;

import com.fintrack.dto.BudgetDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsightServiceTest {

    @Mock private WebClient.Builder webClientBuilder;
    @Mock private BudgetService budgetService;
    @Mock private WebClient mockWebClient;
    @InjectMocks private InsightService insightService;

    private BudgetDto.MonthlySummary emptySummary;

    @BeforeEach
    void setUp() {
        // Inject the mock WebClient directly, bypassing @PostConstruct
        ReflectionTestUtils.setField(insightService, "webClient", mockWebClient);
        ReflectionTestUtils.setField(insightService, "openAiKey", "test-key");
        ReflectionTestUtils.setField(insightService, "openAiUrl", "https://api.openai.com/v1/chat/completions");
        ReflectionTestUtils.setField(insightService, "model", "gpt-4o-mini");

        emptySummary = BudgetDto.MonthlySummary.builder()
                .month(5).year(2026)
                .totalIncome(BigDecimal.ZERO).totalExpenses(BigDecimal.ZERO)
                .netBalance(BigDecimal.ZERO)
                .expensesByCategory(Map.of())
                .budgets(List.of())
                .build();
    }

    @Test
    void getSpendingInsights_success_returnsContent() {
        when(budgetService.getMonthlySummary(1L, 5, 2026)).thenReturn(emptySummary);
        InsightService.OpenAiResponse response = new InsightService.OpenAiResponse(
                List.of(new InsightService.OpenAiChoice(
                        new InsightService.OpenAiMessage("• Save more · Spend less"))));
        stubWebClient(Mono.just(response));

        String result = insightService.getSpendingInsights(1L, 5, 2026);

        assertThat(result).isEqualTo("• Save more · Spend less");
    }

    @Test
    void getSpendingInsights_nullResponse_returnsFallback() {
        when(budgetService.getMonthlySummary(1L, 5, 2026)).thenReturn(emptySummary);
        stubWebClient(Mono.empty());

        String result = insightService.getSpendingInsights(1L, 5, 2026);

        assertThat(result).contains("Unable to generate insights");
    }

    @Test
    void getSpendingInsights_emptyChoices_returnsFallback() {
        when(budgetService.getMonthlySummary(1L, 5, 2026)).thenReturn(emptySummary);
        stubWebClient(Mono.just(new InsightService.OpenAiResponse(List.of())));

        String result = insightService.getSpendingInsights(1L, 5, 2026);

        assertThat(result).contains("Unable to generate insights");
    }

    @Test
    void getSpendingInsights_networkException_returnsFallback() {
        when(budgetService.getMonthlySummary(1L, 5, 2026)).thenReturn(emptySummary);
        when(mockWebClient.post()).thenThrow(new RuntimeException("Connection refused"));

        String result = insightService.getSpendingInsights(1L, 5, 2026);

        assertThat(result).contains("Unable to generate insights");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void stubWebClient(Mono<InsightService.OpenAiResponse> responseBody) {
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(mockWebClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec);
        when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(InsightService.OpenAiResponse.class)).thenReturn(responseBody);
    }
}
