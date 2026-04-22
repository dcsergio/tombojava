package it.sdc.tombojava.tombola;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TombolaVerificationReportWriterTests {

    @Test
    void writesReadableVerificationReport() throws Exception {
        TombolaSeriesGenerator generator = new TombolaSeriesGenerator();
        TombolaVerificationReportWriter writer = new TombolaVerificationReportWriter();
        GenerationResult result = generator.generateSeriesBatch(new Random(42L), new GenerationRequest(4, 5_000, 42L));

        Path reportPath = Files.createTempFile("tombojava-report-", ".txt");
        try {
            writer.write(reportPath, result);
            String report = Files.readString(reportPath);

            assertTrue(report.contains("Tombojava verification report"));
            assertTrue(report.contains("Status: SUCCESS"));
            assertTrue(report.contains("Requested series: 4"));
            assertTrue(report.contains("Generated series: 4"));
            assertTrue(report.contains("Seed: 42"));
            assertTrue(report.contains("Max shared numbers across generated rows:"));
        } finally {
            Files.deleteIfExists(reportPath);
        }
    }
}

