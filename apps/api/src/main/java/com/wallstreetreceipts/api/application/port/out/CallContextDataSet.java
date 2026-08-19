package com.wallstreetreceipts.api.application.port.out;

import java.util.List;

import com.wallstreetreceipts.api.domain.context.EventContext;
import com.wallstreetreceipts.api.domain.context.MacroObservation;
import com.wallstreetreceipts.api.domain.context.MacroSnapshot;
import com.wallstreetreceipts.api.domain.source.SourceDocument;
import com.wallstreetreceipts.api.domain.source.SourceReference;

public record CallContextDataSet(
        List<SourceDocument> sourceDocuments,
        List<SourceReference> sourceReferences,
        List<MacroObservation> macroObservations,
        List<MacroSnapshot> macroSnapshots,
        List<EventContext> eventContexts,
        List<String> knownEmptyCallIds) {

    public CallContextDataSet {
        sourceDocuments = List.copyOf(sourceDocuments);
        sourceReferences = List.copyOf(sourceReferences);
        macroObservations = List.copyOf(macroObservations);
        macroSnapshots = List.copyOf(macroSnapshots);
        eventContexts = List.copyOf(eventContexts);
        knownEmptyCallIds = List.copyOf(knownEmptyCallIds);
    }

    public static CallContextDataSet empty() {
        return new CallContextDataSet(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
