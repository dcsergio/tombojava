package it.sdc.tombojava.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.sdc.tombojava.job.JobState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationHistoryRepositoryTests {

    @Test
    void saveAndFindByJobIdRoundTripsSeriesSnapshots(@TempDir Path tempDir) {
        GenerationHistoryRepository repository = new GenerationHistoryRepository(
                new ObjectMapper().findAndRegisterModules(),
                tempDir,
                10
        );
        GenerationHistoryEntry entry = sampleEntry("job-1", Instant.parse("2026-05-08T10:15:30Z"), 1500L);

        repository.save(entry);

        GenerationHistoryEntry restored = repository.findByJobId("job-1").orElseThrow();
        assertEquals(JobState.COMPLETED, restored.state());
        assertEquals(1, restored.seriesSnapshots().size());
        assertTrue(restored.findSeries(1).isPresent());
        assertEquals(6, restored.findSeries(1).orElseThrow().cards().size());
    }

    @Test
    void retentionKeepsNewestEntriesAndIgnoresCorruptedFiles(@TempDir Path tempDir) throws Exception {
        GenerationHistoryRepository repository = new GenerationHistoryRepository(
                new ObjectMapper().findAndRegisterModules(),
                tempDir,
                2
        );

        repository.save(sampleEntry("job-1", Instant.parse("2026-05-08T10:00:00Z"), 1000L));
        repository.save(sampleEntry("job-2", Instant.parse("2026-05-08T11:00:00Z"), 1200L));
        repository.save(sampleEntry("job-3", Instant.parse("2026-05-08T12:00:00Z"), 1400L));
        Files.writeString(tempDir.resolve("broken.json"), "{not-valid-json}");

        List<GenerationHistoryEntry> entries = repository.listAll();

        assertEquals(2, entries.size());
        assertEquals(List.of("job-3", "job-2"), entries.stream().map(GenerationHistoryEntry::jobId).toList());
        assertTrue(repository.findByJobId("job-1").isEmpty());
    }

    private GenerationHistoryEntry sampleEntry(String jobId, Instant completedAt, long durationMillis) {
        Instant startedAt = completedAt.minusMillis(durationMillis);
        return new GenerationHistoryEntry(
                jobId,
                JobState.COMPLETED,
                100,
                "PDF creato con successo",
                jobId + ".pdf",
                123456L,
                1,
                "C:/tmp/" + jobId + ".pdf",
                startedAt,
                completedAt,
                durationMillis,
                5000,
                1,
                List.of(4),
                2,
                null,
                List.of(SeriesSnapshot.fromSeries(TestSeriesFactory.series()))
        );
    }
}

