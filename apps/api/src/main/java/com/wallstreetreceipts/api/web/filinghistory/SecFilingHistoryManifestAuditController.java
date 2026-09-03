package com.wallstreetreceipts.api.web.filinghistory;

import java.util.Set;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wallstreetreceipts.api.application.filinghistory.InvalidSecFilingHistoryManifestAuditQueryException;
import com.wallstreetreceipts.api.application.filinghistory.SecFilingHistoryManifestAuditQueryService;
import com.wallstreetreceipts.api.web.filinghistory.SecFilingHistoryManifestAuditResponses.Accession;
import com.wallstreetreceipts.api.web.filinghistory.SecFilingHistoryManifestAuditResponses.Descriptor;
import com.wallstreetreceipts.api.web.filinghistory.SecFilingHistoryManifestAuditResponses.Occurrence;
import com.wallstreetreceipts.api.web.filinghistory.SecFilingHistoryManifestAuditResponses.Page;
import com.wallstreetreceipts.api.web.filinghistory.SecFilingHistoryManifestAuditResponses.Summary;

@RestController
@RequestMapping(SecFilingHistoryManifestAuditController.PATH)
public class SecFilingHistoryManifestAuditController {

    static final String PATH = "/v1/sec/filing-history/manifests";

    private static final String EVALUATION_AS_OF = "evaluationAsOf";
    private static final String PAGE = "page";
    private static final String SIZE = "size";
    private static final Set<String> SUMMARY_PARAMETERS = Set.of(EVALUATION_AS_OF);
    private static final Set<String> PAGE_PARAMETERS =
            Set.of(EVALUATION_AS_OF, PAGE, SIZE);

    private final SecFilingHistoryManifestAuditQueryService queryService;

    public SecFilingHistoryManifestAuditController(
            SecFilingHistoryManifestAuditQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{manifestId}")
    public ResponseEntity<Summary> summary(
            @PathVariable String manifestId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        AuditQuery query = AuditQuery.summary(queryParameters);
        return noStore(SecFilingHistoryManifestAuditResponses.summary(
                queryService.summary(manifestId, query.evaluationAsOf())));
    }

    @GetMapping("/{manifestId}/descriptors")
    public ResponseEntity<Page<Descriptor>> descriptors(
            @PathVariable String manifestId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        AuditQuery query = AuditQuery.page(queryParameters);
        return noStore(SecFilingHistoryManifestAuditResponses.descriptors(
                queryService.descriptors(
                        manifestId,
                        query.evaluationAsOf(),
                        query.page(),
                        query.size())));
    }

    @GetMapping("/{manifestId}/accessions")
    public ResponseEntity<Page<Accession>> accessions(
            @PathVariable String manifestId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        AuditQuery query = AuditQuery.page(queryParameters);
        return noStore(SecFilingHistoryManifestAuditResponses.accessions(
                queryService.accessions(
                        manifestId,
                        query.evaluationAsOf(),
                        query.page(),
                        query.size())));
    }

    @GetMapping("/{manifestId}/occurrences")
    public ResponseEntity<Page<Occurrence>> occurrences(
            @PathVariable String manifestId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        AuditQuery query = AuditQuery.page(queryParameters);
        return noStore(SecFilingHistoryManifestAuditResponses.occurrences(
                queryService.occurrences(
                        manifestId,
                        query.evaluationAsOf(),
                        query.page(),
                        query.size())));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    private record AuditQuery(String evaluationAsOf, String page, String size) {

        private static AuditQuery summary(
                MultiValueMap<String, String> queryParameters) {
            rejectUnknown(queryParameters, SUMMARY_PARAMETERS);
            return new AuditQuery(
                    single(queryParameters, EVALUATION_AS_OF, true),
                    null,
                    null);
        }

        private static AuditQuery page(
                MultiValueMap<String, String> queryParameters) {
            rejectUnknown(queryParameters, PAGE_PARAMETERS);
            return new AuditQuery(
                    single(queryParameters, EVALUATION_AS_OF, true),
                    single(queryParameters, PAGE, false),
                    single(queryParameters, SIZE, false));
        }

        private static void rejectUnknown(
                MultiValueMap<String, String> queryParameters,
                Set<String> allowed) {
            if (queryParameters == null) {
                throw invalid();
            }
            for (String parameter : queryParameters.keySet()) {
                if (!allowed.contains(parameter)) {
                    throw invalid();
                }
            }
        }

        private static String single(
                MultiValueMap<String, String> queryParameters,
                String name,
                boolean required) {
            java.util.List<String> values = queryParameters.get(name);
            if (values == null) {
                if (required) {
                    throw invalid();
                }
                return null;
            }
            if (values.size() != 1 || values.getFirst() == null
                    || values.getFirst().isEmpty()) {
                throw invalid();
            }
            return values.getFirst();
        }

        private static InvalidSecFilingHistoryManifestAuditQueryException invalid() {
            return new InvalidSecFilingHistoryManifestAuditQueryException(
                    "The closed manifest audit query grammar was not satisfied");
        }
    }
}
