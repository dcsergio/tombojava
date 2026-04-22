package it.sdc.tombojava.tombola;

import java.util.List;

public record TombolaSeries(List<TombolaCard> cards) {

    public TombolaSeries {
        if (cards == null || cards.size() != 6) {
            throw new IllegalArgumentException("A series must contain exactly 6 cards");
        }
        cards = List.copyOf(cards);
    }
}

