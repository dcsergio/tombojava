package it.sdc.tombojava.job;

import it.sdc.tombojava.history.GenerationHistoryEntry;
import it.sdc.tombojava.history.GenerationHistoryRepository;
import it.sdc.tombojava.history.SeriesSnapshot;
import it.sdc.tombojava.tombola.GenerationRequest;
import it.sdc.tombojava.tombola.GenerationResult;
import it.sdc.tombojava.tombola.TombolaSeries;
import it.sdc.tombojava.tombola.TombolaPdfWriter;
import it.sdc.tombojava.tombola.TombolaSeriesGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
public class GenerationJobService {

    private final TombolaSeriesGenerator generator;
    private final TombolaPdfWriter pdfWriter;
    private final Path outputDir;
    private final int defaultMaxAttemptsPerSeries;
    private final boolean historyStoreSeries;
    private final GenerationHistoryRepository historyRepository;
    private final ExecutorService executor;
    private final Map<String, JobRuntimeState> jobs;

    @Autowired
    public GenerationJobService(
            TombolaSeriesGenerator generator,
            TombolaPdfWriter pdfWriter,
            @Value("${tombojava.output-dir:out}") String outputDir,
            @Value("${tombojava.max-series-attempts-default:5000}") int defaultMaxAttemptsPerSeries,
            @Value("${tombojava.history-store-series:true}") boolean historyStoreSeries,
            GenerationHistoryRepository historyRepository
    ) {
        this.generator = generator;
        this.pdfWriter = pdfWriter;
        this.outputDir = Path.of(outputDir).toAbsolutePath().normalize();
        this.defaultMaxAttemptsPerSeries = defaultMaxAttemptsPerSeries;
        this.historyStoreSeries = historyStoreSeries;
        this.historyRepository = historyRepository;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.jobs = new ConcurrentHashMap<>();
    }

    GenerationJobService(
            TombolaSeriesGenerator generator,
            TombolaPdfWriter pdfWriter,
            String outputDir,
            int defaultMaxAttemptsPerSeries
    ) {
        this(
                generator,
                pdfWriter,
                outputDir,
                defaultMaxAttemptsPerSeries,
                true,
                new GenerationHistoryRepository(Path.of(outputDir).toAbsolutePath().normalize().resolve("history"), 50)
        );
    }

    public String startJob(int seriesCount, Long seed, int maxWaitSeconds, Integer maxSeriesAttempts) {
        if (seriesCount < 1) {
            throw new IllegalArgumentException("seriesCount must be a positive integer");
        }
        if (maxWaitSeconds < 1) {
            throw new IllegalArgumentException("maxWaitSeconds must be a positive integer");
        }

        int resolvedMaxAttempts = (maxSeriesAttempts != null && maxSeriesAttempts > 0)
                ? maxSeriesAttempts
                : defaultMaxAttemptsPerSeries;

        long resolvedSeed = seed != null ? seed : ThreadLocalRandom.current().nextLong();
        String jobId = UUID.randomUUID().toString();
        String fileName = resolvedSeed + "-" + seriesCount + ".pdf";
        Path outputPath = outputDir.resolve(fileName).normalize();

        if (!outputPath.startsWith(outputDir)) {
            throw new IllegalArgumentException("Invalid output path");
        }

        JobRuntimeState state = new JobRuntimeState(jobId, resolvedSeed, seriesCount, fileName, outputPath, resolvedMaxAttempts);
        jobs.put(jobId, state);
        executor.submit(() -> runJob(state, maxWaitSeconds));

        return jobId;
    }

    public Optional<GenerationJobStatus> findStatus(String jobId) {
        JobRuntimeState state = jobs.get(jobId);
        if (state != null) {
            return Optional.of(state.toStatus());
        }
        return historyRepository.findByJobId(jobId).map(GenerationHistoryEntry::toJobStatus);
    }

    public Optional<Resource> loadPdf(String jobId) {
        JobRuntimeState state = jobs.get(jobId);
        if (state != null && state.state == JobState.COMPLETED && Files.exists(state.outputPath)) {
            return toResource(state.outputPath);
        }
        return historyRepository.findByJobId(jobId)
                .filter(entry -> entry.state() == JobState.COMPLETED && entry.pdfPath() != null)
                .map(entry -> Path.of(entry.pdfPath()).toAbsolutePath().normalize())
                .filter(Files::exists)
                .flatMap(this::toResource);
    }

