package cn.yumis.fight.modules.dungeons.api;

import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.combat.api.Runtime;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static cn.yumis.fight.modules.dungeons.api.TowerBattleCard.*;

/** Owner-side effects of the retained deck. Cooldowns and accumulated growth survive reconnects. */
public final class TowerBattleState {
    public record Checkpoint(int revision, Map<TowerBattleCard,Integer> growth,
                             Map<TowerBattleCard,Long> cooldowns) {}

    private int revision;
    private final Map<TowerBattleCard,Integer> growth = new EnumMap<>(TowerBattleCard.class);
    private final Map<TowerBattleCard,Long> cooldowns = new EnumMap<>(TowerBattleCard.class);
    private final Map<TowerBattleCard,Long> expires = new EnumMap<>(TowerBattleCard.class);
    private final Map<TowerBattleCard,Integer> stacks = new EnumMap<>(TowerBattleCard.class);
    private final Map<String,Double> siphoned = new HashMap<>();
    private final ArrayDeque<Long> killBuffs = new ArrayDeque<>();
    private long generation = -1, outSince, recoveryAt;
    private boolean mountainGiven;
    private double mercy;

    public int revision() { return revision; }

    public Checkpoint checkpoint() {
        return new Checkpoint(revision, Map.copyOf(growth), Map.copyOf(cooldowns));
    }

    public void restore(Checkpoint saved) {
        if (saved.revision() <= revision) return;
        revision = saved.revision();
        growth.clear(); growth.putAll(saved.growth());
        cooldowns.clear(); cooldowns.putAll(saved.cooldowns());
    }

    public void life(long nextGeneration, Runtime resources) {
        if (generation == nextGeneration) return;
        if (generation >= 0) clearShields(resources);
        generation = nextGeneration;
        expires.clear();
        stacks.clear();
        siphoned.clear();
        killBuffs.clear();
        outSince = recoveryAt = 0;
        mercy = 0;
        mountainGiven = false;
    }

    public static void clearShields(Runtime resources) {
        resources.absorption.keySet().removeIf(key -> key.startsWith("towerCard/"));
        resources.absorptionUntil.keySet().removeIf(key -> key.startsWith("towerCard/"));
    }

    public boolean ready(TowerBattleCard card, long wall) { return cooldowns.getOrDefault(card, 0L) <= wall; }

    public boolean resetCooldowns() {
        if (cooldowns.isEmpty()) return false;
        cooldowns.clear();
        revision++;
        return true;
    }

    public boolean use(TowerBattleCard card, Stats stats, long wall) {
        if (!card.held(stats) || !ready(card, wall)) return false;
        cooldowns.put(card, wall + TowerCardEffects.cooldownMillis(stats, card.millis("cooldown")));
        revision++;
        return true;
    }

    public void buff(TowerBattleCard card, long now) { expires.put(card, now + card.millis("duration")); }
    public boolean active(TowerBattleCard card, long now) { return expires.getOrDefault(card, 0L) > now; }

    public boolean invulnerable(long now) { return active(DIVINE, now) || active(UNSTOPPABLE, now); }

    public void stack(TowerBattleCard card, long now) {
        int current = card == HASTE || active(card, now) ? stacks.getOrDefault(card, 0) : 0;
        stacks.put(card, Math.min(card.count("limit"), current + 1));
        if (card != HASTE) buff(card, now);
    }

    public void prepareMercy(double points) { mercy = points; }

    public double takeMercy() {
        double points = mercy;
        mercy = 0;
        return points;
    }

    public double siphon(String action, double heal, double cap) {
        double used = siphoned.getOrDefault(action, 0d);
        double allowed = Math.min(heal, Math.max(0, cap - used));
        siphoned.put(action, used + allowed);
        return allowed;
    }

    public boolean killed(Stats stats, long now, boolean alive) {
        if (!SOULS.held(stats) && !TANK.held(stats) && !SHRINK.held(stats) && !THRILL.held(stats) && !CLEANSE.held(stats)) return false;
        if (SOULS.held(stats)) grow(SOULS);
        if (TANK.held(stats)) grow(TANK);
        if (SHRINK.held(stats)) grow(SHRINK);
        if (alive && THRILL.held(stats)) killBuffs.addLast(now + THRILL.millis("duration"));
        revision++;
        return true;
    }

    public void grow(TowerBattleCard card) {
        growth.merge(card, 1, Integer::sum);
        revision++;
    }

