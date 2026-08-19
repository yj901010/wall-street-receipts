package com.wallstreetreceipts.api.application.port.out;

import java.util.List;

import com.wallstreetreceipts.api.domain.call.AnalystCall;
import com.wallstreetreceipts.api.domain.call.AnalystCallRevision;
import com.wallstreetreceipts.api.domain.market.MarketSnapshot;
import com.wallstreetreceipts.api.domain.master.Analyst;
import com.wallstreetreceipts.api.domain.master.Asset;
import com.wallstreetreceipts.api.domain.master.Institution;
import com.wallstreetreceipts.api.domain.outcome.CallOutcome;
import com.wallstreetreceipts.api.domain.outcome.ScoringMethodology;

public record AnalystCallDataSet(
        List<Institution> institutions,
        List<Analyst> analysts,
        List<Asset> assets,
        List<AnalystCall> calls,
        List<AnalystCallRevision> revisions,
        List<MarketSnapshot> snapshots,
        List<ScoringMethodology> methodologies,
        List<CallOutcome> outcomes,
        CallContextDataSet contexts) {

    public AnalystCallDataSet {
        institutions = List.copyOf(institutions);
        analysts = List.copyOf(analysts);
        assets = List.copyOf(assets);
        calls = List.copyOf(calls);
        revisions = List.copyOf(revisions);
        snapshots = List.copyOf(snapshots);
        methodologies = List.copyOf(methodologies);
        outcomes = List.copyOf(outcomes);
        contexts = contexts == null ? CallContextDataSet.empty() : contexts;
    }
}
