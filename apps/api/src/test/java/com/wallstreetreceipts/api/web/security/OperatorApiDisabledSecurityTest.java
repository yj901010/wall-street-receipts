package com.wallstreetreceipts.api.web.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OperatorApiDisabledSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void disabledOperatorApiFallsThroughToOrdinaryNotFoundWithoutAuthChallenge()
            throws Exception {
        mockMvc.perform(post("/internal/v1/sec/collection-attempts/root")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operatorRequestId": "00000000-0000-4000-8000-000000000001",
                                  "cik": "0000320193"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }
}
