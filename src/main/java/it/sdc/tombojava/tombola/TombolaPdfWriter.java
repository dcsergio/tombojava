package it.sdc.tombojava.tombola;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class TombolaPdfWriter {

    private static final float A4_WIDTH_MM = 210f;
    private static final float A4_HEIGHT_MM = 297f;
    private static final float POINTS_PER_INCH = 72f;
    private static final float MM_PER_INCH = 25.4f;
    private static final float A4_WIDTH_POINTS = (A4_WIDTH_MM / MM_PER_INCH) * POINTS_PER_INCH;
    private static final float A4_HEIGHT_POINTS = (A4_HEIGHT_MM / MM_PER_INCH) * POINTS_PER_INCH;

    private static final float PAGE_MARGIN = 12f;
    private static final float CARD_GAP = 8f;
    private static final float HEADER_HEIGHT = 44f;   // più spazio dopo numero di serie
    private static final float LABEL_HEIGHT = 12f;    // meno spazio sopra la griglia (etichetta cartella)
    private static final int CARDS_PER_PAGE = 3;
    private static final float LINE_WIDTH = 0.5f;
    private static final float GRID_CELL_GRAY = 0.95f;

    public void write(Path outputPath, List<TombolaSeries> series) throws IOException {
        write(outputPath, series, null);
    }

    public void write(Path outputPath, List<TombolaSeries> series, Long seed) throws IOException {
        if (series == null || series.isEmpty()) {
            throw new IllegalArgumentException("At least one series is required to write a PDF");
        }

        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        try (PDDocument document = new PDDocument()) {
            PDType1Font titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font numberFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            int pageNumber = 0;
            for (int seriesIndex = 0; seriesIndex < series.size(); seriesIndex++) {
                TombolaSeries currentSeries = series.get(seriesIndex);

                // Pagina A: cartelle 0, 1, 2
                PDRectangle a4 = new PDRectangle(A4_WIDTH_POINTS, A4_HEIGHT_POINTS);
                PDPage pageA = new PDPage(a4);
                pageA.setMediaBox(a4);
                pageA.setCropBox(a4);
                pageA.setTrimBox(a4);
                pageA.setBleedBox(a4);
                pageA.setArtBox(a4);
                document.addPage(pageA);

                try (PDPageContentStream content = new PDPageContentStream(document, pageA)) {
                    drawPage(content, pageA.getMediaBox(), currentSeries, 0, 3, seriesIndex + 1, 'a', seed, titleFont, numberFont);
                }

                // Pagina B: cartelle 3, 4, 5
                PDPage pageB = new PDPage(a4);
                pageB.setMediaBox(a4);
                pageB.setCropBox(a4);
                pageB.setTrimBox(a4);
                pageB.setBleedBox(a4);
                pageB.setArtBox(a4);
                document.addPage(pageB);

                try (PDPageContentStream content = new PDPageContentStream(document, pageB)) {
                    drawPage(content, pageB.getMediaBox(), currentSeries, 3, 6, seriesIndex + 1, 'b', seed, titleFont, numberFont);
                }
            }

            document.save(outputPath.toFile());
        }
    }

    private void drawPage(
            PDPageContentStream content,
            PDRectangle pageSize,
            TombolaSeries series,
            int cardStart,
            int cardEnd,
            int seriesNumber,
            char seriesLetter,
            Long seed,
            PDType1Font titleFont,
            PDType1Font numberFont
    ) throws IOException {
        float usableWidth = pageSize.getWidth() - (2 * PAGE_MARGIN);

        // Titolo serie: più grande e con più spazio sotto
        float seriesFontSize = 18f;
        drawText(content, titleFont, seriesFontSize, PAGE_MARGIN,
                pageSize.getHeight() - PAGE_MARGIN - seriesFontSize, "Serie " + seriesNumber + "/" + seriesLetter);

        if (seed != null) {
            drawText(content, titleFont, 9f, PAGE_MARGIN,
                    pageSize.getHeight() - PAGE_MARGIN - seriesFontSize - 12f, "Seed: " + seed);
        }

        float cardsAreaTop = pageSize.getHeight() - PAGE_MARGIN - HEADER_HEIGHT;

        // Calcola dimensioni celle dinamicamente per riempire tutta la pagina
        // cellWidth: divide la larghezza utile per 9 colonne
        float cellWidth = usableWidth / 9f;
        // cellHeight: divide l'altezza disponibile (esclusi header, etichette e gap) per 9 righe (3 carte × 3)
        float totalLabels = CARDS_PER_PAGE * LABEL_HEIGHT;
        float totalGaps = (CARDS_PER_PAGE - 1) * CARD_GAP;
        float availableForGrids = (cardsAreaTop - PAGE_MARGIN) - totalLabels - totalGaps;
        float cellHeight = availableForGrids / 9f;  // 9 righe totali (3 carte × 3 righe)

        float cardWidth = usableWidth;
        float gridHeight = cellHeight * 3f;
        float cardHeight = gridHeight + LABEL_HEIGHT;

        for (int cardIndex = cardStart; cardIndex < cardEnd; cardIndex++) {
            int positionInPage = cardIndex - cardStart;
            float y = cardsAreaTop - (positionInPage + 1) * cardHeight - positionInPage * CARD_GAP;

            drawCard(content, series.cards().get(cardIndex), cardIndex + 1, PAGE_MARGIN, y,
                    cardWidth, cardHeight, cellWidth, cellHeight, titleFont, numberFont);
        }
    }

    private void drawCard(
            PDPageContentStream content,
            it.sdc.tombojava.tombola.TombolaCard card,
            int cardNumber,
            float x,
            float y,
            float width,
            float height,
            float cellWidth,
            float cellHeight,
            PDType1Font titleFont,
            PDType1Font numberFont
    ) throws IOException {
        // Grid: celle di dimensione cellWidth × cellHeight
        float gridHeight = cellHeight * 3f;

        // Etichetta cartella: posizionata dentro lo spazio LABEL_HEIGHT, appena sopra la griglia
        float labelFontSize = 11f;
        drawText(content, titleFont, labelFontSize, x + 4f, y + gridHeight + 2f, "Cartella " + cardNumber);
        float gridWidth = cellWidth * 9f;
        float gridX = x + (width - gridWidth) / 2f;  // centra la griglia orizzontalmente

        // Bordo griglia
        content.setLineWidth(LINE_WIDTH);
        content.setStrokingColor(0.7f, 0.7f, 0.7f);
        content.addRect(gridX, y, gridWidth, gridHeight);
        content.stroke();

        // Linee verticali
        content.setLineWidth(LINE_WIDTH * 0.7f);
        for (int col = 1; col < 9; col++) {
            float lineX = gridX + col * cellWidth;
            content.moveTo(lineX, y);
            content.lineTo(lineX, y + gridHeight);
        }

        // Linee orizzontali
        for (int row = 1; row < 3; row++) {
            float lineY = y + row * cellHeight;
            content.moveTo(gridX, lineY);
            content.lineTo(gridX + gridWidth, lineY);
        }
        content.setStrokingColor(0.85f, 0.85f, 0.85f);
        content.stroke();

        // Numeri: dimensione font proporzionale alla cella
        float numFontSize = Math.min(cellWidth, cellHeight) * 0.55f;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int value = card.get(row, col);
                if (value == 0) continue;

                String text = Integer.toString(value);
                float textWidth = numberFont.getStringWidth(text) / 1000f * numFontSize;
                float cellX = gridX + col * cellWidth;
                float cellY = y + (2 - row) * cellHeight;

                float textX = cellX + (cellWidth - textWidth) / 2f;
                float textY = cellY + (cellHeight - numFontSize) / 2f + 2f;

                drawText(content, numberFont, numFontSize, textX, textY, text);
            }
        }
    }

    private void drawText(PDPageContentStream content, PDType1Font font, float fontSize, float x, float y, String text)
            throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }

    private static float mmToPoints(float millimeters) {
        return (millimeters / MM_PER_INCH) * POINTS_PER_INCH;
    }
}

