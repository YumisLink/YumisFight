package cn.yumis.fight;
import cn.yumis.fight.modules.skills.api.Stamina;

import cn.yumis.fight.modules.skills.api.SkillCatalog;
import cn.yumis.fight.modules.skills.api.SkillLearning;
import cn.yumis.fight.modules.character.api.Progress;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;

class ProgressMovementTest {
    @Test void currentBindingsNormalizeIdempotentlyAndOldLayoutsAreIgnored(){
        Progress p=new Progress();p.slots[0]=SkillCatalog.GHOST.id;p.slots[8]=SkillCatalog.WAVE.id;p.slots[16]=SkillCatalog.DRAW.id;p.slots[1]=null;
        p.normalizeSlots();assertEquals(24,p.slots.length);assertEquals(SkillCatalog.DRAW.id,p.slots[16]);assertEquals("",p.slots[1]);
        String[] current=p.slots.clone();p.normalizeSlots();assertArrayEquals(current,p.slots);
        p.slots=new String[32];Arrays.fill(p.slots,SkillCatalog.STORM.id);p.normalizeSlots();
        assertEquals(24,p.slots.length);assertTrue(Arrays.stream(p.slots).allMatch(String::isEmpty));
    }
    @Test void batchLearningSpendsOnlyAffordableLevelsAndCaps(){
        Progress p=new Progress();p.level=80;p.sp=36;
        assertEquals(2,SkillLearning.learn(p,SkillCatalog.GHOST,5));assertEquals(6,p.sp);assertEquals(2,p.learned(SkillCatalog.GHOST.id));
        assertThrows(IllegalArgumentException.class,()->SkillLearning.learn(p,SkillCatalog.GHOST,5));assertEquals(6,p.sp);
        p.sp=10000;p.skills.put("yumis_dungeon_and_fight:ghost",SkillCatalog.GHOST.cap-1);
        assertEquals(1,SkillLearning.learn(p,SkillCatalog.GHOST,5));assertEquals(9985,p.sp);
        assertThrows(IllegalArgumentException.class,()->SkillLearning.learn(p,SkillCatalog.GHOST,2));
    }

    @Test void fiveLeapsExhaustPoolAndCooldownCannotBeSkipped(){
        Stamina s=new Stamina();
        for(int i=0;i<5;i++)assertTrue(s.use(i*5,false));
        assertEquals(0,s.value);assertFalse(s.use(25,false));assertEquals(20,s.lastUse);
        for(int i=21;i<40;i++)assertFalse(s.recover(i,true,false));
        assertTrue(s.recover(40,true,false));assertEquals(1,s.value);
        assertFalse(s.recover(40,true,false));
        for(int i=41;i<60;i++)s.recover(i,true,false);
        assertEquals(20,s.value);assertTrue(s.use(60,false));assertEquals(0,s.value);
        assertFalse(s.use(64,true));assertTrue(s.use(65,true));assertEquals(0,s.value);
    }

}
