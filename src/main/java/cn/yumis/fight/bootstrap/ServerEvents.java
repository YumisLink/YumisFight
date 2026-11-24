package cn.yumis.fight.bootstrap;
import cn.yumis.fight.verification.CombatVerification;
import cn.yumis.fight.verification.EconomyVerification;
import cn.yumis.fight.verification.VerificationChecks;
import cn.yumis.fight.modules.presentation.api.ModText;
import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import cn.yumis.fight.modules.equipment.api.EquipmentLayout;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.platform.ItemRegistry;
import cn.yumis.fight.modules.equipment.api.MaterialCatalog;
import cn.yumis.fight.modules.equipment.api.EquipmentTemplates;
import cn.yumis.fight.network.Network;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.LevelAccessor;
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
        cn.yumis.fight.modules.mining.api.VeinMining.registerEvents();
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.LevelEvent.CreateSpawnPosition e)->cn.yumis.fight.modules.mapgeneration.api.MapGeneration.spawnCreated(e.getLevel()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e)->cn.yumis.fight.modules.mapgeneration.api.MapGeneration.tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e)->{if(cn.yumis.fight.modules.mapgeneration.api.MapGeneration.blocksHostileSpawn(e.getEntity(),e.getLevel()))e.setCanceled(true);});
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent e)->{if(e.getEntity() instanceof net.minecraft.world.entity.player.Player){if(e.getEntity() instanceof ServerPlayer p&&ServerApplication.ready()&&ServerApplication.get().careers.steadfast(p))e.setCanceled(true);else e.setStrength(e.getStrength()*.1f);}});
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable e)->{if(e.getEntity() instanceof ServerPlayer p&&ServerApplication.ready()&&e.getEffectInstance().getEffect()==cn.yumis.fight.modules.combat.api.CombatContentTypes.STUN&&(ServerApplication.get().careers.steadfast(p)||ServerApplication.get().careers.invulnerable(p)))e.setResult(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable.Result.DO_NOT_APPLY);});
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent e)->{
            if(ServerApplication.ready()&&!e.getLevel().isClientSide()&&e.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item)ServerApplication.get().loot.nativeLeft(item);
            if(ServerApplication.ready()&&!e.getLevel().isClientSide()&&e.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile&&projectile.getRemovalReason()!=net.minecraft.world.entity.Entity.RemovalReason.CHANGED_DIMENSION&&projectile.getRemovalReason()!=net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK)ServerApplication.get().nativeHits.projectileLeft(projectile.getUUID());
            if(ServerApplication.ready()&&!e.getLevel().isClientSide()&&e.getEntity() instanceof LivingEntity living&&!(living instanceof net.minecraft.world.entity.player.Player)){
                var reason=living.getRemovalReason();
                if(reason==Entity.RemovalReason.KILLED||reason==Entity.RemovalReason.DISCARDED){
                    boolean persistentSpecial=living instanceof DungeonEnemy
                        ||living instanceof cn.yumis.fight.modules.dungeons.api.WorldTargetEntity
                        ||living instanceof cn.yumis.fight.modules.dungeons.api.TrainingSandbag
                        ||living instanceof cn.yumis.fight.modules.combat.api.WorldVitalEntity
                        ||living instanceof cn.yumis.fight.modules.dungeons.api.RitualBossEntity
                        ||living instanceof cn.yumis.fight.modules.dungeons.api.RitualMechanismEntity;
                    ServerApplication.get().targets.forget(living.getUUID(),!persistentSpecial);
                }
            }
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
            if(ServerApplication.ready()&&!e.getLevel().isClientSide()&&e.getEntity() instanceof DungeonEnemy enemy&&!(enemy instanceof cn.yumis.fight.modules.dungeons.api.WorldTargetEntity)){
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
            if (blocksDungeonEdit(e.getLevel(), e.getPlayer())) e.setCanceled(true);
        }
        );
        NeoForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent e)->{
            if (blocksDungeonEdit(e.getLevel(), e.getEntity())) e.setCanceled(true);
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
        NeoForge.EVENT_BUS.addListener((ItemEntityPickupEvent.Post e)->{
            if(!ServerApplication.ready()||!(e.getPlayer() instanceof ServerPlayer player))return;
            var original=e.getOriginalStack();
            int taken=original.getCount()-e.getCurrentStack().getCount();
            if(taken<=0)return;
            var view=EquipmentItems.view(original);
            if(view!=null&&view.creativeTemplate())ServerApplication.get().equipmentApplication.inventoryArrived(player);
            groundPickup(player,original.copyWithCount(taken));
        });
    }
    static void groundPickup(ServerPlayer player,ItemStack stack){
        var runtime=ServerApplication.get();
        lootAcquired(player,stack);
        if(stack.getItem() instanceof cn.yumis.fight.modules.industry.api.EnergyCrystalItem){
            Network.send(player,"tutorialCrystalPickup",Map.of("session",runtime.session(player).id.toString()));
        }
        UUID id=player.getUUID();
        if(runtime.inventory.bagOption(player).autoStore()
            &&(runtime.awakeningQuests.tracksBagItem(id,stack)||runtime.commissions.tracksBagItem(id,stack)))runtime.profile(player);
    }
    static void lootAcquired(ServerPlayer player,ItemStack stack){
        var gear=EquipmentItems.gear(stack);
        if(gear==null||gear.instance==null||gear.rarity==null||gear.creativeTemplate)return;
        Network.send(player,"lootAcquired",Map.of(
            "session",ServerApplication.get().session(player).id.toString(),
            "instance",gear.instance.toString(),
            "rarity",gear.rarity.name(),
            "level",gear.level));
    }
    private static boolean blocksDungeonEdit(LevelAccessor level, Entity actor) {
        return level instanceof ServerLevel world
            && (world.dimension() == DungeonService.DIMENSION || world.dimension() == cn.yumis.fight.modules.arkguard.api.ArkWar.DIMENSION)
            && (!ServerApplication.ready() || !ServerApplication.get().dungeons.canEditBlocks(actor));
    }
    private static void commands(RegisterCommandsEvent e){
        var root=Commands.literal("ydnf").executes(c->{
            ServerPlayer p=c.getSource().getPlayerOrException();ServerApplication.get().profile(p);c.getSource().sendSuccess(()->cn.yumis.fight.modules.presentation.api.ModText.of("按 I 打开地下城与战斗菜单"),false);return 1;
        }
        );
        root.then(cn.yumis.fight.modules.mapgeneration.api.MapGeneration.command());
        var design = Commands.literal("design").executes(c -> {
            boolean enabled = ServerApplication.get().dungeons.designMode();
            c.getSource().sendSuccess(() -> ModText.of(enabled ? "副本设计模式已开启" : "副本设计模式已关闭"), false);
            return enabled ? 1 : 0;
        });
        for (String mode : List.of("on", "off")) {
            design.then(Commands.literal(mode).executes(c -> {
                boolean enabled = mode.equals("on");
                ServerApplication.get().dungeons.setDesignMode(enabled);
                c.getSource().sendSuccess(() -> ModText.of(enabled ? "已开启副本设计模式" : "已关闭副本设计模式"), true);
                return 1;
            }));
        }
        root.then(Commands.literal("dungeon")
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            .then(design));
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
        var job=Commands.literal("job").requires(source->source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER));
        job.then(Commands.literal("mark").then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("base",StringArgumentType.word()).suggests((context,builder)->{builder.suggest("ghost_swordsman");builder.suggest("mage");return builder.buildFuture();}).executes(c->{var value=StringArgumentType.getString(c,"base");ServerApplication.get().progressService.markQuest(EntityArgument.getPlayer(c,"player"),value.contains(":")?value:itemNamespace()+value);return 1;}))));
        job.then(Commands.literal("awaken").then(Commands.argument("player",EntityArgument.player()).executes(c->{ServerApplication.get().progressService.awaken(EntityArgument.getPlayer(c,"player"));return 1;})));
        job.then(Commands.argument("career",StringArgumentType.word()).suggests((context,builder)->{for(var definition:cn.yumis.fight.modules.character.api.JobCatalog.all())if(!definition.id().equals(cn.yumis.fight.modules.character.api.JobCatalog.NONE))builder.suggest(definition.id().substring(definition.id().indexOf(':')+1));return builder.buildFuture();}).then(Commands.argument("player",EntityArgument.player()).executes(c->master(c))));
        root.then(job);
        var points=Commands.literal("points").requires(source->source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER));
        points.then(Commands.literal("get").then(Commands.argument("player",EntityArgument.player()).executes(c->{var player=EntityArgument.getPlayer(c,"player");long value=ServerApplication.get().economyService.points(player);c.getSource().sendSuccess(()->ModText.of(player.getName().getString()+"："+value+"点券"),false);return 1;})));
        for(String operation:List.of("give","take"))points.then(Commands.literal(operation).then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("amount",LongArgumentType.longArg(1)).executes(c->{long amount=LongArgumentType.getLong(c,"amount")*(operation.equals("give")?1:-1);ServerApplication.get().economyService.changePoints(EntityArgument.getPlayer(c,"player"),amount);return 1;}))));
        root.then(points);
        var gold=Commands.literal("gold").requires(source->source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER));
        gold.then(Commands.literal("get").then(Commands.argument("player",EntityArgument.player()).executes(c->{var player=EntityArgument.getPlayer(c,"player");long balance=ServerApplication.get().economyService.gold(player);c.getSource().sendSuccess(()->ModText.of(player.getName().getString()+"："+balance+"金币"),false);return 1;})));
        for(String operation:List.of("give","take"))gold.then(Commands.literal(operation).then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("amount",LongArgumentType.longArg(1)).executes(c->{
            var r=ServerApplication.get();var player=EntityArgument.getPlayer(c,"player");long amount=LongArgumentType.getLong(c,"amount");long before=r.economyService.gold(player);long balance=r.economyService.changeGold(player,operation.equals("give")?amount:-amount);long changed=operation.equals("give")?balance-before:before-balance;
            String text=(operation.equals("give")?"已向 ":"已从 ")+player.getName().getString()+(operation.equals("give")?" 发放 ":" 扣除 ")+changed+" 金币，当前余额 "+balance+(operation.equals("take")&&changed<amount?"（余额不足，已扣至 0）":"");
            c.getSource().sendSuccess(()->ModText.of(text),true);
            return 1;
        }))));
        root.then(gold);
        root.then(Commands.literal("profession").requires(source->source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)).then(Commands.literal("addxp").then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("profession",StringArgumentType.word()).then(Commands.argument("experience",LongArgumentType.longArg(1)).executes(c->{
            var runtime=ServerApplication.get();var player=EntityArgument.getPlayer(c,"player");runtime.professionService.awardAdmin(player.getUUID(),StringArgumentType.getString(c,"profession"),LongArgumentType.getLong(c,"experience"),"administrator/"+UUID.randomUUID());runtime.professions(player);return 1;
        }
        ))))));
        var dev=Commands.literal("dev").requires(s->s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER));
        dev.then(Commands.literal("level").then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("level",IntegerArgumentType.integer(1,cn.yumis.fight.modules.character.api.CharacterRules.MAX_LEVEL)).executes(c->{
            ServerPlayer p=EntityArgument.getPlayer(c,"player");var r=ServerApplication.get();Progress pr=r.session(p).progress;int level=IntegerArgumentType.getInteger(c,"level");while(pr.level<level)pr.grant(nextExperience(pr.level));r.savePlayer(p);r.profile(p);r.message(p,"开发测试：角色等级已提高");return 1;
        }
        ))));
        dev.then(Commands.literal("white").executes(c->white(c,Weapon.KATANA)).then(Commands.argument("weapon",StringArgumentType.word()).suggests((context,builder)->{for(var kind:Weapon.values())builder.suggest(kind.name());return builder.buildFuture();}).executes(c->white(c,Weapon.valueOf(StringArgumentType.getString(c,"weapon").toUpperCase())))));
        dev.then(Commands.literal("give").then(Commands.argument("id",StringArgumentType.word()).executes(c->{
            var r=ServerApplication.get();ServerPlayer p=c.getSource().getPlayerOrException();String id=StringArgumentType.getString(c,"id");ItemStack item;if(MaterialCatalog.current().materials.containsKey(id))item=EquipmentItems.material(id,64);else if(id.equals("test_gem")){
                Gem gem=new Gem();gem.level=1;gem.group="all";gem.name="开发样本 · 力量";Affix a=new Affix("test_strength",Stat.STRENGTH,5,false);a.fixed=true;gem.affixes.add(a);item=EquipmentItems.payload("affix_gem",gem,gem.name);
            }
            else if(ItemRegistry.ALL.containsKey(id))item=ItemRegistry.item(id,64);else throw new IllegalArgumentException("未知测试物品");JsonObject intent=new JsonObject();intent.addProperty("give",id);r.inventory.transact(p,UUID.randomUUID().toString(),intent,()->r.inventory.requireSpace(p,item),Map.of());return 1;
        }
        )));
        for(String mode:List.of("on","off"))dev.then(Commands.literal("nocd").then(Commands.literal(mode).executes(c->{
            var r=ServerApplication.get();ServerPlayer p=c.getSource().getPlayerOrException();var s=r.session(p);s.resources.controlEpoch++;boolean enabled=mode.equals("on");if(enabled)s.resources.cooldowns.clear();if(enabled&&!s.resources.training)s.resources.trainingBoundary=s.resources.mp;if(!enabled&&s.resources.training&&s.resources.trainingBoundary>=0)s.resources.mp=Math.min(s.resources.mp,s.resources.trainingBoundary);s.resources.training=enabled;s.resources.mp=Math.min(s.resources.mp,r.stats(p).get(Stat.MP));r.savePlayer(p);r.profile(p);r.message(p,"开发训练模式："+mode);return 1;
        }
        )));
        root.then(Commands.literal("verify").requires(source->Boolean.getBoolean("ydnf.verify")&&source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)).executes(c->VerificationChecks.run(ServerApplication.get(),c.getSource().getPlayerOrException()))
            .then(Commands.literal("combat").then(Commands.argument("case",StringArgumentType.word()).executes(c->{try{return CombatVerification.run(ServerApplication.get(),c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"case"));}catch(Exception failure){throw new IllegalStateException(failure);}})))
            .then(Commands.literal("economy").executes(c->{try{return EconomyVerification.run(ServerApplication.get(),c.getSource().getPlayerOrException());}catch(Exception failure){throw new IllegalStateException(failure);}}).then(Commands.literal("fixture").executes(c->{try{return EconomyVerification.fixture(ServerApplication.get(),c.getSource().getPlayerOrException());}catch(Exception failure){throw new IllegalStateException(failure);}}))));
        root.then(dev);
        cn.yumis.fight.modules.economy.application.ChestService.commands(root,()->ServerApplication.get().chests);
        cn.yumis.fight.modules.economy.application.JarService.commands(root,()->ServerApplication.get().jars);
        cn.yumis.fight.modules.npc.application.NpcService.commands(root,()->ServerApplication.get().npcs);
        cn.yumis.fight.modules.quests.application.CommissionService.commands(root,()->ServerApplication.get().commissions);
        cn.yumis.fight.modules.dungeons.application.DungeonCommands.commands(root,()->ServerApplication.get().dungeons);
        cn.yumis.fight.modules.equipment.application.EquipmentCommands.commands(root,()->new cn.yumis.fight.modules.equipment.application.EquipmentCommands.Admin(ServerApplication.get().inventory,ServerApplication.get()::profile));
        e.getDispatcher().register(root);
    }
    private static int white(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c,Weapon weapon)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var r=ServerApplication.get();ServerPlayer p=c.getSource().getPlayerOrException();JsonObject intent=new JsonObject();intent.addProperty("dev","white");intent.addProperty("weapon",weapon.name());r.inventory.transact(p,UUID.randomUUID().toString(),intent,()->{
            for(Slot slot:Slot.values())if(r.session(p).progress.level>=slot.minimum())r.inventory.requireSpace(p,EquipmentItems.gear(EquipmentTemplates.createStandard(slot,weapon,Rarity.COMMON,r.session(p).progress.level,r.random)));
        }
        ,Map.of());r.profile(p);r.message(p,"开发测试白装："+weapon.title());return 1;
    }
    /** `/ydnf job <职业> <玩家>`：该职业配置直接满级全技能，并同事务发放对应武器。 */
    private static int master(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var r=ServerApplication.get();ServerPlayer p=EntityArgument.getPlayer(c,"player");
        String raw=StringArgumentType.getString(c,"career"),id=raw.contains(":")?raw:itemNamespace()+raw;
        var definition=cn.yumis.fight.modules.character.api.JobCatalog.require(id);
        if(definition.id().equals(cn.yumis.fight.modules.character.api.JobCatalog.NONE))throw new IllegalArgumentException("请指定具体职业");
        var mastered=cn.yumis.fight.modules.skills.api.CareerMastering.apply(definition.id(),r.session(p).progress,r.characters.profiles(p.getUUID()));
        var writes=r.characters.writes(p.getUUID(),mastered.active(),mastered.book());
        JsonObject intent=new JsonObject();intent.addProperty("job",definition.id());
        r.inventory.transact(p,UUID.randomUUID().toString(),intent,()->{
            for(var type:cn.yumis.fight.modules.character.api.JobCatalog.weapons(definition.id())){
                if(type.equals("BROOM"))continue;
                r.inventory.requireSpace(p,EquipmentItems.gear(EquipmentTemplates.createStandard(Slot.WEAPON,Weapon.valueOf(type),Rarity.COMMON,cn.yumis.fight.modules.character.api.CharacterRules.MAX_LEVEL,r.random)));
            }
        },writes);
        r.session(p).progress=mastered.active();
        r.profile(p);
        c.getSource().sendSuccess(()->ModText.of("已拉满 "+p.getName().getString()+" 的"+definition.title()+"：满级全技能，并发放对应武器"),true);
        return 1;
    }
    private static String itemNamespace(){return cn.yumis.fight.platform.ItemData.NS+":";}
}
