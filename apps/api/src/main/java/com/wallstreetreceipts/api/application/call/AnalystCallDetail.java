package com.wallstreetreceipts.api.application.call;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.call.AnalystCall;
import com.wallstreetreceipts.api.domain.market.MarketSnapshot;

public record AnalystCallDetail(AnalystCall call, MarketSnapshot snapshot) {

    public AnalystCallDetail {
        Objects.requireNonNull(call, "call must not be null");
    }
}
