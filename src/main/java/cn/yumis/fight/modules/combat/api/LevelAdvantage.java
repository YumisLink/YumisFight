package cn.yumis.fight.modules.combat.api;
import java.util.*;

public final class LevelAdvantage {
    private LevelAdvantage(){}
    public static double advantage(int attacker,int defender){
        int n=Math.max(0,Math.abs(attacker-defender)-5);
        return attacker>=defender?1+.08*n:Math.max(.1,1-.03*n);
    }
}
