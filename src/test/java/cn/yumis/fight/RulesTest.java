package cn.yumis.fight;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.dungeons.api.Difficulty;
import static cn.yumis.fight.modules.character.api.CharacterRules.spAt;
import static cn.yumis.fight.modules.character.api.CharacterRules.nextExperience;
import static cn.yumis.fight.modules.dungeons.api.EncounterScaling.partyMultiplier;
import static cn.yumis.fight.modules.dungeons.api.EncounterScaling.expandedQ;
import static cn.yumis.fight.modules.professions.api.ProfessionRules.professionThreshold;
import cn.yumis.fight.modules.economy.api.LootOdds;
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
    @Test void equipmentRarityUsesTheThousandPointDropRateBase(){
        double[] base={45,30,17,6,1.8,.2};
        for(double dropRate:new double[]{0,100,1000}){
            double[] expected=new double[base.length];double total=0;
            for(int i=0;i<base.length;i++)total+=expected[i]=base[i]*Math.pow(1+dropRate/1000,i);
            double start=0;
            for(int i=0;i<expected.length;i++){
                double midpoint=(start+expected[i]/2)/total;
                assertEquals(Rarity.values()[i],LootOdds.rarity(Difficulty.NORMAL,true,false,dropRate,new Random(){@Override public double nextDouble(){return midpoint;}}));
                start+=expected[i];
            }
        }
    }
    @Test void level80KeepsEveryGrantAndStopsExperience(){
        Progress p=new Progress();
        long total=0;
        for(int l=1;l<80;l++)total+=nextExperience(l);
        p.grant(total);
        assertEquals(80,p.level);
        assertEquals(158,p.free());
        assertEquals(3600,p.sp);
        assertEquals(spAt(80),p.sp);
        p.grant(1000000);
        assertEquals(0,p.experience);
        assertEquals(3600,p.sp);
    }
    @Test void capsAreLearnedOnlyButEquipmentAddsEffectiveRanks(){
        Progress p=new Progress();
        p.level=80;
        p.promote(cn.yumis.fight.modules.character.api.JobCatalog.SWORDMASTER);
        assertFalse(cn.yumis.fight.modules.character.api.JobCatalog.canAccept(p,cn.yumis.fight.modules.character.api.JobCatalog.BASE));
        p.sp=10000;
        for(SkillDefinition skill:SkillCatalog.all()){
            if(skill.advanced()&&!p.hasJob(skill.requiredJob)){assertThrows(IllegalArgumentException.class,()->SkillLearning.learn(p,skill,1));continue;}
            for(int i=0;i<skill.cap;i++)SkillLearning.learn(p,skill,1);
            assertThrows(IllegalArgumentException.class,()->SkillLearning.learn(p,skill,1));
        }
        Stats stats=new Stats();
        stats.ranks.put("SWORD",3);
        assertEquals(SkillCatalog.DANCE.cap+3,SkillLearning.effective(stats,p,SkillCatalog.DANCE));
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

    @Test void expansionPreservesAbsoluteLostHealthAndInvalidatesOldBasis(){
        for(int old=1;old<15;old++){
            double q=.31;
            double before=(1-q)*partyMultiplier(old);
            double after=(1-expandedQ(q,old,old+1))*partyMultiplier(old+1);
            assertEquals(before,after,1e-9);
        }
        assertEquals(20.6,partyMultiplier(8));
        assertEquals(25.6,partyMultiplier(9));
    }
    @Test void skillParametersAreImmutableSnapshots(){
        var changes=new ArrayList<ParameterModifier>();
        changes.add(new ParameterModifier("test",SkillCatalog.DANCE.id,"slashes",2,1));
        changes.add(new ParameterModifier("test",SkillCatalog.DANCE.id,"damage",0,1.5));
        var snapshot=SkillParameters.resolve(SkillCatalog.DANCE,1,changes);
        assertEquals(12,snapshot.get("slashes",0));
        assertEquals(1.05,snapshot.get("damage",0),1e-9);
        changes.clear();
        assertEquals(12,snapshot.get("slashes",0));
        assertThrows(UnsupportedOperationException.class,()->snapshot.values().put("slashes",99d));
    }
}
