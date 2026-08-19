package com.wallstreetreceipts.api.application.call;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wallstreetreceipts.api.application.port.out.AnalystCallRepository;
import com.wallstreetreceipts.api.application.port.out.CallContextRepository;
import com.wallstreetreceipts.api.domain.context.CallContext;

@Service
public class AnalystCallContextQueryService {

    private final AnalystCallRepository callRepository;
    private final CallContextRepository contextRepository;

    public AnalystCallContextQueryService(
            AnalystCallRepository callRepository,
            CallContextRepository contextRepository) {
        this.callRepository = callRepository;
        this.contextRepository = contextRepository;
    }

    @Transactional(readOnly = true)
    public CallContext findByCallId(String callId) {
        if (callRepository.findById(callId).isEmpty()) {
            throw new AnalystCallNotFoundException(callId);
        }
        return new CallContext(
                contextRepository.findMacroSnapshotByCallId(callId).orElse(null),
                contextRepository.findEventContextByCallId(callId).orElse(null));
    }
}
