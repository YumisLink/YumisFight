package cn.yumis.fight.modules.dungeons.api;
import java.util.*;

public final class EncounterScaling {
    private EncounterScaling(){}
    public static double partyMultiplier(int n){
        double[] v={
            1,2.1,3.6,5.6,8.2,11.5,15.6,20.6
        }
        ;
        return n<=8?v[Math.clamp(n-1,0,7)]:20.6+5*(n-8);
    }
    public static double expandedQ(double q,int oldSize,int newSize){
        return 1-(1-q)*partyMultiplier(oldSize)/partyMultiplier(newSize);
    }
}
