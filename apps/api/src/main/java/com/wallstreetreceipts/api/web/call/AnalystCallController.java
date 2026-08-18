package com.wallstreetreceipts.api.web.call;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wallstreetreceipts.api.application.call.AnalystCallFilter;
import com.wallstreetreceipts.api.application.call.AnalystCallQueryService;
import com.wallstreetreceipts.api.application.call.AnalystCallRevisionQueryService;
import com.wallstreetreceipts.api.application.call.CallSortField;
import com.wallstreetreceipts.api.application.call.SortOrder;
import com.wallstreetreceipts.api.domain.call.CallDirection;
import com.wallstreetreceipts.api.domain.call.CallStatus;
import com.wallstreetreceipts.api.domain.market.DataMode;

@RestController
@RequestMapping("/v1/calls")
public class AnalystCallController {

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");
    private static final Pattern TICKER = Pattern.compile("^[A-Za-z0-9.^/-]{1,24}$");

    private final AnalystCallQueryService queryService;
    private final AnalystCallRevisionQueryService revisionQueryService;

    public AnalystCallController(
            AnalystCallQueryService queryService,
            AnalystCallRevisionQueryService revisionQueryService) {
        this.queryService = queryService;
        this.revisionQueryService = revisionQueryService;
    }

    @GetMapping
    public AnalystCallResponses.Page list(
            @RequestParam(required = false) String assetId,
            @RequestParam(required = false) String ticker,
            @RequestParam(required = false) String institutionId,
            @RequestParam(required = false) String analystId,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dataMode,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order) {
        AnalystCallFilter filter = new AnalystCallFilter(
                optionalIdentifier(assetId, "assetId"), optionalTicker(ticker),
                optionalIdentifier(institutionId, "institutionId"), optionalIdentifier(analystId, "analystId"),
                optionalEnum(CallDirection.class, direction, "direction"),
                optionalEnum(CallStatus.class, status, "status"),
                optionalEnum(DataMode.class, dataMode, "dataMode"),
                optionalInstant(from, "from"), optionalInstant(to, "to"),
                integerOrDefault(page, 0, "page"), integerOrDefault(size, 25, "size"),
                CallSortField.fromApiName(valueOrDefault(sort, "eventTime")),
                SortOrder.fromApiName(valueOrDefault(order, "desc")));
        return AnalystCallResponseMapper.toPage(queryService.findAll(filter));
    }

    @GetMapping("/{id}")
    public AnalystCallResponses.Detail detail(@PathVariable String id) {
        return AnalystCallResponseMapper.toDetail(queryService.findById(requiredIdentifier(id, "id")));
    }

    @GetMapping("/{id}/revisions")
    public List<AnalystCallResponses.Revision> revisions(@PathVariable String id) {
        return AnalystCallResponseMapper.toRevisions(
                revisionQueryService.findByCallId(requiredIdentifier(id, "id")));
    }

    private static String optionalIdentifier(String value, String field) {
        if (value == null) {
            return null;
        }
        return requiredIdentifier(value, field);
    }

    private static String requiredIdentifier(String value, String field) {
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is not a valid opaque identifier");
        }
        return value;
    }

    private static String optionalTicker(String value) {
        if (value == null) {
            return null;
        }
        if (!TICKER.matcher(value).matches()) {
            throw new IllegalArgumentException("ticker is not valid");
        }
        return value;
    }

    private static Instant optionalInstant(String value, String field) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " must be an RFC 3339 instant", exception);
        }
    }

    private static <E extends Enum<E>> E optionalEnum(Class<E> type, String value, String field) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unsupported " + field + ": " + value, exception);
        }
    }

    private static int integerOrDefault(String value, int defaultValue, String field) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be an integer", exception);
        }
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }
}
