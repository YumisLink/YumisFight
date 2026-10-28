package cn.yumis.fight;
import cn.yumis.fight.modules.equipment.api.EquipmentAffixes;
import cn.yumis.fight.modules.equipment.api.EquipmentAttributes;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.Slot;
import cn.yumis.fight.modules.equipment.api.Weapon;
import cn.yumis.fight.modules.skills.api.StormChoreography;
import cn.yumis.fight.modules.skills.api.SkillSchedule;
import cn.yumis.fight.modules.skills.api.WeaponSkills;
import cn.yumis.fight.modules.affixes.api.AttackSpeedRoll;
import cn.yumis.fight.modules.equipment.api.WeaponTraits;
import cn.yumis.fight.modules.skills.api.SkillDefinition;
import cn.yumis.fight.modules.skills.api.SkillCatalog;

import cn.yumis.fight.modules.equipment.api.EquipmentRules;

import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.affixes.api.Affix;
import cn.yumis.fight.modules.equipment.api.Gear;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.skills.api.SkillParameters;
import static cn.yumis.fight.bootstrap.ApplicationJson.JSON;

import static cn.yumis.fight.modules.combat.api.LifeRules.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.*;

class CombatEquipmentRevisionTest {
    @Test void currentWeaponCurvesRemainConsistentAfterUpgrade(){
        for(Weapon w:Weapon.standard()){
            Gear g=EquipmentRules.create(Slot.WEAPON,w,Rarity.COMMON,25,new Random(1));
            assertEquals(111*WeaponTraits.attackScale(w,Stat.PHYSICAL_ATTACK),EquipmentRules.contribution(cn.yumis.fight.modules.equipment.api.EquipmentResolution.initialize(g)).get(Stat.PHYSICAL_ATTACK),1e-9);
            Gear next=EquipmentRules.process(cn.yumis.fight.modules.equipment.api.EquipmentResolution.initialize(g),"upgrade_stone",new Random());
            assertEquals(131*WeaponTraits.attackScale(w,Stat.MAGICAL_ATTACK),EquipmentRules.contribution(cn.yumis.fight.modules.equipment.api.EquipmentResolution.initialize(next)).get(Stat.MAGICAL_ATTACK),1e-9);
        }
    }
    @Test void speedAddsOnceAndGiantPenaltyRequiresLearnedMastery(){
        Progress p=new Progress();p.level=80;p.promote(cn.yumis.fight.modules.character.api.JobCatalog.SWORDMASTER);
        var gear=EquipmentRules.create(Slot.WEAPON,Weapon.GREATSWORD,Rarity.COMMON,1,new Random());var s=cn.yumis.fight.modules.equipment.api.EquipmentAttributes.stats(p,List.of(gear));
        assertEquals(-10,s.get(Stat.ATTACK_SPEED));assertEquals(650/.9,WeaponSkills.interval(p,s),1e-9);
        p.skills.put("yumis_dungeon_and_fight:mastery",1);assertEquals(1300/.9,WeaponSkills.interval(p,s),1e-9);
        s.add(Stat.ATTACK_SPEED,30);assertEquals(1300/1.2,WeaponSkills.interval(p,s),1e-9);
    }
    @Test void speedAffixEndpointsAndGrowthPreserveRoll(){
        assertEquals(3,AttackSpeedRoll.sample(1,.8));assertEquals(5,AttackSpeedRoll.sample(1,1.2));assertEquals(10,AttackSpeedRoll.sample(80,.8));assertEquals(15,AttackSpeedRoll.sample(80,1.2));
        var g=EquipmentRules.create(Slot.RING,Weapon.KATANA,Rarity.RARE,75,new Random());g.affixes.removeIf(a->!a.base);var a=new Affix("attack_speed",Stat.ATTACK_SPEED,1,false);a.roll=.93;g.affixes.add(a);
        var next=EquipmentRules.process(cn.yumis.fight.modules.equipment.api.EquipmentResolution.initialize(g),"upgrade_stone",new Random());var speed=next.affixes.stream().filter(v->v.id.equals("attack_speed")).findFirst().orElseThrow();assertEquals(.93,speed.roll);assertEquals(AttackSpeedRoll.sample(80,.93),speed.value,1e-9);
        for(Slot slot:Slot.values())assertEquals(slot==Slot.WEAPON||EquipmentAffixes.sixJewelry(slot),EquipmentAffixes.flatAllowed(gFor(slot),Stat.ATTACK_SPEED));
    }
    private Gear gFor(Slot slot){Gear gear=new Gear();gear.slot=slot;return gear;}
    @Test void familyIsUnrerollableButReplaceableAndLockable(){
        var g=EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.LEGENDARY,35,new Random());g.affixes.removeIf(a->!a.base);var family=new Affix();family.id="family_sword";family.family="SWORD";family.operation="SKILL_FAMILY";family.rank=3;family.fixed=true;g.affixes.add(family);
        assertTrue(EquipmentRules.replaceable(family));assertThrows(IllegalArgumentException.class,()->EquipmentRules.process(cn.yumis.fight.modules.equipment.api.EquipmentResolution.initialize(g),"divine_stone",new Random()));
        var locked=EquipmentRules.process(cn.yumis.fight.modules.equipment.api.EquipmentResolution.initialize(g),"fracturing_orb",new Random());assertTrue(locked.affixes.getLast().locked);assertFalse(EquipmentRules.replaceable(locked.affixes.getLast()));assertThrows(IllegalArgumentException.class,()->EquipmentRules.process(cn.yumis.fight.modules.equipment.api.EquipmentResolution.initialize(locked),"chaos_stone",new Random()));
        assertEquals(g.affixes.size(),EquipmentRules.process(cn.yumis.fight.modules.equipment.api.EquipmentResolution.initialize(g),"chaos_stone",new Random()).affixes.size());
    }
    @Test void masteryCountsStayFixedAcrossRanksAndDanceStillLasts1250ms(){
        var p=new Progress();p.level=80;p.promote(cn.yumis.fight.modules.character.api.JobCatalog.SWORDMASTER);var s=new Stats();s.weaponType="LIGHTSABER";
        for(int rank:List.of(1,7)){p.skills.put("yumis_dungeon_and_fight:mastery",rank);assertEquals(7,parameters(p,s,SkillCatalog.TRIPLE).get("slashes",0));assertEquals(5,parameters(p,s,SkillCatalog.DRAGON).get("slashes",0));var dance=SkillSchedule.dance(parameters(p,s,SkillCatalog.DANCE));assertEquals(20,dance.slashes());assertEquals(1250,dance.durationMillis());assertEquals(20,dance.due(1250));}
        s.weaponType="BLUNT";assertEquals(5,parameters(p,s,SkillCatalog.TRIPLE).get("slashes",0));
    }
    private SkillParameters.Snapshot parameters(Progress p,Stats s,SkillDefinition skill){return WeaponSkills.skill(SkillParameters.resolve(skill,1,List.of()),p,s);}
    @Test void stormOrderIsSharedUniqueAlternatingAndVariesAcrossSeeds(){
        Set<String> orders=new HashSet<>();for(long seed=0;seed<200;seed++){int[] order=StormChoreography.order(seed);assertArrayEquals(order,StormChoreography.order(seed));assertEquals(24,Arrays.stream(order).distinct().count());for(int i=0;i<24;i++)assertEquals(i%2,order[i]/12);orders.add(Arrays.toString(order));}assertTrue(orders.size()>100);
        assertEquals(8000d/1.5,SkillParameters.resolve(SkillCatalog.STORM,1,List.of()).get("duration",0)*1000,1e-8);
    }
}
