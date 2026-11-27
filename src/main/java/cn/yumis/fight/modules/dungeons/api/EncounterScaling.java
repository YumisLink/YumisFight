package cn.yumis.fight.modules.dungeons.api;
import java.util.*;

public final class EncounterScaling {
    private EncounterScaling(){}
    public static double partyMultiplier(int n){
        return EnemyBalanceRules.current().global.partyMultiplier(n);
    }
}
