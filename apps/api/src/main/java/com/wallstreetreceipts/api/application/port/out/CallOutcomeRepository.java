package com.wallstreetreceipts.api.application.port.out;

import java.util.List;

import com.wallstreetreceipts.api.domain.outcome.CallOutcome;

public interface CallOutcomeRepository {

    int importAll(List<CallOutcome> outcomes);

    boolean saveIfAbsent(CallOutcome outcome);

    List<CallOutcome> findByCallId(String callId);

    long count();
}
