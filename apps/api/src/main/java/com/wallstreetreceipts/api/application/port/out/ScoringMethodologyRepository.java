package com.wallstreetreceipts.api.application.port.out;

import java.util.List;
import java.util.Optional;

import com.wallstreetreceipts.api.domain.outcome.ScoringMethodology;

public interface ScoringMethodologyRepository {

    int importAll(List<ScoringMethodology> methodologies);

    boolean saveIfAbsent(ScoringMethodology methodology);

    Optional<ScoringMethodology> findByIdAndVersion(String methodologyId, String methodologyVersion);

    long count();
}
