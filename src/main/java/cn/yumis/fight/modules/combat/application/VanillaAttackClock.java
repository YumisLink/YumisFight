package cn.yumis.fight.modules.combat.application;
import java.util.*;

/** Accepted vanilla attacks share a wall-clock deadline only for this attacker/player life pair. */
final class VanillaAttackClock {
    private record Pair(UUID attacker,UUID player,String session,long generation){}
    private final Map<Pair,Long> deadlines=new HashMap<>();
    boolean accept(UUID attacker,UUID player,String session,long generation,long now){
        deadlines.values().removeIf(until->until<=now);var pair=new Pair(attacker,player,session,generation);
        if(deadlines.getOrDefault(pair,0L)>now)return false;deadlines.put(pair,now+650);return true;
    }
    @Override public String toString(){return deadlines.toString();}
}
