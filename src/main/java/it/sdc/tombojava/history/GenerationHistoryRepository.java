package it.sdc.tombojava.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class GenerationHistoryRepository {

    private final ObjectMapper objectMapper;
    private final Path historyDir;
    private final int retentionMaxJobs;

    @Autowired
    public GenerationHistoryRepository(
            @Value("${tombojava.history-dir:out/history}") String historyDir,
            @Value("${tombojava.history-retention-max-jobs:50}") int retentionMaxJobs
    ) {
        this(Path.of(historyDir).toAbsolutePath().normalize(), retentionMaxJobs);
    }

    public GenerationHistoryRepository(Path historyDir, int retentionMaxJobs) {
        this(defaultObjectMapper(), historyDir, retentionMaxJobs);
    }

    public GenerationHistoryRepository(ObjectMapper objectMapper, Path historyDir, int retentionMaxJobs) {
        this.objectMapper = objectMapper;
        this.historyDir = historyDir;
        this.retentionMaxJobs = Math.max(1, retentionMaxJobs);
    }

    public static ObjectMapper defaultObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    public synchronized void save(GenerationHistoryEntry entry) {
        try {
            Files.createDirectories(historyDir);
            Path target = resolveEntryPath(entry.jobId());
            Path temp = resolveEntryPath(entry.jobId() + ".tmp");
            objectMapper.writeValue(temp.toFile(), entry);
            moveAtomically(temp, target);
            enforceRetention();
        } catch (IOException ex) {
            throw new IllegalStateException("Impossibile salvare lo storico del job " + entry.jobId(), ex);
        }
    }

    public Optional<GenerationHistoryEntry> findByJobId(String jobId) {
        Path entryPath = resolveEntryPath(jobId);
        if (!Files.exists(entryPath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(entryPath.toFile(), GenerationHistoryEntry.class));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    public List<GenerationHistoryEntry> listRecent(int limit) {
        return listAll().stream()
                .limit(Math.max(0, limit))
                .toList();
    }

    public List<GenerationHistoryEntry> listAll() {
        if (!Files.isDirectory(historyDir)) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(historyDir)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::readSafely)
                    .flatMap(Optional::stream)
                    .sorted(historyEntryComparator())
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    private Optional<GenerationHistoryEntry> readSafely(Path path) {
        try {
            return Optional.of(objectMapper.readValue(path.toFile(), GenerationHistoryEntry.class));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private Comparator<GenerationHistoryEntry> historyEntryComparator() {
        return Comparator
                .comparing(GenerationHistoryEntry::completedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(GenerationHistoryEntry::startedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private void enforceRetention() throws IOException {
        List<GenerationHistoryEntry> entries = listAll();
        for (int index = retentionMaxJobs; index < entries.size(); index++) {
            Files.deleteIfExists(resolveEntryPath(entries.get(index).jobId()));
        }
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path resolveEntryPath(String jobId) {
        return historyDir.resolve(jobId + ".json").normalize();
    }
}


