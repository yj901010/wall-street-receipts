package com.wallstreetreceipts.api.application.filinghistory;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import com.wallstreetreceipts.api.application.port.out.FilingHistoryCollectionManifestRepository;
import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest;

/** Exact, point-in-time read boundary over one fully verified immutable manifest. */
public final class SecFilingHistoryManifestAuditQueryService {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAX_PAGE_SIZE = 100;

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern UTC_MICROSECOND_INSTANT = Pattern.compile(
            "[0-9]{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12][0-9]|3[01])T"
                    + "(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]"
                    + "(?:\\.[0-9]{1,6})?Z");
    private static final Pattern CANONICAL_UNSIGNED_DECIMAL =
            Pattern.compile("0|[1-9][0-9]*");

    private final FilingHistoryCollectionManifestRepository repository;

    public SecFilingHistoryManifestAuditQueryService(
            FilingHistoryCollectionManifestRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public AuditResult summary(String manifestId, String evaluationAsOf) {
        return findVerified(manifestId, evaluationAsOf);
    }

    public AuditPage<FilingHistoryCollectionManifest.DescriptorMember> descriptors(
            String manifestId,
            String evaluationAsOf,
            String page,
            String size) {
        PageRequest pageRequest = requirePageRequest(page, size);
        AuditResult audit = findVerified(manifestId, evaluationAsOf);
        return page(
                audit,
                audit.manifest().descriptors(),
                pageRequest,
                "descriptorOrdinal");
    }

    public AuditPage<FilingHistoryCollectionManifest.AccessionGroup> accessions(
            String manifestId,
            String evaluationAsOf,
            String page,
            String size) {
        PageRequest pageRequest = requirePageRequest(page, size);
        AuditResult audit = findVerified(manifestId, evaluationAsOf);
        return page(
                audit,
                audit.manifest().accessionGroups(),
                pageRequest,
                "groupOrdinal");
    }

    public AuditPage<FilingHistoryCollectionManifest.FilingOccurrence> occurrences(
            String manifestId,
            String evaluationAsOf,
            String page,
            String size) {
        PageRequest pageRequest = requirePageRequest(page, size);
        AuditResult audit = findVerified(manifestId, evaluationAsOf);
        return page(
                audit,
                audit.manifest().occurrences(),
                pageRequest,
                "occurrenceOrdinal");
    }

    private AuditResult findVerified(String manifestId, String evaluationAsOfText) {
        requireManifestId(manifestId);
        Instant evaluationAsOf = requireEvaluationAsOf(evaluationAsOfText);

        Optional<FilingHistoryCollectionManifest> found;
        try {
            found = repository.findByManifestIdAtOrBefore(manifestId, evaluationAsOf);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Manifest repository rejected a validated audit lookup", exception);
        }
        if (found == null) {
            throw new IllegalStateException("Manifest repository returned a null lookup result");
        }
        FilingHistoryCollectionManifest manifest = found.orElseThrow(
                SecFilingHistoryManifestAuditNotFoundException::new);
        if (!manifestId.equals(manifest.manifestId())
                || manifest.assembledAt().isAfter(evaluationAsOf)) {
            throw new IllegalStateException(
                    "Manifest repository returned evidence outside the exact audit lookup");
        }
        return new AuditResult(manifest, evaluationAsOf);
    }

    private static void requireManifestId(String manifestId) {
        if (manifestId == null || !SHA_256.matcher(manifestId).matches()) {
            throw invalid("manifestId must be lowercase SHA-256 hex");
        }
    }

    private static Instant requireEvaluationAsOf(String value) {
        if (value == null || !UTC_MICROSECOND_INSTANT.matcher(value).matches()) {
            throw invalid(
                    "evaluationAsOf must be a UTC Z instant with at most microsecond precision");
        }
        try {
            Instant parsed = Instant.parse(value);
            PersistentInstant.requireMicrosecondPrecision(parsed, "evaluationAsOf");
            return parsed;
        } catch (DateTimeException | IllegalArgumentException exception) {
            throw invalid(
                    "evaluationAsOf must be a valid UTC Z instant with at most microsecond precision");
        }
    }

    private static int requirePageNumber(String value) {
        return value == null
                ? DEFAULT_PAGE
                : canonicalInt(value, "page", 0, Integer.MAX_VALUE);
    }

    private static int requirePageSize(String value) {
        return value == null
                ? DEFAULT_PAGE_SIZE
                : canonicalInt(value, "size", 1, MAX_PAGE_SIZE);
    }

    private static PageRequest requirePageRequest(String page, String size) {
        return new PageRequest(requirePageNumber(page), requirePageSize(size));
    }

    private static int canonicalInt(String value, String field, int minimum, int maximum) {
        if (!CANONICAL_UNSIGNED_DECIMAL.matcher(value).matches()) {
            throw invalid(field + " must use canonical unsigned decimal form");
        }
        final int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw invalid(field + " is outside the supported range");
        }
        if (parsed < minimum || parsed > maximum) {
            throw invalid(field + " is outside the supported range");
        }
        return parsed;
    }

    private static <T> AuditPage<T> page(
            AuditResult audit,
            List<T> completeItems,
            PageRequest pageRequest,
            String orderField) {
        int number = pageRequest.number();
        int size = pageRequest.size();
        long totalElements = completeItems.size();
        int totalPages = totalElements == 0
                ? 0
                : (int) ((totalElements + size - 1L) / size);
        long start = (long) number * size;
        List<T> items;
        if (start >= totalElements) {
            items = List.of();
        } else {
            int fromIndex = Math.toIntExact(start);
            int toIndex = (int) Math.min(start + size, totalElements);
            items = List.copyOf(completeItems.subList(fromIndex, toIndex));
        }
        boolean first = number == 0;
        boolean last = totalPages == 0 || number >= totalPages - 1;
        return new AuditPage<>(
                audit,
                items,
                number,
                size,
                totalElements,
                totalPages,
                first,
                last,
                orderField);
    }

    private static InvalidSecFilingHistoryManifestAuditQueryException invalid(
            String message) {
        return new InvalidSecFilingHistoryManifestAuditQueryException(message);
    }

    private record PageRequest(int number, int size) {
    }

    public record AuditResult(
            FilingHistoryCollectionManifest manifest,
            Instant evaluationAsOf) {

        public AuditResult {
            Objects.requireNonNull(manifest, "manifest");
            Objects.requireNonNull(evaluationAsOf, "evaluationAsOf");
        }
    }

    public record AuditPage<T>(
            AuditResult audit,
            List<T> items,
            int number,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last,
            String orderField) {

        public AuditPage {
            Objects.requireNonNull(audit, "audit");
            items = List.copyOf(items);
            Objects.requireNonNull(orderField, "orderField");
        }
    }
}
