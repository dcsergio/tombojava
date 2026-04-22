package it.sdc.tombojava.web;

import it.sdc.tombojava.job.GenerationJobService;
import it.sdc.tombojava.job.GenerationJobStatus;
import it.sdc.tombojava.job.JobState;
import it.sdc.tombojava.tombola.TombolaCard;
import it.sdc.tombojava.tombola.TombolaSeries;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/jobs")
public class GenerationJobController {

    private final GenerationJobService generationJobService;

    public GenerationJobController(GenerationJobService generationJobService) {
        this.generationJobService = generationJobService;
    }

    @PostMapping
    public ResponseEntity<StartGenerationResponse> start(@RequestBody StartGenerationRequest request) {
        try {
            request.validate();
            String jobId = generationJobService.startJob(request.seriesCount(), request.seed(), request.maxWaitSeconds(), request.maxSeriesAttempts());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new StartGenerationResponse(jobId));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping("/{jobId}")
    public GenerationJobStatus status(@PathVariable String jobId) {
        return generationJobService.findStatus(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job non trovato"));
    }

    @GetMapping("/{jobId}/download")
    public ResponseEntity<Resource> download(@PathVariable String jobId) {
        GenerationJobStatus status = generationJobService.findStatus(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job non trovato"));

        if (status.downloadUrl() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Il PDF non e' ancora disponibile");
        }

        Resource resource = generationJobService.loadPdf(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File PDF non trovato"));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + status.fileName() + "\"")
                .body(resource);
    }

    @PostMapping("/{jobId}/verify")
    public VerifySeriesResponse verifySeries(@PathVariable String jobId, @RequestBody VerifySeriesRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Richiesta di verifica mancante");
        }
        try {
            request.validate();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }

        GenerationJobStatus status = generationJobService.findStatus(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job non trovato"));

        if (status.state() != JobState.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La verifica e' disponibile solo a generazione completata");
        }

        if (status.seriesCount() == null || request.seriesNumber() > status.seriesCount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Numero di serie non valido per questo job");
        }

        TombolaSeries series = generationJobService.findGeneratedSeries(jobId, request.seriesNumber())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Serie non trovata"));

        return toResponse(request.seriesNumber(), series, request.extractedNumbersSet());
    }

    private VerifySeriesResponse toResponse(int seriesNumber, TombolaSeries series, Set<Integer> extractedNumbers) {
        List<VerifySeriesResponse.VerifyCard> cards = new ArrayList<>();
        for (int cardIndex = 0; cardIndex < series.cards().size(); cardIndex++) {
            TombolaCard card = series.cards().get(cardIndex);
            int[][] matrix = card.toMatrixCopy();
            List<List<VerifySeriesResponse.VerifyCell>> rows = new ArrayList<>();

            for (int row = 0; row < matrix.length; row++) {
                List<VerifySeriesResponse.VerifyCell> cells = new ArrayList<>();
                for (int col = 0; col < matrix[row].length; col++) {
                    int value = matrix[row][col];
                    boolean drawn = value > 0 && extractedNumbers.contains(value);
                    cells.add(new VerifySeriesResponse.VerifyCell(value, drawn));
                }
                rows.add(cells);
            }

            cards.add(new VerifySeriesResponse.VerifyCard(cardIndex + 1, rows));
        }
        return new VerifySeriesResponse(seriesNumber, cards);
    }
}

