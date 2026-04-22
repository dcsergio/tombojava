package it.sdc.tombojava.tombola;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class TombolaVerificationReportWriter {

    public void write(Path outputPath, it.sdc.tombojava.tombola.GenerationResult result) throws IOException {
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        Files.writeString(outputPath, buildReport(result));
    }

    private String buildReport(it.sdc.tombojava.tombola.GenerationResult result) {
        StringBuilder report = new StringBuilder();
        report.append("Tombojava verification report").append(System.lineSeparator());
        report.append("===========================").append(System.lineSeparator());
        report.append("Status: ").append(result.successful() ? "SUCCESS" : "FAILED").append(System.lineSeparator());
        report.append("Requested series: ").append(result.request().seriesCount()).append(System.lineSeparator());
        report.append("Generated series: ").append(result.generatedSeriesCount()).append(System.lineSeparator());
        report.append("Seed: ").append(result.request().seed()).append(System.lineSeparator());
        report.append("Max attempts per series: ").append(result.request().maxAttemptsPerSeries()).append(System.lineSeparator());
        report.append("Attempts used per accepted series: ").append(result.attemptsPerAcceptedSeries()).append(System.lineSeparator());
        report.append("Max shared numbers across generated rows: ")
                .append(result.maxSharedNumbersAcrossGeneratedRows())
                .append(System.lineSeparator());
        report.append("Message: ").append(result.message()).append(System.lineSeparator());

        result.blockingConflictOptional().ifPresent(conflict -> {
            report.append(System.lineSeparator());
            report.append("Blocking conflict").append(System.lineSeparator());
            report.append("-----------------").append(System.lineSeparator());
            report.append("Shared numbers: ").append(conflict.sharedNumbers()).append(System.lineSeparator());
            report.append("Candidate row: serie ").append(conflict.candidateSeriesNumber())
                    .append(", cartella ").append(conflict.candidateCardNumber())
                    .append(", riga ").append(conflict.candidateRowNumber())
                    .append(" -> ").append(conflict.candidateNumbers())
                    .append(System.lineSeparator());
            report.append("Existing row: serie ").append(conflict.existingSeriesNumber())
                    .append(", cartella ").append(conflict.existingCardNumber())
                    .append(", riga ").append(conflict.existingRowNumber())
                    .append(" -> ").append(conflict.existingNumbers())
                    .append(System.lineSeparator());
        });

        return report.toString();
    }
}

