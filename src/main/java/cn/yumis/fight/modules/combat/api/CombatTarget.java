package cn.yumis.fight.modules.combat.api;
import java.util.*;
/** Encounter providers expose combat state without exposing dungeon internals. */
public interface CombatTarget {
    UUID id();
    Life life();
    void life(Life life);
    Map<String,Double> contributions();
    double maximumHealth(int playerLevel,boolean sync);
    String power();
    boolean rewardsEligible();
}
