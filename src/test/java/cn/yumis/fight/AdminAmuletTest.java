package cn.yumis.fight;
import cn.yumis.fight.modules.affixes.api.AffixText;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.combat.api.CombatRules;
import cn.yumis.fight.modules.combat.api.CombatTiming;
import cn.yumis.fight.modules.dungeons.api.Difficulty;
import cn.yumis.fight.modules.economy.api.LootOdds;
import cn.yumis.fight.modules.equipment.api.EquipmentAttributes;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.modules.equipment.api.EquipmentRules;
import cn.yumis.fight.modules.equipment.api.EquipmentTemplates;
import cn.yumis.fight.modules.equipment.api.Gear;
import cn.yumis.fight.modules.equipment.api.NamedEquipment;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.Slot;
import cn.yumis.fight.modules.equipment.api.Weapon;
import cn.yumis.fight.modules.skills.api.SkillDamageRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Random;

/** 管理员的护符：太初稀有度、四条固定独特词条、技能／普攻统一总增与脱战恢复。 */
class AdminAmuletTest {
    private Gear amulet(){return NamedEquipment.create("named/admin_amulet",new Random(0));}

    @Test void templateBuildsPrimordialAmulet(){
        Gear g=amulet();
        assertEquals(Rarity.PRIMORDIAL,g.rarity);
        assertEquals("太初",g.rarity.title());
        assertEquals(Slot.NECKLACE,g.slot);
        assertEquals(1,g.level);
        assertEquals(0,g.sockets);
        assertEquals("管理员的护符",EquipmentItems.name(g));
        assertEquals(4,g.affixes.stream().filter(a->!a.base).count());
        assertTrue(g.affixes.stream().filter(a->!a.base).allMatch(a->a.fixed));
        assertEquals(10,g.affixes.stream().filter(a->a.id.equals("unique/admin_damage_total")).findFirst().orElseThrow().value,0d);
        assertEquals(1000000,g.affixes.stream().filter(a->a.id.equals("unique/admin_health")).findFirst().orElseThrow().value,0d);
        assertEquals(100000,g.affixes.stream().filter(a->a.id.equals("unique/admin_health_regen")).findFirst().orElseThrow().value,0d);
        assertEquals(1000000,g.affixes.stream().filter(a->a.id.equals("unique/admin_mana")).findFirst().orElseThrow().value,0d);
        var catalog=EquipmentTemplates.creative(Rarity.PRIMORDIAL);
        assertEquals(1,catalog.size());
        assertEquals("named/admin_amulet",catalog.getFirst().template);
        assertTrue(catalog.getFirst().creativeTemplate);
        assertTrue(EquipmentTemplates.creative(Rarity.MYTHIC).stream().noneMatch(x->x.rarity==Rarity.PRIMORDIAL));
        assertThrows(IllegalArgumentException.class,()->EquipmentTemplates.creative(Rarity.LEGENDARY));
        assertThrows(IllegalArgumentException.class,()->EquipmentRules.process(g,"socket_stone",new Random(0)));
        assertThrows(IllegalArgumentException.class,()->EquipmentTemplates.createStandard(Slot.NECKLACE,Weapon.KATANA,Rarity.PRIMORDIAL,1,new Random(0)));
    }

    @Test void equippedStatsApplyFullAdminBudget(){
        // 项链槽位 1 级开放，护符需求等级 1，新角色即可完整生效。
        Stats s=EquipmentAttributes.stats(new Progress(),List.of(amulet()));
        assertEquals(11,s.modifiers.getOrDefault("allSkillAttackProduct",1d),0d);
        assertTrue(s.get(Stat.HEALTH)>=1000000);
        assertTrue(s.get(Stat.MP)>=1000000);
        assertEquals(100000,s.get(Stat.HEALTH_REGENERATION),0d);
        Progress p=new Progress();p.level=3;
        assertEquals(11,EquipmentAttributes.stats(p,List.of(amulet())).modifiers.getOrDefault("allSkillAttackProduct",1d),0d);
    }

    @Test void basicAndSkillDamageShareAllSkillAttackProduct(){
        Stats s=new Stats();s.modifiers.merge("allSkillAttackProduct",11d,(x,y)->x*y);
        CombatRules.Damage parts=new CombatRules.Damage(100,0,-1,new double[]{100,0,0,0,0},false);
        assertEquals(1100,SkillDamageRules.skillDamage(parts,s,null,0,true,false,1).total(),1e-9);
        assertEquals(100,SkillDamageRules.skillDamage(parts,s,null,0,true,true,1).total(),1e-9);
        assertEquals(100,SkillDamageRules.skillDamage(parts,s,null,0,false,false,1).total(),1e-9);
        Stats plain=new Stats();
        assertEquals(100,SkillDamageRules.skillDamage(parts,plain,null,0,true,false,1).total(),1e-9);
    }

    @Test void outOfCombatRegenerationCombinesBaseAndFlat(){
        assertEquals(.02/20,CombatTiming.regeneration(1/20d,1000000,0),1e-12);
        assertEquals((.02+100000d/1000000)/20,CombatTiming.regeneration(1/20d,1000000,100000),1e-12);
        assertEquals(.02/20,CombatTiming.regeneration(1/20d,0,100000),1e-12);
    }

    @Test void naturalGenerationNeverProducesPrimordial(){
        Random random=new Random(7);
        for(Difficulty difficulty:Difficulty.values())for(boolean boss:new boolean[]{false,true})for(boolean nativeBoss:new boolean[]{false,true})for(double luck:new double[]{0,100000})
            for(int i=0;i<200;i++)assertTrue(LootOdds.rarity(difficulty,boss,nativeBoss,luck,random).ordinal()<=Rarity.MYTHIC.ordinal());
    }

    @Test void affixTextRendersAdminEffects(){
        for(var a:amulet().affixes.stream().filter(v->!v.base).toList()){
            String line=AffixText.affix(a);
            switch(a.id){
                case "unique/admin_damage_total"->{assertTrue(line.contains("创世权柄"));assertTrue(line.contains("1000%"));}
                case "unique/admin_health"->{assertTrue(line.contains("最大生命"));assertTrue(line.contains("1000000"));}
                case "unique/admin_health_regen"->{assertTrue(line.contains("生命恢复速度"));assertTrue(line.contains("100000"));}
                case "unique/admin_mana"->{assertTrue(line.contains("最大魔力"));assertTrue(line.contains("1000000"));}
                default->fail("未登记的独特词条："+a.id);
            }
        }
    }
}
