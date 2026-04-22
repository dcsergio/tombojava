package it.sdc.tombojava.cli;

import it.sdc.tombojava.tombola.GenerationRequest;
import it.sdc.tombojava.tombola.GenerationResult;
import it.sdc.tombojava.tombola.TombolaPdfWriter;
import it.sdc.tombojava.tombola.TombolaSeries;
import it.sdc.tombojava.tombola.TombolaSeriesGenerator;
import it.sdc.tombojava.tombola.TombolaVerificationReportWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import java.util.List;
import java.util.Random;

@Component
@ConditionalOnProperty(name = "tombojava.cli.enabled", havingValue = "true", matchIfMissing = false)
public class TombojavaCliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TombojavaCliRunner.class);

    private final TombolaSeriesGenerator generator;
    private final TombolaPdfWriter pdfWriter;
    private final TombolaVerificationReportWriter reportWriter;

    public TombojavaCliRunner(
            TombolaSeriesGenerator generator,
            TombolaPdfWriter pdfWriter,
            TombolaVerificationReportWriter reportWriter
    ) {
        this.generator = generator;
        this.pdfWriter = pdfWriter;
        this.reportWriter = reportWriter;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        CliOptions options = parseOptions(args);
        Random random = new Random(options.seed());
        GenerationRequest request = new GenerationRequest(options.seriesCount(), options.maxSeriesAttempts(), options.seed());
        GenerationResult result = generator.generateSeriesBatch(random, request);

        if (options.reportPath() != null) {
            reportWriter.write(options.reportPath(), result);
            log.info("Report di verifica scritto in {}", options.reportPath().toAbsolutePath());
        }

        if (!result.successful()) {
            throw new IllegalStateException(result.message());
        }

        List<TombolaSeries> series = result.series();
        pdfWriter.write(options.outputPath(), series, options.seed());
        log.info("Generato PDF: {} (serie: {}, seed: {})",
                options.outputPath().toAbsolutePath(), options.seriesCount(), options.seed());
    }

    private CliOptions parseOptions(ApplicationArguments args) {
        String output = optionValue(args, "output", "tombojava.pdf");
        int seriesCount = parsePositiveInt(optionValue(args, "series", "1"), "series");
        int maxSeriesAttempts = parsePositiveInt(optionValue(args, "max-series-attempts", "5000"), "max-series-attempts");
        long seed = parseSeed(optionValue(args, "seed", null));
        String report = optionValue(args, "report", null);
        Path reportPath = report == null || report.isBlank() ? null : Path.of(report);
        return new CliOptions(Path.of(output), seriesCount, maxSeriesAttempts, seed, reportPath);
    }

    private String optionValue(ApplicationArguments args, String key, String defaultValue) {
        List<String> values = args.getOptionValues(key);
        if (values == null || values.isEmpty()) {
            return defaultValue;
        }
        return values.getLast();
    }

    private int parsePositiveInt(String raw, String optionName) {
        try {
            int value = Integer.parseInt(raw);
            if (value < 1) {
                throw new IllegalArgumentException();
            }
            return value;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Option --" + optionName + " must be a positive integer");
        }
    }

    private long parseSeed(String rawSeed) {
        if (rawSeed == null || rawSeed.isBlank()) {
            return ThreadLocalRandom.current().nextLong();
        }
        try {
            return Long.parseLong(rawSeed);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Option --seed must be a valid long integer");
        }
    }

    private record CliOptions(Path outputPath, int seriesCount, int maxSeriesAttempts, long seed, Path reportPath) {
    }
}


