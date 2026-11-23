package cn.yumis.fight.modules.skills;

import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.modules.skills.api.CareerMastering;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** `/ydnf job <职业> <玩家>` 的纯逻辑：单个职业拉满为满级配置，全技能满级并自动放入快捷槽。 */
class CareerMasteringTest {
    private static final String NS="yumis_dungeon_and_fight:";

    @Test void mastersTheActiveProfileInPlaceKeepingBindings(){
        var book=CareerProfiles.first();
        var current=new Progress();
        current.promote(JobCatalog.BERSERKER);
        current.slots[0]=NS+"ghost";
        var result=CareerMastering.apply(JobCatalog.BERSERKER,current,book);
        var active=result.active();
        assertEquals(JobCatalog.BERSERKER,active.jobId());
        assertEquals(80,active.level);
        assertTrue(active.awakened());
        assertTrue(active.learned(NS+"blood_sword")>0);
        assertTrue(active.learned(NS+"mountain_crash")>0);
        assertEquals(NS+"ghost",active.slots[0]);
        assertTrue(active.sp>=0);
        assertTrue(result.book().entries.values().stream().noneMatch(e->e.growth!=null&&e.growth.get("job").getAsString().equals(JobCatalog.BERSERKER)));
        result.book().validate(active);
    }

}
