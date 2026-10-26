package cn.yumis.fight;
import cn.yumis.fight.rules.Models.Progress;
import cn.yumis.fight.rules.Stamina;
import static cn.yumis.fight.rules.Rules.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;

class ProgressMovementTest {
    @Test void migratesOnlyOldAltAndMigrationIsIdempotent(){
        Progress p=new Progress();p.slots=new String[32];Arrays.fill(p.slots,"");
        p.slots[0]="GHOST";p.slots[8]="WAVE";p.slots[16]="STORM";p.slots[24]="DRAW";
        p.normalizeSlots();assertEquals(24,p.slots.length);assertEquals("DRAW",p.slots[16]);
        assertEquals("GHOST",p.slots[0]);assertEquals("WAVE",p.slots[8]);
        assertFalse(Arrays.asList(p.slots).contains("STORM"));
        String[] migrated=p.slots.clone();p.normalizeSlots();assertArrayEquals(migrated,p.slots);
    }
    @Test void batchLearningSpendsOnlyAffordableLevelsAndCaps(){
        Progress p=new Progress();p.level=80;p.sp=36;
        assertEquals(2,p.learn(Skill.GHOST,5));assertEquals(6,p.sp);assertEquals(2,p.learned(Skill.GHOST));
        assertThrows(IllegalArgumentException.class,()->p.learn(Skill.GHOST,5));assertEquals(6,p.sp);
        p.sp=10000;p.skills.put("GHOST",Skill.GHOST.cap-1);
        assertEquals(1,p.learn(Skill.GHOST,5));assertEquals(9985,p.sp);
        assertThrows(IllegalArgumentException.class,()->p.learn(Skill.GHOST,2));
    }
    @Test void batchAttributesUseAvailablePointsAndRejectUnknownStat(){
        Progress p=new Progress();p.level=4;
        assertEquals(5,p.allocate("strength",5));assertEquals(1,p.allocate("spirit",5));
        assertEquals(0,p.free());assertThrows(IllegalArgumentException.class,()->p.allocate("intelligence",5));
        Progress other=new Progress();other.level=2;
        assertThrows(IllegalArgumentException.class,()->other.allocate("health",5));assertEquals(2,other.free());
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
    @Test void airborneAndExemptModesFreezeResource(){
        Stamina s=new Stamina();assertTrue(s.use(0,false));
        for(int i=1;i<100;i++)s.recover(i,false,false);assertEquals(80,s.value);
        assertTrue(s.use(100,true));
        for(int i=101;i<200;i++)s.recover(i,true,true);assertEquals(80,s.value);
        assertTrue(s.recover(200,true,false));assertEquals(81,s.value);
        for(int i=201;i<400;i++)s.recover(i,true,false);assertEquals(100,s.value);
    }
}
