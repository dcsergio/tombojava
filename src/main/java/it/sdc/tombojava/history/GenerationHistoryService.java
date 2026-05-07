package it.sdc.tombojava.history;

import it.sdc.tombojava.job.JobState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class GenerationHistoryService {

    private final GenerationHistoryRepository historyRepository;

    public GenerationHistoryService(GenerationHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    public List<GenerationHistoryEntry> listRecent(int limit) {
        return historyRepository.listRecent(limit);
    }

    public Optional<GenerationHistoryEntry> findJob(String jobId) {
        return historyRepository.findByJobId(jobId);
    }

    public FunStatsResponse buildFunStats() {
        List<GenerationHistoryEntry> allJobs = historyRepository.listAll();
        List<GenerationHistoryEntry> completedJobs = allJobs.stream()
                .filter(entry -> entry.state() == JobState.COMPLETED)
                .toList();
        int failedJobs = (int) allJobs.stream()
                .filter(entry -> entry.state() == JobState.FAILED)
                .count();
        int successRatePercent = allJobs.isEmpty()
                ? 0
                : (int) Math.round((completedJobs.size() * 100.0) / allJobs.size());
        long averageDurationMillis = Math.round(completedJobs.stream()
                .map(GenerationHistoryEntry::durationMillis)
                .filter(duration -> duration != null && duration >= 0)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0));

        List<FunStatsResponse.FunStatCard> highlights = new ArrayList<>();
        findFastest(completedJobs).ifPresent(entry -> highlights.add(new FunStatsResponse.FunStatCard(
                "Più veloce",
                describeDuration(entry.durationMillis()),
                "Seed " + entry.seed() + " • " + entry.seriesCount() + " serie completate al volo",
                entry.jobId()
        )));
        findSlowest(completedJobs).ifPresent(entry -> highlights.add(new FunStatsResponse.FunStatCard(
                "Generazione maratona",
                describeDuration(entry.durationMillis()),
                "Il job più lungo finora: seed " + entry.seed(),
                entry.jobId()
        )));
        findMostAmbitious(completedJobs).ifPresent(entry -> highlights.add(new FunStatsResponse.FunStatCard(
                "Più ambiziosa",
                entry.seriesCount() + " serie",
                "Ha chiesto il lotto più ricco tra i job completati",
                entry.jobId()
        )));
        findMostStubborn(allJobs).ifPresent(entry -> highlights.add(new FunStatsResponse.FunStatCard(
                "Più ostinata",
                entry.maxSeriesAttempts() + " tentativi/serie",
                "Configurazione con la soglia più alta impostata",
                entry.jobId()
        )));
        mostFrequentSeed(completedJobs).ifPresent(seedCount -> highlights.add(new FunStatsResponse.FunStatCard(
                "Seed portafortuna",
                String.valueOf(seedCount.seed()),
                "Comparso " + seedCount.count() + " volte nei job completati",
                seedCount.jobId()
        )));

        return new FunStatsResponse(
                allJobs.size(),
                completedJobs.size(),
                failedJobs,
                successRatePercent,
                averageDurationMillis,
                highlights
        );
    }

    private Optional<GenerationHistoryEntry> findFastest(List<GenerationHistoryEntry> jobs) {
        return jobs.stream()
                .filter(entry -> entry.durationMillis() != null)
                .min(Comparator.comparingLong(GenerationHistoryEntry::durationMillis));
    }

    private Optional<GenerationHistoryEntry> findSlowest(List<GenerationHistoryEntry> jobs) {
        return jobs.stream()
                .filter(entry -> entry.durationMillis() != null)
                .max(Comparator.comparingLong(GenerationHistoryEntry::durationMillis));
    }

    private Optional<GenerationHistoryEntry> findMostAmbitious(List<GenerationHistoryEntry> jobs) {
        return jobs.stream()
                .filter(entry -> entry.seriesCount() != null)
                .max(Comparator.comparingInt(GenerationHistoryEntry::seriesCount));
    }

    private Optional<GenerationHistoryEntry> findMostStubborn(List<GenerationHistoryEntry> jobs) {
        return jobs.stream()
                .filter(entry -> entry.maxSeriesAttempts() != null)
                .max(Comparator.comparingInt(GenerationHistoryEntry::maxSeriesAttempts));
    }

    private Optional<SeedCount> mostFrequentSeed(List<GenerationHistoryEntry> jobs) {
        return jobs.stream()
                .filter(entry -> entry.seed() != null)
                .collect(Collectors.groupingBy(GenerationHistoryEntry::seed))
                .entrySet()
                .stream()
                .map(group -> {
                    GenerationHistoryEntry sample = group.getValue().stream()
                            .max(Comparator.comparing(GenerationHistoryEntry::completedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                            .orElse(null);
                    return sample == null ? null : new SeedCount(group.getKey(), group.getValue().size(), sample.jobId());
                })
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(SeedCount::count).thenComparingLong(SeedCount::seed));
    }

    private String describeDuration(Long durationMillis) {
        if (durationMillis == null || durationMillis < 1000) {
            return (durationMillis == null ? 0 : durationMillis) + " ms";
        }
        long seconds = durationMillis / 1000;
        long millisRemainder = durationMillis % 1000;
        return seconds + "," + String.format("%03d", millisRemainder) + " s";
    }

    private record SeedCount(long seed, int count, String jobId) {
    }
}


