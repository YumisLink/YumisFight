package cn.yumis.fight.modules.equipment;

import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.equipment.api.*;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** 强化/增幅的数值、概率、降级、侵染与属性贡献的纯逻辑。 */
class EnhancementRulesTest {
    private static final class Forced extends Random {
        private final int value;
        private boolean used;
        Forced(int value){this.value=value;}
        @Override public int nextInt(int bound){used=true;return value;}
        boolean consumed(){return used;}
    }

    private static Gear weapon(int enhanceLevel){
        Gear g=new Gear();
        g.slot=Slot.WEAPON;g.weapon=Weapon.KATANA;g.rarity=Rarity.COMMON;g.level=1;
        g.baseGrowth.put(Stat.PHYSICAL_ATTACK,new double[]{100,110});
        g.baseGrowth.put(Stat.MAGICAL_ATTACK,new double[]{20,22});
        g.enhanceLevel=enhanceLevel;
        return g;
    }

    @Test void rollOutcomes(){
        assertEquals(EnhancementRules.Outcome.SUCCESS,EnhancementRules.roll(0,100,new Forced(0)));
        assertEquals(EnhancementRules.Outcome.SUCCESS,EnhancementRules.roll(16,20,new Forced(0)));
        assertEquals(EnhancementRules.Outcome.FAILURE,EnhancementRules.roll(0,20,new Forced(20)));
        assertEquals(EnhancementRules.Outcome.SHATTER,EnhancementRules.roll(23,20,new Forced(20)));
        assertThrows(IllegalArgumentException.class,()->EnhancementRules.roll(24,20,new Forced(0)));
    }




}
