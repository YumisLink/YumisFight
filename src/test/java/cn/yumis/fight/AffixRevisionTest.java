package cn.yumis.fight;
import cn.yumis.fight.modules.equipment.api.Gear;
import cn.yumis.fight.modules.equipment.api.EquipmentResolution;
import cn.yumis.fight.modules.affixes.api.AffixDefinition;
import cn.yumis.fight.modules.affixes.api.Affix;
import cn.yumis.fight.modules.affixes.api.AffixText;
import cn.yumis.fight.modules.equipment.api.EquipmentAffixRanges;
import cn.yumis.fight.modules.affixes.api.AffixDefinitions;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.Slot;
import cn.yumis.fight.modules.equipment.api.Weapon;

import cn.yumis.fight.modules.equipment.api.EquipmentRules;
import cn.yumis.fight.architecture.RuleStore;
import cn.yumis.fight.modules.affixes.api.*;
import cn.yumis.fight.modules.equipment.api.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static cn.yumis.fight.bootstrap.ApplicationJson.JSON;
class AffixRevisionTest {
    private Gear speed(){
        Gear g=new Gear();g.level=30;
        Affix a=new Affix("attack_speed",Stat.ATTACK_SPEED,18,false);
        a.baseline=10;a.baselineMaximum=20;a.roll=1.12;a.locked=true;g.affixes.add(a);
        return EquipmentResolution.initialize(g);
    }
    private AffixDefinitions revised(AffixDefinitions previous){
        AffixDefinitions next=AffixDefinitions.parse(JSON.toJson(previous));
        var d=next.affixes.get("attack_speed");
        next.affixes.put(d.id(),new AffixDefinition(d.id(),"新迅捷",d.operation(),d.group(),d.stat(),d.element(),1,false,20d,40d,null,null,null));
        next.retainRetiredFrom(previous);return next;
    }
    @Test void currentReadKeepsPrecisionAndRejectsOldFormat(){
        Gear old=speed();String raw=JSON.toJson(old);
        assertThrows(IllegalArgumentException.class,()->EquipmentResolution.read("{\"version\":8}"));
        Gear parsed=EquipmentResolution.read(raw);
        assertEquals(18,parsed.affixes.getFirst().value,0);
        assertEquals(.8,parsed.affixes.getFirst().origin.quality,1e-12);
        assertTrue(parsed.affixes.getFirst().locked);
        assertEquals(raw,JSON.toJson(old));
        assertEquals(parsed.affixes.getFirst().instanceId,EquipmentResolution.read(raw).affixes.getFirst().instanceId);
    }
    @Test void rebalancePreservesPercentileAcrossRepeatedReadsAndRestarts(){
        AffixDefinitions before=AffixDefinitions.current();
        try{
            Gear old=EquipmentResolution.resolve(speed());AffixDefinitions replacement=revised(before);AffixDefinitions.source(()->replacement);
            Gear next=EquipmentResolution.resolve(old);
            assertEquals(36,next.affixes.getFirst().value,1e-12);
            assertEquals(old.instance,next.instance);assertEquals(old.revision,next.revision);
            for(int i=0;i<5;i++){next=EquipmentResolution.read(JSON.toJson(next));assertEquals(36,next.affixes.getFirst().value,1e-12);}
            assertTrue(AffixText.affix(next.affixes.getFirst()).startsWith("新迅捷"));
        }finally{AffixDefinitions.source(()->before);}
    }
    @Test void retirementStopsNewGenerationButKeepsCurrentEquipmentGrowth(){
        AffixDefinitions before=AffixDefinitions.current();
        try{
            AffixDefinitions next=AffixDefinitions.parse(JSON.toJson(before));next.affixes.remove("extra_0");next.affixes.remove("flat_strength");next.retainRetiredFrom(before);AffixDefinitions.source(()->next);
            assertTrue(next.affixes.get("extra_0").retired());assertFalse(next.affixes.get("flat_strength").active());
            for(int seed=0;seed<100;seed++){Gear generated=EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.LEGENDARY,30,new Random(seed));assertTrue(generated.affixes.stream().noneMatch(a->a.id.equals("extra_0")||a.id.equals("flat_strength")));}
            Gear old=new Gear();old.level=30;old.slot=Slot.CHEST;
            Affix a=new Affix("flat_strength",Stat.STRENGTH,8,false);a.baseline=8;a.roll=1;old.affixes.add(a);
            Gear grown=EquipmentRules.process(EquipmentResolution.initialize(old),"upgrade_stone",new Random(1));
            assertEquals("flat_strength",grown.affixes.stream().filter(x->!x.base).findFirst().orElseThrow().id);
            assertTrue(grown.affixes.stream().filter(x->!x.base).findFirst().orElseThrow().value>8);
        }finally{AffixDefinitions.source(()->before);}
    }
    @Test void unknownAndSkillParameterSnapshotsNeverGuessRandomQuality(){
        Gear g=new Gear();Affix unknown=new Affix("external_unknown",Stat.STRENGTH,17.25,false);unknown.roll=7;g.affixes.add(unknown);
        Affix skill=new Affix();skill.id="external_parameter";skill.operation="SKILL_PARAMETER";skill.roll=2;skill.value=3;g.affixes.add(skill);
        g=EquipmentResolution.initialize(g);String saved=JSON.toJson(g);Gear next=EquipmentResolution.read(saved);
        assertEquals(saved,JSON.toJson(next));assertEquals(2,next.affixes.get(1).roll);
        var raw=com.google.gson.JsonParser.parseString(saved).getAsJsonObject();
        raw.getAsJsonArray("affixes").get(0).getAsJsonObject().addProperty("providerFutureField","preserve-me");
        Gear roundTrip=EquipmentResolution.read(raw.toString());roundTrip.level++;
        assertEquals("preserve-me",com.google.gson.JsonParser.parseString(JSON.toJson(roundTrip)).getAsJsonObject().getAsJsonArray("affixes").get(0).getAsJsonObject().get("providerFutureField").getAsString());

    }
    @Test void invalidRevisionReplacementCannotPublishPartialRules(){
        AffixDefinitions initial=AffixDefinitions.parse(JSON.toJson(AffixDefinitions.current()));
        RuleStore<AffixDefinitions> store=new RuleStore<>(initial,d->AffixDefinitions.parse(new com.google.gson.GsonBuilder().serializeNulls().create().toJson(d)));
        var snapshot=store.snapshot();
        AffixDefinitions broken=AffixDefinitions.parse(JSON.toJson(initial));broken.affixes.put("invalid",null);
        assertThrows(IllegalArgumentException.class,()->store.publish(broken));assertSame(snapshot,store.snapshot());
        AffixDefinitions unversioned=AffixDefinitions.parse(JSON.toJson(initial));var d=unversioned.affixes.get("flat_strength");
        unversioned.affixes.put(d.id(),new AffixDefinition(d.id(),d.name(),d.operation(),d.group(),d.stat(),d.element(),d.revision(),false,null,null,null,null,2d));
        assertThrows(IllegalArgumentException.class,()->unversioned.retainRetiredFrom(initial));
        assertEquals(1,initial.affixes.get(d.id()).scale());
    }
    @Test void rerollUsesNewRangeAndKeepsLockedIdentity(){
        AffixDefinitions before=AffixDefinitions.current();
        try{
            Gear old=EquipmentResolution.resolve(speed());AffixDefinitions replacement=revised(before);AffixDefinitions.source(()->replacement);
            Gear next=EquipmentRules.process(cn.yumis.fight.modules.equipment.api.EquipmentResolution.initialize(old),"divine_stone",new Random(42));
            Affix a=next.affixes.getFirst();
            assertTrue(a.value>=20&&a.value<=40);assertTrue(a.locked);assertEquals(old.instance,next.instance);
            assertEquals(a.value,EquipmentResolution.read(JSON.toJson(next)).affixes.getFirst().value,1e-12);
            var range=EquipmentAffixRanges.reroll(a,next.rarity,false);assertEquals(20,range.minimum());assertEquals(40,range.maximum());
        }finally{AffixDefinitions.source(()->before);}
    }
}

