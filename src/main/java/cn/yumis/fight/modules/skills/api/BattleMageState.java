package cn.yumis.fight.modules.skills.api;

import java.util.*;

/**
 * Connection/life-scoped Battle Mage state: random 5-color Chaser FIFO queue,
 * independent 5-color Chaser buffs, Fusion Resonance buff, Battle Groove combo stages,
 * and Teana Transformation window. Owned by the skills module.
 */
public final class BattleMageState {
    /** Chaser color indices: 0=Neutral, 1=Light, 2=Fire, 3=Ice, 4=Dark. */
    public static final int NEUTRAL = 0, LIGHT = 1, FIRE = 2, ICE = 3, DARK = 4;

    public static final class Chaser {
        public int type;
        public long expiresAt;
        public boolean teana;

        public Chaser() {}

        public Chaser(int type, long expiresAt, boolean teana) {
            this.type = Math.clamp(type, 0, 4);
            this.expiresAt = expiresAt;
            this.teana = teana;
        }

        public Chaser copy() {
            return new Chaser(type, expiresAt, teana);
        }
    }

    public List<Chaser> chasers = new ArrayList<>();
    /** Expiration timestamps (server wall-clock ms) for 5-color Chaser buffs (0..4). */
    public long[] buffUntil = new long[5];
    /** Fusion Resonance buff expiration and cached power ratios. */
    public long fusionUntil;
    public double fusionAttackBonus;
    public double fusionCritBonus;
    /** Battle Groove combo tracking (1..comboStageCap). */
    public int comboCount;
    public int grooveStage;
    public long comboHoldUntil;
    public long nextComboDecayAt;
    /** Auto-chaser generation clock (server wall-clock ms). */
    public long nextAutoChaserAt;
    /** Teana Transformation active expiration (server wall-clock ms) and cast id. */
    public long teanaUntil;
    public String teanaCast = "";
    public boolean teanaFinisherUsed;
    /** Last two generated colors for mild anti-streak smoothing. */
    public int lastColor = -1;
    public int secondLastColor = -1;
    public long revision;

    public BattleMageState copy() {
        var value = new BattleMageState();
        value.chasers = new ArrayList<>(chasers == null ? 0 : chasers.size());
        if (chasers != null) {
            for (var c : chasers) {
                if (c != null) value.chasers.add(c.copy());
            }
        }
        value.buffUntil = buffUntil == null ? new long[5] : buffUntil.clone();
        value.fusionUntil = fusionUntil;
        value.fusionAttackBonus = fusionAttackBonus;
        value.fusionCritBonus = fusionCritBonus;
        value.comboCount = comboCount;
        value.grooveStage = grooveStage;
        value.comboHoldUntil = comboHoldUntil;
        value.nextComboDecayAt = nextComboDecayAt;
        value.nextAutoChaserAt = nextAutoChaserAt;
        value.teanaUntil = teanaUntil;
        value.teanaCast = teanaCast == null ? "" : teanaCast;
        value.teanaFinisherUsed = teanaFinisherUsed;
        value.lastColor = lastColor;
        value.secondLastColor = secondLastColor;
        value.revision = revision;
        return value;
    }

    public boolean transformed(long now) {
        return teanaUntil > now;
    }

    public int chaserCount(long now) {
        if (chasers == null) return 0;
        int count = 0;
        for (var c : chasers) {
            if (c != null && c.expiresAt > now) count++;
        }
        return count;
    }

    /** Packs up to 10 chaser colors (3 bits each: color+1 in 1..5, 0 = empty) for compact actor sync. */
    public long packedChasers(long now) {
        if (chasers == null || chasers.isEmpty()) return 0L;
        long packed = 0L;
        int idx = 0;
        for (var c : chasers) {
            if (c == null || c.expiresAt <= now) continue;
            if (idx >= 10) break;
            packed |= ((long) (Math.clamp(c.type, 0, 4) + 1)) << (idx * 3);
            idx++;
        }
        return packed;
    }

    public int activeBuffMask(long now) {
        if (buffUntil == null) return 0;
        int mask = 0;
        for (int i = 0; i < 5 && i < buffUntil.length; i++) {
            if (buffUntil[i] > now) mask |= (1 << i);
        }
        return mask;
    }

    public boolean expire(long now) {
        boolean changed = false;
        if (chasers == null) {
            chasers = new ArrayList<>();
        } else if (chasers.removeIf(c -> c == null || c.expiresAt <= now)) {
            changed = true;
        }
        if (buffUntil == null) {
            buffUntil = new long[5];
        } else {
            for (int i = 0; i < 5 && i < buffUntil.length; i++) {
                if (buffUntil[i] > 0 && buffUntil[i] <= now) {
                    buffUntil[i] = 0;
                    changed = true;
                }
            }
        }
        if (fusionUntil > 0 && fusionUntil <= now) {
            fusionUntil = 0;
            fusionAttackBonus = 0;
            fusionCritBonus = 0;
            changed = true;
        }
        if (teanaUntil > 0 && teanaUntil <= now) {
            teanaUntil = 0;
            teanaCast = "";
            teanaFinisherUsed = false;
            changed = true;
        }
        if (grooveStage > 0 && comboHoldUntil > 0 && now >= comboHoldUntil) {
            if (nextComboDecayAt <= 0) nextComboDecayAt = comboHoldUntil;
            while (grooveStage > 0 && now >= nextComboDecayAt) {
                grooveStage--;
                if (grooveStage <= 0) {
                    comboCount = 0;
                    comboHoldUntil = 0;
                    nextComboDecayAt = 0;
                } else {
                    comboCount = BattleMageRules.minComboForStage(grooveStage, 1.0);
                    nextComboDecayAt += 2000L;
                }
                changed = true;
            }
        }
        if (changed) revision++;
        return changed;
    }

    public void pushChaser(int type, long expiresAt, boolean teana, int maxCap) {
        if (chasers == null) chasers = new ArrayList<>();
        int cap = Math.max(1, maxCap);
        while (chasers.size() >= cap) {
            chasers.removeFirst();
        }
        int clamped = Math.clamp(type, 0, 4);
        chasers.add(new Chaser(clamped, expiresAt, teana));
        secondLastColor = lastColor;
        lastColor = clamped;
        revision++;
    }

    public Chaser pollChaser(long now) {
        if (chasers == null) return null;
        while (!chasers.isEmpty()) {
            var head = chasers.removeFirst();
            if (head != null && head.expiresAt > now) {
                revision++;
                return head;
            }
        }
        return null;
    }

    public Chaser peekChaser(long now) {
        if (chasers == null) return null;
        for (var c : chasers) {
            if (c != null && c.expiresAt > now) return c;
        }
        return null;
    }

    public List<Chaser> drainChasers(long now, int keepCount) {
        var drained = new ArrayList<Chaser>();
        if (chasers == null || chasers.isEmpty()) return drained;
        chasers.removeIf(c -> c == null || c.expiresAt <= now);
        int keep = Math.max(0, keepCount);
        while (chasers.size() > keep) {
            drained.add(chasers.removeFirst());
        }
        if (!drained.isEmpty()) revision++;
        return drained;
    }
}
