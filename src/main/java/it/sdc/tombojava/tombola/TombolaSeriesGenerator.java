package it.sdc.tombojava.tombola;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Component
public class TombolaSeriesGenerator {

    private static final int MAX_BATCH_RETRIES_PER_SERIES = 5_000;
    private static final int ROWS_PER_CARD = 3;
    private static final int CARDS_PER_SERIES = 6;
    private static final int ROWS_PER_SERIES = ROWS_PER_CARD * CARDS_PER_SERIES;
    private static final int NUMBERS_PER_ROW = 5;
    private static final int TOTAL_NUMBERS = ROWS_PER_SERIES * NUMBERS_PER_ROW;
    private static final int MAX_PERMUTATION_ATTEMPTS = 20_000;

    public TombolaSeries generateSeries(Random random) {
        for (int attempt = 1; attempt <= MAX_PERMUTATION_ATTEMPTS; attempt++) {
            int[] permutation = tryBuildValidPermutation(random);
            if (permutation != null) {
                return materializeSeries(permutation);
            }
        }
        throw new IllegalStateException("Unable to generate a valid series permutation");
    }

    public List<TombolaSeries> generateSeriesBatch(Random random, int seriesCount) {
        GenerationResult result = generateSeriesBatch(random, new GenerationRequest(seriesCount, MAX_BATCH_RETRIES_PER_SERIES, 0L));
        if (!result.successful()) {
            throw new IllegalStateException(result.message());
        }
        return result.series();
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
        return Math.max(0, Math.min(90, value));
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

    private int[] tryBuildValidPermutation(Random random) {
        int[] remainingNumbers = initialNumbers();
        int[] permutation = new int[TOTAL_NUMBERS];
        int[] rowWeightedMask = new int[ROWS_PER_SERIES];
        int[] rowFilledCount = new int[ROWS_PER_SERIES];
        int remainingCount = TOTAL_NUMBERS;

        for (int index = 0; index < TOTAL_NUMBERS; index++) {
            int row = index / NUMBERS_PER_ROW;
            int pickIndex = pickEligibleIndex(remainingNumbers, remainingCount, rowWeightedMask[row], random);
            if (pickIndex < 0) {
                return null;
            }

            int number = remainingNumbers[pickIndex];
            permutation[index] = number;
            rowWeightedMask[row] += columnWeight(number);
            rowFilledCount[row]++;

            remainingCount--;
            remainingNumbers[pickIndex] = remainingNumbers[remainingCount];

            if (!isStillFeasible(remainingNumbers, remainingCount, rowWeightedMask, rowFilledCount)) {
                return null;
            }
        }

        return permutation;
    }

    private int[] initialNumbers() {
        int[] numbers = new int[TOTAL_NUMBERS];
        for (int value = 1; value <= TOTAL_NUMBERS; value++) {
            numbers[value - 1] = value;
        }
        return numbers;
    }

    private int pickEligibleIndex(int[] remainingNumbers, int remainingCount, int rowMask, Random random) {
        int[] eligibleIndexes = new int[remainingCount];
        int eligibleCount = 0;
        for (int index = 0; index < remainingCount; index++) {
            int weight = columnWeight(remainingNumbers[index]);
            if ((rowMask & weight) == 0) {
                eligibleIndexes[eligibleCount++] = index;
            }
        }
        if (eligibleCount == 0) {
            return -1;
        }
        return eligibleIndexes[random.nextInt(eligibleCount)];
    }

    private boolean isStillFeasible(
            int[] remainingNumbers,
            int remainingCount,
            int[] rowWeightedMask,
            int[] rowFilledCount
    ) {
        int[] remainingByColumn = new int[9];
        for (int index = 0; index < remainingCount; index++) {
            remainingByColumn[columnOf(remainingNumbers[index])]++;
        }

        for (int row = 0; row < ROWS_PER_SERIES; row++) {
            int slotsLeftInRow = NUMBERS_PER_ROW - rowFilledCount[row];
            if (slotsLeftInRow <= 0) {
                continue;
            }

            int distinctColumnsAvailable = 0;
            for (int col = 0; col < 9; col++) {
                int weight = 1 << col;
                if (remainingByColumn[col] > 0 && (rowWeightedMask[row] & weight) == 0) {
                    distinctColumnsAvailable++;
                }
            }
            if (distinctColumnsAvailable < slotsLeftInRow) {
                return false;
            }
        }

        for (int col = 0; col < 9; col++) {
            int compatibleRows = 0;
            int weight = 1 << col;
            for (int row = 0; row < ROWS_PER_SERIES; row++) {
                int slotsLeftInRow = NUMBERS_PER_ROW - rowFilledCount[row];
                if (slotsLeftInRow > 0 && (rowWeightedMask[row] & weight) == 0) {
                    compatibleRows++;
                }
            }
            if (remainingByColumn[col] > compatibleRows) {
                return false;
            }
        }

        return true;
    }

    private TombolaSeries materializeSeries(int[] permutation) {
        List<TombolaCard> cards = new ArrayList<>(CARDS_PER_SERIES);
        for (int cardIndex = 0; cardIndex < CARDS_PER_SERIES; cardIndex++) {
            int[][] card = new int[ROWS_PER_CARD][9];
            for (int row = 0; row < ROWS_PER_CARD; row++) {
                int globalRow = cardIndex * ROWS_PER_CARD + row;
                int start = globalRow * NUMBERS_PER_ROW;
                for (int offset = 0; offset < NUMBERS_PER_ROW; offset++) {
                    int number = permutation[start + offset];
                    int column = columnOf(number);
                    card[row][column] = number;
                }
            }
            sortColumnsAscending(card);
            cards.add(new TombolaCard(card));
        }
        return new TombolaSeries(cards);
    }

    private void sortColumnsAscending(int[][] card) {
        for (int col = 0; col < 9; col++) {
            int count = 0;
            int[] values = new int[ROWS_PER_CARD];
            int[] rowIndexes = new int[ROWS_PER_CARD];
            for (int row = 0; row < ROWS_PER_CARD; row++) {
                if (card[row][col] != 0) {
                    rowIndexes[count] = row;
                    values[count++] = card[row][col];
                }
            }
            Arrays.sort(values, 0, count);
            for (int index = 0; index < count; index++) {
                card[rowIndexes[index]][col] = values[index];
            }
        }
    }

    private int columnWeight(int number) {
        return 1 << columnOf(number);
    }

    static int columnOf(int number) {
        if (number < 1 || number > 90) {
            throw new IllegalArgumentException("Number out of range: " + number);
        }
        if (number == 90) {
            return 8;
        }
        return number / 10;
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

