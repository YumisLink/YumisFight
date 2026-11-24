package cn.yumis.fight.modules.combat.api;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Stateless, immutable action timeline. Per-cast execution stores only an integer cursor,
 * allowing concurrent instances of the same skill or move without shared mutable flags.
 */
public final class ActionTimeline<C> {
    public record Cue<C>(long atMillis, String label, Consumer<C> action) {
        public Cue {
            if (atMillis < 0) throw new IllegalArgumentException("Negative cue time: " + atMillis);
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(action, "action");
        }
    }

    private final List<Cue<C>> cues;

    private ActionTimeline(List<Cue<C>> cues) {
        this.cues = List.copyOf(cues);
    }

    public static <C> Builder<C> builder() {
        return new Builder<>();
    }

    public List<Cue<C>> cues() {
        return cues;
    }

    public int size() {
        return cues.size();
    }

    public boolean isEmpty() {
        return cues.isEmpty();
    }

    public boolean finished(int cursor) {
        return cursor >= cues.size();
    }

    public long lastCueMillis() {
        return cues.isEmpty() ? 0 : cues.getLast().atMillis();
    }

    /**
     * Advances from {@code cursor} through all cues due at {@code elapsedMillis}.
     * Re-checks {@code stillActive} before every cue to prevent mid-abort re-entrancy.
     */
    public int advance(C context, int cursor, long elapsedMillis, BooleanSupplier stillActive) {
        int next = Math.max(0, cursor);
        while (next < cues.size()) {
            if (stillActive != null && !stillActive.getAsBoolean()) break;
            Cue<C> cue = cues.get(next);
            if (elapsedMillis < cue.atMillis()) break;
            next++;
            cue.action().accept(context);
        }
        return next;
    }

    public static final class Builder<C> {
        private final List<Cue<C>> cues = new ArrayList<>();

        public Builder<C> at(long atMillis, Consumer<C> action) {
            return at(atMillis, "", action);
        }

        public Builder<C> at(long atMillis, String label, Consumer<C> action) {
            cues.add(new Cue<>(Math.max(0, atMillis), label == null ? "" : label, action));
            return this;
        }

        public Builder<C> every(long startMillis, long intervalMillis, int count, String label, Consumer<C> action) {
            long gap = Math.max(1, intervalMillis);
            int total = Math.max(0, count);
            for (int i = 0; i < total; i++) {
                at(startMillis + i * gap, label, action);
            }
            return this;
        }

        public ActionTimeline<C> build() {
            List<Cue<C>> sorted = new ArrayList<>(cues);
            sorted.sort(Comparator.comparingLong(Cue::atMillis));
            return new ActionTimeline<>(sorted);
        }
    }
}
