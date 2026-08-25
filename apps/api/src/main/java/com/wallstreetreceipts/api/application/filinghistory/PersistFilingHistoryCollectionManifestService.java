package com.wallstreetreceipts.api.application.filinghistory;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureRepository;
import com.wallstreetreceipts.api.application.port.out.FilingHistoryCollectionManifestAppendOutcome;
import com.wallstreetreceipts.api.application.port.out.FilingHistoryCollectionManifestRepository;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureRepository;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;

/** Assembles already-durable SEC evidence without provider or latest-capture lookup. */
public final class PersistFilingHistoryCollectionManifestService {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private final FilingCatalogCaptureRepository rootRepository;
    private final HistoricalFilingSegmentCaptureRepository segmentRepository;
    private final FilingHistoryCollectionManifestRepository manifestRepository;
    private final Clock clock;

    public PersistFilingHistoryCollectionManifestService(
            FilingCatalogCaptureRepository rootRepository,
            HistoricalFilingSegmentCaptureRepository segmentRepository,
            FilingHistoryCollectionManifestRepository manifestRepository,
            Clock clock) {
        this.rootRepository = Objects.requireNonNull(rootRepository, "rootRepository");
        this.segmentRepository = Objects.requireNonNull(
                segmentRepository, "segmentRepository");
        this.manifestRepository = Objects.requireNonNull(
                manifestRepository, "manifestRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public FilingHistoryCollectionManifestAppendOutcome persist(
            String rootCaptureId,
            List<DescriptorCaptureSelection> selections) {
        requireCaptureId(rootCaptureId, "rootCaptureId");
        Objects.requireNonNull(selections, "selections must not be null");
        List<DescriptorCaptureSelection> ownedSelections;
        try {
            ownedSelections = List.copyOf(selections);
        } catch (NullPointerException exception) {
            throw new NullPointerException("selections must not contain null");
        }
        requireUniqueSelections(ownedSelections);

        FilingCatalogCapture root = rootRepository.findByCaptureId(rootCaptureId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "durable filing catalog root capture was not found"));
        if (!rootCaptureId.equals(root.captureId())) {
            throw new IllegalStateException(
                    "filing catalog repository returned another root capture identity");
        }
        List<HistoricalFilingSegmentCapture> selectedCaptures = ownedSelections.stream()
                .map(selection -> exactSegment(root, selection))
                .toList();
        FilingHistoryCollectionManifest manifest = FilingHistoryCollectionManifest.assemble(
                root,
                selectedCaptures,
                clock.instant().truncatedTo(ChronoUnit.MICROS));
        return manifestRepository.append(manifest);
    }

    private HistoricalFilingSegmentCapture exactSegment(
            FilingCatalogCapture root,
            DescriptorCaptureSelection selection) {
        if (selection.descriptorOrdinal() >= root.catalog().historicalSegments().size()) {
            throw new IllegalArgumentException(
                    "descriptorOrdinal must identify an advertised root descriptor");
        }
        HistoricalFilingSegmentCapture capture = segmentRepository
                .findByCaptureId(selection.segmentCaptureId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "durable historical segment capture was not found"));
        if (!selection.segmentCaptureId().equals(capture.captureId())) {
            throw new IllegalStateException(
                    "historical segment repository returned another capture identity");
        }
        if (capture.segment().descriptorOrdinal() != selection.descriptorOrdinal()) {
            throw new IllegalArgumentException(
                    "segment capture must match the explicitly selected descriptorOrdinal");
        }
        return capture;
    }

    private static void requireUniqueSelections(
            List<DescriptorCaptureSelection> selections) {
        Set<Integer> ordinals = new HashSet<>();
        Set<String> captureIds = new HashSet<>();
        for (DescriptorCaptureSelection selection : selections) {
            if (!ordinals.add(selection.descriptorOrdinal())) {
                throw new IllegalArgumentException(
                        "descriptorOrdinal must be unique within selections");
            }
            if (!captureIds.add(selection.segmentCaptureId())) {
                throw new IllegalArgumentException(
                        "segmentCaptureId must be unique within selections");
            }
        }
    }

    private static void requireCaptureId(String value, String field) {
        if (value == null || !SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    field + " must be lowercase SHA-256 hex");
        }
    }

    public record DescriptorCaptureSelection(
            int descriptorOrdinal,
            String segmentCaptureId) {

        public DescriptorCaptureSelection {
            if (descriptorOrdinal < 0) {
                throw new IllegalArgumentException(
                        "descriptorOrdinal must be nonnegative");
            }
            requireCaptureId(segmentCaptureId, "segmentCaptureId");
        }
    }
}
