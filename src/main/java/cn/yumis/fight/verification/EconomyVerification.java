package cn.yumis.fight.verification;
import cn.yumis.fight.bootstrap.ServerApplication;
import cn.yumis.fight.modules.equipment.api.EquipmentStats;
import cn.yumis.fight.modules.inventory.api.MenuHooks;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.platform.ItemRegistry;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.modules.equipment.api.MageBlood;
import cn.yumis.fight.modules.equipment.api.EquipmentTemplates;
import cn.yumis.fight.modules.equipment.api.EquipmentDefinitions;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.Slot;
import cn.yumis.fight.modules.equipment.api.Weapon;
import cn.yumis.fight.modules.dungeons.api.Difficulty;
import cn.yumis.fight.modules.economy.api.RewardSettings;

import cn.yumis.fight.modules.equipment.api.EquipmentRules;


import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.affixes.api.Affix;
import cn.yumis.fight.modules.equipment.api.Gear;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.dungeons.api.Target;
import static cn.yumis.fight.bootstrap.ApplicationJson.JSON;
import static cn.yumis.fight.bootstrap.ApplicationJson.copy;
import static cn.yumis.fight.modules.combat.api.LifeRules.*;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.nio.file.*;
import java.util.*;

/** Opt-in verification of production transactions, rewards, registration and native attributes. */
public final class EconomyVerification {
    private EconomyVerification(){}
    private static JsonObject request(ServerApplication r,ServerPlayer p,int slot,boolean bag){
        var stack=(bag?MenuHooks.extra(p).bag:p.getInventory()).getItem(slot);var g=EquipmentItems.gear(stack);var j=new JsonObject();
        j.addProperty("operation","verify/economy/"+UUID.randomUUID());j.addProperty("session",r.session(p).id.toString());j.addProperty("menu",p.containerMenu.containerId);j.addProperty("slot",slot);j.addProperty("bag",bag);j.addProperty("equipment",g.instance.toString());j.addProperty("revision",g.revision);j.add("item",r.inventory.encode(p,stack));return j;
    }
    public static int run(ServerApplication r,ServerPlayer p)throws Exception{
        if(!Boolean.getBoolean("ydnf.verify"))throw new IllegalStateException("verification disabled");
        p.closeContainer();List<String> passed=new ArrayList<>();var inventory=r.inventory.capture(p,"economy-before");var progress=copy(r.session(p).progress,Progress.class);var rewards=r.rewards;long gold=r.economyService.gold(p),revision=r.economyService.goldRevision(p);
        java.util.function.BiConsumer<Boolean,String> check=(ok,name)->{if(!ok)throw new IllegalStateException(name);passed.add(name);};
        List<Entity> mobs=new ArrayList<>();
        long operationBase=java.util.concurrent.ThreadLocalRandom.current().nextLong(1,Long.MAX_VALUE-2);
        try{
            r.session(p).progress.level=60;r.session(p).progress.sync=false;
            p.getInventory().setSelectedSlot(0);p.getInventory().clearContent();MenuHooks.extra(p).clearContent();MenuHooks.extra(p).bag.clearContent();
            p.getInventory().setItem(10,EquipmentItems.gear(MageBlood.create(new Random(2))));
            var sell=request(r,p,10,false);r.economyService.sell(p,sell);
            check.accept(r.economyService.gold(p)==gold+3000&&p.getInventory().getItem(10).isEmpty(),"sale_removes_item_and_commits_3000_gold");
            r.economyService.sell(p,sell);check.accept(r.economyService.gold(p)==gold+3000,"sale_retry_is_idempotent");
            p.getInventory().setItem(10,EquipmentItems.gear(MageBlood.create(new Random(3))));var stale=request(r,p,10,false);p.getInventory().setItem(10,EquipmentItems.gear(MageBlood.create(new Random(4))));
            try{r.economyService.sell(p,stale);throw new IllegalStateException("stale sale accepted");}catch(IllegalArgumentException expected){passed.add("stale_item_sale_rejected");}
            check.accept(r.economyService.gold(p)==gold+3000&&!p.getInventory().getItem(10).isEmpty(),"rejected_sale_keeps_item_and_balance");
            p.getInventory().setItem(0,EquipmentItems.gear(EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.EPIC,60,new Random())));
            try{r.economyService.sell(p,request(r,p,0,false));throw new IllegalStateException("held sale accepted");}catch(IllegalArgumentException expected){passed.add("held_weapon_cannot_be_sold");}
            MenuHooks.extra(p).bag.setItem(0,EquipmentItems.gear(MageBlood.create(new Random(5))));r.rewards=new RewardSettings(2,10);r.economyService.sell(p,request(r,p,0,true));
            check.accept(r.economyService.gold(p)==gold+6000&&MenuHooks.extra(p).bag.getItem(0).isEmpty(),"bag_sale_ignores_tenfold_kill_gold_multiplier");
            var original=EquipmentItems.gear(p.getInventory().getItem(10));String before=JSON.toJson(original.affixes);
            int menuSlot=-1;for(var slot:p.inventoryMenu.slots)if(slot.container==p.getInventory()&&slot.getContainerSlot()==10)menuSlot=slot.index;
            p.inventoryMenu.setCarried(ItemRegistry.item("divine_stone",1));JsonObject divine=new JsonObject();divine.addProperty("rulesRevision",EquipmentDefinitions.current().revision);divine.addProperty("operation","verify/divine/"+UUID.randomUUID());divine.addProperty("session",r.session(p).id.toString());divine.addProperty("menu",p.containerMenu.containerId);divine.addProperty("slot",menuSlot);divine.addProperty("equipment",original.instance.toString());divine.addProperty("revision",original.revision);divine.add("cursor",r.inventory.encode(p,p.inventoryMenu.getCarried()));r.equipmentApplication.currencyRequest(p,divine);
            var rerolled=EquipmentItems.gear(p.getInventory().getItem(10));check.accept(p.inventoryMenu.getCarried().isEmpty()&&rerolled.revision==original.revision+1&&!before.equals(JSON.toJson(rerolled.affixes)),"divine_transaction_consumes_one_and_rerolls_magic");
            r.equipmentApplication.currencyRequest(p,divine);check.accept(EquipmentItems.gear(p.getInventory().getItem(10)).revision==rerolled.revision,"divine_retry_does_not_reroll_twice");
            for(Rarity rarity:List.of(Rarity.EPIC,Rarity.MYTHIC)){
                Gear old=EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,rarity,49,new Random());old.appearance="";String name=EquipmentItems.name(old),appearance=cn.yumis.fight.modules.equipment.api.EquipmentTemplates.appearance(old);Gear next=EquipmentRules.process(old,rarity==Rarity.EPIC?"epic_upgrade_stone":"mythic_upgrade_stone",new Random());
                check.accept(EquipmentItems.name(next).equals(name)&&cn.yumis.fight.modules.equipment.api.EquipmentTemplates.appearance(next).equals(appearance),"legacy_"+rarity+"_upgrade_keeps_name_and_model");
            }
            var speed=new Stats();speed.add(Stat.MOVEMENT_SPEED,1);speed.mut(Stat.MOVEMENT_SPEED,.3);EquipmentStats.applyMovement(p,speed);var nativeSpeed=p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);var speedId=net.minecraft.resources.Identifier.fromNamespaceAndPath(ItemData.NS,"equipment_move_speed");
            check.accept(nativeSpeed.getModifier(speedId)!=null&&Math.abs(nativeSpeed.getModifier(speedId).amount()-.3)<1e-9,"movement_speed_reaches_native_attribute");
            speed.attribute(Stat.MOVEMENT_SPEED).mut=0;EquipmentStats.applyMovement(p,speed);check.accept(nativeSpeed.getModifier(speedId)==null,"removing_movement_bonus_removes_native_modifier");
            r.rewards=new RewardSettings(2,3);long initialXp=r.session(p).progress.experience;
            for(boolean noRewards:List.of(false,true)){
                Zombie mob=new Zombie(EntityTypes.ZOMBIE,p.level());mob.setNoAi(true);mob.setPos(p.getX()+3,p.getY(),p.getZ());p.level().addFreshEntity(mob);mobs.add(mob);
                Target target=r.targets.resolve(mob);target.originalLevel=60;target.elite=false;target.kind="NORMAL";target.difficulty=Difficulty.NORMAL;target.noRewards=noRewards;r.journal.put("target/"+mob.getUUID(),target);mob.setData(cn.yumis.fight.modules.combat.api.CombatContentTypes.TARGET,JSON.toJson(target));
                var hit=new JsonObject();hit.addProperty("operation",operationBase+mobs.size());hit.addProperty("session",r.session(p).id.toString());hit.addProperty("sourceGeneration",r.session(p).life.generation);hit.addProperty("dimension",p.level().dimension().identifier().toString());hit.addProperty("target",mob.getStringUUID());hit.addProperty("generation",target.life.generation);hit.addProperty("basis",target.life.basis);hit.addProperty("fraction",1);
                long beforeGold=r.economyService.gold(p);r.combatApplication.report(p,"damage",hit);r.combatApplication.report(p,"damage",hit);
                check.accept(r.economyService.gold(p)==beforeGold+(noRewards?0:396),noRewards?"no_rewards_target_provides_no_gold":"kill_commits_396_gold_once_with_multiplier");
            }
            check.accept(r.session(p).progress.experience==initialXp+260,"kill_experience_multiplier_is_independent");
            check.accept(EquipmentTemplates.creative(Rarity.MYTHIC).stream().filter(MageBlood::is).count()==1,"creative_mythic_catalog_has_unique_approved_pants");
        }finally{
            for(Entity entity:mobs)entity.discard();r.rewards=rewards;r.session(p).progress=progress;r.journal.transaction(Map.of("gold/"+p.getUUID(),JSON.toJsonTree(gold),"goldRevision/"+p.getUUID(),JSON.toJsonTree(Math.max(revision,r.economyService.goldRevision(p))+1)));
            r.inventory.verificationRestore(p,inventory);r.savePlayer(p);r.profile(p);
        }
        Path dir=r.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("verification");Files.createDirectories(dir);Files.writeString(dir.resolve("economy-checks.json"),JSON.toJson(Map.of("passed",passed,"count",passed.size())));return passed.size();
    }
    public static int fixture(ServerApplication r,ServerPlayer p)throws Exception{
        if(!Boolean.getBoolean("ydnf.verify"))throw new IllegalStateException("verification disabled");p.closeContainer();var s=r.session(p);s.progress.level=80;s.progress.promote(cn.yumis.fight.modules.character.api.JobCatalog.SWORDMASTER);s.progress.skills.put("yumis_dungeon_and_fight:dance",1);s.progress.slots[0]="yumis_dungeon_and_fight:dance";s.resources.training=true;s.resources.controlEpoch++;
        var pants=MageBlood.create(new Random(8));for(Affix a:pants.affixes)if(a.operation.equals("MAGIC"))a.family="SILVER";
        p.getInventory().setSelectedSlot(0);p.getInventory().clearContent();p.getInventory().setItem(0,EquipmentItems.gear(EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.MYTHIC,50,new Random())));p.setItemSlot(EquipmentSlot.LEGS,EquipmentItems.gear(pants));
        p.getInventory().setItem(9,EquipmentItems.gear(MageBlood.create(new Random(7))));p.getInventory().setItem(10,EquipmentItems.gear(EquipmentRules.create(Slot.RING,Weapon.KATANA,Rarity.EPIC,50,new Random())));p.getInventory().setItem(11,ItemRegistry.item("divine_stone",16));
        r.savePlayer(p);r.profile(p);return 1;
    }
}
