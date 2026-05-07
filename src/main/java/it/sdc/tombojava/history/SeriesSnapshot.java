package it.sdc.tombojava.history;

import it.sdc.tombojava.tombola.TombolaCard;
import it.sdc.tombojava.tombola.TombolaSeries;

import java.util.ArrayList;
import java.util.List;

public record SeriesSnapshot(List<CardSnapshot> cards) {

    public SeriesSnapshot {
        cards = List.copyOf(cards);
    }

    public static SeriesSnapshot fromSeries(TombolaSeries series) {
        List<CardSnapshot> snapshots = new ArrayList<>();
        for (TombolaCard card : series.cards()) {
            snapshots.add(CardSnapshot.fromCard(card));
        }
        return new SeriesSnapshot(snapshots);
    }

    public TombolaSeries toSeries() {
        List<TombolaCard> rebuiltCards = cards.stream()
                .map(CardSnapshot::toCard)
                .toList();
        return new TombolaSeries(rebuiltCards);
    }

    public record CardSnapshot(List<List<Integer>> rows) {

        public CardSnapshot {
            rows = rows.stream()
                    .map(List::copyOf)
                    .toList();
        }

        public static CardSnapshot fromCard(TombolaCard card) {
            int[][] matrix = card.toMatrixCopy();
            List<List<Integer>> rows = new ArrayList<>();
            for (int[] row : matrix) {
                List<Integer> values = new ArrayList<>(row.length);
                for (int value : row) {
                    values.add(value);
                }
                rows.add(values);
            }
            return new CardSnapshot(rows);
        }

        public TombolaCard toCard() {
            int[][] matrix = new int[rows.size()][];
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                List<Integer> row = rows.get(rowIndex);
                matrix[rowIndex] = new int[row.size()];
                for (int colIndex = 0; colIndex < row.size(); colIndex++) {
                    matrix[rowIndex][colIndex] = row.get(colIndex);
                }
            }
            return new TombolaCard(matrix);
        }
    }
}

