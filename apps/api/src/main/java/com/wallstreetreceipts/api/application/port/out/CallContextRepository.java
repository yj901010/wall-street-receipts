package com.wallstreetreceipts.api.application.port.out;

import java.util.Optional;

import com.wallstreetreceipts.api.domain.context.EventContext;
import com.wallstreetreceipts.api.domain.context.MacroObservation;
import com.wallstreetreceipts.api.domain.context.MacroSnapshot;

public interface CallContextRepository {

    int importDataSet(CallContextDataSet dataSet);

    Optional<MacroSnapshot> findMacroSnapshotByCallId(String callId);

    Optional<EventContext> findEventContextByCallId(String callId);

    Optional<MacroObservation> findObservationById(String macroObservationId);

    long observationCount();

    long macroSnapshotCount();

    long eventContextCount();
}
