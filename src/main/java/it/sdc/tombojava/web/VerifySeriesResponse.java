package it.sdc.tombojava.web;

import java.util.List;

public record VerifySeriesResponse(
        Integer seriesNumber,
        List<VerifyCard> cards
) {
    public record VerifyCard(
            Integer cardNumber,
            List<List<VerifyCell>> rows
    ) {
    }

    public record VerifyCell(
            Integer value,
            boolean drawn
    ) {
    }
}

