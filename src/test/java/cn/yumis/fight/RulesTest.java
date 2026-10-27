package cn.yumis.fight;
import cn.yumis.fight.rules.*;
import cn.yumis.fight.rules.Models.*;
import cn.yumis.fight.api.SkillParameters;
import static cn.yumis.fight.rules.Rules.*;
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
                assertEquals(Rarity.values()[i],CombatRules.rarity(Difficulty.NORMAL,true,false,dropRate,new Random(){@Override public double nextDouble(){return midpoint;}}));
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
        p.swordmaster=true;
        p.sp=10000;
        for(Skill skill:Skill.values()){
            for(int i=0;i<skill.cap;i++)p.learn(skill);
            assertThrows(IllegalArgumentException.class,()->p.learn(skill));
        }
        Stats stats=new Stats();
        stats.ranks.put(Family.SWORD,3);
        assertEquals(Skill.DANCE.cap+3,stats.effective(p,Skill.DANCE));
        p.skills.remove(Skill.DANCE.name());
        assertEquals(0,stats.effective(p,Skill.DANCE));
        p.resetSkills();
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
    @Test void regionalMaterialIsFixedForEveryLevelAndSlot(){
        Catalog c=Catalog.defaults();
        for(int l=1;l<=80;l++)for(Slot slot:Slot.values())if(l>=slot.minimum()){
            var recipe=c.recipe(slot,l);
            var regional=recipe.stream().filter(Catalog.Need::regional).toList();
            assertEquals(1,regional.size());
            assertEquals(slot.materialCost(),regional.getFirst().count());
            assertEquals(c.material(slot,l),regional.getFirst().id());
        }
        assertEquals("goblin_ingot",c.material(Slot.WEAPON,14));
        assertEquals("spinel",c.material(Slot.RING,15));
        assertEquals("fluorite",c.material(Slot.WEAPON,20));
        assertEquals("stigma_silk",c.material(Slot.CHEST,24));
        assertEquals("magtonium",c.material(Slot.HEAD,75));
        assertEquals("terranium",c.material(Slot.EARRING,80));
        assertEquals(16,c.recipe(Slot.WEAPON,80).getFirst().count());
        assertEquals(1,c.recipe(Slot.WEAPON,80).get(1).count());
        assertThrows(IllegalArgumentException.class,()->c.recipe(Slot.SUB_EQUIPMENT,49));
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
        var changes=new ArrayList<SkillParameters.Change>();
        changes.add(new SkillParameters.Change("test",Skill.DANCE,"slashes",2,1));
        changes.add(new SkillParameters.Change("test",Skill.DANCE,"damage",0,1.5));
        var snapshot=SkillParameters.resolve(Skill.DANCE,1,changes);
        assertEquals(12,snapshot.get("slashes",0));
        assertEquals(1.05,snapshot.get("damage",0),1e-9);
        changes.clear();
        assertEquals(12,snapshot.get("slashes",0));
        assertThrows(UnsupportedOperationException.class,()->snapshot.values().put("slashes",99d));
    }
}
