package com.wallstreetreceipts.api.web.call;

import java.util.regex.Pattern;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wallstreetreceipts.api.application.call.AnalystCallContextQueryService;

@RestController
@RequestMapping("/v1/calls")
public class AnalystCallContextController {

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");

    private final AnalystCallContextQueryService queryService;

    public AnalystCallContextController(AnalystCallContextQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{id}/context")
    public AnalystCallResponses.Context context(@PathVariable String id) {
        if (!IDENTIFIER.matcher(id).matches()) {
            throw new IllegalArgumentException("id is not a valid opaque identifier");
        }
        return AnalystCallResponseMapper.toContext(queryService.findByCallId(id));
    }
}
