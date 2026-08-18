package com.wallstreetreceipts.api.application.port.out;

import java.util.List;

import com.wallstreetreceipts.api.domain.call.AnalystCallRevision;

public interface AnalystCallRevisionRepository {

    int importAll(List<AnalystCallRevision> revisions);

    boolean saveIfAbsent(AnalystCallRevision revision);

    List<AnalystCallRevision> findByCallId(String callId);

    long count();
}
