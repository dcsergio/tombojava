package it.sdc.tombojava.tombola;

import java.util.List;

public record CompatibilityConflict(
        int candidateSeriesNumber,
        int candidateCardNumber,
        int candidateRowNumber,
        List<Integer> candidateNumbers,
        int existingSeriesNumber,
        int existingCardNumber,
        int existingRowNumber,
        List<Integer> existingNumbers,
        int sharedNumbers
) {

    public CompatibilityConflict {
        candidateNumbers = List.copyOf(candidateNumbers);
        existingNumbers = List.copyOf(existingNumbers);
    }
}

