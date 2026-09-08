package com.wallstreetreceipts.api.application.cpi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import com.wallstreetreceipts.api.WallStreetReceiptsApiApplication;
import com.wallstreetreceipts.api.infrastructure.provider.bls.BlsCpiParser;
import com.wallstreetreceipts.api.support.CpiTestFixture;

class CollectCpiCommandTest {
    @Test void manualModeClosesContextAfterSuccessCooldownAndFailure() {
        var snapshot = new BlsCpiParser().parse(CpiTestFixture.bytes(), UUID.randomUUID(),
                Instant.parse("2026-09-08T14:00:00Z"), 2023, 2026);
        for (int scenario = 0; scenario < 3; scenario++) {
            var job = mock(CpiCollectionJob.class);
            var context = mock(ConfigurableApplicationContext.class);
            var command = new CollectCpiCommand(job, context);
            if (scenario == 0) {
                when(job.collect()).thenReturn(Optional.of(snapshot));
                command.run(new DefaultApplicationArguments());
            } else if (scenario == 1) {
                when(job.collect()).thenReturn(Optional.empty());
                assertThatThrownBy(() -> command.run(new DefaultApplicationArguments())).hasMessage("CPI collection cooldown is active");
            } else {
                when(job.collect()).thenThrow(new IllegalStateException("collection failed"));
                assertThatThrownBy(() -> command.run(new DefaultApplicationArguments())).hasMessage("collection failed");
            }
            verify(job).collect();
            verify(context).close();
        }
    }

    @Test void manualModeCannotCollectInsideWebContext() {
        var job = mock(CpiCollectionJob.class);
        var context = mock(AnnotationConfigServletWebServerApplicationContext.class);
        assertThatThrownBy(() -> new CollectCpiCommand(job, context).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(job, context);
    }

    @Test void reservedCliModesRejectUnknownSuffixesArgumentsAndMixedModesBeforeSpringStarts() {
        for (String option : new String[]{"--wsr-collect-bls-cpi", "--wsr-schedule-bls-cpi"}) {
            for (String[] args : new String[][]{{option + "=true"}, {option + "x"}, {option, "--password=SECRET"},
                    {option, option}, {option, "--wsr-collect-bls-cpi", "--wsr-schedule-bls-cpi"}}) {
                assertThatThrownBy(() -> WallStreetReceiptsApiApplication.main(args))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("SECRET").hasNoCause();
            }
        }
    }
}
