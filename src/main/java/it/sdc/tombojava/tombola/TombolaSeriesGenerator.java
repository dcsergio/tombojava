package it.sdc.tombojava.tombola;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Component
public class TombolaSeriesGenerator {

    private static final int MAX_BATCH_RETRIES_PER_SERIES = 5_000;
    private static final int ROWS_PER_CARD = 3;
    private static final int CARDS_PER_SERIES = 6;
    private static final int ROWS_PER_SERIES = ROWS_PER_CARD * CARDS_PER_SERIES;
    private static final int NUMBERS_PER_ROW = 5;

    public TombolaSeries generateSeries(Random random) {
        boolean[][] slotLayout = buildSlots(random);
        List<List<Integer>> columnPools = buildColumnPools(random);
        List<TombolaCard> cards = buildCards(slotLayout, columnPools);

        return new TombolaSeries(cards);
    }

    private List<TombolaCard> buildCards(boolean[][] slotLayout, List<List<Integer>> columnPools) {
        List<TombolaCard> cards = new ArrayList<>(CARDS_PER_SERIES);
        for (int cardIndex = 0; cardIndex < CARDS_PER_SERIES; cardIndex++) {
            cards.add(new TombolaCard(buildCardMatrix(cardIndex, slotLayout, columnPools)));
        }
        return cards;
    }

    private int[][] buildCardMatrix(int cardIndex, boolean[][] slotLayout, List<List<Integer>> columnPools) {
        int[][] cardMatrix = new int[ROWS_PER_CARD][9];
        for (int columnIndex = 0; columnIndex < 9; columnIndex++) {
            List<Integer> numbersForColumn = takeColumnNumbers(cardIndex, columnIndex, slotLayout, columnPools);
            placeSortedColumnNumbers(cardIndex, columnIndex, slotLayout, cardMatrix, numbersForColumn);
        }
        return cardMatrix;
    }

