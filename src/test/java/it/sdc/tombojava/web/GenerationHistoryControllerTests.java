package it.sdc.tombojava.web;

import it.sdc.tombojava.history.FunStatsResponse;
import it.sdc.tombojava.history.GenerationHistoryEntry;
import it.sdc.tombojava.history.GenerationHistoryService;
import it.sdc.tombojava.history.SeriesSnapshot;
import it.sdc.tombojava.job.JobState;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GenerationHistoryControllerTests {

    @Test
    void historyEndpointReturnsRecentJobs() throws Exception {
        GenerationHistoryService historyService = mock(GenerationHistoryService.class);
        when(historyService.listRecent(5)).thenReturn(List.of(sampleEntry()));

        MockMvc mockMvc = buildMockMvc(historyService);

        mockMvc.perform(get("/api/history").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobId").value("job-1"))
                .andExpect(jsonPath("$[0].state").value("COMPLETED"))
                .andExpect(jsonPath("$[0].seriesAvailable").value(true));
    }

    @Test
    void funStatsEndpointReturnsHighlights() throws Exception {
        GenerationHistoryService historyService = mock(GenerationHistoryService.class);
        when(historyService.buildFunStats()).thenReturn(new FunStatsResponse(
                2,
                1,
                1,
                50,
                1200L,
                List.of(new FunStatsResponse.FunStatCard("Più veloce", "1,200 s", "Seed 77", "job-1"))
        ));

        MockMvc mockMvc = buildMockMvc(historyService);

        mockMvc.perform(get("/api/stats/fun"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalJobs").value(2))
                .andExpect(jsonPath("$.highlights[0].title").value("Più veloce"));
    }

    private MockMvc buildMockMvc(GenerationHistoryService historyService) {
        return MockMvcBuilders.standaloneSetup(new GenerationHistoryController(historyService))
                .build();
    }

    private GenerationHistoryEntry sampleEntry() {
        return new GenerationHistoryEntry(
                "job-1",
                JobState.COMPLETED,
                100,
                "PDF creato con successo",
                "job-1.pdf",
                77L,
                2,
                "C:/tmp/job-1.pdf",
                Instant.parse("2026-05-08T09:59:58Z"),
                Instant.parse("2026-05-08T10:00:00Z"),
                2000L,
                5000,
                2,
                List.of(3, 5),
                1,
                null,
                List.of(SeriesSnapshot.fromSeries(it.sdc.tombojava.history.TestSeriesFactory.series()))
        );
    }
}

