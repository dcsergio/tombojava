package it.sdc.tombojava.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.sdc.tombojava.job.JobState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationHistoryServiceTests {

    @Test
    void buildFunStatsSummarizesCompletedAndFailedJobs(@TempDir Path tempDir) {
        GenerationHistoryRepository repository = new GenerationHistoryRepository(
                new ObjectMapper().findAndRegisterModules(),
                tempDir,
                10
        );
        repository.save(sampleEntry("job-fast", JobState.COMPLETED, 1000L, 2, 5000, 77L, Instant.parse("2026-05-08T10:00:00Z")));
        repository.save(sampleEntry("job-slow", JobState.COMPLETED, 3000L, 5, 8000, 77L, Instant.parse("2026-05-08T11:00:00Z")));
        repository.save(sampleEntry("job-fail", JobState.FAILED, 2000L, 3, 9000, 88L, Instant.parse("2026-05-08T12:00:00Z")));

        GenerationHistoryService service = new GenerationHistoryService(repository);
        FunStatsResponse response = service.buildFunStats();

        assertEquals(3, response.totalJobs());
        assertEquals(2, response.completedJobs());
        assertEquals(1, response.failedJobs());
        assertEquals(67, response.successRatePercent());
        assertEquals(2000L, response.averageDurationMillis());

        Set<String> titles = response.highlights().stream().map(FunStatsResponse.FunStatCard::title).collect(java.util.stream.Collectors.toSet());
        assertTrue(titles.containsAll(Set.of(
                "Più veloce",
                "Generazione maratona",
                "Più ambiziosa",
                "Più ostinata",
                "Seed portafortuna"
        )));
    }

    private GenerationHistoryEntry sampleEntry(String jobId, JobState state, long durationMillis, int seriesCount, int maxAttempts, long seed, Instant completedAt) {
        Instant startedAt = completedAt.minusMillis(durationMillis);
        return new GenerationHistoryEntry(
                jobId,
                state,
                state == JobState.COMPLETED ? 100 : 85,
                state == JobState.COMPLETED ? "PDF creato con successo" : "Generazione interrotta",
                jobId + ".pdf",
                seed,
                seriesCount,
                "C:/tmp/" + jobId + ".pdf",
                startedAt,
                completedAt,
                durationMillis,
                maxAttempts,
                state == JobState.COMPLETED ? seriesCount : Math.max(0, seriesCount - 1),
                List.of(4, 6),
                3,
                state == JobState.COMPLETED ? null : "Conflitto di compatibilita'",
                state == JobState.COMPLETED ? List.of(SeriesSnapshot.fromSeries(TestSeriesFactory.series())) : List.of()
        );
    }
}

