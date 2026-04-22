package it.sdc.tombojava.tombola;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TombolaSeriesGeneratorTests {

    private final TombolaSeriesGenerator generator = new TombolaSeriesGenerator();

    @Test
    void generatedSeriesRespectsProjectRules() {
        TombolaSeries series = generator.generateSeries(new Random(42L));

        assertEquals(6, series.cards().size(), "Each series must contain 6 cards");

        Set<Integer> allNumbers = new HashSet<>();
        int[] perColumnCount = new int[9];

        for (TombolaCard card : series.cards()) {
            for (int row = 0; row < 3; row++) {
                int numbersInRow = 0;
                for (int col = 0; col < 9; col++) {
                    int value = card.get(row, col);
                    if (value == 0) {
                        continue;
                    }

                    numbersInRow++;
                    int expectedCol = TombolaSeriesGenerator.columnOf(value);
                    assertEquals(expectedCol, col, "Each number must stay in its decade column");

                    assertTrue(value >= 1 && value <= 90, "Numbers must be in range 1..90");
                    allNumbers.add(value);
                    perColumnCount[col]++;
                }
                assertEquals(5, numbersInRow, "Each row must contain exactly 5 numbers");
            }
        }

        assertEquals(90, allNumbers.size(), "A series must use all numbers 1..90 without repetition");
        assertEquals(9, perColumnCount[0], "Column 0 must contain numbers 1..9");
        for (int col = 1; col < 8; col++) {
            assertEquals(10, perColumnCount[col], "Middle columns must contribute 10 numbers in the series");
        }
        assertEquals(11, perColumnCount[8], "Last column must contain numbers 80..90");
    }

    @Test
    void decadeColumnMappingHandlesBoundaryValues() {
        assertEquals(0, TombolaSeriesGenerator.columnOf(1));
        assertEquals(0, TombolaSeriesGenerator.columnOf(9));
        assertEquals(1, TombolaSeriesGenerator.columnOf(10));
        assertEquals(1, TombolaSeriesGenerator.columnOf(19));
        assertEquals(2, TombolaSeriesGenerator.columnOf(20));
        assertEquals(7, TombolaSeriesGenerator.columnOf(79));
        assertEquals(8, TombolaSeriesGenerator.columnOf(80));
        assertEquals(8, TombolaSeriesGenerator.columnOf(89));
        assertEquals(8, TombolaSeriesGenerator.columnOf(90));
    }

    @Test
    void generatedSeriesBatchAvoidsSharedTerneQuaterneAndCinquineAcrossRows() {
        GenerationResult result = generator.generateSeriesBatch(new Random(42L), new GenerationRequest(4, 5_000, 42L));
        List<TombolaSeries> batch = result.series();

        assertTrue(result.successful(), "The batch generation should succeed for 4 series");
        assertEquals(4, batch.size(), "The batch must contain the requested number of series");
        assertTrue(result.maxSharedNumbersAcrossGeneratedRows() <= 2,
                "The verification metric must confirm no shared terne/quaterne/cinquine");

        List<int[]> allRows = batch.stream()
                .flatMap(series -> series.cards().stream())
                .flatMap(card -> extractRows(card).stream())
                .toList();

        for (int first = 0; first < allRows.size(); first++) {
            for (int second = first + 1; second < allRows.size(); second++) {
                assertTrue(countCommonNumbers(allRows.get(first), allRows.get(second)) <= 2,
                        "No two rows may share a terna, quaterna or cinquina");
            }
        }
    }

    @Test
    void generateSeriesBatchRejectsNonPositiveCounts() {
        Random random = new Random(42L);
        assertThrows(IllegalArgumentException.class, () -> generator.generateSeriesBatch(random, 0));
    }

    @Test
    void generateSeriesBatchRejectsNonPositiveAttemptLimit() {
        assertThrows(IllegalArgumentException.class, () -> new GenerationRequest(2, 0, 42L));
    }

    @Test
    void generationResultContainsHelpfulFailureMessageWhenRequestIsTooAmbitious() {
        GenerationResult result = generator.generateSeriesBatch(new Random(42L), new GenerationRequest(10, 1, 42L));

        assertFalse(result.successful(), "A very ambitious request with one attempt per series should fail");
        assertTrue(result.generatedSeriesCount() < result.request().seriesCount(),
                "The result should expose that only part of the batch was generated");
        assertTrue(result.message().contains("--series"), "The message should suggest reducing --series");
        assertTrue(result.message().contains("--max-series-attempts"),
                "The message should suggest increasing --max-series-attempts");
        assertTrue(result.blockingConflictOptional().isPresent(),
                "A failed result should expose the blocking conflict for diagnostics");
    }

    @Test
    void sameSeedProducesTheSameBatch() {
        GenerationRequest request = new GenerationRequest(3, 5_000, 987654321L);

        GenerationResult first = generator.generateSeriesBatch(new Random(request.seed()), request);
        GenerationResult second = generator.generateSeriesBatch(new Random(request.seed()), request);

        assertEquals(first.series().size(), second.series().size(), "The number of generated series must match");
        for (int seriesIndex = 0; seriesIndex < first.series().size(); seriesIndex++) {
            TombolaSeries firstSeries = first.series().get(seriesIndex);
            TombolaSeries secondSeries = second.series().get(seriesIndex);
            for (int cardIndex = 0; cardIndex < firstSeries.cards().size(); cardIndex++) {
                assertEquals(firstSeries.cards().get(cardIndex).toString(), secondSeries.cards().get(cardIndex).toString(),
                        "The same seed must reproduce identical tombojava cards");
            }
        }
    }

    private List<int[]> extractRows(TombolaCard card) {
        int[][] matrix = card.toMatrixCopy();
        return List.of(extractRow(matrix[0]), extractRow(matrix[1]), extractRow(matrix[2]));
    }

    private int[] extractRow(int[] row) {
        int[] values = new int[5];
        int index = 0;
        for (int value : row) {
            if (value != 0) {
                values[index++] = value;
            }
        }
        return values;
    }

    private int countCommonNumbers(int[] firstRow, int[] secondRow) {
        int common = 0;
        for (int first : firstRow) {
            for (int second : secondRow) {
                if (first == second) {
                    common++;
                    break;
                }
            }
        }
        return common;
    }
}

