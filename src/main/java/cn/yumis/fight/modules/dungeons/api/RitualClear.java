package cn.yumis.fight.modules.dungeons.api;

import java.util.List;

/** Immutable ritual clear fact; every settlement path (kill or weakness ending) publishes exactly one per run. */
public record RitualClear(String runId,String bossId,int level,String ending,String dimension,
                           double x,double y,double z,int radius,List<String> participants,long clearedAt){
    public RitualClear{
        participants=List.copyOf(participants);
    }
    public static String idOf(String runId,String player){return "ritual/"+runId+"/"+player;}
}
