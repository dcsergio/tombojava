package it.sdc.tombojava.tombola;

import java.util.List;
import java.util.Optional;

public record GenerationResult(
        GenerationRequest request,
        List<TombolaSeries> series,
        List<Integer> attemptsPerAcceptedSeries,
        int maxSharedNumbersAcrossGeneratedRows,
        CompatibilityConflict blockingConflict,
        String message
) {

    public GenerationResult {
        series = List.copyOf(series);
        attemptsPerAcceptedSeries = List.copyOf(attemptsPerAcceptedSeries);
    }

    public boolean successful() {
        return series.size() == request.seriesCount();
    }

    public int generatedSeriesCount() {
        return series.size();
    }

    public Optional<CompatibilityConflict> blockingConflictOptional() {
        return Optional.ofNullable(blockingConflict);
    }
}

