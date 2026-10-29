package cn.yumis.fight.modules.combat.api;

import java.util.Map;

/** Semantic client-owned resource state. Sample clocks and server-owned effects are not dirty changes. */
public record RuntimeCheckpoint(double mp,double shield,long epoch,Map<String,Long> cooldowns,
        Map<String,Long> buffs,Map<String,Long> absorptionUntil,Map<String,Double> absorption) {
    public static RuntimeCheckpoint capture(Runtime value){return new RuntimeCheckpoint(value.mp,value.shield,value.controlEpoch,
        Map.copyOf(value.cooldowns),Map.copyOf(value.buffs),Map.copyOf(value.absorptionUntil),Map.copyOf(value.absorption));}
}
