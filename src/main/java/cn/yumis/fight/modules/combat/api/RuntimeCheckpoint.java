package cn.yumis.fight.modules.combat.api;

import java.util.Map;

/** Semantic client-owned resource state. Sample clocks are not dirty changes. */
public record RuntimeCheckpoint(double mp,double shield,long epoch,Map<String,Long> cooldowns,
        Map<String,Long> buffs,Map<String,Long> absorptionUntil,Map<String,Double> absorption,String equipmentBuffs) {
    public static RuntimeCheckpoint capture(Runtime value){return new RuntimeCheckpoint(value.mp,value.shield,value.controlEpoch,
        Map.copyOf(value.cooldowns),Map.copyOf(value.buffs),Map.copyOf(value.absorptionUntil),Map.copyOf(value.absorption),new com.google.gson.Gson().toJson(value.equipmentBuffs));}
}
