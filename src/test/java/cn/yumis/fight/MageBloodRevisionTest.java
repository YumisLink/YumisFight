package cn.yumis.fight;
import cn.yumis.fight.modules.equipment.api.MageBlood;
import cn.yumis.fight.modules.equipment.api.EquipmentAttributes;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.Slot;
import cn.yumis.fight.modules.equipment.api.Weapon;
import cn.yumis.fight.modules.dungeons.api.Difficulty;
import cn.yumis.fight.modules.economy.api.CurrencyRules;
import cn.yumis.fight.modules.economy.api.RewardSettings;
import cn.yumis.fight.modules.skills.api.SkillPreview;
import cn.yumis.fight.modules.combat.api.CombatRules;
import cn.yumis.fight.bootstrap.ApplicationJson;
import cn.yumis.fight.modules.equipment.api.EquipmentRules;

import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.combat.api.Runtime;
import cn.yumis.fight.modules.affixes.api.Affix;
import cn.yumis.fight.modules.equipment.api.Gear;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.attributes.api.AttributeSources;
import cn.yumis.fight.modules.attributes.api.AttributeSources.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.*;

class MageBloodRevisionTest {
    private Gear magic(String... names){
        Gear g=MageBlood.create(new Random(0));g.affixes.removeIf(a->a.operation.equals("MAGIC"));g.affixes.getFirst().value=.5;
        for(String name:names){Affix a=new Affix();a.id="magic";a.operation="MAGIC";a.family=name;g.affixes.add(a);}return g;
    }
    @Test void repeatedMagicCountsHaveNoOneCase(){
        assertEquals(0,MageBlood.duplicates(magic("JADE","ROCK","GOLD","SILVER")));
        assertEquals(2,MageBlood.duplicates(magic("JADE","JADE","GOLD","SILVER")));
        assertEquals(2,MageBlood.duplicates(magic("JADE","JADE","GOLD","GOLD")));
        assertEquals(3,MageBlood.duplicates(magic("JADE","JADE","JADE","GOLD")));
        assertEquals(4,MageBlood.duplicates(magic("JADE","JADE","JADE","JADE")));
    }
    @Test void duplicatesAmplifyOnceRatherThanStackingFourFlats(){
        var g=magic("JADE","JADE","JADE","JADE");var s=new Stats();s.add(Stat.EVASION,1000);MageBlood.apply(g,s);
        assertEquals(3,MageBlood.multiplier(g));assertEquals(7000,s.get(Stat.EVASION));assertEquals(6000,s.attribute(Stat.EVASION).add);
    }
    @Test void armorAndEvasionMutNeverMultiplyMineralAdd(){
        var s=new Stats();s.add(Stat.PHYSICAL_DEFENSE,1000);s.add(Stat.MAGICAL_DEFENSE,500);s.add(Stat.EVASION,1000);
        MageBlood.apply(magic("BASALT","ROCK","JADE","ANTIMONY"),s);
        assertEquals(6500,s.get(Stat.PHYSICAL_DEFENSE));assertEquals(5250,s.get(Stat.MAGICAL_DEFENSE));assertEquals(4500,s.get(Stat.EVASION));
    }
    @Test void allFourSameFinalDamageIncreasesMutOnlyOnce(){
        var s=new Stats();s.modifiers.put("final",.2);MageBlood.apply(magic("SULPHUR","SULPHUR","SULPHUR","SULPHUR"),s);
        assertEquals(2,s.modifiers.get("final"),1e-12);assertEquals(3,s.modifier("final").value(),1e-12);
    }
    @Test void elementalMagicAndRarityUseConfirmedUnits(){
        var s=new Stats();MageBlood.apply(magic("RUBY","SAPPHIRE","TOPAZ","AMETHYST"),s);
        for(Stat stat:List.of(Stat.FIRE,Stat.FIRE_RESIST,Stat.ICE,Stat.ICE_RESIST,Stat.LIGHT,Stat.LIGHT_RESIST,Stat.DARK,Stat.DARK_RESIST))assertEquals(30,s.get(stat));
        var gold=new Stats();MageBlood.apply(magic("GOLD","DIAMOND","MERCURY","SILVER"),gold);assertEquals(80,gold.get(Stat.DROP_RATE));assertEquals(.1,gold.modifiers.get("crit"));
    }
    @Test void unifiedLayersAndGsonSnapshotsRetainEveryBucket(){
        var s=new Stats();var a=s.attribute(Stat.PHYSICAL_DEFENSE);a.base=1000;a.mut=1.5;a.add=4000;a.more(.2);a.fadd=200;
        assertEquals(8000,s.get(Stat.PHYSICAL_DEFENSE));var saved=ApplicationJson.copy(s,Stats.class);assertEquals(8000,saved.values.get(Stat.PHYSICAL_DEFENSE));assertEquals(1.5,saved.attribute(Stat.PHYSICAL_DEFENSE).mut);saved.mut(Stat.PHYSICAL_DEFENSE,.5);assertEquals(8600,saved.get(Stat.PHYSICAL_DEFENSE));assertEquals(8000,s.get(Stat.PHYSICAL_DEFENSE));
        s.modifiers.put("final",.2);s.modifier("final").more(.3);assertEquals(.56,ApplicationJson.copy(s,Stats.class).modifiers.get("final"),1e-12);
    }
    @Test void oldCharacterRatiosAndOverdriveNowShareMut(){
        var gear=EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.COMMON,1,new Random());
        var a=new Affix("character",Stat.PHYSICAL_ATTACK,.2,false);a.operation="CHARACTER_PERCENT";gear.affixes.add(a);
        var b=new Affix();b.id="physicalAttackRatio";b.operation="MODIFIER";b.value=.3;gear.affixes.add(b);
        var currentGear=cn.yumis.fight.modules.equipment.api.EquipmentResolution.initialize(gear);
        var s=cn.yumis.fight.modules.equipment.api.EquipmentAttributes.stats(new Progress(),List.of(currentGear));assertEquals(25.5,s.get(Stat.PHYSICAL_ATTACK),1e-9);
        var r=new cn.yumis.fight.modules.combat.api.Runtime();r.buffs.put("yumis_dungeon_and_fight:overdrive",1000L);r.absorption.put("overdrivePower",.4);assertEquals(32.3,SkillPreview.currentStats(s,r,999).get(Stat.PHYSICAL_ATTACK),1e-9);
    }
    @Test void spiritIsAddedAfterEveryTargetMultiplier(){
        var p=new Progress();p.level=80;var sources=new AttributeSources();sources.replace(1,List.of(new Source("test",1,List.of(new Value(Stat.MAGICAL_DEFENSE,1,Operation.BASE_RATIO,Stage.CHARACTER),new Value(Stat.MAGICAL_DEFENSE,.2,Operation.TOTAL_RATIO,Stage.CHARACTER)))));
        var s=cn.yumis.fight.modules.equipment.api.EquipmentAttributes.stats(p,List.of(),sources,0,ignored->{});assertEquals(384,s.get(Stat.MAGICAL_DEFENSE));assertEquals(336,s.attribute(Stat.MAGICAL_DEFENSE).fadd);
    }
    @Test void moreSourcesMultiplyAndRemovalLeavesNoStaleEffects(){
        var a=new AttributeSources();var one=new Source("a",1,List.of(new Value(Stat.HEALTH,.2,Operation.TOTAL_RATIO,Stage.CHARACTER)));var two=new Source("b",1,List.of(new Value(Stat.HEALTH,.3,Operation.TOTAL_RATIO,Stage.CHARACTER)));
        a.replace(1,List.of(one,two));assertEquals(156,a.apply(Map.of(Stat.HEALTH,100d)).get(Stat.HEALTH));
        var s=cn.yumis.fight.modules.equipment.api.EquipmentAttributes.stats(new Progress(),List.of(),a,0,ignored->{});assertEquals(156,s.get(Stat.HEALTH));a.replace(2,List.of());assertEquals(100,cn.yumis.fight.modules.equipment.api.EquipmentAttributes.stats(new Progress(),List.of(),a,0,ignored->{}).get(Stat.HEALTH));
    }
    @Test void rerollAndUpgradesPreserveIdentityAndEmptyBases(){
        var g=MageBlood.create(new Random(2));String original=ApplicationJson.JSON.toJson(g);var rolled=EquipmentRules.process(cn.yumis.fight.modules.equipment.api.EquipmentResolution.initialize(g),"divine_stone",new Random(99));assertEquals(original,ApplicationJson.JSON.toJson(g));assertEquals(g.instance,rolled.instance);assertEquals(5,rolled.affixes.size());assertTrue(MageBlood.amplification(rolled)>=.25&&MageBlood.amplification(rolled)<=.5);
        String affixes=ApplicationJson.JSON.toJson(rolled.affixes);for(int i=0;i<4;i++)rolled=EquipmentRules.process(cn.yumis.fight.modules.equipment.api.EquipmentResolution.initialize(rolled),"mythic_upgrade_stone",new Random());
        assertEquals(80,rolled.level);assertEquals(affixes,ApplicationJson.JSON.toJson(rolled.affixes));assertTrue(EquipmentRules.contribution(cn.yumis.fight.modules.equipment.api.EquipmentResolution.initialize(rolled)).isEmpty());assertEquals("",rolled.set);assertEquals(MageBlood.APPEARANCE,rolled.appearance);
        assertThrows(IllegalArgumentException.class,()->EquipmentRules.create(Slot.LEGS,Weapon.KATANA,Rarity.MYTHIC,60,new Random()));
        assertThrows(IllegalArgumentException.class,()->EquipmentRules.process(cn.yumis.fight.modules.equipment.api.EquipmentResolution.initialize(g),"chaos_stone",new Random()));
    }

    @Test void evasionHasContinuousSoftCapAndReachableHardCap(){
        assertEquals(.75,CombatRules.evasionChance(30000));assertEquals(.8,CombatRules.evasionChance(60000));assertEquals(.9,CombatRules.evasionChance(210000));assertEquals(.9,CombatRules.evasionChance(Double.MAX_VALUE));
        assertTrue(CombatRules.evasionChance(30000.001)>.75);assertTrue(CombatRules.evasionChance(29999.999)<.75);assertThrows(IllegalArgumentException.class,()->CombatRules.evasionChance(Double.NaN));
    }
    @Test void rewardCurvesAndSalePricesUseIndependentMultipliers(){
        assertEquals(132,CurrencyRules.killGold(60,false,false,Difficulty.NORMAL,1));assertEquals(396,CurrencyRules.killGold(60,true,false,Difficulty.NORMAL,1));assertEquals(1320,CurrencyRules.killGold(60,false,true,Difficulty.NORMAL,1));assertEquals(0,CurrencyRules.killGold(60,false,true,Difficulty.NORMAL,0));
        assertEquals(3000,CurrencyRules.salePrice(MageBlood.create(new Random())));assertEquals(3000,CurrencyRules.salePrice(MageBlood.create(new Random())));assertEquals(1,CurrencyRules.roundedReward(.5));assertThrows(ArithmeticException.class,()->CurrencyRules.roundedReward(Double.MAX_VALUE));
        assertEquals(RewardSettings.DEFAULT,RewardSettings.parse("worldDifficulty=\"NORMAL\""));assertEquals(new RewardSettings(0,.25),RewardSettings.parse("combatExperienceMultiplier=0\nkillGoldMultiplier=0.25 # comment"));
        for(String text:List.of("killGoldMultiplier=-1","killGoldMultiplier=NaN","combatExperienceMultiplier=Infinity","killGoldMultiplier=1\nkillGoldMultiplier=2"))assertThrows(IllegalArgumentException.class,()->RewardSettings.parse(text));
    }

}
