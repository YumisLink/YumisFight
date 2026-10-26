package cn.yumis.fight.server;
import cn.yumis.fight.*;
import cn.yumis.fight.game.*;
import cn.yumis.fight.rules.*;
import cn.yumis.fight.rules.Rules.Rarity;
import static cn.yumis.fight.rules.Rules.*;
import static cn.yumis.fight.rules.Models.*;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.arguments.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.minecraft.util.TriState;
import java.util.*;
public final class ServerEvents {
    private ServerEvents(){
    }
    private static boolean mature(net.minecraft.world.level.block.state.BlockState s){
        return s.getBlock() instanceof CropBlock crop&&crop.isMaxAge(s)||s.is(Blocks.NETHER_WART)&&s.getValue(NetherWartBlock.AGE)==3||s.is(Blocks.COCOA)&&s.getValue(CocoaBlock.AGE)==2||s.is(Blocks.SWEET_BERRY_BUSH)&&s.getValue(SweetBerryBushBlock.AGE)==3;
    }
    public static void register(){
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent e)->{
            if(ServerRuntime.ready()&&!e.getLevel().isClientSide()&&e.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item)ServerRuntime.get().loot.nativeLeft(item);
        }
        );
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.item.ItemTossEvent e)->{
            if(ServerRuntime.ready()&&ServerRuntime.get().inventory.inside())e.setCanceled(true);
        }
        );
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e)->{
            if(ServerRuntime.ready()&&!e.getLevel().isClientSide()&&e.getEntity() instanceof DungeonEnemy enemy){
                var instance=ServerRuntime.get().dungeons.byId(enemy.instance());
                if(instance==null||instance.status.equals("CLOSED"))e.setCanceled(true);
            }
        }
        );
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent e)->{
            if(net.minecraft.world.entity.EntitySpawnReason.isSpawner(e.getSpawnType()))e.getEntity().getPersistentData().putBoolean("ydnf_spawner",true);
        }
        );
        NeoForge.EVENT_BUS.addListener((BlockDropsEvent e)->{
            if(ServerRuntime.ready()&&!e.isCanceled()&&e.getBreaker() instanceof ServerPlayer p&&mature(e.getState())&&!e.getDrops().isEmpty())ServerRuntime.get().awardProfession(p,"farmer",10,"harvest/"+UUID.randomUUID());
        }
        );
        NeoForge.EVENT_BUS.addListener((ItemFishedEvent e)->{
            if(ServerRuntime.ready()&&!e.isCanceled()&&e.getEntity() instanceof ServerPlayer p&&!e.getDrops().isEmpty())ServerRuntime.get().awardProfession(p,"fisherman",20,"fishing/"+e.getHookEntity().getUUID());
        }
        );
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.block.BreakBlockEvent e)->{
            if(e.getLevel() instanceof net.minecraft.server.level.ServerLevel l&&l.dimension()==DungeonService.DIMENSION)e.setCanceled(true);
        }
        );
        NeoForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent e)->{
            if(e.getLevel() instanceof net.minecraft.server.level.ServerLevel l&&l.dimension()==DungeonService.DIMENSION)e.setCanceled(true);
        }
        );
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent e)->{
            if(ServerRuntime.ready()){
                ServerRuntime.get().loadDefinitions();if(e.getPlayer()==null)ServerRuntime.get().refreshAll();
            }
        }
        );
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e)->commands(e));
        NeoForge.EVENT_BUS.addListener((ItemEntityPickupEvent.Pre e)->{
            if(!ServerRuntime.ready()||!(e.getPlayer() instanceof ServerPlayer p))return;String id=e.getItemEntity().getPersistentData().getStringOr("ydnf_drop","");if(id.isEmpty())return;e.setCanPickup(TriState.FALSE);var r=ServerRuntime.get();LootService.Drop drop=r.journal.get("loot/"+id,LootService.Drop.class,null);if(drop==null||!r.journal.get("lootEnd/"+id).isJsonNull())return;ItemStack actual=e.getItemEntity().getItem();int room=0;for(int i=0;i<36;i++){
                ItemStack s=p.getInventory().getItem(i);room+=s.isEmpty()?actual.getMaxStackSize():ItemStack.isSameItemSameComponents(s,actual)?Math.max(0,s.getMaxStackSize()-s.getCount()):0;
            }
            int take=Math.min(room,actual.getCount());if(take<=0)return;ItemStack result=actual.copyWithCount(take),left=actual.copyWithCount(actual.getCount()-take);var next=copy(drop,LootService.Drop.class);next.item=r.inventory.encode(p,left);Map<String,JsonElement> edits=new HashMap<>();edits.put("loot/"+id,JSON.toJsonTree(next));if(left.isEmpty())edits.put("lootEnd/"+id,JSON.toJsonTree("picked"));JsonObject intent=new JsonObject();intent.addProperty("publicPickup",id);intent.addProperty("count",take);try{
                r.inventory.transact(p,"public-pickup/"+UUID.randomUUID(),intent,()->r.inventory.requireSpace(p,result),edits);if(left.isEmpty())e.getItemEntity().discard();else e.getItemEntity().setItem(left);
            }
            catch(Exception failure){
                r.message(p,failure.getMessage());
            }
        }
        );
    }
    private static void commands(RegisterCommandsEvent e){
        var root=Commands.literal("ydnf").executes(c->{
            ServerPlayer p=c.getSource().getPlayerOrException();ServerRuntime.get().profile(p);c.getSource().sendSuccess(()->cn.yumis.fight.game.ModText.of("按 I 打开地下城与战斗菜单"),false);return 1;
        }
        );
        var equipment=Commands.literal("equipment").then(Commands.literal("claim").executes(c->{
            ServerRuntime.get().handle(c.getSource().getPlayerOrException(),"claim",new JsonObject());return 1;
        }
        ));
        var slots=Commands.literal("slots").requires(source->source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER));
        for(String action:List.of("query","add","remove")){
            var group=Commands.argument("group",net.minecraft.commands.arguments.IdentifierArgument.id());
            if(action.equals("query"))group.executes(c->{
                ServerPlayer player=EntityArgument.getPlayer(c,"player");String id=net.minecraft.commands.arguments.IdentifierArgument.getId(c,"group").toString();EquipmentLayout.CURRENT.group(id);int count=EquipmentService.bonus(ServerRuntime.get(),player).getOrDefault(id,0);c.getSource().sendSuccess(()->cn.yumis.fight.game.ModText.of(id+" 额外槽数："+count),false);return count;
            }
            );
            else group.then(Commands.argument("count",IntegerArgumentType.integer(1,64)).executes(c->{
                ServerPlayer player=EntityArgument.getPlayer(c,"player");String id=net.minecraft.commands.arguments.IdentifierArgument.getId(c,"group").toString();int delta=IntegerArgumentType.getInteger(c,"count")*(action.equals("remove")?-1:1);int count=EquipmentService.change(ServerRuntime.get(),player,id,delta);c.getSource().sendSuccess(()->cn.yumis.fight.game.ModText.of(id+" 额外槽数："+count),false);return count;
            }
            ));
            slots.then(Commands.literal(action).then(Commands.argument("player",EntityArgument.player()).then(group)));
        }
        root.then(equipment.then(slots));
        root.then(Commands.literal("profession").requires(source->source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)).then(Commands.literal("addxp").then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("profession",StringArgumentType.word()).then(Commands.argument("experience",LongArgumentType.longArg(1)).executes(c->{
            var runtime=ServerRuntime.get();var player=EntityArgument.getPlayer(c,"player");runtime.awardProfession(player,StringArgumentType.getString(c,"profession"),LongArgumentType.getLong(c,"experience"),"administrator/"+UUID.randomUUID(),true);runtime.professions(player);return 1;
        }
        ))))));
        var dev=Commands.literal("dev").requires(s->s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER));
        dev.then(Commands.literal("level").then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("level",IntegerArgumentType.integer(1,80)).executes(c->{
            ServerPlayer p=EntityArgument.getPlayer(c,"player");var r=ServerRuntime.get();Progress pr=r.session(p).progress;int level=IntegerArgumentType.getInteger(c,"level");while(pr.level<level)pr.grant(nextExperience(pr.level));r.savePlayer(p);r.profile(p);r.message(p,"开发测试：角色等级已提高");return 1;
        }
        ))));
        dev.then(Commands.literal("job").executes(c->{
            var r=ServerRuntime.get();ServerPlayer p=c.getSource().getPlayerOrException();if(r.session(p).progress.level<20)throw new IllegalArgumentException("开发转职仍需20级");r.session(p).progress.swordmaster=true;r.savePlayer(p);r.profile(p);r.message(p,"开发测试：已跳过任务转职剑魂");return 1;
        }
        ));
        dev.then(Commands.literal("white").executes(c->{
            var r=ServerRuntime.get();ServerPlayer p=c.getSource().getPlayerOrException();JsonObject intent=new JsonObject();intent.addProperty("dev","white");r.inventory.transact(p,UUID.randomUUID().toString(),intent,()->{
                for(Slot slot:Slot.values())if(r.session(p).progress.level>=slot.minimum())r.inventory.requireSpace(p,GameItems.gear(EquipmentRules.create(slot,Weapon.KATANA,Rarity.COMMON,r.session(p).progress.level,r.random)));
            }
            ,Map.of());r.profile(p);return 1;
        }
        ));
        dev.then(Commands.literal("give").then(Commands.argument("id",StringArgumentType.word()).executes(c->{
            var r=ServerRuntime.get();ServerPlayer p=c.getSource().getPlayerOrException();String id=StringArgumentType.getString(c,"id");ItemStack item;if(Catalog.CURRENT.materials.containsKey(id))item=GameItems.material(id,64);else if(id.equals("test_gem")){
                Gem gem=new Gem();gem.level=1;gem.group="all";gem.name="开发样本 · 力量";Affix a=new Affix("test_strength",Stat.STRENGTH,5,false);a.fixed=true;gem.affixes.add(a);item=GameItems.payload("affix_gem",gem,gem.name);
            }
            else if(id.equals("epic")||id.equals("mythic"))item=GameItems.gear(EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,id.equals("epic")?Rarity.EPIC:Rarity.MYTHIC,r.session(p).progress.level,r.random));else if(GameItems.ALL.containsKey(id))item=GameItems.item(id,64);else throw new IllegalArgumentException("未知测试物品");JsonObject intent=new JsonObject();intent.addProperty("give",id);r.inventory.transact(p,UUID.randomUUID().toString(),intent,()->r.inventory.requireSpace(p,item),Map.of());return 1;
        }
        )));
        for(String mode:List.of("on","off"))dev.then(Commands.literal("nocd").then(Commands.literal(mode).executes(c->{
            var r=ServerRuntime.get();ServerPlayer p=c.getSource().getPlayerOrException();var s=r.session(p);s.resources.controlEpoch++;boolean enabled=mode.equals("on");if(enabled&&!s.resources.training)s.resources.trainingBoundary=s.resources.mp;if(!enabled&&s.resources.training&&s.resources.trainingBoundary>=0)s.resources.mp=Math.min(s.resources.mp,s.resources.trainingBoundary);s.resources.training=enabled;s.resources.mp=Math.min(s.resources.mp,r.stats(p).get(Stat.MP));r.savePlayer(p);r.profile(p);r.message(p,"开发训练模式："+mode);return 1;
        }
        )));
        root.then(Commands.literal("verify").requires(source->Boolean.getBoolean("ydnf.verify")&&source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)).executes(c->VerificationChecks.run(ServerRuntime.get(),c.getSource().getPlayerOrException()))
            .then(Commands.literal("combat").then(Commands.argument("case",StringArgumentType.word()).executes(c->{try{return CombatVerification.run(ServerRuntime.get(),c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"case"));}catch(Exception failure){throw new IllegalStateException(failure);}}))));
        root.then(dev);
        e.getDispatcher().register(root);
    }
}
