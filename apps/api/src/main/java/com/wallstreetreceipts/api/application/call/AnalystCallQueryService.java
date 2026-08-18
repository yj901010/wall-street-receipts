package com.wallstreetreceipts.api.application.call;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wallstreetreceipts.api.application.port.out.AnalystCallRepository;

@Service
public class AnalystCallQueryService {

    private final AnalystCallRepository repository;

    public AnalystCallQueryService(AnalystCallRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AnalystCallPage findAll(AnalystCallFilter filter) {
        return repository.findAll(filter);
    }

    @Transactional(readOnly = true)
    public AnalystCallDetail findById(String callId) {
        return repository.findById(callId)
                .orElseThrow(() -> new AnalystCallNotFoundException(callId));
    }
}
