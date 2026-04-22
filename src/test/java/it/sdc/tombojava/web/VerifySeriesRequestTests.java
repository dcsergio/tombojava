package it.sdc.tombojava.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VerifySeriesRequestTests {

    @Test
    void validateAcceptsValidSeriesAndExtractedNumbers() {
        it.sdc.tombojava.web.VerifySeriesRequest request = new it.sdc.tombojava.web.VerifySeriesRequest(2, List.of(1, 45, 90));

        assertDoesNotThrow(request::validate);
        assertEquals(List.of(1, 45, 90), request.extractedNumbers().stream().toList());
    }

    @Test
    void validateRejectsOutOfRangeExtractedNumber() {
        it.sdc.tombojava.web.VerifySeriesRequest request = new it.sdc.tombojava.web.VerifySeriesRequest(1, List.of(0, 12));

        assertThrows(IllegalArgumentException.class, request::validate);
    }

    @Test
    void extractedNumbersSetRemovesDuplicates() {
        it.sdc.tombojava.web.VerifySeriesRequest request = new it.sdc.tombojava.web.VerifySeriesRequest(1, List.of(5, 8, 5, 8, 9));

        assertEquals(3, request.extractedNumbersSet().size());
    }
}

