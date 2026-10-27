package cn.yumis.fight;

import cn.yumis.fight.rules.*;
import cn.yumis.fight.rules.Models.*;
import cn.yumis.fight.api.SkillParameters;
import static cn.yumis.fight.rules.Rules.*;
import static cn.yumis.fight.rules.Models.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.*;

class CombatEquipmentRevisionTest {
    @Test void weaponCurvesAndLegacyUpgradeRemainDistinct(){
        for(Weapon w:Weapon.values()){
            Gear g=EquipmentRules.create(Slot.WEAPON,w,Rarity.COMMON,25,new Random(1));
            assertEquals(111*WeaponRules.attackScale(w,Stat.PHYSICAL_ATTACK),EquipmentRules.contribution(g).get(Stat.PHYSICAL_ATTACK),1e-9);
            Gear next=EquipmentRules.process(g,"upgrade_stone",new Random());
            assertEquals(131*WeaponRules.attackScale(w,Stat.MAGICAL_ATTACK),EquipmentRules.contribution(next).get(Stat.MAGICAL_ATTACK),1e-9);
            g.weaponCurve=0;var legacy=EquipmentRules.process(g,"upgrade_stone",new Random());assertEquals(131,EquipmentRules.contribution(legacy).get(Stat.PHYSICAL_ATTACK),1e-9);
        }
        assertEquals(0,JSON.fromJson("{}",Gear.class).weaponCurve);
    }
    @Test void speedAddsOnceAndGiantPenaltyRequiresLearnedMastery(){
        Progress p=new Progress();p.level=80;p.swordmaster=true;
        var gear=EquipmentRules.create(Slot.WEAPON,Weapon.GREATSWORD,Rarity.COMMON,1,new Random());var s=EquipmentRules.stats(p,List.of(gear));
        assertEquals(-10,s.get(Stat.ATTACK_SPEED));assertEquals(650/.9,WeaponRules.interval(p,s),1e-9);
        p.skills.put("MASTERY",1);assertEquals(1300/.9,WeaponRules.interval(p,s),1e-9);
        s.add(Stat.ATTACK_SPEED,30);assertEquals(1300/1.2,WeaponRules.interval(p,s),1e-9);
    }
    @Test void speedAffixEndpointsAndGrowthPreserveRoll(){
        assertEquals(3,WeaponRules.speedAffix(1,.8));assertEquals(5,WeaponRules.speedAffix(1,1.2));assertEquals(10,WeaponRules.speedAffix(80,.8));assertEquals(15,WeaponRules.speedAffix(80,1.2));
        var g=EquipmentRules.create(Slot.RING,Weapon.KATANA,Rarity.RARE,75,new Random());g.affixes.removeIf(a->!a.base);var a=new Affix("attack_speed",Stat.ATTACK_SPEED,1,false);a.roll=.93;g.affixes.add(a);
        var next=EquipmentRules.process(g,"upgrade_stone",new Random());var speed=next.affixes.stream().filter(v->v.id.equals("attack_speed")).findFirst().orElseThrow();assertEquals(.93,speed.roll);assertEquals(WeaponRules.speedAffix(80,.93),speed.value,1e-9);
        for(Slot slot:Slot.values())assertEquals(slot==Slot.WEAPON||AffixRules.sixJewelry(slot),AffixRules.flatAllowed(gFor(slot),Stat.ATTACK_SPEED));
    }
    private Gear gFor(Slot slot){Gear gear=new Gear();gear.slot=slot;return gear;}
    @Test void familyIsUnrerollableButReplaceableAndLockable(){
        var g=EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.LEGENDARY,35,new Random());g.affixes.removeIf(a->!a.base);var family=new Affix();family.id="family_sword";family.family="SWORD";family.operation="SKILL_FAMILY";family.rank=3;family.fixed=true;g.affixes.add(family);
        assertTrue(EquipmentRules.replaceable(family));assertThrows(IllegalArgumentException.class,()->EquipmentRules.process(g,"divine_stone",new Random()));
        var locked=EquipmentRules.process(g,"fracturing_orb",new Random());assertTrue(locked.affixes.getLast().locked);assertFalse(EquipmentRules.replaceable(locked.affixes.getLast()));assertThrows(IllegalArgumentException.class,()->EquipmentRules.process(locked,"chaos_stone",new Random()));
        assertEquals(g.affixes.size(),EquipmentRules.process(g,"chaos_stone",new Random()).affixes.size());
    }
    @Test void masteryCountsStayFixedAcrossRanksAndDanceStillLasts1250ms(){
        var p=new Progress();p.level=80;p.swordmaster=true;var s=new Stats();s.weaponType="LIGHTSABER";
        for(int rank:List.of(1,7)){p.skills.put("MASTERY",rank);assertEquals(7,parameters(p,s,Skill.TRIPLE).get("slashes",0));assertEquals(5,parameters(p,s,Skill.DRAGON).get("slashes",0));var dance=SkillSchedule.dance(parameters(p,s,Skill.DANCE));assertEquals(20,dance.slashes());assertEquals(1250,dance.durationMillis());assertEquals(20,dance.due(1250));}
        s.weaponType="BLUNT";assertEquals(5,parameters(p,s,Skill.TRIPLE).get("slashes",0));
    }
    private SkillParameters.Snapshot parameters(Progress p,Stats s,Skill skill){return WeaponRules.skill(SkillParameters.resolve(skill,1,List.of()),p,s);}
    @Test void stormOrderIsSharedUniqueAlternatingAndVariesAcrossSeeds(){
        Set<String> orders=new HashSet<>();for(long seed=0;seed<200;seed++){int[] order=StormChoreography.order(seed);assertArrayEquals(order,StormChoreography.order(seed));assertEquals(24,Arrays.stream(order).distinct().count());for(int i=0;i<24;i++)assertEquals(i%2,order[i]/12);orders.add(Arrays.toString(order));}assertTrue(orders.size()>100);
        assertEquals(8000d/1.5,SkillParameters.resolve(Skill.STORM,1,List.of()).get("duration",0)*1000,1e-8);
    }
}
