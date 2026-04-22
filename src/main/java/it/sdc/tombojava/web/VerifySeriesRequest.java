package it.sdc.tombojava.web;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record VerifySeriesRequest(
        Integer seriesNumber,
        List<Integer> extractedNumbers
) {

    public void validate() {
        if (seriesNumber == null || seriesNumber < 1) {
            throw new IllegalArgumentException("Il numero di serie deve essere un intero positivo");
        }
        if (extractedNumbers == null) {
            throw new IllegalArgumentException("La lista dei numeri estratti non puo' essere nulla");
        }
        for (Integer value : extractedNumbers) {
            if (value == null || value < 1 || value > 90) {
                throw new IllegalArgumentException("I numeri estratti devono essere compresi tra 1 e 90");
            }
        }
    }

    public Set<Integer> extractedNumbersSet() {
        return new LinkedHashSet<>(extractedNumbers);
    }
}


