package com.wallstreetreceipts.api.support;

import java.nio.charset.StandardCharsets;
/** Synthetic test indices; never a provider response or deployable fixture. */
public final class CpiTestFixture {
    private CpiTestFixture() {}
    public static byte[] bytes() { return json().getBytes(StandardCharsets.UTF_8); }
    public static String json() {
        String rows = """
            [{"year":"2026","period":"M07","value":"103.050","footnotes":[{}]},
             {"year":"2026","period":"M06","value":"-","footnotes":[{"code":"X","text":"Synthetic missing value"}]},
             {"year":"2025","period":"M07","value":"100.000","footnotes":[]}]
            """;
        return "{\"status\":\"REQUEST_SUCCEEDED\",\"responseTime\":1,\"message\":[],\"Results\":{\"series\":["
                + "{\"seriesID\":\"CUUR0000SA0\",\"data\":" + rows + "},"
                + "{\"seriesID\":\"CUUR0000SA0L1E\",\"data\":" + rows + "}]}}";
    }
}
