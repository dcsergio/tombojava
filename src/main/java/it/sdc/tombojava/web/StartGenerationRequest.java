package it.sdc.tombojava.web;

public record StartGenerationRequest(
        Integer seriesCount,
        Long seed,
        Integer maxWaitSeconds,
        Integer maxSeriesAttempts
) {

    public void validate() {
        if (seriesCount == null || seriesCount < 1) {
            throw new IllegalArgumentException("Il numero di serie deve essere un intero positivo");
        }
        if (maxWaitSeconds == null || maxWaitSeconds < 1) {
            throw new IllegalArgumentException("Il tempo massimo di attesa deve essere un intero positivo (secondi)");
        }
        if (maxSeriesAttempts != null && maxSeriesAttempts < 1) {
            throw new IllegalArgumentException("Il numero massimo di tentativi deve essere un intero positivo");
        }
    }
}

