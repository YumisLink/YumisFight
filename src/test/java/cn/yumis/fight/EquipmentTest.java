package cn.yumis.fight;
import cn.yumis.fight.rules.*;
import cn.yumis.fight.rules.Models.*;
import static cn.yumis.fight.rules.Rules.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class EquipmentTest {
    @Test void basicAttributesAndSpiritAreCountedExactlyOnce(){
        Progress p=new Progress();
        var stats=EquipmentRules.stats(p,List.of(EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.COMMON,1,new Random(1))));
        assertEquals(100,stats.get(Stat.HEALTH));
        assertEquals(100,stats.get(Stat.MP));
        assertEquals(40,stats.get(Stat.MAGICAL_DEFENSE));
        assertEquals(17,stats.get(Stat.PHYSICAL_ATTACK));
        assertEquals(20.4,stats.panel(false),1e-9);
    }
    @Test void localPercentageDoesNotMultiplyAnotherItem(){
        Gear a=EquipmentRules.create(Slot.BRACELET,Weapon.KATANA,Rarity.COMMON,1,new Random(1));
        a.affixes.add(new Affix("flat_health",Stat.HEALTH,20,false));
        Affix percent=new Affix("percent_health",Stat.HEALTH,.2,false);
        percent.operation="PERCENT";
        a.affixes.add(percent);
        assertEquals(36,EquipmentRules.contribution(a).get(Stat.HEALTH),1e-9);
    }
    @Test void upgradeOnlyChangesBasesAndClampsAt80(){
        Random r=new Random(3);
        Gear old=EquipmentRules.create(Slot.WEAPON,Weapon.SHORTSWORD,Rarity.EPIC,78,r);
        old.sockets=2;
        Gem sample=new Gem();
        sample.name="sample";
        old.gems.add(sample);
        var fixed=Models.JSON.toJson(old.affixes.stream().filter(a->!a.base).toList());
        Gear next=EquipmentRules.process(old,"epic_upgrade_stone",r);
        assertEquals(80,next.level);
        assertEquals(78,old.level);
        assertEquals(old.instance,next.instance);
        assertEquals(2,next.sockets);
        assertEquals("sample",next.gems.getFirst().name);
        assertEquals(fixed,Models.JSON.toJson(next.affixes.stream().filter(a->!a.base).toList()));
        assertEquals((15+4*79)*1.6*.8,EquipmentRules.contribution(next).get(Stat.PHYSICAL_ATTACK),1e-9);
        assertThrows(IllegalArgumentException.class,()->EquipmentRules.process(next,"epic_upgrade_stone",r));
    }
    @Test void socketsRespectPartSourceLevelDuplicatesAndOverwrite(){
        Random r=new Random(1);
        Gear g=EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.COMMON,20,r);
        g=EquipmentRules.process(g,"socket_stone",r);
        g=EquipmentRules.process(g,"socket_stone",r);
        Gear finalGear=g;
        assertThrows(IllegalArgumentException.class,()->EquipmentRules.process(finalGear,"socket_stone",r));
        Gem sample=new Gem();
        sample.group="WEAPON";
        sample.level=20;
        sample.affixes.add(new Affix("sample",Stat.STRENGTH,5,false));
        Gear one=EquipmentRules.socket(g,sample,0);
        assertThrows(IllegalArgumentException.class,()->EquipmentRules.socket(one,sample,1));
        Gem replacement=new Gem();
        replacement.group="all";
        replacement.affixes.add(new Affix("replacement",Stat.STRENGTH,7,false));
        Gear two=EquipmentRules.socket(one,replacement,0);
        assertEquals("replacement",two.gems.getFirst().affixes.getFirst().id);
        sample.level=21;
        assertThrows(IllegalArgumentException.class,()->EquipmentRules.socket(two,sample,1));
    }
    @Test void skillFamilyDistributionAndDivineNeverChangesFixedRank(){
        Random random=new Random(712623);
        int family=0;
        int[][] cells=new int[4][3];
        for(int i=0;i<60000;i++){
            Gear gear=EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.RARE,35,random);
            Affix last=gear.affixes.getLast();
            if(last.operation.equals("SKILL_FAMILY")){
                family++;
                cells[Family.valueOf(last.family).ordinal()][last.rank-1]++;
                assertTrue(last.fixed);
                int rank=last.rank;
                assertThrows(IllegalArgumentException.class,()->EquipmentRules.process(gear,"divine_stone",random));
                assertEquals(rank,last.rank);
            }
        }
        assertTrue(family>8400&&family<9600,"15% skill family pool");
        for(var row:cells)for(int count:row)assertTrue(count>590&&count<900,"equiprobable family/rank");
    }
    @Test void fixedRaritiesHaveNoNaturalRandomAndAuxiliaryBasesMatch(){
        for(Rarity r:List.of(Rarity.EPIC,Rarity.MYTHIC)){
            Gear g=EquipmentRules.create(Slot.MAGIC_STONE,Weapon.KATANA,r,60,new Random(1));
            assertTrue(g.affixes.stream().allMatch(a->a.base||a.fixed));
            double scale=r==Rarity.EPIC?1.5:1.9;
            assertEquals((3+.15*59)*scale,EquipmentRules.contribution(g).get(Stat.LIGHT),1e-9);
            Gear e=EquipmentRules.create(Slot.EARRING,Weapon.KATANA,r,70,new Random(1));
            assertEquals((15+4*69)*.05*scale,EquipmentRules.contribution(e).get(Stat.MAGICAL_ATTACK),1e-9);
        }
        assertThrows(IllegalArgumentException.class,()->EquipmentRules.create(Slot.CHEST,Weapon.KATANA,Rarity.MYTHIC,80,new Random()));
    }
}
