package dev.vfyjxf.cumulus.mc.pref;

import dev.vfyjxf.cumulus.kernel.space.Siting;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The frequency-weighted memory of one blueprint (7.1): the last {@value #capacity} choices, the mode
 * wins and a tie goes to the choice whose latest occurrence is the most recent. The list keeps the
 * most recent choice last.
 */
public record ChoiceHistory(List<Siting> recent) {

    /** The measured window of the frequency weighting. */
    public static final int capacity = 8;

    public ChoiceHistory {
        recent = List.copyOf(recent);
        if (recent.size() > capacity) {
            recent = List.copyOf(recent.subList(recent.size() - capacity, recent.size()));
        }
    }

    public static ChoiceHistory empty() {
        return new ChoiceHistory(List.of());
    }

    /** Appends one choice, dropping the oldest beyond the window. */
    public ChoiceHistory append(Siting choice) {
        Objects.requireNonNull(choice, "choice");
        List<Siting> next = new ArrayList<>(recent);
        next.add(choice);
        return new ChoiceHistory(next);
    }

    /** The mode, with the most recent occurrence breaking a tie; null for an empty history. */
    public @Nullable Siting preferred() {
        if (recent.isEmpty()) {
            return null;
        }
        Map<Siting, Integer> counts = new LinkedHashMap<>();
        for (Siting choice : recent) {
            counts.merge(choice, 1, Integer::sum);
        }
        Siting best = null;
        int bestCount = 0;
        for (int index = recent.size() - 1; index >= 0; index--) {
            Siting choice = recent.get(index);
            int count = counts.getOrDefault(choice, 0);
            if (count > bestCount) {
                best = choice;
                bestCount = count;
            }
        }
        return best;
    }

    public boolean isEmpty() {
        return recent.isEmpty();
    }
}
