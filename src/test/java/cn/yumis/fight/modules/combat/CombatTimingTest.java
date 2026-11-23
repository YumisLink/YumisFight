package cn.yumis.fight.modules.combat;

import cn.yumis.fight.modules.combat.api.CombatTiming;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 基础回魔：每秒恢复最大MP的(1%＋0.05%×精神)；精神20为2%，满级890MP对应每秒17.8点。 */
class CombatTimingTest {
    @Test void baseManaRegenerationScalesWithSpirit(){
        assertEquals(1.0,CombatTiming.manaRegeneration(100,0),1e-9);
        assertEquals(13.35,CombatTiming.manaRegeneration(890,10),1e-9);
        assertEquals(17.8,CombatTiming.manaRegeneration(890,20),1e-9);
        assertEquals(0,CombatTiming.manaRegeneration(0,10),1e-9);
    }
}
