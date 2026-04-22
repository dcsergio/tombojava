package it.sdc.tombojava.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StartGenerationRequestTests {

    @Test
    void validateAcceptsPositiveInputs() {
        StartGenerationRequest request = new StartGenerationRequest(2, 123L, 30, null);
        assertDoesNotThrow(request::validate);
    }

    @Test
    void validateRejectsMissingSeriesCount() {
        StartGenerationRequest request = new StartGenerationRequest(null, 123L, 30, null);
        assertThrows(IllegalArgumentException.class, request::validate);
    }
}

