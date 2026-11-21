package cn.yumis.fight.modules.dungeons.api;

import java.util.List;
import java.util.UUID;

/** Run snapshot; changing rules never changes an active challenge. */
public final class WaystoneRun {
    public Waystone stone;
    public UUID owner,ownerSession;
    public WaystoneRules rules;
    public int monsterLevel,resultingTier;
    public long startedAt,finishedAt,penaltyMillis;
    public boolean settled;
    public String result="";
    public record MonsterModifiers(int tier,double statGrowth,List<Waystone.Modifier> modifiers){
        public MonsterModifiers{modifiers=List.copyOf(modifiers);}
        double multiplier(){return WaystoneRules.statMultiplier(tier,statGrowth);}
        double effect(String effect){return Waystone.effect(modifiers,effect);}
    }
    public MonsterModifiers monsterModifiers(){
        return new MonsterModifiers(stone.tier,rules.statGrowth,stone.modifiers.stream().filter(m->m.effect().startsWith("monster_")).toList());
    }
    public long elapsed(long now){return Math.max(0,(finishedAt>0?finishedAt:now)-startedAt)+penaltyMillis;}
}
