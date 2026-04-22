package it.sdc.tombojava.job;

import it.sdc.tombojava.tombola.GenerationRequest;
import it.sdc.tombojava.tombola.GenerationResult;
import it.sdc.tombojava.tombola.TombolaSeries;
import it.sdc.tombojava.tombola.TombolaPdfWriter;
import it.sdc.tombojava.tombola.TombolaSeriesGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class GenerationJobService {

    private final TombolaSeriesGenerator generator;
    private final TombolaPdfWriter pdfWriter;
    private final Path outputDir;
    private final int defaultMaxAttemptsPerSeries;
    private final ExecutorService executor;
    private final Map<String, JobRuntimeState> jobs;

    public GenerationJobService(
            TombolaSeriesGenerator generator,
            TombolaPdfWriter pdfWriter,
            @Value("${tombojava.output-dir:out}") String outputDir,
            @Value("${tombojava.max-series-attempts-default:5000}") int defaultMaxAttemptsPerSeries
    ) {
        this.generator = generator;
        this.pdfWriter = pdfWriter;
        this.outputDir = Path.of(outputDir).toAbsolutePath().normalize();
        this.defaultMaxAttemptsPerSeries = defaultMaxAttemptsPerSeries;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.jobs = new ConcurrentHashMap<>();
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
        if (state == null) {
            return Optional.empty();
        }
        return Optional.of(state.toStatus());
    }

    public Optional<Resource> loadPdf(String jobId) {
        JobRuntimeState state = jobs.get(jobId);
        if (state == null || state.state != JobState.COMPLETED || !Files.exists(state.outputPath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new UrlResource(state.outputPath.toUri()));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public Optional<TombolaSeries> findGeneratedSeries(String jobId, int seriesNumber) {
        if (seriesNumber < 1) {
            return Optional.empty();
        }
        JobRuntimeState state = jobs.get(jobId);
        if (state == null || state.state != JobState.COMPLETED || state.generatedSeries.isEmpty()) {
            return Optional.empty();
        }
        if (seriesNumber > state.generatedSeries.size()) {
            return Optional.empty();
        }
        return Optional.of(state.generatedSeries.get(seriesNumber - 1));
    }

    @PreDestroy
    void shutdownExecutor() {
        executor.shutdownNow();
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
                state.update(JobState.FAILED, state.progress, result.message());
                return;
            }

            state.generatedSeries = result.series();

            state.update(JobState.RUNNING, 95, "Scrittura PDF");
            pdfWriter.write(state.outputPath, result.series(), state.seed);
            state.update(JobState.COMPLETED, 100, "PDF creato con successo");
        } catch (IOException ex) {
            state.update(JobState.FAILED, state.progress, "Errore I/O: " + ex.getMessage());
        } catch (Exception ex) {
            state.update(JobState.FAILED, state.progress, "Errore: " + ex.getMessage());
        }
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

        private volatile JobState state;
        private volatile int progress;
        private volatile String message;
        private volatile List<TombolaSeries> generatedSeries;

        private JobRuntimeState(String jobId, long seed, int seriesCount, String fileName, Path outputPath, int maxSeriesAttempts) {
            this.jobId = jobId;
            this.seed = seed;
            this.seriesCount = seriesCount;
            this.fileName = fileName;
            this.outputPath = outputPath;
            this.maxSeriesAttempts = maxSeriesAttempts;
            this.state = JobState.PENDING;
            this.progress = 0;
            this.message = "In coda";
            this.generatedSeries = List.of();
        }

        private void update(JobState state, int progress, String message) {
            this.state = state;
            this.progress = progress;
            this.message = message;
        }

        private GenerationJobStatus toStatus() {
            String downloadUrl = state == JobState.COMPLETED ? "/api/jobs/" + jobId + "/download" : null;
            return new GenerationJobStatus(jobId, state, progress, message, fileName, downloadUrl, seed, seriesCount);
        }
    }
}