    private Optional<Resource> toResource(Path path) {
        try {
            return Optional.of(new UrlResource(path.toUri()));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public Optional<TombolaSeries> findGeneratedSeries(String jobId, int seriesNumber) {
        if (seriesNumber < 1) {
            return Optional.empty();
        }
        JobRuntimeState state = jobs.get(jobId);
        if (state != null && state.state == JobState.COMPLETED && !state.generatedSeries.isEmpty()) {
            if (seriesNumber > state.generatedSeries.size()) {
                return Optional.empty();
            }
            return Optional.of(state.generatedSeries.get(seriesNumber - 1));
        }
        return historyRepository.findByJobId(jobId)
                .filter(entry -> entry.state() == JobState.COMPLETED)
                .flatMap(entry -> entry.findSeries(seriesNumber));
    }

    @PreDestroy
    void shutdownExecutor() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private void runJob(JobRuntimeState state, int maxWaitSeconds) {
        state.update(JobState.RUNNING, 5, "Avvio generazione");
        try {
            Files.createDirectories(outputDir);

            GenerationRequest request = new GenerationRequest(state.seriesCount, state.maxSeriesAttempts, state.seed);
            GenerationResult result = generator.generateSeriesBatch(
                    new Random(state.seed),
                    request,
                    Duration.ofSeconds(maxWaitSeconds),
                    (percent, message) -> state.update(JobState.RUNNING, clamp(percent, 5, 90), message)
            );

            if (!result.successful()) {
                state.finish(JobState.FAILED, state.progress, result.message());
                persistHistory(state, result);
                return;
            }

            state.generatedSeries = result.series();

            state.update(JobState.RUNNING, 95, "Scrittura PDF");
            pdfWriter.write(state.outputPath, result.series(), state.seed);
            state.finish(JobState.COMPLETED, 100, "PDF creato con successo");
            persistHistory(state, result);
        } catch (IOException ex) {
            state.finish(JobState.FAILED, state.progress, "Errore I/O: " + ex.getMessage());
            persistHistory(state, null);
        } catch (Exception ex) {
            state.finish(JobState.FAILED, state.progress, "Errore: " + ex.getMessage());
            persistHistory(state, null);
        }
    }

    private void persistHistory(JobRuntimeState state, GenerationResult result) {
        List<SeriesSnapshot> snapshots = historyStoreSeries
                ? state.generatedSeries.stream().map(SeriesSnapshot::fromSeries).toList()
                : List.of();
        historyRepository.save(new GenerationHistoryEntry(
                state.jobId,
                state.state,
                state.progress,
                state.message,
                state.fileName,
                state.seed,
                state.seriesCount,
                state.outputPath.toString(),
                state.startedAt,
                state.completedAt,
                state.durationMillis,
                state.maxSeriesAttempts,
                result != null ? result.generatedSeriesCount() : state.generatedSeries.size(),
                result != null ? result.attemptsPerAcceptedSeries() : List.of(),
                result != null ? result.maxSharedNumbersAcrossGeneratedRows() : null,
                describeBlockingConflict(result),
                snapshots
        ));
    }

    private String describeBlockingConflict(GenerationResult result) {
        if (result == null || result.blockingConflict() == null) {
            return null;
        }
        return "Serie " + result.blockingConflict().candidateSeriesNumber()
                + ", cartella " + result.blockingConflict().candidateCardNumber()
                + ", riga " + result.blockingConflict().candidateRowNumber()
                + " in conflitto con serie " + result.blockingConflict().existingSeriesNumber()
                + ", cartella " + result.blockingConflict().existingCardNumber()
                + ", riga " + result.blockingConflict().existingRowNumber()
                + " (numeri condivisi: " + result.blockingConflict().sharedNumbers() + ")";
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class JobRuntimeState {
        private final String jobId;
        private final long seed;
        private final int seriesCount;
        private final String fileName;
        private final Path outputPath;
        private final int maxSeriesAttempts;
        private final Instant startedAt;

        private volatile JobState state;
        private volatile int progress;
        private volatile String message;
        private volatile List<TombolaSeries> generatedSeries;
        private volatile Instant completedAt;
        private volatile Long durationMillis;

        private JobRuntimeState(String jobId, long seed, int seriesCount, String fileName, Path outputPath, int maxSeriesAttempts) {
            this.jobId = jobId;
            this.seed = seed;
            this.seriesCount = seriesCount;
            this.fileName = fileName;
            this.outputPath = outputPath;
            this.maxSeriesAttempts = maxSeriesAttempts;
            this.startedAt = Instant.now();
            this.state = JobState.PENDING;
            this.progress = 0;
            this.message = "In coda";
            this.generatedSeries = List.of();
            this.completedAt = null;
            this.durationMillis = null;
        }

        private void update(JobState state, int progress, String message) {
            this.state = state;
            this.progress = progress;
            this.message = message;
        }

        private void finish(JobState state, int progress, String message) {
            update(state, progress, message);
            this.completedAt = Instant.now();
            this.durationMillis = Duration.between(startedAt, completedAt).toMillis();
        }

        private GenerationJobStatus toStatus() {
            String downloadUrl = state == JobState.COMPLETED ? "/api/jobs/" + jobId + "/download" : null;
            return new GenerationJobStatus(jobId, state, progress, message, fileName, downloadUrl, seed, seriesCount);
        }
    }
}


