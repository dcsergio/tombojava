package it.sdc.tombojava.web;

import it.sdc.tombojava.history.FunStatsResponse;
import it.sdc.tombojava.history.GenerationHistoryEntry;
import it.sdc.tombojava.history.GenerationHistoryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api")
public class GenerationHistoryController {

    private final GenerationHistoryService historyService;

    public GenerationHistoryController(GenerationHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/history")
    public List<HistoryJobResponse> history(@RequestParam(defaultValue = "10") int limit) {
        return historyService.listRecent(limit).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/history/{jobId}")
    public HistoryJobResponse historyDetail(@PathVariable String jobId) {
        return historyService.findJob(jobId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job storico non trovato"));
    }

    @GetMapping("/stats/fun")
    public FunStatsResponse funStats() {
        return historyService.buildFunStats();
    }

    private HistoryJobResponse toResponse(GenerationHistoryEntry entry) {
        return new HistoryJobResponse(
                entry.jobId(),
                entry.state().name(),
                entry.progress(),
                entry.message(),
                entry.fileName(),
                entry.toJobStatus().downloadUrl(),
                entry.seed(),
                entry.seriesCount(),
                entry.startedAt(),
                entry.completedAt(),
                entry.durationMillis(),
                entry.maxSeriesAttempts(),
                entry.generatedSeriesCount(),
                entry.attemptsPerAcceptedSeries(),
                entry.maxSharedNumbersAcrossGeneratedRows(),
                entry.blockingConflictDescription(),
                entry.seriesAvailable()
        );
    }

    public record HistoryJobResponse(
            String jobId,
            String state,
            int progress,
            String message,
            String fileName,
            String downloadUrl,
            Long seed,
            Integer seriesCount,
            Instant startedAt,
            Instant completedAt,
            Long durationMillis,
            Integer maxSeriesAttempts,
            Integer generatedSeriesCount,
            List<Integer> attemptsPerAcceptedSeries,
            Integer maxSharedNumbersAcrossGeneratedRows,
            String blockingConflictDescription,
            boolean seriesAvailable
    ) {
    }
}

