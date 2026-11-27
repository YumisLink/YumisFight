package cn.yumis.fight.modules.combat.api;
import java.util.*;

public final class LevelAdvantage {
    private LevelAdvantage(){}
    public static double advantage(int attacker,int defender){
        var c=CombatBalanceRules.current().combat;
        int n=Math.max(0,Math.abs(attacker-defender)-c.levelAdvantageGrace());
        return attacker>=defender?1+c.levelAdvantageHighPerLevel()*n:Math.max(c.levelAdvantageFloor(),1-c.levelAdvantageLowPerLevel()*n);
    }
}
