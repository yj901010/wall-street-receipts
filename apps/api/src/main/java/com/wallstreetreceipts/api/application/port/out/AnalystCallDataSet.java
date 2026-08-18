package com.wallstreetreceipts.api.application.port.out;

import java.util.List;

import com.wallstreetreceipts.api.domain.call.AnalystCall;
import com.wallstreetreceipts.api.domain.market.MarketSnapshot;
import com.wallstreetreceipts.api.domain.master.Analyst;
import com.wallstreetreceipts.api.domain.master.Asset;
import com.wallstreetreceipts.api.domain.master.Institution;

public record AnalystCallDataSet(
        List<Institution> institutions,
        List<Analyst> analysts,
        List<Asset> assets,
        List<AnalystCall> calls,
        List<MarketSnapshot> snapshots) {

    public AnalystCallDataSet {
        institutions = List.copyOf(institutions);
        analysts = List.copyOf(analysts);
        assets = List.copyOf(assets);
        calls = List.copyOf(calls);
        snapshots = List.copyOf(snapshots);
    }
}