    public void hurt(Stats stats, long now) {
        if (HOME.held(stats)) expires.putIfAbsent(HOME, now + HOME.millis("duration"));
        if (PORCUPINE.held(stats)) buff(PORCUPINE, now);
    }

    public double incoming(Stats stats, long now) {
        double factor = active(PORCUPINE, now) ? 1 - PORCUPINE.ratio("reduction") : 1;
        if (CRIT_GUARD.held(stats)) factor *= 1 - stats.modifiers.getOrDefault("crit", 0d) * CRIT_GUARD.ratio("reduction");
        return factor;
    }

    public void combatTick(boolean fighting, long now) {
        if (fighting) {
            outSince = 0;
            mountainGiven = false;
        } else if (outSince == 0) {
            outSince = now;
            expires.remove(HOME);
            stacks.remove(HASTE);
        }
        while (!killBuffs.isEmpty() && killBuffs.getFirst() <= now) killBuffs.removeFirst();
    }

    public boolean mountainReady(long now) {
        if (mountainGiven || outSince == 0 || now - outSince < MOUNTAIN.millis("delay")) return false;
        mountainGiven = true;
        return true;
    }

    public boolean regenReady(long now) {
        if (recoveryAt == 0) recoveryAt = now + 1000;
        if (now < recoveryAt) return false;
        recoveryAt = now + 1000;
        return true;
    }

    public static void shield(Runtime resources, TowerBattleCard card, double points, long until) {
        String key = "towerCard/" + card.id();
        resources.absorption.put(key, points);
        if (until > 0) resources.absorptionUntil.put(key, until);
    }

    public double slapIncrease(long now) {
        return active(SLAP, now) ? stacks.getOrDefault(SLAP, 0) * SLAP.ratio("damage") : 0;
    }

    public void apply(Stats stats, long now) {
        double extraAttack = steps(SOULS) * SOULS.value("attack");
        if (extraAttack > 0) {
            stats.attribute(Stat.PHYSICAL_ATTACK).fadd += extraAttack;
            stats.attribute(Stat.MAGICAL_ATTACK).fadd += extraAttack;
        }
        int magic = growth.getOrDefault(EVIL, 0);
        if (magic > 0) stats.attribute(Stat.MAGICAL_ATTACK).fadd += magic * EVIL.value("attack");
        if (growth.containsKey(TANK)) stats.mut(Stat.HEALTH, steps(TANK) * TANK.ratio("health"));
        double haste = steps(SHRINK) * SHRINK.ratio("haste") + stacks.getOrDefault(HASTE, 0) * HASTE.ratio("haste");
        if (haste != 0) stats.mut(Stat.COOLDOWN_RECOVERY, haste);
        if (active(BRAVADO, now)) {
            double defense = stacks.getOrDefault(BRAVADO, 0) * BRAVADO.ratio("defense");
            stats.mut(Stat.PHYSICAL_DEFENSE, defense);
            stats.mut(Stat.MAGICAL_DEFENSE, defense);
        }
        if (active(ADAPTIVE, now)) stats.mut(Stat.PHYSICAL_DEFENSE, ADAPTIVE.ratio("defense"));
        if (active(ADVANCE, now)) stats.mut(Stat.MOVEMENT_SPEED, ADVANCE.ratio("movement"));
        if (active(ESCAPE, now)) stats.mut(Stat.MOVEMENT_SPEED, ESCAPE.ratio("movement"));
        if (HOME.held(stats) && (!expires.containsKey(HOME) || active(HOME, now))) stats.mut(Stat.MOVEMENT_SPEED, HOME.ratio("movement"));
        if (active(HELP, now)) {
            stats.extra(Stat.ATTACK_SPEED, HELP.value("speed"));
            attack(stats, HELP.ratio("attack"));
        }
        long thrill = killBuffs.stream().filter(until -> until > now).count();
        if (thrill > 0) {
            stats.mut(Stat.MOVEMENT_SPEED, thrill * THRILL.ratio("movement"));
            attack(stats, thrill * THRILL.ratio("attack"));
        }
        double slap = slapIncrease(now);
        if (slap > 0) stats.modifiers.put(TowerCardEffects.SLAP_DAMAGE, slap);
    }

    private int steps(TowerBattleCard card) { return growth.getOrDefault(card, 0) / card.count("kills"); }

    private static void attack(Stats stats, double increase) {
        stats.mut(Stat.PHYSICAL_ATTACK, increase);
        stats.mut(Stat.MAGICAL_ATTACK, increase);
    }
}
