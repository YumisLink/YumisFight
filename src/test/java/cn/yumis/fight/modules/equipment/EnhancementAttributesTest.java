package cn.yumis.fight.modules.equipment;

import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.equipment.api.*;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 强化/增幅收益进入角色属性聚合的端到端纯逻辑。 */
class EnhancementAttributesTest {
    private static Gear katana(int level){
        Gear g=new Gear();
        g.slot=Slot.WEAPON;g.weapon=Weapon.KATANA;g.rarity=Rarity.COMMON;g.level=1;
        g.baseGrowth.put(Stat.PHYSICAL_ATTACK,new double[]{100,110});
        g.baseGrowth.put(Stat.MAGICAL_ATTACK,new double[]{20,22});
        g.enhanceLevel=level;
        return g;
    }

    @Test void enhancedWeaponRaisesBasePanelInAggregation(){
        var progress=new Progress();
        Stats plain=EquipmentAttributes.stats(progress,List.of(katana(0)));
        Stats enhanced=EquipmentAttributes.stats(progress,List.of(katana(4)));
        // +4 累计 10%：基础物攻 100 → 聚合后基础物攻 +10。
        assertEquals(plain.get(Stat.PHYSICAL_ATTACK)+10,enhanced.get(Stat.PHYSICAL_ATTACK),1e-9);
        assertEquals(plain.get(Stat.MAGICAL_ATTACK)+2,enhanced.get(Stat.MAGICAL_ATTACK),1e-9);
    }

    @Test void contaminatedGearUsesAmplifyLevelOnly(){
        Gear g=katana(20);
        EnhancementRules.contaminate(g,new java.util.Random(3));
        g.amplifyLevel=3;
        var progress=new Progress();
        Stats plain=EquipmentAttributes.stats(progress,List.of(katana(0)));
        Stats amplified=EquipmentAttributes.stats(progress,List.of(g));
        double attackGain=amplified.get(Stat.PHYSICAL_ATTACK)-plain.get(Stat.PHYSICAL_ATTACK);
        assertEquals(6,attackGain,1e-9);
        var dimensional=EnhancementRules.contaminationStat(g);
        assertNotNull(dimensional);
        assertEquals(6,amplified.get(dimensional)-plain.get(dimensional),1e-9);
    }
}
