package cn.yumis.fight.modules.skills.api;

/** Connection/life-scoped Elementalist four-element rune lamp clocks, owned by the skills module. */
public final class ElementalistState {
    /** Expiration timestamps (server wall-clock ms) for Light(0), Fire(1), Ice(2), Dark(3) lamps. */
    public long[] lampUntil = new long[4];
    public long revision;

    public ElementalistState copy() {
        var value = new ElementalistState();
        value.lampUntil = lampUntil == null ? new long[4] : lampUntil.clone();
        value.revision = revision;
        return value;
    }

    public int litCount(long now) {
        if (lampUntil == null) return 0;
        int count = 0;
        for (int i = 0; i < 4 && i < lampUntil.length; i++) {
            if (lampUntil[i] > now) count++;
        }
        return count;
    }

    public int mask(long now) {
        if (lampUntil == null) return 0;
        int mask = 0;
        for (int i = 0; i < 4 && i < lampUntil.length; i++) {
            if (lampUntil[i] > now) mask |= (1 << i);
        }
        return mask;
    }

    public boolean expire(long now) {
        if (lampUntil == null) {
            lampUntil = new long[4];
            return false;
        }
        boolean changed = false;
        for (int i = 0; i < 4 && i < lampUntil.length; i++) {
            if (lampUntil[i] > 0 && lampUntil[i] <= now) {
                lampUntil[i] = 0;
                changed = true;
            }
        }
        if (changed) revision++;
        return changed;
    }

    public long shortestRemaining(long now) {
        if (lampUntil == null) return 0L;
        long shortest = Long.MAX_VALUE;
        for (int i = 0; i < 4 && i < lampUntil.length; i++) {
            long left = lampUntil[i] - now;
            if (left > 0 && left < shortest) shortest = left;
        }
        return shortest == Long.MAX_VALUE ? 0L : shortest;
    }
}
