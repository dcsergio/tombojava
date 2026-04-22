package it.sdc.tombojava.tombola;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TombolaPdfWriterTests {

    @Test
    void generatedPdfUsesA4PortraitSize() throws Exception {
        TombolaSeriesGenerator generator = new TombolaSeriesGenerator();
        TombolaSeries series = generator.generateSeries(new Random(123L));
        TombolaPdfWriter writer = new TombolaPdfWriter();

        Path output = Files.createTempFile("tombojava-a4-", ".pdf");
        try {
            writer.write(output, List.of(series));

            try (PDDocument document = Loader.loadPDF(output.toFile())) {
                float expectedWidth = mmToPoints(210f);
                float expectedHeight = mmToPoints(297f);
                PDPage firstPage = document.getPage(0);

                assertEquals(expectedWidth, firstPage.getMediaBox().getWidth(), 0.1f);
                assertEquals(expectedHeight, firstPage.getMediaBox().getHeight(), 0.1f);
                assertEquals(expectedWidth, firstPage.getCropBox().getWidth(), 0.1f);
                assertEquals(expectedHeight, firstPage.getCropBox().getHeight(), 0.1f);
            }
        } finally {
            Files.deleteIfExists(output);
        }
    }

    @Test
    void generatedPdfContainsSeedText() throws IOException {
        TombolaSeriesGenerator generator = new TombolaSeriesGenerator();
        TombolaSeries series = generator.generateSeries(new Random(456L));
        TombolaPdfWriter writer = new TombolaPdfWriter();
        long seed = 456L;

        Path output = Files.createTempFile("tombojava-seed-", ".pdf");
        try {
            writer.write(output, List.of(series), seed);

            try (PDDocument document = Loader.loadPDF(output.toFile())) {
                String text = new PDFTextStripper().getText(document);
                assertTrue(text.contains("Seed: " + seed), "The PDF should print the seed in the header");
            }
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private float mmToPoints(float millimeters) {
        return (millimeters / 25.4f) * 72f;
    }

    private float pointsToMm(float points) {
        return (points / 72f) * 25.4f;
    }
}


