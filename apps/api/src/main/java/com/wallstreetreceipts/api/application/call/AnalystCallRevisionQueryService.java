package com.wallstreetreceipts.api.application.call;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wallstreetreceipts.api.application.port.out.AnalystCallRepository;
import com.wallstreetreceipts.api.application.port.out.AnalystCallRevisionRepository;
import com.wallstreetreceipts.api.domain.call.AnalystCallRevision;

@Service
public class AnalystCallRevisionQueryService {

    private final AnalystCallRepository callRepository;
    private final AnalystCallRevisionRepository revisionRepository;

    public AnalystCallRevisionQueryService(
            AnalystCallRepository callRepository,
            AnalystCallRevisionRepository revisionRepository) {
        this.callRepository = callRepository;
        this.revisionRepository = revisionRepository;
    }

    @Transactional(readOnly = true)
    public List<AnalystCallRevision> findByCallId(String callId) {
        if (callRepository.findById(callId).isEmpty()) {
            throw new AnalystCallNotFoundException(callId);
        }
        return revisionRepository.findByCallId(callId);
    }
}
