package com.wallstreetreceipts.api.application.scoring;

import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis;
import static org.assertj.core.api.Assertions.*;
import static com.wallstreetreceipts.api.application.scoring.EndpointScoringFixture.*;
import static com.wallstreetreceipts.api.application.scoring.ComparativeScoringFixture.input;
import static com.wallstreetreceipts.api.application.scoring.ComparativeScoringEvaluatorTest.*;

class ComparativeScoringInputTest {
    @TestFactory Stream<DynamicTest> allThirteenCandidateListsAreImmutableNonNullAndBounded() {
        return CANDIDATES.stream().map(field -> DynamicTest.dynamicTest(field, () -> {
            var in = input();
            var item = ((List<?>) path(in, field)).getFirst();
            var mutable = new ArrayList<>(List.of(item));
            var copy = replace(in, field, mutable);
            mutable.clear();
            assertThat((List<?>) path(copy, field)).hasSize(1);
            assertThatThrownBy(() -> ((List<?>) path(copy, field)).clear()).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> replace(in, field, null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> replace(in, field, Arrays.asList((Object) null))).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> replace(in, field, Collections.nCopies(4097, item))).isInstanceOf(IllegalArgumentException.class);
            assertThat((List<?>) path(replace(in, field, Collections.nCopies(4096, item)), field)).hasSize(4096);
        }));
    }

    @TestFactory Stream<DynamicTest> requestsMustShareExactBasisAssetAndCutoff() {
        return Stream.of("benchmark", "sector").flatMap(leg -> Stream.of("basis", "assetId", "evaluationAsOf")
                .map(field -> DynamicTest.dynamicTest(leg + "/" + field, () -> {
                    Object wrong = switch (field) {
                        case "basis" -> new OutcomeBasis.Correction("demo-call", "demo-correction", BASIS);
                        case "assetId" -> "demo-other-asset";
                        default -> AS_OF.plusSeconds(1);
                    };
                    assertThatThrownBy(() -> replace(input(), leg + ".assignment." + field, wrong))
                            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("share");
                })));
    }

    @TestFactory Stream<DynamicTest> erasedGenericListsCannotSmuggleWrongEvidenceTypes() {
        return CANDIDATES.stream().filter(field -> !field.contains(".assignment.")).map(field -> DynamicTest.dynamicTest(field, () -> {
            assertThatThrownBy(() -> replace(input(), field, List.of("not-evidence")))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("candidate type");
        }));
    }

    @Test void demoAndRequiredInputsCannotBeBypassed() {
        for (String field : List.of("endpoint", "benchmark", "sector")) {
            assertThatThrownBy(() -> edit(input(), field, null)).isInstanceOf(NullPointerException.class);
        }
        for (var mode : com.wallstreetreceipts.api.domain.market.DataMode.values()) {
            if (mode != com.wallstreetreceipts.api.domain.market.DataMode.DEMO) {
                assertThatThrownBy(() -> replace(input(), "endpoint.dataMode", mode)).isInstanceOf(IllegalArgumentException.class);
            }
        }
    }
}
