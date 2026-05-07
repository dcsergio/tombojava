package it.sdc.tombojava.history;

import java.util.List;

public record FunStatsResponse(
        int totalJobs,
        int completedJobs,
        int failedJobs,
        int successRatePercent,
        long averageDurationMillis,
        List<FunStatCard> highlights
) {

    public FunStatsResponse {
        highlights = List.copyOf(highlights);
    }

    public record FunStatCard(String title, String value, String description, String jobId) {
    }
}

