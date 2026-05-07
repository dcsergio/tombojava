package it.sdc.tombojava.history;

import it.sdc.tombojava.tombola.TombolaCard;
import it.sdc.tombojava.tombola.TombolaSeries;

import java.util.List;

public final class TestSeriesFactory {

    private TestSeriesFactory() {
    }

    public static TombolaSeries series() {
        TombolaCard card = new TombolaCard(new int[][]{
                {1, 0, 0, 0, 41, 0, 0, 0, 81},
                {0, 12, 0, 0, 0, 52, 0, 72, 0},
                {0, 0, 23, 33, 0, 0, 63, 0, 0}
        });
        return new TombolaSeries(List.of(card, card, card, card, card, card));
    }
}


