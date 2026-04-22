package it.sdc.tombojava.job;

import it.sdc.tombojava.tombola.GenerationRequest;
import it.sdc.tombojava.tombola.GenerationResult;
import it.sdc.tombojava.tombola.TombolaCard;
import it.sdc.tombojava.tombola.TombolaPdfWriter;
import it.sdc.tombojava.tombola.TombolaSeries;
import it.sdc.tombojava.tombola.TombolaSeriesGenerator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenerationJobServiceTests {

    @Test
    void startedJobUsesExpectedFileNamePattern() {
        TombolaSeriesGenerator generator = mock(TombolaSeriesGenerator.class);
        TombolaPdfWriter pdfWriter = mock(TombolaPdfWriter.class);

        when(generator.generateSeriesBatch(any(Random.class), any(GenerationRequest.class), any(Duration.class), any()))
                .thenAnswer(invocation -> {
                    GenerationRequest request = invocation.getArgument(1);
                    return new GenerationResult(request, List.of(), List.of(), 0, null, "failed");
                });

        GenerationJobService service = new GenerationJobService(generator, pdfWriter, "build/tmp", 1000);
        try {
            String jobId = service.startJob(3, 777L, 5);
            Optional<GenerationJobStatus> status = service.findStatus(jobId);

            assertTrue(status.isPresent());
            assertEquals("777-3.pdf", status.get().fileName());
        } finally {
            service.shutdownExecutor();
        }
    }

    @Test
    void completedJobExposesGeneratedSeriesForVerification() throws Exception {
        TombolaSeriesGenerator generator = mock(TombolaSeriesGenerator.class);
        TombolaPdfWriter pdfWriter = mock(TombolaPdfWriter.class);

        TombolaSeries series = buildSeries();
        when(generator.generateSeriesBatch(any(Random.class), any(GenerationRequest.class), any(Duration.class), any()))
                .thenAnswer(invocation -> {
                    GenerationRequest request = invocation.getArgument(1);
                    return new GenerationResult(request, List.of(series), List.of(1), 0, null, "ok");
                });
        doNothing().when(pdfWriter).write(any(), any(), any(Long.class));

        GenerationJobService service = new GenerationJobService(generator, pdfWriter, "build/tmp", 1000);
        try {
            String jobId = service.startJob(1, 123L, 5);
            waitForCompletion(service, jobId);

            Optional<TombolaSeries> found = service.findGeneratedSeries(jobId, 1);

            assertTrue(found.isPresent());
            assertEquals(6, found.get().cards().size());
            assertTrue(service.findGeneratedSeries(jobId, 2).isEmpty());
        } finally {
            service.shutdownExecutor();
        }
    }

    private void waitForCompletion(GenerationJobService service, String jobId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Optional<GenerationJobStatus> status = service.findStatus(jobId);
            if (status.isPresent() && status.get().state() == JobState.COMPLETED) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Job did not complete in time");
    }

    private TombolaSeries buildSeries() {
        TombolaCard card = new TombolaCard(new int[][]{
                {1, 0, 0, 0, 41, 0, 0, 0, 81},
                {0, 12, 0, 0, 0, 52, 0, 72, 0},
                {0, 0, 23, 33, 0, 0, 63, 0, 0}
        });
        return new TombolaSeries(List.of(card, card, card, card, card, card));
    }
}

