package cn.yumis.fight;
import cn.yumis.fight.rules.*;
import cn.yumis.fight.rules.Models.*;
import static cn.yumis.fight.rules.Models.*;
import static cn.yumis.fight.rules.Rules.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.*;
class AffixCurrencyTest {
    private Gear gear(){return EquipmentRules.create(Slot.CHEST,Weapon.KATANA,Rarity.COMMON,35,new Random(1));}
    private Affix affix(String id,Stat stat){return new Affix(id,stat,10,false);}
    @Test void chaosOnlyReplacesOneEligibleAffixAndKeepsItsPosition(){
        Gear original=gear();original.rarity=Rarity.LEGENDARY;original.sockets=1;original.set="first_sword";
        original.affixes.add(affix("old",Stat.STRENGTH));
        int index=original.affixes.size()-1;
        Affix fixed=affix("fixed",Stat.SPIRIT);fixed.fixed=true;original.affixes.add(fixed);
        Affix locked=affix("locked",Stat.INTELLIGENCE);locked.locked=true;original.affixes.add(locked);
        Gem gem=new Gem();gem.affixes.add(affix("gem",Stat.HEALTH));original.gems.add(gem);
        String before=JSON.toJson(original);
        for(int seed=0;seed<200;seed++){
            Gear changed=EquipmentRules.process(original,"chaos_stone",new Random(seed));
            assertEquals(original.instance,changed.instance);assertEquals(original.revision+1,changed.revision);
            assertEquals(original.level,changed.level);assertEquals(original.rarity,changed.rarity);
            assertEquals(original.sockets,changed.sockets);assertEquals(original.set,changed.set);
            assertEquals(JSON.toJson(original.gems),JSON.toJson(changed.gems));
            assertEquals(original.affixes.size(),changed.affixes.size());
            assertFalse(changed.affixes.get(index).locked);
            assertNotEquals("old",changed.affixes.get(index).id);
            for(int i=0;i<original.affixes.size();i++)if(i!=index)assertEquals(JSON.toJson(original.affixes.get(i)),JSON.toJson(changed.affixes.get(i)));
        }
        assertEquals(before,JSON.toJson(original));
    }
    @Test void chaosCanRollTheRemovedTypeOrDuplicateASurvivingLockedType(){
        Gear original=gear();original.rarity=Rarity.LEGENDARY;
        Affix keep=affix("flat_health",Stat.HEALTH);keep.locked=true;original.affixes.add(keep);
        original.affixes.add(affix("flat_strength",Stat.STRENGTH));
        Set<String> generated=new HashSet<>();
        Random random=new Random(22747);
        for(int i=0;i<1000;i++){
            Gear changed=EquipmentRules.process(original,"chaos_stone",random);
            generated.add(changed.affixes.getLast().id);
            if(changed.affixes.getLast().id.equals(keep.id)){
                assertEquals(2,changed.affixes.stream().filter(a->a.id.equals(keep.id)).count());
                assertTrue(changed.affixes.get(changed.affixes.size()-2).locked);
                assertFalse(changed.affixes.getLast().locked);
            }
        }
        assertTrue(generated.contains("flat_health"),"must allow duplicates of retained affixes");
        assertTrue(generated.contains("flat_strength"),"must allow rolling back the removed type");
    }
    @Test void repeatedFracturingLocksOneNewAffixUntilEveryEligibleAffixIsLocked(){
        Gear original=gear();original.rarity=Rarity.LEGENDARY;
        for(Stat stat:List.of(Stat.STRENGTH,Stat.INTELLIGENCE,Stat.SPIRIT,Stat.HEALTH))original.affixes.add(affix("flat_"+stat.name(),stat));
        Affix fixed=affix("fixed",Stat.SHIELD);fixed.fixed=true;original.affixes.add(fixed);
        Gear changed=original;
        for(int count=1;count<=4;count++){
            Gear next=EquipmentRules.process(changed,"fracturing_orb",new Random(count));
            assertEquals(count,next.affixes.stream().filter(a->a.locked).count());
            assertEquals(changed.revision+1,next.revision);assertEquals(original.instance,next.instance);
            assertEquals(changed.affixes.size(),next.affixes.size());
            for(int i=0;i<changed.affixes.size();i++){
                Affix before=copy(changed.affixes.get(i),Affix.class),after=copy(next.affixes.get(i),Affix.class);
                before.locked=after.locked=false;assertEquals(JSON.toJson(before),JSON.toJson(after));
            }
            changed=next;
        }
        Gear allLocked=changed;
        assertFalse(allLocked.affixes.getLast().locked,"fixed effects must not be selected");
        assertThrows(IllegalArgumentException.class,()->EquipmentRules.process(allLocked,"fracturing_orb",new Random()));
        assertThrows(IllegalArgumentException.class,()->EquipmentRules.process(allLocked,"chaos_stone",new Random()));
        assertEquals(0,original.affixes.stream().filter(a->a.locked).count());
    }
    @Test void fractureRejectsEveryOtherRarityAndChaosRejectsFixedEquipment(){
        for(Rarity rarity:Rarity.values()){
            Gear original=gear();original.rarity=rarity;original.affixes.add(affix("flat_strength",Stat.STRENGTH));
            String before=JSON.toJson(original);
            if(rarity!=Rarity.LEGENDARY)assertThrows(IllegalArgumentException.class,()->EquipmentRules.process(original,"fracturing_orb",new Random()));
            if(rarity==Rarity.EPIC||rarity==Rarity.MYTHIC)assertThrows(IllegalArgumentException.class,()->EquipmentRules.process(original,"chaos_stone",new Random()));
            assertEquals(before,JSON.toJson(original));
        }
        Gear empty=gear();empty.rarity=Rarity.LEGENDARY;
        assertThrows(IllegalArgumentException.class,()->EquipmentRules.process(empty,"fracturing_orb",new Random()));
        assertThrows(IllegalArgumentException.class,()->EquipmentRules.process(empty,"chaos_stone",new Random()));
    }
    @Test void locksPersistWithoutTurningIntoFixedAffixesAndDivineCanRerollTheirValues(){
        Gear original=gear();original.rarity=Rarity.LEGENDARY;
        Affix affix=affix("flat_strength",Stat.STRENGTH);affix.locked=true;affix.value=1;original.affixes.add(affix);
        Gear saved=JSON.fromJson(JSON.toJson(original),Gear.class);
        assertTrue(saved.affixes.getLast().locked);assertFalse(saved.affixes.getLast().fixed);
        Gear changed=EquipmentRules.process(saved,"divine_stone",new Random(8));
        assertTrue(changed.affixes.getLast().locked);assertFalse(changed.affixes.getLast().fixed);
        assertTrue(changed.affixes.getLast().value>=8&&changed.affixes.getLast().value<=12);
        assertNotNull(AffixRanges.reroll(changed.affixes.getLast(),Rarity.LEGENDARY,false));
        assertFalse(JSON.fromJson("{\"id\":\"legacy\",\"fixed\":false,\"base\":false}",Affix.class).locked);
    }
    @Test void sixAffixEquipmentCanBeChangedWithoutIncreasingItsAffixCount(){
        Gear original=gear();original.rarity=Rarity.LEGENDARY;
        for(int i=0;i<6;i++)original.affixes.add(affix("flat_"+i,Stat.STRENGTH));
        Gear changed=EquipmentRules.process(original,"chaos_stone",new Random(4));
        assertEquals(6,changed.affixes.stream().filter(a->!a.base).count());
        assertThrows(IllegalArgumentException.class,()->EquipmentRules.process(changed,"exalted_stone",new Random()));
    }
    @Test void ordinaryGenerationStillDeduplicatesFlatTypesAfterChaos(){
        Gear original=gear();original.rarity=Rarity.LEGENDARY;
        for(int i=0;i<2;i++){Affix keep=affix("flat_strength",Stat.STRENGTH);keep.locked=true;original.affixes.add(keep);}
        for(int seed=0;seed<300;seed++){
            Gear changed=EquipmentRules.process(original,"exalted_stone",new Random(seed));
            assertNotEquals("flat_strength",changed.affixes.getLast().id);
        }
    }
}
