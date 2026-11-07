package com.kyroxova.lootgamesolver.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Orders discovered loot by known per-draw probability; unknown entries are retained. */
final class RewardLootPolicy {
    static final class Entry {
        final int chest, slot;
        final double probability;
        Entry(int chest, int slot, double probability) {
            this.chest = chest;
            this.slot = slot;
            this.probability = probability;
        }
    }

    static List<Entry> ordered(List<Entry> entries) {
        List<Entry> copy = new ArrayList<Entry>(entries);
        Collections.sort(copy, new Comparator<Entry>() {
            public int compare(Entry a, Entry b) {
                boolean aUnknown = Double.isNaN(a.probability), bUnknown = Double.isNaN(b.probability);
                if (aUnknown != bUnknown) return aUnknown ? 1 : -1;
                return aUnknown ? 0 : Double.compare(a.probability, b.probability);
            }
        });
        return copy;
    }
}
