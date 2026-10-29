package cn.yumis.fight.bootstrap;
import cn.yumis.fight.verification.CombatVerification;
import cn.yumis.fight.verification.EconomyVerification;
import cn.yumis.fight.verification.VerificationChecks;
import cn.yumis.fight.platform.Permissions;
import cn.yumis.fight.modules.presentation.api.ModText;
import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import cn.yumis.fight.modules.equipment.api.EquipmentLayout;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.platform.ItemRegistry;
import cn.yumis.fight.modules.equipment.api.MaterialCatalog;
import cn.yumis.fight.modules.equipment.api.EquipmentTemplates;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.Slot;
import cn.yumis.fight.modules.equipment.api.Weapon;
import static cn.yumis.fight.modules.character.api.CharacterRules.nextExperience;

import cn.yumis.fight.modules.dungeons.application.DungeonService;
import cn.yumis.fight.modules.equipment.api.EquipmentRules;
import cn.yumis.fight.*;

import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.affixes.api.Affix;
import cn.yumis.fight.modules.equipment.api.Gem;
import static cn.yumis.fight.modules.combat.api.LifeRules.*;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.item.*;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.arguments.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.entity.player.*;

import java.util.*;
public final class ServerEvents {
    private ServerEvents(){
    }
    public static void register(){
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent e)->{if(e.getEntity() instanceof net.minecraft.world.entity.player.Player)e.setStrength(e.getStrength()*.1f);});
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent e)->{
            if(ServerApplication.ready()&&!e.getLevel().isClientSide()&&e.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item)ServerApplication.get().loot.nativeLeft(item);
            if(ServerApplication.ready()&&!e.getLevel().isClientSide()&&e.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile&&projectile.getRemovalReason()!=net.minecraft.world.entity.Entity.RemovalReason.CHANGED_DIMENSION&&projectile.getRemovalReason()!=net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK)ServerApplication.get().nativeHits.projectileLeft(projectile.getUUID());
        }
        );
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,true,(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e)->{
            if(ServerApplication.ready()&&!e.isCanceled()&&!e.loadedFromDisk()&&!e.getLevel().isClientSide()&&e.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile)ServerApplication.get().nativeHits.launched(projectile);
        });
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,true,(net.neoforged.neoforge.event.entity.item.ItemTossEvent e)->{
            if(ServerApplication.ready())ServerApplication.get().inventory.tossEvent(e);
        }
        );
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e)->{
            if(ServerApplication.ready()&&!e.getLevel().isClientSide()&&e.getEntity() instanceof DungeonEnemy enemy){
                var instance=ServerApplication.get().dungeons.byId(enemy.instance());
                if(instance==null||instance.status.equals("CLOSED"))e.setCanceled(true);
            }
        }
        );
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent e)->{
            if(net.minecraft.world.entity.EntitySpawnReason.isSpawner(e.getSpawnType()))e.getEntity().getPersistentData().putBoolean("ydnf_spawner",true);
        }
        );
        cn.yumis.fight.modules.professions.application.ProfessionEvents.register(ServerApplication::ready,()->ServerApplication.get().professionService);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.block.BreakBlockEvent e)->{
            if(e.getLevel() instanceof net.minecraft.server.level.ServerLevel l&&l.dimension()==DungeonService.DIMENSION)e.setCanceled(true);
        }
        );
        NeoForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent e)->{
            if(e.getLevel() instanceof net.minecraft.server.level.ServerLevel l&&l.dimension()==DungeonService.DIMENSION)e.setCanceled(true);
        }
        );
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,(OnDatapackSyncEvent e)->{
            if(ServerApplication.ready()&&e.getPlayer()==null){
                // Curios 16.0.0+26.2 replaces its resource object on reload but only
                // populates it at server startup. Rebind parsed slots before capability queries.
                top.theillusivec4.curios.common.data.CuriosSlotResources.SERVER.populateData();
                ServerApplication.get().loadDefinitions();ServerApplication.get().refreshAll();
            }
        }
        );
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e)->commands(e));
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,(ItemEntityPickupEvent.Pre e)->{if(ServerApplication.ready())ServerApplication.get().loot.nativePickup(e);});
    }
    private static void commands(RegisterCommandsEvent e){
        var root=Commands.literal("ydnf").executes(c->{
            ServerPlayer p=c.getSource().getPlayerOrException();ServerApplication.get().profile(p);c.getSource().sendSuccess(()->cn.yumis.fight.modules.presentation.api.ModText.of("按 I 打开地下城与战斗菜单"),false);return 1;
        }
        );
        var equipment=Commands.literal("equipment").then(Commands.literal("claim").executes(c->{
            ServerApplication.get().handle(c.getSource().getPlayerOrException(),"claim",new JsonObject());return 1;
        }
        ));
        var slots=Commands.literal("slots").requires(source->source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER));
        for(String action:List.of("query","add","remove")){
            var group=Commands.argument("group",net.minecraft.commands.arguments.IdentifierArgument.id());
            if(action.equals("query"))group.executes(c->{
                ServerPlayer player=EntityArgument.getPlayer(c,"player");String id=net.minecraft.commands.arguments.IdentifierArgument.getId(c,"group").toString();EquipmentLayout.current().group(id);int count=ServerApplication.get().equipmentLayout.bonus(player).getOrDefault(id,0);c.getSource().sendSuccess(()->cn.yumis.fight.modules.presentation.api.ModText.of(id+" 额外槽数："+count),false);return count;
            }
            );
            else group.then(Commands.argument("count",IntegerArgumentType.integer(1,64)).executes(c->{
                ServerPlayer player=EntityArgument.getPlayer(c,"player");String id=net.minecraft.commands.arguments.IdentifierArgument.getId(c,"group").toString();int delta=IntegerArgumentType.getInteger(c,"count")*(action.equals("remove")?-1:1);int count=ServerApplication.get().equipmentLayout.change(player,id,delta);c.getSource().sendSuccess(()->cn.yumis.fight.modules.presentation.api.ModText.of(id+" 额外槽数："+count),false);return count;
            }
            ));
            slots.then(Commands.literal(action).then(Commands.argument("player",EntityArgument.player()).then(group)));
        }
        root.then(equipment.then(slots));
        root.then(Commands.literal("profession").requires(source->source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)).then(Commands.literal("addxp").then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("profession",StringArgumentType.word()).then(Commands.argument("experience",LongArgumentType.longArg(1)).executes(c->{
            var runtime=ServerApplication.get();var player=EntityArgument.getPlayer(c,"player");runtime.professionService.award(player.getUUID(),StringArgumentType.getString(c,"profession"),LongArgumentType.getLong(c,"experience"),"administrator/"+UUID.randomUUID(),true);runtime.professions(player);return 1;
        }
        ))))));
        var dev=Commands.literal("dev").requires(s->s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER));
        dev.then(Commands.literal("level").then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("level",IntegerArgumentType.integer(1,80)).executes(c->{
            ServerPlayer p=EntityArgument.getPlayer(c,"player");var r=ServerApplication.get();Progress pr=r.session(p).progress;int level=IntegerArgumentType.getInteger(c,"level");while(pr.level<level)pr.grant(nextExperience(pr.level));r.savePlayer(p);r.profile(p);r.message(p,"开发测试：角色等级已提高");return 1;
        }
        ))));
        dev.then(Commands.literal("job").executes(c->{
            var r=ServerApplication.get();ServerPlayer p=c.getSource().getPlayerOrException();if(r.session(p).progress.level<20)throw new IllegalArgumentException("开发转职仍需20级");r.session(p).progress.promote(cn.yumis.fight.modules.character.api.JobCatalog.SWORDMASTER);r.savePlayer(p);r.profile(p);r.message(p,"开发测试：已跳过任务转职剑魂");return 1;
        }
        ));
        dev.then(Commands.literal("white").executes(c->{
            var r=ServerApplication.get();ServerPlayer p=c.getSource().getPlayerOrException();JsonObject intent=new JsonObject();intent.addProperty("dev","white");r.inventory.transact(p,UUID.randomUUID().toString(),intent,()->{
                for(Slot slot:Slot.values())if(r.session(p).progress.level>=slot.minimum())r.inventory.requireSpace(p,EquipmentItems.gear(EquipmentTemplates.createStandard(slot,Weapon.KATANA,Rarity.COMMON,r.session(p).progress.level,r.random)));
            }
            ,Map.of());r.profile(p);return 1;
        }
        ));
        dev.then(Commands.literal("give").then(Commands.argument("id",StringArgumentType.word()).executes(c->{
            var r=ServerApplication.get();ServerPlayer p=c.getSource().getPlayerOrException();String id=StringArgumentType.getString(c,"id");ItemStack item;if(MaterialCatalog.current().materials.containsKey(id))item=EquipmentItems.material(id,64);else if(id.equals("test_gem")){
                Gem gem=new Gem();gem.level=1;gem.group="all";gem.name="开发样本 · 力量";Affix a=new Affix("test_strength",Stat.STRENGTH,5,false);a.fixed=true;gem.affixes.add(a);item=EquipmentItems.payload("affix_gem",gem,gem.name);
            }
            else if(id.equals("epic")||id.equals("mythic"))item=EquipmentItems.gear(EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,id.equals("epic")?Rarity.EPIC:Rarity.MYTHIC,r.session(p).progress.level,r.random));else if(ItemRegistry.ALL.containsKey(id))item=ItemRegistry.item(id,64);else throw new IllegalArgumentException("未知测试物品");JsonObject intent=new JsonObject();intent.addProperty("give",id);r.inventory.transact(p,UUID.randomUUID().toString(),intent,()->r.inventory.requireSpace(p,item),Map.of());return 1;
        }
        )));
        for(String mode:List.of("on","off"))dev.then(Commands.literal("nocd").then(Commands.literal(mode).executes(c->{
            var r=ServerApplication.get();ServerPlayer p=c.getSource().getPlayerOrException();var s=r.session(p);s.resources.controlEpoch++;boolean enabled=mode.equals("on");if(enabled&&!s.resources.training)s.resources.trainingBoundary=s.resources.mp;if(!enabled&&s.resources.training&&s.resources.trainingBoundary>=0)s.resources.mp=Math.min(s.resources.mp,s.resources.trainingBoundary);s.resources.training=enabled;s.resources.mp=Math.min(s.resources.mp,r.stats(p).get(Stat.MP));r.savePlayer(p);r.profile(p);r.message(p,"开发训练模式："+mode);return 1;
        }
        )));
        root.then(Commands.literal("verify").requires(source->Boolean.getBoolean("ydnf.verify")&&source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)).executes(c->VerificationChecks.run(ServerApplication.get(),c.getSource().getPlayerOrException()))
            .then(Commands.literal("combat").then(Commands.argument("case",StringArgumentType.word()).executes(c->{try{return CombatVerification.run(ServerApplication.get(),c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"case"));}catch(Exception failure){throw new IllegalStateException(failure);}})))
            .then(Commands.literal("economy").executes(c->{try{return EconomyVerification.run(ServerApplication.get(),c.getSource().getPlayerOrException());}catch(Exception failure){throw new IllegalStateException(failure);}}).then(Commands.literal("fixture").executes(c->{try{return EconomyVerification.fixture(ServerApplication.get(),c.getSource().getPlayerOrException());}catch(Exception failure){throw new IllegalStateException(failure);}}))));
        root.then(dev);
        e.getDispatcher().register(root);
    }
}
