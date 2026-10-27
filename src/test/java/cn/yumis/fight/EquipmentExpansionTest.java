package cn.yumis.fight;

import cn.yumis.fight.rules.*;
import cn.yumis.fight.rules.Models.*;
import static cn.yumis.fight.rules.Rules.*;
import static cn.yumis.fight.rules.Models.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.*;

class EquipmentExpansionTest {
    private Gear armor(ArmorType type,Slot slot,int level){return EquipmentRules.create(slot,Weapon.KATANA,Rarity.COMMON,level,type,JewelryType.LEGACY,new Random(0));}
    private Gear jewelry(JewelryType type,Slot slot,int level){return EquipmentRules.create(slot,Weapon.KATANA,Rarity.COMMON,level,ArmorType.LEGACY,type,new Random(0));}
    private double sum(ArmorType type,Stat stat,int level){return Arrays.stream(Slot.values()).filter(Slot::armor).mapToDouble(s->EquipmentRules.contribution(armor(type,s,level)).getOrDefault(stat,0d)).sum();}
    @Test void exactArmorBudgetsPreserveFractionalBasesAndNativeLegacy(){
        var expected=Map.of(ArmorType.CLOTH,new double[]{163,106,540,0},ArmorType.LEATHER,new double[]{163,106,270,1000},ArmorType.LIGHT,new double[]{163,106,0,2000},ArmorType.HEAVY,new double[]{489,318,270,0},ArmorType.PLATE,new double[]{815,530,0,0});
        for(var entry:expected.entrySet())for(int i=0;i<4;i++)assertEquals(entry.getValue()[i],sum(entry.getKey(),new Stat[]{Stat.PHYSICAL_DEFENSE,Stat.MAGICAL_DEFENSE,Stat.SHIELD,Stat.EVASION}[i],50),1e-8);
        assertEquals(65.4,EquipmentRules.contribution(armor(ArmorType.CLOTH,Slot.CHEST,50)).get(Stat.PHYSICAL_DEFENSE),1e-9);
        assertEquals(40.5,EquipmentRules.contribution(armor(ArmorType.LEATHER,Slot.HEAD,50)).get(Stat.SHIELD),1e-9);
        assertEquals(327,EquipmentRules.contribution(EquipmentRules.create(Slot.CHEST,Weapon.KATANA,Rarity.COMMON,50,new Random(0))).get(Stat.PHYSICAL_DEFENSE));
        assertEquals(840,sum(ArmorType.CLOTH,Stat.SHIELD,80));
        assertEquals(.4375,CombatRules.evasionChance(sum(ArmorType.LIGHT,Stat.EVASION,80)),1e-12);
    }
    @Test void allJewelrySlotsHaveElevenDirectionsAndNoUnrequestedBases(){
        for(Slot slot:List.of(Slot.BRACELET,Slot.NECKLACE,Slot.RING)){
            assertEquals(810,EquipmentRules.contribution(jewelry(JewelryType.HEALTH,slot,50)).get(Stat.HEALTH));
            assertEquals(589.5,EquipmentRules.contribution(jewelry(JewelryType.MP,slot,50)).get(Stat.MP));
            assertEquals(33.4,EquipmentRules.contribution(jewelry(JewelryType.STRENGTH,slot,50)).get(Stat.STRENGTH),1e-9);
            assertEquals(10,EquipmentRules.contribution(jewelry(JewelryType.DROP_RATE,slot,50)).get(Stat.DROP_RATE));
            var all=EquipmentRules.contribution(jewelry(JewelryType.ALL_RESIST,slot,50));
            assertEquals(4,all.size());
            for(double value:all.values())assertEquals(7.7625,value,1e-9);
            assertEquals(1.5*20.7,all.values().stream().mapToDouble(Double::doubleValue).sum(),1e-9);
            assertEquals(1,EquipmentRules.contribution(jewelry(JewelryType.HEALTH,slot,50)).size());
        }
        assertEquals(61,EquipmentTemplates.creative().size());
    }
    @Test void fortyOneDefinitionsAndBaseOnlyQualificationAcrossEveryLevel(){
        assertEquals(42,Definitions.CURRENT.affixes.values().stream().filter(d->d.group().equals("random")).count());
        Gear gear=armor(ArmorType.LIGHT,Slot.CHEST,50);
        gear.affixes.add(new Affix("flat_health",Stat.HEALTH,999,false));
        Affix special=new Affix("special_mp",Stat.MP,999,false);special.fixed=true;gear.affixes.add(special);
        Gem gem=new Gem();gem.affixes.add(new Affix("gem_shield",Stat.SHIELD,999,false));gear.gems.add(gem);
        assertFalse(AffixRules.percentAllowed(gear,Stat.HEALTH));assertFalse(AffixRules.percentAllowed(gear,Stat.MP));assertFalse(AffixRules.flatAllowed(gear,Stat.SHIELD));
        assertTrue(AffixRules.percentAllowed(jewelry(JewelryType.HEALTH,Slot.NECKLACE,50),Stat.HEALTH));
        Random random=new Random(91);
        for(int level=1;level<=80;level++)for(var template:EquipmentTemplates.creative()){
            if(level<template.slot.minimum())continue;
            Gear item=EquipmentRules.create(template.slot,template.weapon,Rarity.LEGENDARY,level,template.armorType,template.jewelryType,random);
            for(Affix a:item.affixes)if(!a.base&&!a.fixed){
                if(a.operation.equals("FLAT"))assertTrue(AffixRules.flatAllowed(item,Stat.valueOf(a.stat)),a.id);
                else if(a.operation.equals("PERCENT"))assertTrue(AffixRules.percentAllowed(item,Stat.valueOf(a.stat)),a.id);
                else if(a.operation.equals(AffixRules.ALL_RESIST_FLAT))assertTrue(AffixRules.elementFlat(item.slot));
                else assertEquals("EXTRA",a.operation);
            }
        }
    }
    @Test void localPercentagesStillMultiplyExtrasAndCompositeResistanceIsOneSource(){
        Gear gear=jewelry(JewelryType.ALL_RESIST,Slot.RING,50);
        Affix all=new Affix();all.id="flat_all_resistance";all.operation=AffixRules.ALL_RESIST_FLAT;all.value=4;gear.affixes.add(all);
        gear.affixes.add(new Affix("flat_fire_resist",Stat.FIRE_RESIST,8,false));
        Affix old=new Affix("percent_fire_resist",Stat.FIRE_RESIST,.2,false);old.operation="PERCENT";gear.affixes.add(old);
        assertEquals((7.7625+4+8)*1.2,EquipmentRules.contribution(gear).get(Stat.FIRE_RESIST),1e-9);
        assertEquals(11.7625,EquipmentRules.contribution(gear).get(Stat.ICE_RESIST),1e-9);
        var ids=AffixRules.candidates(gear,false,false).stream().map(Definitions.AffixDefinition::id).toList();
        assertFalse(ids.contains("flat_all_resistance"));assertTrue(ids.contains("flat_ice_resist"));
        Gear clean=jewelry(JewelryType.ALL_RESIST,Slot.RING,50);
        assertEquals(1,AffixRules.candidates(clean,false,false).stream().filter(d->d.id().equals("flat_all_resistance")).count());
        assertTrue(AffixRules.candidates(clean,true,false).stream().noneMatch(d->d.stat().name().endsWith("_RESIST")));
    }
    @Test void defensivePercentageCurveAndEvasionEndpointsAreExact(){
        for(Stat stat:List.of(Stat.PHYSICAL_DEFENSE,Stat.MAGICAL_DEFENSE,Stat.SHIELD,Stat.EVASION)){
            assertEquals(.175,AffixRules.percentBaseline(1,stat));assertEquals(1.25,AffixRules.percentBaseline(80,stat));
            assertEquals(1.010126582278481,AffixRules.percentBaseline(50,stat)*1.2,1e-12);
        }
        assertEquals(.175,AffixRules.percentBaseline(80,Stat.HEALTH));
        assertEquals(0,CombatRules.evasionChance(0));assertEquals(0,CombatRules.evasionChance(-1));
        assertEquals(.5,CombatRules.evasionChance(4000));assertEquals(2d/3,CombatRules.evasionChance(8000));assertEquals(.8,CombatRules.evasionChance(16000));
        assertFalse(CombatRules.evades(0,()->{fail("zero evasion must not consume RNG");return 0;}));
        assertTrue(CombatRules.evades(4000,()->.499999));assertFalse(CombatRules.evades(4000,()->.5));
    }
    @Test void upgradeGrowsLockedNumbersAndSharedExtraRollWithoutMutatingTheInput(){
        Gear original=armor(ArmorType.LEATHER,Slot.CHEST,50);original.rarity=Rarity.LEGENDARY;original.sockets=1;original.set="saved";
        Affix flat=new Affix("flat_evasion",Stat.EVASION,60*.91,false);flat.baseline=60;flat.roll=.91;flat.locked=true;original.affixes.add(flat);
        Affix pct=new Affix("percent_shield",Stat.SHIELD,AffixRules.percentBaseline(50,Stat.SHIELD)*1.1,false);pct.operation="PERCENT";pct.baseline=pct.value/1.1;pct.roll=1.1;original.affixes.add(pct);
        Gem gem=new Gem();gem.name="saved gem";original.gems.add(gem);String before=JSON.toJson(original);
        Gear changed=EquipmentRules.process(original,"upgrade_stone",new Random(1));
        assertEquals(55,changed.level);assertEquals(original.instance,changed.instance);assertEquals(ArmorType.LEATHER,changed.armorType);
        assertEquals(before,JSON.toJson(original));assertEquals("saved",changed.set);assertEquals(JSON.toJson(original.gems),JSON.toJson(changed.gems));
        Affix next=changed.affixes.stream().filter(a->a.id.equals("flat_evasion")).findFirst().orElseThrow();
        assertTrue(next.locked);assertEquals(.91,next.roll);assertEquals(AffixRules.reference(changed,Stat.EVASION)*.075*.91,next.value,1e-9);
        assertEquals(AffixRules.percentBaseline(55,Stat.SHIELD)*1.1,changed.affixes.stream().filter(a->a.id.equals("percent_shield")).findFirst().orElseThrow().value,1e-12);
        Gear weapon=EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.COMMON,50,new Random(1));
        Affix extra=new Affix();extra.id="extra_0";extra.operation="EXTRA";extra.roll=.8;extra.value=extra.baseline=1;extra.maximum=extra.baselineMaximum=2;weapon.affixes.add(extra);
        Gear upgraded=EquipmentRules.process(weapon,"upgrade_stone",new Random(3));Affix e=upgraded.affixes.stream().filter(a->a.operation.equals("EXTRA")).findFirst().orElseThrow();
        assertEquals(.1*EquipmentRules.budget(55)*.8,e.value,1e-9);assertEquals(2*e.value,e.maximum,1e-9);
        Gear washed=EquipmentRules.process(upgraded,"divine_stone",new Random(4));Affix w=washed.affixes.stream().filter(a->a.operation.equals("EXTRA")).findFirst().orElseThrow();assertEquals(2*w.value,w.maximum,1e-9);
        weapon.level=78;assertEquals(80,EquipmentRules.process(weapon,"upgrade_stone",new Random()).level);
        weapon.level=80;assertThrows(IllegalArgumentException.class,()->EquipmentRules.process(weapon,"upgrade_stone",new Random()));
        for(Rarity rarity:List.of(Rarity.EPIC,Rarity.MYTHIC)){Gear fixed=EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,rarity,70,new Random());assertThrows(IllegalArgumentException.class,()->EquipmentRules.process(fixed,"upgrade_stone",new Random()));}
        assertEquals("upgrade_stone",CurrencyRules.salvageCurrency(Rarity.LEGENDARY));assertThrows(IllegalArgumentException.class,()->CurrencyRules.salvageCurrency(Rarity.ARTIFACT));
    }
    @Test void legacyReadsAndFormattingKeepAllOriginalPrecision(){
        Gear legacy=JSON.fromJson("{\"slot\":\"CHEST\",\"level\":50,\"affixes\":[{\"id\":\"flat_health\",\"stat\":\"HEALTH\",\"value\":38.88,\"baseline\":48.6,\"roll\":0.8}]}",Gear.class);
        assertEquals(ArmorType.LEGACY,legacy.armorType);String before=JSON.toJson(legacy);
        assertEquals("生机：最大生命 +39",AffixText.affix(legacy.affixes.getFirst()));assertEquals(before,JSON.toJson(legacy));
        assertEquals("0",AffixText.integer(.36));assertEquals("590",AffixText.integer(589.5));assertEquals("0",AffixText.integer(-.01));
        assertFalse(AffixText.affix(new Affix("base_health",Stat.HEALTH,38.88,true)).contains("生机"));
        Gear epic=EquipmentRules.create(Slot.CHEST,Weapon.KATANA,Rarity.EPIC,50,new Random());assertTrue(epic.affixes.stream().anyMatch(a->a.id.equals("epic_shield")));
        assertThrows(IllegalArgumentException.class,()->EquipmentRules.create(Slot.CHEST,Weapon.KATANA,Rarity.EPIC,50,ArmorType.CLOTH,JewelryType.LEGACY,new Random()));
        assertEquals(77.38,.1*EquipmentRules.budget(50)*.8,1e-9);assertEquals(232.14,.2*EquipmentRules.budget(50)*1.2,1e-9);
    }
    @Test void damageAndShieldUseIndependentClocks(){
        Models.Runtime r=new Models.Runtime();
        CombatTiming.taken(r,1000);assertEquals(4000,r.combatUntil);assertEquals(6000,r.shieldRecoveryAt);
        CombatTiming.dealt(r,5500);assertEquals(8500,r.combatUntil);assertEquals(6000,r.shieldRecoveryAt);
        assertFalse(CombatTiming.out(r,8499));assertTrue(CombatTiming.out(r,8500));
        assertFalse(CombatTiming.out(r,6000));assertTrue(CombatTiming.shieldReady(r,6000));assertFalse(CombatTiming.shieldReady(r,5999));
        CombatTiming.taken(r,500);assertEquals(8500,r.combatUntil);assertEquals(6000,r.shieldRecoveryAt);
    }
    @Test void zeroHpOutcomesNeverClaimActualDamageOrChangeTheLifeRevision(){
        Life life=new Life();life.target=UUID.randomUUID();life.q=.43;life.revision=19;
        String before=JSON.toJson(life);Receipt receipt=Models.noDamageReceipt(life);
        assertTrue(receipt.accepted());assertEquals(0,receipt.actual());assertEquals(.43,receipt.q());assertEquals(19,receipt.revision());
        assertEquals(before,JSON.toJson(life));assertEquals("",receipt.deathId());
        life.dead=true;assertFalse(Models.noDamageReceipt(life).accepted());assertEquals(0,Models.noDamageReceipt(life).actual());
    }
}
