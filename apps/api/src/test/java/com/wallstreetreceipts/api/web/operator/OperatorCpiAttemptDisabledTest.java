package com.wallstreetreceipts.api.web.operator;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.wallstreetreceipts.api.application.cpi.CpiAttemptQueryService;
import com.wallstreetreceipts.api.application.cpi.CpiCollectionJob;
import com.wallstreetreceipts.api.application.cpi.CpiRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OperatorCpiAttemptDisabledTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;
    @MockitoBean CpiRepository repository;
    @Test void defaultsExposeNoOperatorCpiRouteQueryServiceOrCollector() throws Exception {
        for (var path : new String[]{OperatorCpiAttemptController.PATH,
                OperatorCpiAttemptController.PATH + "/00000000-0000-0000-0000-000000000001"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound()).andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
        }
        assertThat(context.getBeansOfType(OperatorCpiAttemptController.class)).isEmpty();
        assertThat(context.getBeansOfType(CpiAttemptQueryService.class)).isEmpty();
        assertThat(context.getBeansOfType(CpiCollectionJob.class)).isEmpty();
        verifyNoInteractions(repository);
    }
}
