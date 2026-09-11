package com.wallstreetreceipts.api.application.scoring;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.wallstreetreceipts.api.application.port.out.ScoringReceiptRepository;
import com.wallstreetreceipts.api.application.port.out.ScoringReceiptRepository.Entry;
import com.wallstreetreceipts.api.application.port.out.AnalystCallRepository;
import com.wallstreetreceipts.api.domain.market.DataMode;

/** Explicit in-process DEMO append; no scheduler, provider activation, or HTTP writer. */
@Service
public class ScoringReceiptService {
    private final ScoringReceiptRepository repository;
    private final ScoringLedgerVerifier ledger;
    private final AnalystCallRepository calls;
    private final Clock clock;
    private final EndpointScoringEvaluator evaluator = new EndpointScoringEvaluator();
    public ScoringReceiptService(ScoringReceiptRepository repository, ScoringLedgerVerifier ledger, AnalystCallRepository calls, Clock clock) {
        this.repository = repository; this.ledger = ledger; this.calls = calls; this.clock = clock;
    }
    public record Verified(Entry stored, EndpointScoringEvaluator.Receipt evaluation, String snapshotProvenanceId) {}
    public record Page(List<Verified> items, boolean hasMore) { public Page { items = List.copyOf(items); } }
    public static class Missing extends RuntimeException { public Missing() { super("DEMO scoring receipt not found"); } }
    public static class Unavailable extends RuntimeException { public Unavailable() { super("DEMO scoring receipt verification unavailable"); } }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Verified append(EndpointScoringInput input, String snapshotId) {
        String callId = input.horizon().basis().callId();
        // Transaction-scoped lock makes duplicate submissions serialize before the identity lookup.
        if (!repository.lockCall(callId)) throw new Missing();
        var binding = ledger.verify(input, snapshotId);
        var evaluated = evaluator.evaluate(input);
        var existing = repository.findByIdentity(evaluated.inputFingerprint(), binding.fingerprint());
        if (existing.isPresent()) return replay(existing.get());
        var recordedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (recordedAt.isBefore(input.evaluationAsOf())) throw new IllegalArgumentException("Evaluation cannot be in the future");
        var entry = new Entry(UUID.randomUUID(), callId, snapshotId, input.horizon().basis().basisRevisionId(), binding.revisionSequence(),
                evaluated.methodologyId(), evaluated.methodologyVersion(), evaluated.methodologyDefinitionHash(),
                EndpointScoringMethodology.canonicalDefinition(), evaluated.inputFingerprint(), binding.fingerprint(),
                input.evaluationAsOf(), recordedAt, EndpointScoringInputCodec.encode(input));
        repository.insert(entry);
        return new Verified(entry, evaluated, binding.snapshotProvenanceId());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Verified find(String callId, UUID id) {
        try {
            requireDemoCall(callId);
            return replay(repository.find(callId, id).orElseThrow(Missing::new));
        } catch (Missing missing) { throw missing; }
        catch (RuntimeException failure) { throw new Unavailable(); }
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page recent(String callId) {
        try {
            requireDemoCall(callId);
            var rows = repository.recent(callId);
            // Verify every returned item before publishing anything. Never silently skip a broken row.
            return new Page(rows.stream().limit(20).map(this::replay).toList(), rows.size() > 20);
        } catch (Missing missing) { throw missing; }
        catch (RuntimeException failure) { throw new Unavailable(); }
    }
    private void requireDemoCall(String id) {
        var call = calls.findById(id).orElseThrow(Missing::new).call();
        if (call.dataMode() != DataMode.DEMO) throw new Missing();
    }
    private Verified replay(Entry row) {
        var bytes = row.inputBytes();
        var input = EndpointScoringInputCodec.decode(bytes);
        var basis = input.horizon().basis();
        if (!row.callId().equals(basis.callId()) || !Objects.equals(row.basisRevisionId(), basis.basisRevisionId())
                || !row.evaluationAsOf().equals(input.evaluationAsOf()) || row.recordedAt().isBefore(row.evaluationAsOf())
                || !row.inputFingerprint().equals(EndpointScoringInputCodec.hash(bytes))
                || !row.methodologyDefinition().equals(EndpointScoringMethodology.canonicalDefinition())) throw new Unavailable();
        var binding = ledger.verify(input, row.snapshotId());
        if (!binding.fingerprint().equals(row.ledgerFingerprint()) || !Objects.equals(binding.revisionSequence(), row.basisRevisionSequence())) throw new Unavailable();
        var evaluated = evaluator.evaluate(row.methodologyId(), row.methodologyVersion(), row.methodologyDefinitionHash(), input);
        return new Verified(row, evaluated, binding.snapshotProvenanceId());
    }
}