    private List<Integer> takeColumnNumbers(
            int cardIndex,
            int columnIndex,
            boolean[][] slotLayout,
            List<List<Integer>> columnPools
    ) {
        List<Integer> numbersForColumn = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < ROWS_PER_CARD; rowIndex++) {
            int globalRow = cardIndex * ROWS_PER_CARD + rowIndex;
            if (slotLayout[globalRow][columnIndex]) {
                numbersForColumn.add(columnPools.get(columnIndex).removeFirst());
            }
        }
        return numbersForColumn;
    }

    private void placeSortedColumnNumbers(
            int cardIndex,
            int columnIndex,
            boolean[][] slotLayout,
            int[][] cardMatrix,
            List<Integer> numbersForColumn
    ) {
        Collections.sort(numbersForColumn);
        int nextNumberIndex = 0;
        for (int rowIndex = 0; rowIndex < ROWS_PER_CARD; rowIndex++) {
            int globalRow = cardIndex * ROWS_PER_CARD + rowIndex;
            if (slotLayout[globalRow][columnIndex]) {
                cardMatrix[rowIndex][columnIndex] = numbersForColumn.get(nextNumberIndex++);
            }
        }
    }

    public void generateSeriesBatch(Random random, int seriesCount) {
        GenerationResult result = generateSeriesBatch(random, new GenerationRequest(seriesCount, MAX_BATCH_RETRIES_PER_SERIES, 0L));
        if (!result.successful()) {
            throw new IllegalStateException(result.message());
        }
    }

    public GenerationResult generateSeriesBatch(Random random, GenerationRequest request) {
        return generateSeriesBatch(random, request, null, null);
    }

    public GenerationResult generateSeriesBatch(
            Random random,
            GenerationRequest request,
            Duration maxWait,
            ProgressListener progressListener
    ) {
        List<TombolaSeries> acceptedSeries = new ArrayList<>(request.seriesCount());
        List<RowData> acceptedRows = new ArrayList<>(request.seriesCount() * ROWS_PER_SERIES);
        List<Integer> attemptsPerAcceptedSeries = new ArrayList<>(request.seriesCount());
        Long deadlineNanos = resolveDeadline(maxWait);

        emitProgress(progressListener, 0, "Preparazione generazione");

        for (int seriesIndex = 0; seriesIndex < request.seriesCount(); seriesIndex++) {
            final int currentSeriesIndex = seriesIndex;

            if (isTimeLimitReached(deadlineNanos)) {
                return timeoutResult(request, acceptedSeries, attemptsPerAcceptedSeries, acceptedRows, maxWait);
            }

            emitProgress(progressListener, progressPercent(currentSeriesIndex, 0, request),
                    "Generazione serie " + (currentSeriesIndex + 1) + "/" + request.seriesCount());

            SeriesAttemptOutcome outcome = tryGenerateCompatibleSeries(
                    random,
                    seriesIndex,
                    acceptedRows,
                    request.maxAttemptsPerSeries(),
                    deadlineNanos,
                    attempt -> emitProgress(
                            progressListener,
                            progressPercent(currentSeriesIndex, attempt, request),
                            "Serie " + (currentSeriesIndex + 1) + "/" + request.seriesCount() + " - tentativo " + attempt
                    )
            );

            if (outcome.timedOut()) {
                return timeoutResult(request, acceptedSeries, attemptsPerAcceptedSeries, acceptedRows, maxWait);
            }

            if (!outcome.successful()) {
                int maxSharedNumbers = maxSharedNumbers(acceptedRows);
                return new GenerationResult(
                        request,
                        acceptedSeries,
                        attemptsPerAcceptedSeries,
                        maxSharedNumbers,
                        outcome.strongestConflict(),
                        buildFailureMessage(request, acceptedSeries.size(), outcome.strongestConflict())
                );
            }

            acceptedSeries.add(outcome.series());
            attemptsPerAcceptedSeries.add(outcome.attemptsUsed());
            acceptedRows.addAll(outcome.rows());
            emitProgress(progressListener, progressPercent(seriesIndex + 1, 0, request),
                    "Serie " + (seriesIndex + 1) + " completata");
        }

        emitProgress(progressListener, 90, "Generazione serie completata");
        return new GenerationResult(
                request,
                acceptedSeries,
                attemptsPerAcceptedSeries,
                maxSharedNumbers(acceptedRows),
                null,
                "Serie compatibili generate con successo."
        );
    }

    private SeriesAttemptOutcome tryGenerateCompatibleSeries(
            Random random,
            int seriesIndex,
            List<RowData> acceptedRows,
            int maxAttemptsPerSeries,
            Long deadlineNanos,
            AttemptProgressListener progressListener
    ) {
        CompatibilityConflict strongestConflict = null;

        for (int attempt = 1; attempt <= maxAttemptsPerSeries; attempt++) {
            if (attempt == 1 || attempt % 25 == 0 || attempt == maxAttemptsPerSeries) {
                progressListener.onAttempt(attempt);
            }
            if (isTimeLimitReached(deadlineNanos)) {
                return new SeriesAttemptOutcome(null, List.of(), attempt - 1, strongestConflict, true);
            }
            if (Thread.currentThread().isInterrupted()) {
                return new SeriesAttemptOutcome(null, List.of(), attempt - 1, strongestConflict, true);
            }

            TombolaSeries candidate = generateSeries(random);
            List<RowData> candidateRows = extractRows(candidate, seriesIndex + 1);
            CompatibilityConflict conflict = findConflict(candidateRows, acceptedRows);
            if (conflict == null) {
                return new SeriesAttemptOutcome(candidate, candidateRows, attempt, null, false);
            }
            strongestConflict = keepStrongerConflict(strongestConflict, conflict);
        }

        return new SeriesAttemptOutcome(null, List.of(), maxAttemptsPerSeries, strongestConflict, false);
    }

    private GenerationResult timeoutResult(
            GenerationRequest request,
            List<TombolaSeries> acceptedSeries,
            List<Integer> attemptsPerAcceptedSeries,
            List<RowData> acceptedRows,
            Duration maxWait
    ) {
        return new GenerationResult(
                request,
                acceptedSeries,
                attemptsPerAcceptedSeries,
                maxSharedNumbers(acceptedRows),
                null,
                "Tempo massimo di attesa superato (" + maxWait.toSeconds() + " secondi)."
        );
    }

    private Long resolveDeadline(Duration maxWait) {
        if (maxWait == null || maxWait.isZero() || maxWait.isNegative()) {
            return null;
        }
        return System.nanoTime() + maxWait.toNanos();
    }

    private boolean isTimeLimitReached(Long deadlineNanos) {
        return deadlineNanos != null && System.nanoTime() >= deadlineNanos;
    }

    private int progressPercent(int seriesIndex, int attempt, GenerationRequest request) {
        double seriesProgress = seriesIndex;
        if (attempt > 0) {
            seriesProgress += Math.min(1.0, (double) attempt / request.maxAttemptsPerSeries());
        }
        double ratio = seriesProgress / request.seriesCount();
        int value = (int) Math.floor(ratio * 90.0);
        return Math.clamp(value, 0, 90);
    }

    private void emitProgress(ProgressListener listener, int percent, String message) {
        if (listener != null) {
            listener.onProgress(percent, message);
        }
    }

    private String buildFailureMessage(GenerationRequest request, int generatedSeriesCount, CompatibilityConflict blockingConflict) {
        StringBuilder message = new StringBuilder();
        message.append("Impossibile generare ")
                .append(request.seriesCount())
                .append(" serie compatibili senza terne/quaterne/cinquine condivise. ")
                .append("Serie generate: ")
                .append(generatedSeriesCount)
                .append("/")
                .append(request.seriesCount())
                .append(". Tentativi massimi per serie: ")
                .append(request.maxAttemptsPerSeries())
                .append('.');

        if (blockingConflict != null) {
            message.append(" Conflitto migliore trovato: ")
                    .append(blockingConflict.sharedNumbers())
                    .append(" numeri in comune tra serie ")
                    .append(blockingConflict.candidateSeriesNumber())
                    .append(" e serie ")
                    .append(blockingConflict.existingSeriesNumber())
                    .append('.');
        }

        message.append(" Prova a ridurre --series oppure aumentare --max-series-attempts.");
        return message.toString();
    }

    private boolean[][] buildSlots(Random random) {
        int[] colRemaining = new int[9];
        Arrays.fill(colRemaining, 10);
        boolean[][] slots = new boolean[ROWS_PER_SERIES][9];

        if (!fillRow(0, colRemaining, slots, random)) {
            throw new IllegalStateException("Unable to generate a valid slot layout for the series");
        }
        return slots;
    }

    private boolean fillRow(int rowIndex, int[] colRemaining, boolean[][] slots, Random random) {
        if (rowIndex == ROWS_PER_SERIES) {
            return allColumnsConsumed(colRemaining);
        }

        List<Integer> availableColumns = new ArrayList<>();
        for (int col = 0; col < 9; col++) {
            if (colRemaining[col] > 0) {
                availableColumns.add(col);
            }
        }
        if (availableColumns.size() < 5) {
            return false;
        }

        List<int[]> combinations = combinationsOfFive(availableColumns);
        Collections.shuffle(combinations, random);

        for (int[] pick : combinations) {
            applyPick(rowIndex, pick, slots, colRemaining);

            int rowsLeft = ROWS_PER_SERIES - (rowIndex + 1);
            boolean feasible = isFeasible(colRemaining, rowsLeft);

            if (feasible && fillRow(rowIndex + 1, colRemaining, slots, random)) {
                return true;
            }

            rollbackPick(rowIndex, pick, slots, colRemaining);
        }

        return false;
    }

    private boolean allColumnsConsumed(int[] colRemaining) {
        for (int value : colRemaining) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    private void applyPick(int rowIndex, int[] pick, boolean[][] slots, int[] colRemaining) {
        for (int col : pick) {
            slots[rowIndex][col] = true;
            colRemaining[col]--;
        }
    }

    private void rollbackPick(int rowIndex, int[] pick, boolean[][] slots, int[] colRemaining) {
        for (int col : pick) {
            slots[rowIndex][col] = false;
            colRemaining[col]++;
        }
    }

    private boolean isFeasible(int[] colRemaining, int rowsLeft) {
        for (int remaining : colRemaining) {
            if (remaining < 0 || remaining > rowsLeft) {
                return false;
            }
        }
        return true;
    }

    private List<int[]> combinationsOfFive(List<Integer> values) {
        List<int[]> combinations = new ArrayList<>();
        for (int a = 0; a < values.size() - 4; a++) {
            for (int b = a + 1; b < values.size() - 3; b++) {
                for (int c = b + 1; c < values.size() - 2; c++) {
                    for (int d = c + 1; d < values.size() - 1; d++) {
                        for (int e = d + 1; e < values.size(); e++) {
                            combinations.add(new int[]{
                                    values.get(a),
                                    values.get(b),
                                    values.get(c),
                                    values.get(d),
                                    values.get(e)
                            });
                        }
                    }
                }
            }
        }
        return combinations;
    }

    private List<List<Integer>> buildColumnPools(Random random) {
        List<List<Integer>> pools = new ArrayList<>(9);
        for (int col = 0; col < 9; col++) {
            int start = col * 10 + 1;
            List<Integer> numbers = new ArrayList<>(10);
            for (int value = start; value < start + 10; value++) {
                numbers.add(value);
            }
            Collections.shuffle(numbers, random);
            pools.add(numbers);
        }
        return pools;
    }

    private List<RowData> extractRows(TombolaSeries series, int seriesNumber) {
        List<RowData> rows = new ArrayList<>(ROWS_PER_SERIES);
        for (int cardIndex = 0; cardIndex < series.cards().size(); cardIndex++) {
            TombolaCard card = series.cards().get(cardIndex);
            int[][] matrix = card.toMatrixCopy();
            for (int row = 0; row < ROWS_PER_CARD; row++) {
                int[] values = new int[NUMBERS_PER_ROW];
                int index = 0;
                for (int col = 0; col < 9; col++) {
                    int value = matrix[row][col];
                    if (value != 0) {
                        values[index++] = value;
                    }
                }
                rows.add(new RowData(seriesNumber, cardIndex + 1, row + 1, toNumberList(values)));
            }
        }
        return rows;
    }

    private CompatibilityConflict findConflict(List<RowData> candidateRows, List<RowData> acceptedRows) {
        CompatibilityConflict strongestConflict = null;
        for (RowData candidateRow : candidateRows) {
            for (RowData acceptedRow : acceptedRows) {
                int sharedNumbers = countCommonNumbers(candidateRow.numbers(), acceptedRow.numbers());
                if (sharedNumbers >= 3) {
                    CompatibilityConflict conflict = toConflict(candidateRow, acceptedRow, sharedNumbers);
                    strongestConflict = keepStrongerConflict(strongestConflict, conflict);
                }
            }
        }
        return strongestConflict;
    }

    private int countCommonNumbers(List<Integer> firstRow, List<Integer> secondRow) {
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

    private int maxSharedNumbers(List<RowData> rows) {
        int maxSharedNumbers = 0;
        for (int first = 0; first < rows.size(); first++) {
            for (int second = first + 1; second < rows.size(); second++) {
                maxSharedNumbers = Math.max(
                        maxSharedNumbers,
                        countCommonNumbers(rows.get(first).numbers(), rows.get(second).numbers())
                );
            }
        }
        return maxSharedNumbers;
    }

    private CompatibilityConflict keepStrongerConflict(CompatibilityConflict current, CompatibilityConflict candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.sharedNumbers() > current.sharedNumbers()) {
            return candidate;
        }
        return current;
    }

    private CompatibilityConflict toConflict(RowData candidateRow, RowData acceptedRow, int sharedNumbers) {
        return new CompatibilityConflict(
                candidateRow.seriesNumber(),
                candidateRow.cardNumber(),
                candidateRow.rowNumber(),
                candidateRow.numbers(),
                acceptedRow.seriesNumber(),
                acceptedRow.cardNumber(),
                acceptedRow.rowNumber(),
                acceptedRow.numbers(),
                sharedNumbers
        );
    }

    private List<Integer> toNumberList(int[] numbers) {
        List<Integer> result = new ArrayList<>(numbers.length);
        for (int number : numbers) {
            result.add(number);
        }
        return result;
    }

    private record RowData(int seriesNumber, int cardNumber, int rowNumber, List<Integer> numbers) {
    }

    private record SeriesAttemptOutcome(
            TombolaSeries series,
            List<RowData> rows,
            int attemptsUsed,
            CompatibilityConflict strongestConflict,
            boolean timedOut
    ) {
        private boolean successful() {
            return series != null;
        }
    }

    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(int percent, String message);
    }

    @FunctionalInterface
    private interface AttemptProgressListener {
        void onAttempt(int attempt);
    }
}

