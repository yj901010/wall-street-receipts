package com.wallstreetreceipts.api.application.port.out;

import java.util.Optional;

import com.wallstreetreceipts.api.application.call.AnalystCallDetail;
import com.wallstreetreceipts.api.application.call.AnalystCallFilter;
import com.wallstreetreceipts.api.application.call.AnalystCallPage;
import com.wallstreetreceipts.api.domain.call.AnalystCall;
import com.wallstreetreceipts.api.domain.market.MarketSnapshot;

public interface AnalystCallRepository {

    int importDataSet(AnalystCallDataSet dataSet);

    boolean saveIfAbsent(AnalystCall call, MarketSnapshot snapshot);

    AnalystCallPage findAll(AnalystCallFilter filter);

    Optional<AnalystCallDetail> findById(String callId);

    long count();
}
