package it.sdc.tombojava.tombola;

public record GenerationRequest(int seriesCount, int maxAttemptsPerSeries, long seed) {

    public GenerationRequest {
        if (seriesCount < 1) {
            throw new IllegalArgumentException("seriesCount must be a positive integer");
        }
        if (maxAttemptsPerSeries < 1) {
            throw new IllegalArgumentException("maxAttemptsPerSeries must be a positive integer");
        }
    }
}

