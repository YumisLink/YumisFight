package cn.yumis.fight.modules.combat.api;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Typed, zero-reflection, re-entrancy-safe hook dispatcher bucketed by event enum.
 * Listeners removed during dispatch are nulled in place and compacted when the outermost dispatch unwinds.
 */
public final class HookDispatcher<E extends Enum<E>, L> {
    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        @Override
        void close();
    }

    private static final class Bucket<L> {
        final List<L> items = new ArrayList<>();
        int dispatchDepth;
        boolean needsCompaction;

        void remove(L listener) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) != listener) continue;
                if (dispatchDepth == 0) {
                    items.remove(i);
                } else {
                    items.set(i, null);
                    needsCompaction = true;
                }
                return;
            }
        }

        void endDispatch() {
            dispatchDepth--;
            if (dispatchDepth != 0 || !needsCompaction) return;
            int write = 0;
            for (int read = 0; read < items.size(); read++) {
                L item = items.get(read);
                if (item != null) items.set(write++, item);
            }
            if (write < items.size()) {
                items.subList(write, items.size()).clear();
            }
            needsCompaction = false;
        }
    }

    private final EnumMap<E, Bucket<L>> buckets;

    public HookDispatcher(Class<E> eventType) {
        this.buckets = new EnumMap<>(Objects.requireNonNull(eventType, "eventType"));
    }

    public Registration register(E event, L listener) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(listener, "listener");
        Bucket<L> bucket = buckets.computeIfAbsent(event, ignored -> new Bucket<>());
        bucket.items.add(listener);
        return () -> unregister(event, listener);
    }

    public void unregister(E event, L listener) {
        if (event == null || listener == null) return;
        Bucket<L> bucket = buckets.get(event);
        if (bucket != null) bucket.remove(listener);
    }

    public void dispatch(E event, Consumer<L> invoker) {
        Bucket<L> bucket = buckets.get(event);
        if (bucket == null || bucket.items.isEmpty()) return;
        int end = bucket.items.size();
        bucket.dispatchDepth++;
        try {
            for (int i = 0; i < end; i++) {
                L listener = bucket.items.get(i);
                if (listener != null) invoker.accept(listener);
            }
        } finally {
            bucket.endDispatch();
        }
    }

    public boolean dispatchUntilTrue(E event, Predicate<L> invoker) {
        Bucket<L> bucket = buckets.get(event);
        if (bucket == null || bucket.items.isEmpty()) return false;
        int end = bucket.items.size();
        bucket.dispatchDepth++;
        try {
            for (int i = 0; i < end; i++) {
                L listener = bucket.items.get(i);
                if (listener != null && invoker.test(listener)) return true;
            }
            return false;
        } finally {
            bucket.endDispatch();
        }
    }

    public int listenerCount(E event) {
        Bucket<L> bucket = buckets.get(event);
        if (bucket == null) return 0;
        int count = 0;
        for (L listener : bucket.items) if (listener != null) count++;
        return count;
    }

    public void clear() {
        buckets.clear();
    }
}
