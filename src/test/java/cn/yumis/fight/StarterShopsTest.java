package cn.yumis.fight;
import cn.yumis.fight.modules.dungeons.api.DungeonCatalog;
import cn.yumis.fight.modules.dungeons.api.Waystone;
import cn.yumis.fight.modules.economy.api.ShopDefinitions;
import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.modules.mapgeneration.api.MapBlueprint;
import cn.yumis.fight.modules.npc.api.NpcDefinitions;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 新手村商铺：随机+1白引路石与全套白色基础装备按正式工厂参数出售。 */
class StarterShopsTest {
    private static ShopDefinitions.Definition shop(String id){return ShopDefinitions.defaults().require(id);}
    private static long gold(ShopDefinitions.Offer offer){
        assertEquals(1,offer.costs().size());
        var cost=offer.costs().getFirst();
        assertTrue(cost.gold());
        return cost.count();
    }

    @Test void randomWaystoneOfferIsPlusOneCommonAtTenThousandGold(){
        var offers=shop("waystone").offers();
        assertEquals(1,offers.size());
        var offer=offers.getFirst();
        assertEquals("waystone",offer.kind());
        assertEquals(1,offer.tier());
        assertEquals("COMMON",offer.rarity());
        assertEquals(10000,gold(offer));
        for(long seed:new long[]{1,2}){
            var stone=Waystone.generate(offer.tier(),80,Rarity.COMMON,new Random(seed));
            assertEquals(1,stone.tier);
            assertEquals(Rarity.COMMON,stone.rarity);
            assertTrue(stone.modifiers.isEmpty());
            assertTrue(DungeonCatalog.current().dungeon(stone.dungeon).available());
        }
    }

    @Test void starterGearShopsListEveryWhiteOfferAtTenThousandGold(){
        var shops=ShopDefinitions.defaults();
        assertEquals(11,shops.require("weapon_gear").offers().size());
        assertEquals(20,shops.require("armor_gear").offers().size());
        assertEquals(33,shops.require("jewelry_gear").offers().size());
        assertEquals(3,shops.require("special_gear").offers().size());
        for(var row:List.of("weapon_gear","armor_gear","jewelry_gear","special_gear"))
            for(var offer:shops.require(row).offers()){
                assertEquals("gear",offer.kind());
                assertEquals("COMMON",offer.rarity());
                assertEquals(10000,gold(offer));
                var slot=Slot.valueOf(offer.slot());
                int level=offer.level();
                assertTrue(level>=slot.minimum()&&level<=80);
                var gear=EquipmentRules.create(slot,offer.weapon()==null?Weapon.KATANA:Weapon.valueOf(offer.weapon()),Rarity.COMMON,level,
                        offer.armor()==null?ArmorType.UNTYPED:ArmorType.valueOf(offer.armor()),
                        offer.jewelry()==null?JewelryType.UNTYPED:JewelryType.valueOf(offer.jewelry()),new Random(0));
                assertEquals(Rarity.COMMON,gear.rarity);
                assertEquals(level,gear.level);
                assertEquals(slot,gear.slot);
                assertTrue(gear.affixes.stream().filter(a->!a.base).toList().isEmpty());
            }
    }

    @Test void specialGearShopUsesSlotMinimumLevels(){
        var offers=shop("special_gear").offers();
        assertEquals(50,levelOf(offers,"sub_equipment"));
        assertEquals(60,levelOf(offers,"magic_stone"));
        assertEquals(70,levelOf(offers,"earring"));
    }
    private int levelOf(List<ShopDefinitions.Offer> offers,String id){
        return offers.stream().filter(o->o.id().equals(id)).findFirst().orElseThrow().level();
    }

    @Test void starterVillageMerchantsMountExistingShops(){
        var npcs=NpcDefinitions.defaults();
        var shops=ShopDefinitions.defaults();
        for(var id:List.of("waystone_merchant","weapon_merchant","armor_merchant","jewelry_merchant","special_merchant")){
            var definition=npcs.require(id);
            var shop=definition.actions().stream().filter(a->a.type().equals("shop")).findFirst().orElseThrow();
            assertNotNull(shops.shops.get(shop.value()));
            assertTrue(definition.actions().stream().anyMatch(a->a.type().equals("chat")));
            assertFalse(definition.dialogue().isEmpty());
        }
    }

    @Test void starterVillageBlueprintProvidesEnoughMerchantAnchors() throws java.io.IOException{
        var raw=new String(Objects.requireNonNull(StarterShopsTest.class.getResourceAsStream("/data/yumis_dungeon_and_fight/map_blueprint/starter_village.json")).readAllBytes(),StandardCharsets.UTF_8);
        var blueprint=new Gson().fromJson(raw,MapBlueprint.class);
        blueprint.validate();
        var group=blueprint.facilities.stream().filter(f->f.id.equals("market")).findFirst().orElseThrow()
                .npcGroups.stream().filter(g->g.id.equals("merchants")).findFirst().orElseThrow();
        assertTrue(group.definitions.containsAll(List.of("waystone_merchant","weapon_merchant","armor_merchant","jewelry_merchant","special_merchant")));
        assertTrue(group.anchors.size()>=group.definitions.size());
    }
}
