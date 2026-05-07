package it.sdc.tombojava.history;

import it.sdc.tombojava.job.GenerationJobStatus;
import it.sdc.tombojava.job.JobState;
import it.sdc.tombojava.tombola.TombolaSeries;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record GenerationHistoryEntry(
        String jobId,
        JobState state,
        int progress,
        String message,
        String fileName,
        Long seed,
        Integer seriesCount,
        String pdfPath,
        Instant startedAt,
        Instant completedAt,
        Long durationMillis,
        Integer maxSeriesAttempts,
        Integer generatedSeriesCount,
        List<Integer> attemptsPerAcceptedSeries,
        Integer maxSharedNumbersAcrossGeneratedRows,
        String blockingConflictDescription,
        List<SeriesSnapshot> seriesSnapshots
) {

    public GenerationHistoryEntry {
        attemptsPerAcceptedSeries = attemptsPerAcceptedSeries == null ? List.of() : List.copyOf(attemptsPerAcceptedSeries);
        seriesSnapshots = seriesSnapshots == null ? List.of() : List.copyOf(seriesSnapshots);
    }

    public GenerationJobStatus toJobStatus() {
        return new GenerationJobStatus(
                jobId,
                state,
                progress,
                message,
                fileName,
                state == JobState.COMPLETED ? "/api/jobs/" + jobId + "/download" : null,
                seed,
                seriesCount
        );
    }

    public boolean seriesAvailable() {
        return !seriesSnapshots.isEmpty();
    }

    public Optional<TombolaSeries> findSeries(int seriesNumber) {
        if (seriesNumber < 1 || seriesNumber > seriesSnapshots.size()) {
            return Optional.empty();
        }
        return Optional.of(seriesSnapshots.get(seriesNumber - 1).toSeries());
    }
}

