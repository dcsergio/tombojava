package it.sdc.tombojava.job;

public record GenerationJobStatus(
        String jobId,
        JobState state,
        int progress,
        String message,
        String fileName,
        String downloadUrl,
        Long seed,
        Integer seriesCount
) {
}

