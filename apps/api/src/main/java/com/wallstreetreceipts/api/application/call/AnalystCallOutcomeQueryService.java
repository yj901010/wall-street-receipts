package com.wallstreetreceipts.api.application.call;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wallstreetreceipts.api.application.port.out.AnalystCallRepository;
import com.wallstreetreceipts.api.application.port.out.CallOutcomeRepository;
import com.wallstreetreceipts.api.domain.outcome.CallOutcome;

@Service
public class AnalystCallOutcomeQueryService {

    private final AnalystCallRepository callRepository;
    private final CallOutcomeRepository outcomeRepository;

    public AnalystCallOutcomeQueryService(
            AnalystCallRepository callRepository,
            CallOutcomeRepository outcomeRepository) {
        this.callRepository = callRepository;
        this.outcomeRepository = outcomeRepository;
    }

    @Transactional(readOnly = true)
    public List<CallOutcome> findByCallId(String callId) {
        if (callRepository.findById(callId).isEmpty()) {
            throw new AnalystCallNotFoundException(callId);
        }
        return outcomeRepository.findByCallId(callId);
    }
}
