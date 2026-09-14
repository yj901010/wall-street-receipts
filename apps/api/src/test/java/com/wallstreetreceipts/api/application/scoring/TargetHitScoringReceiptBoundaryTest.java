package com.wallstreetreceipts.api.application.scoring;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.*;
import com.wallstreetreceipts.api.application.port.out.*;
import com.wallstreetreceipts.api.web.scoring.TargetHitScoringReceiptController;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TargetHitScoringReceiptBoundaryTest {
    @ParameterizedTest @ValueSource(strings = {"acquire", "query", "commit", "replay"})
    void allRuntimeFailureBoundariesAreSanitizedWithoutHttpFallback(String boundary) throws Exception {
        RuntimeException failure = switch (boundary) {
            case "acquire" -> new CannotCreateTransactionException("DO_NOT_ECHO credentials");
            case "query" -> new DataAccessResourceFailureException("DO_NOT_ECHO SQL");
            case "commit" -> new TransactionSystemException("DO_NOT_ECHO connection");
            default -> new IllegalArgumentException("DO_NOT_ECHO raw input");
        };
        var service = mock(TargetHitScoringReceiptService.class);
        var id = UUID.randomUUID();
        when(service.recent("demo-call")).thenThrow(failure);
        when(service.find("demo-call", id)).thenThrow(failure);
        var mvc = MockMvcBuilders.standaloneSetup(new TargetHitScoringReceiptController(service)).build();
        for (var route : List.of("/v1/calls/demo-call/target-hit-scoring-receipts", "/v1/calls/demo-call/target-hit-scoring-receipts/" + id)) {
            mvc.perform(get(route)).andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().json("{\"code\":\"TARGET_HIT_SCORING_RECEIPT_UNAVAILABLE\",\"dataMode\":\"DEMO\"}", true));
        }
        verify(service).recent("demo-call");
        verify(service).find("demo-call", id);
        verifyNoMoreInteractions(service);
    }

    @Test void invalidQueriesDoNotReachStorageAndOnlyCallScopedReadsExist() throws Exception {
        var service = mock(TargetHitScoringReceiptService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new TargetHitScoringReceiptController(service)).build();
        String route = "/v1/calls/demo-call/target-hit-scoring-receipts";
        for (var bad : List.of("1-1-1-1-1", UUID.randomUUID().toString().toUpperCase(Locale.ROOT), "secret")) {
            mvc.perform(get(route + "/" + bad)).andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"));
        }
        for (var path : List.of(route, route + "/" + UUID.randomUUID())) {
            mvc.perform(get(path).queryParam("secret", "DO_NOT_ECHO")).andExpect(status().isBadRequest())
                    .andExpect(content().json("{\"code\":\"INVALID_TARGET_HIT_SCORING_QUERY\",\"dataMode\":\"DEMO\"}", true));
        }
        verifyNoInteractions(service);
    }

    @Test void appendIsSeparateAndReadTransactionAndPageBoundariesCannotDrift() throws Exception {
        for (var method : List.of(
                TargetHitScoringReceiptService.class.getMethod("find", String.class, UUID.class),
                TargetHitScoringReceiptService.class.getMethod("recent", String.class))) {
            var tx = method.getAnnotation(Transactional.class);
            assertThat(tx.readOnly()).isTrue();
            assertThat(tx.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
        }
        var append = TargetHitScoringReceiptService.class.getMethod("append", TargetHitScoringInput.class, String.class).getAnnotation(Transactional.class);
        assertThat(append.readOnly()).isFalse();
        assertThat(append.isolation()).isEqualTo(Isolation.READ_COMMITTED);
        assertThat(Arrays.stream(TargetHitScoringReceiptRepository.class.getDeclaredMethods()).map(m -> m.getName()))
                .containsExactlyInAnyOrder("lockCall", "insert", "findByIdentity", "find", "recent");
        var items = new ArrayList<TargetHitScoringReceiptService.Verified>();
        var page = new TargetHitScoringReceiptService.Page(items, false);
        items.add(null);
        assertThat(page.items()).isEmpty();
        assertThatThrownBy(() -> page.items().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }
}
