package cn.yumis.fight;
import cn.yumis.fight.modules.dungeons.api.Target;
import static cn.yumis.fight.modules.dungeons.api.EncounterScaling.partyMultiplier;
import static cn.yumis.fight.modules.professions.api.ProfessionRules.professionThreshold;
import cn.yumis.fight.modules.attributes.api.ParameterModifier;
import cn.yumis.fight.modules.skills.api.SkillDefinition;
import cn.yumis.fight.modules.skills.api.SkillCatalog;
import cn.yumis.fight.modules.skills.api.SkillLearning;

import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.professions.api.Profession;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.skills.api.SkillParameters;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.*;
class RulesTest {
    @Test void capsAreLearnedOnlyButEquipmentAddsEffectiveRanks(){
        Progress p=new Progress();
        p.level=80;
        p.promote(cn.yumis.fight.modules.character.api.JobCatalog.SWORDMASTER);
        p.awakened=true;
        assertFalse(cn.yumis.fight.modules.character.api.JobCatalog.canPromote(p,cn.yumis.fight.modules.character.api.JobCatalog.BASE));
        p.sp=10000;
        for(SkillDefinition skill:SkillCatalog.all()){
            if(skill.equipmentGranted){assertThrows(IllegalArgumentException.class,()->SkillLearning.learn(p,skill,1));continue;}
            if(skill.advanced()&&!p.hasJob(skill.requiredJob)){assertThrows(IllegalArgumentException.class,()->SkillLearning.learn(p,skill,1));continue;}
            for(int i=0;i<skill.limit(p.level,p.jobId());i++)SkillLearning.learn(p,skill,1);
            assertThrows(IllegalArgumentException.class,()->SkillLearning.learn(p,skill,1));
        }
        Stats stats=new Stats();
        stats.ranks.put("SWORD",3);
        assertEquals(SkillCatalog.DANCE.limit(p.level,p.jobId())+3,SkillLearning.effective(stats,p,SkillCatalog.DANCE));
        p.skills.remove(SkillCatalog.DANCE.id);
        assertEquals(0,SkillLearning.effective(stats,p,SkillCatalog.DANCE));
        SkillLearning.reset(p);
        assertTrue(p.skills.isEmpty());
        assertTrue(Arrays.stream(p.slots).allMatch(String::isEmpty));
    }
    @Test void professionsHaveCumulativeThresholdsAndUnboundedExperience(){
        assertEquals(BigInteger.valueOf(2125),professionThreshold(10));
        Profession p=new Profession();
        p.award(2124,false);
        assertEquals(9,p.level());
        p.award(1,false);
        assertEquals(10,p.level());
        p.award(1000000,true);
        assertEquals(10,p.level());
        assertEquals(BigInteger.valueOf(2125),p.coins);
        assertEquals(BigInteger.valueOf(1002125),p.experience);
    }

    @Test void partyExpansionPreservesHealthFractionAndAppliesAnInFlightHit(){
        var target=new Target();
        target.life.q=.60;
        double soloMaximum=target.health(1);
        double inFlightFraction=(soloMaximum*.10)/soloMaximum;
        assertTrue(target.expandParty(2));
        assertEquals(.60,target.life.q);
        assertEquals(1,target.life.generation);
        assertEquals(1,target.life.revision);
        assertEquals(soloMaximum*2.1,target.health(1),1e-9);
        assertFalse(target.expandParty(1));
        assertTrue(target.receive(inFlightFraction,false).accepted());
        assertEquals(.50,target.life.q,1e-9);
        assertEquals(20.6,partyMultiplier(8));
        assertEquals(25.6,partyMultiplier(9));
    }
    @Test void skillParametersAreImmutableSnapshots(){
        double baseDamage=SkillParameters.resolve(SkillCatalog.DANCE,1,List.of()).get("damage",0);
        var changes=new ArrayList<ParameterModifier>();
        changes.add(new ParameterModifier("test",SkillCatalog.DANCE.id,"slashes",2,1));
        changes.add(new ParameterModifier("test",SkillCatalog.DANCE.id,"damage",0,1.5));
        var snapshot=SkillParameters.resolve(SkillCatalog.DANCE,1,changes);
        assertEquals(12,snapshot.get("slashes",0));
        assertEquals(baseDamage*1.5,snapshot.get("damage",0),1e-9);
        changes.clear();
        assertEquals(12,snapshot.get("slashes",0));
        assertThrows(UnsupportedOperationException.class,()->snapshot.values().put("slashes",99d));
    }
}
