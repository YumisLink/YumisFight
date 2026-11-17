package cn.yumis.fight.modules.arkguard.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.arkguard.api.*;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.mapgeneration.api.MapGeneration;
import cn.yumis.fight.modules.npc.api.NpcInteractionComponent;
import com.google.gson.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.effect.MobEffectInstance;
import java.util.*;
import java.util.function.*;

/** Receives owner decisions and maintains the shared world/round lifecycle. */
public final class ArkGuardService {
    private static final Gson JSON=new Gson();
    private static final String RETURNS="arkWar/return/";
    private final MinecraftServer server;
    private final Journal journal;
    private final WorldTargets lives;
    private final Function<ServerPlayer,String> session;
    private final BiConsumer<ServerPlayer,JsonObject> send;
    private final BiConsumer<ServerPlayer,String> message;
    private final Set<UUID> waiting=new LinkedHashSet<>(),registered=new LinkedHashSet<>(),confirmed=new LinkedHashSet<>(),absent=new HashSet<>(),protectedPlayers=new HashSet<>();
    private final Map<UUID,ArkRoster> plans=new HashMap<>();
    private final Map<UUID,JsonObject> sent=new HashMap<>();
    private final ParticipationBoard board=new ParticipationBoard();
    private String lobby=UUID.randomUUID().toString(),lastResult="";
    private boolean prepared;
    private boolean chunksRecovered;
    private long ticks;
    private ArkBattle battle;
    private ArkScene scene;
    private ArkRules lobbyRules=ArkRules.current();

    public ArkGuardService(MinecraftServer server,Journal journal,Function<ServerPlayer,String> session,
                           BiConsumer<ServerPlayer,JsonObject> send,BiConsumer<ServerPlayer,String> message,WorldTargets lives){
        this.server=server;this.journal=journal;this.session=session;this.send=send;this.message=message;this.lives=lives;
        NpcInteractionComponent.unregister("ark_war");
        NpcInteractionComponent.register("ark_war",(player,host,action)->{
            if(player.hasDisconnected()||server.getPlayerList().getPlayer(player.getUUID())!=player||!inside(player)
                ||!ArkWar.contains(host.level(),host.blockPosition())||!NpcInteractionComponent.id(host).equals("ark_war_guide"))return;
            try{
                switch(action.value()){
                    case "join"->choose(player,true);
                    case "withdraw"->choose(player,false);
                    case "cancel_start"->confirm(player,false,null);
                    case "confirm_start"->message.accept(player,"请使用本机交互确认开始，以提交个人商店初始状态。");
                    case "leave"->leave(player);
                    default->throw new IllegalArgumentException("未知的方舟选项");
                }
            }catch(Exception failure){message.accept(player,failure.getMessage());}
        });
    }
    public void request(ServerPlayer player,JsonObject request)throws Exception{
        if(!request.has("session")||!session.apply(player).equals(request.get("session").getAsString()))return;
        absent.remove(player.getUUID());
        switch(request.get("kind").getAsString()){
            case "status"->{sent.remove(player.getUUID());publish(player);}
            case "enter"->{if(inside(player)){publish(player);return;}waiting.add(player.getUUID());publish(player);}
            case "join"->choose(player,true);
            case "withdraw"->choose(player,false);
            case "confirm_start"->{requireLobby(request);confirm(player,true,readRoster(player,request.getAsJsonObject("roster"),lobby,1));}
            case "cancel_start"->{requireLobby(request);confirm(player,false,null);}
            case "owner_facts"->ownerFacts(player,request);
            case "ready"->ready(player,request);
            case "teleport"->teleport(player,request);
            case "leave"->leave(player);
            default->throw new IllegalArgumentException("未知的方舟请求");
        }
    }
    private static boolean inside(ServerPlayer player){return ArkWar.contains(player.level(),player.blockPosition());}
    private ServerLevel level(){var level=server.getLevel(ArkWar.DIMENSION);if(level==null)throw new IllegalStateException("方舟场景维度尚未加载");return level;}
    private Set<UUID> present(){
        var present=new LinkedHashSet<UUID>();
        for(var p:server.getPlayerList().getPlayers())if(!p.hasDisconnected()&&!absent.contains(p.getUUID())&&inside(p))present.add(p.getUUID());
        return present;
    }
    public void tick(){
        ticks++;long now=System.currentTimeMillis();var present=present();
        if(battle==null&&lobbyRules!=ArkRules.current()){
            lobbyRules=ArkRules.current();lobby=UUID.randomUUID().toString();confirmed.clear();plans.clear();sent.clear();
            for(UUID id:registered){var player=server.getPlayerList().getPlayer(id);if(player!=null)message.accept(player,"方舟定义已更新，请重新确认开始。");}
        }
        if(!chunksRecovered&&server.getLevel(ArkWar.DIMENSION)!=null){ArkBattle.recoverChunks(level(),journal);chunksRecovered=true;}
        if(ticks%10==0){
            waiting.removeIf(id->server.getPlayerList().getPlayer(id)==null);
            if(!prepared&&(!waiting.isEmpty()||!present.isEmpty())){
                try{prepared=MapGeneration.prepare(level(),ArkWar.BLUEPRINT,ArkWar.ORIGIN);}
                catch(Exception failure){for(UUID id:List.copyOf(waiting)){var p=server.getPlayerList().getPlayer(id);if(p!=null)message.accept(p,"方舟地图未完成："+failure.getMessage());}waiting.clear();return;}
            }
            if(prepared){
                if(scene==null)scene=new ArkScene(level());
                for(UUID id:List.copyOf(waiting)){waiting.remove(id);var p=server.getPlayerList().getPlayer(id);if(p!=null)try{enter(p);}catch(Exception failure){message.accept(p,failure.getMessage());}}
                present=present();
            }
            for(var p:server.getPlayerList().getPlayers()){
                if(present.contains(p.getUUID()))protect(p);
                else if(protectedPlayers.remove(p.getUUID()))p.removeEffect(ArkContentTypes.OBSERVER);
            }
            if(battle==null){registered.retainAll(present);confirmed.retainAll(registered);plans.keySet().retainAll(registered);}
        }
        if(battle!=null){
            try{battle.tick(now,present);if(battle.ended)endBattle();}
            catch(Exception failure){System.getLogger("ArkWar").log(System.Logger.Level.ERROR,"方舟运行失败",failure);endBattle("方舟运行中断："+failure.getMessage());}
        }
        if(ticks%10==0){
            if(prepared){scene.update(battle,present,now);refreshBoard();}
            for(var p:server.getPlayerList().getPlayers())if(present.contains(p.getUUID())||sent.containsKey(p.getUUID())||waiting.contains(p.getUUID()))publish(p);
        }
    }
    private void protect(ServerPlayer player){player.addEffect(new MobEffectInstance(ArkContentTypes.OBSERVER,60,0,true,false,true));protectedPlayers.add(player.getUUID());}
    private void enter(ServerPlayer player)throws Exception{
        if(!inside(player)){
            var point=new ReturnPoint();point.dimension=player.level().dimension().identifier().toString();point.x=player.getX();point.y=player.getY();point.z=player.getZ();point.yaw=player.getYRot();point.pitch=player.getXRot();
            journal.put(RETURNS+player.getUUID(),point);var entry=ArkWar.entrance();var pos=entry.pos();
            if(!player.teleportTo(level(),pos.getX()+.5,pos.getY(),pos.getZ()+.5,Set.of(),entry.yaw(),0,true))throw new IllegalStateException("进入方舟失败");
        }
        protect(player);publish(player);message.accept(player,battle!=null&&battle.seat(player.getUUID())!=null?"已返回本局，阵容与方舟币已保留。":"已进入方舟战争，请在水晶旁报名；本局开始后，新来访者只能观战。");
    }
    private void choose(ServerPlayer player,boolean participate){
        if(!inside(player))throw new IllegalStateException("请先进入方舟战争");
        if(battle!=null){if(participate&&battle.seat(player.getUUID())!=null){publish(player);return;}throw new IllegalStateException("本局名单已锁定，可观战或离开地图");}
        if(participate){if(registered.size()>=4&&!registered.contains(player.getUUID()))throw new IllegalStateException("四处阵地已满，本局可观战");registered.add(player.getUUID());}
        else{registered.remove(player.getUUID());confirmed.remove(player.getUUID());plans.remove(player.getUUID());}
        publishAll();refreshBoard();
    }
    private void requireLobby(JsonObject request){if(battle!=null||!request.has("round")||!lobby.equals(request.get("round").getAsString()))throw new IllegalStateException("报名阶段已改变");}
    private void confirm(ServerPlayer player,boolean value,ArkRoster plan){
        if(battle!=null||!inside(player)||!registered.contains(player.getUUID()))throw new IllegalStateException("请先在水晶旁报名");
        if(value){
            if(plan==null)throw new IllegalArgumentException("缺少本人商店初始事实");
            plans.put(player.getUUID(),plan);confirmed.add(player.getUUID());persistOwner(lobby,player.getUUID(),plan);
        }else confirmed.remove(player.getUUID());
        var present=present();registered.retainAll(present);confirmed.retainAll(registered);
        if(!registered.isEmpty()&&confirmed.containsAll(registered))begin();
        publishAll();refreshBoard();
    }
    private void begin(){
        var seats=new ArrayList<ArkBattle.Seat>();int index=0;
        for(UUID id:registered){var p=server.getPlayerList().getPlayer(id);if(p==null||!inside(p)||!confirmed.contains(id))throw new IllegalStateException("报名名单已变化");seats.add(new ArkBattle.Seat(id,p.getScoreboardName(),index++,plans.get(id)));}
        battle=new ArkBattle(lobby,level(),journal,lives,seats,lobbyRules,System.currentTimeMillis(),this::effects);
        for(var seat:seats){var p=server.getPlayerList().getPlayer(seat.owner);var position=seat.plot().shop();if(p!=null)p.teleportTo(level(),position.x,position.y,position.z,Set.of(),seat.plot().facing(),0,true);}
        confirmed.clear();if(scene!=null)scene.update(battle,present(),System.currentTimeMillis());
    }
    private void ownerFacts(ServerPlayer player,JsonObject request){
        var seat=requirePhase(player,request,false);ArkRoster roster=readRoster(player,request.getAsJsonObject("roster"),battle.id,battle.wave);
        if(roster.revision<seat.roster.revision){publish(player);return;}
        if(roster.revision==seat.roster.revision){if(!JSON.toJsonTree(roster).equals(JSON.toJsonTree(seat.roster)))throw new IllegalArgumentException("个人事实版本与原内容冲突");publish(player);return;}
        if(roster.revision!=seat.roster.revision+1)throw new IllegalArgumentException("个人事实序列不连续，请重新同步");
        persistOwner(battle.id,seat.owner,roster);battle.accept(seat,roster);publish(player);
    }
    private ArkBattle.Seat requirePhase(ServerPlayer player,JsonObject request,boolean preparation){
        if(battle==null||!inside(player)||!request.has("round")||!battle.id.equals(request.get("round").getAsString())
            ||!request.has("stage")||!battle.stage.equals(request.get("stage").getAsString())||preparation&&!battle.editing())throw new IllegalStateException("场次或准备阶段已改变，请等待同步");
        var seat=battle.seat(player.getUUID());if(seat==null)throw new IllegalStateException("本局为观战状态");return seat;
    }
    private void ready(ServerPlayer player,JsonObject request){var seat=requirePhase(player,request,true);battle.ready(seat,request.get("value").getAsBoolean());publishAll();}
    private void teleport(ServerPlayer player,JsonObject request){
        if(!inside(player))return;
        double x=request.get("x").getAsDouble(),y=request.get("y").getAsDouble(),z=request.get("z").getAsDouble();
        float yaw=request.get("yaw").getAsFloat(),pitch=request.get("pitch").getAsFloat();
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(yaw)||!Float.isFinite(pitch))throw new IllegalArgumentException("传送位置格式无效");
        if(!player.teleportTo(level(),x,y,z,Set.of(),yaw,pitch,true))throw new IllegalStateException("方舟传送失败");
    }
    /** Identity/schema checks only: prices, draws, stock, merges and placements are never replayed on the server. */
    private ArkRoster readRoster(ServerPlayer player,JsonObject value,String round,int wave){
        if(value==null)throw new IllegalArgumentException("缺少个人方舟事实");ArkRoster r=JSON.fromJson(value,ArkRoster.class);
        if(r==null||!player.getStringUUID().equals(r.owner)||!round.equals(r.round)||r.wave!=wave||r.revision<0||r.level<1||r.level>7
            ||r.coins<0||r.cards==null||r.shop==null||r.pool==null||r.future==null||r.future.size()!=20-wave
            ||!cn.yumis.fight.modules.economy.api.CurrencyRules.ARK_COIN.id().equals(r.currency))throw new IllegalArgumentException("个人方舟事实格式无效");
        ArkRules rules=battle==null?lobbyRules:battle.rules;Set<String> tokens=new HashSet<>();
        if(r.shop.size()!=rules.shopSlots(r.level))throw new IllegalArgumentException("商店栏位格式无效");
        for(var card:r.cards){if(card==null||!tokens.add(card.token)||card.star<1||card.star>3)throw new IllegalArgumentException("卡牌身份格式无效");UUID.fromString(card.token);rules.unit(card.unit);}
        for(String id:r.shop)if(!id.isBlank())rules.unit(id);
        Set<Integer> waves=new HashSet<>();
        for(var frame:r.future){if(frame==null||frame.wave<=wave||frame.wave>20||!waves.add(frame.wave)||frame.coins<0||frame.pool==null||frame.shop==null||frame.shop.size()!=rules.shopSlots(r.level))throw new IllegalArgumentException("后续事实格式无效");for(String id:frame.shop)if(!id.isBlank())rules.unit(id);}
        return r;
    }
    private void persistOwner(String round,UUID owner,ArkRoster roster){try{journal.put("arkWar/owners/"+round+"/"+owner,roster);}catch(Exception failure){throw new IllegalStateException("个人方舟事实保存失败",failure);}}
    private void leave(ServerPlayer player)throws Exception{
        waiting.remove(player.getUUID());var point=journal.get(RETURNS+player.getUUID(),ReturnPoint.class,null);
        if(inside(player)||point!=null){
            ServerLevel target=point==null?server.overworld():server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(point.dimension)));if(target==null)target=server.overworld();
            var spawn=target.getRespawnData().pos();double x=point==null?spawn.getX()+.5:point.x,y=point==null?spawn.getY():point.y,z=point==null?spawn.getZ()+.5:point.z;
            if(!player.teleportTo(target,x,Math.max(target.getMinY()+1,y),z,Set.of(),point==null?0:point.yaw,point==null?0:point.pitch,true))throw new IllegalStateException("离开方舟失败");
            journal.put(RETURNS+player.getUUID(),JsonNull.INSTANCE);
        }
        player.removeEffect(ArkContentTypes.OBSERVER);protectedPlayers.remove(player.getUUID());if(battle==null){registered.remove(player.getUUID());confirmed.remove(player.getUUID());plans.remove(player.getUUID());}
        if(battle!=null&&battle.presentCount(present())==0)endBattle("所有参战者已离场，本局结束");publishAll();refreshBoard();
    }
    private void publishAll(){for(var p:server.getPlayerList().getPlayers())if(inside(p)||sent.containsKey(p.getUUID()))publish(p);}
    private void effects(JsonObject packet){
        for(var player:server.getPlayerList().getPlayers())if(inside(player)&&!player.hasDisconnected()){
            var personal=packet.deepCopy();personal.addProperty("session",session.apply(player));send.accept(player,personal);
        }
    }
    private void publish(ServerPlayer player){
        var state=new JsonObject();state.addProperty("session",session.apply(player));state.addProperty("inside",inside(player));state.addProperty("returnAvailable",!journal.get(RETURNS+player.getUUID()).isJsonNull());
        state.addProperty("waiting",waiting.contains(player.getUUID()));state.addProperty("participating",battle==null?registered.contains(player.getUUID()):battle.seat(player.getUUID())!=null);
        state.addProperty("lastResult",lastResult);var war=battle==null?new JsonObject():battle.snapshot(battle.seat(player.getUUID()),present());
        if(battle==null){war.addProperty("round",lobby);war.addProperty("phase","LOBBY");war.addProperty("seat",-1);war.addProperty("registered",registered.size());war.addProperty("confirmed",confirmed.size());war.addProperty("personalConfirmed",confirmed.contains(player.getUUID()));war.add("rules",JSON.toJsonTree(lobbyRules));if(plans.containsKey(player.getUUID()))war.add("roster",JSON.toJsonTree(plans.get(player.getUUID())));}
        var previous=sent.get(player.getUUID());
        if(previous!=null&&previous.has("war")&&previous.getAsJsonObject("war").get("round").equals(war.get("round")))war.remove("rules");
        state.add("war",war);state.addProperty("serverNow",System.currentTimeMillis());
        if(!state.equals(sent.get(player.getUUID()))){send.accept(player,state);sent.put(player.getUUID(),state.deepCopy());}
    }
    private void refreshBoard(){
        if(!prepared)return;var rows=new ArrayList<Component>();
        if(battle==null){
            for(UUID id:registered){var p=server.getPlayerList().getPlayer(id);if(p!=null&&inside(p))rows.add(Component.literal(p.getScoreboardName()+" · "+(confirmed.contains(id)?"已确认":"未确认")).withColor(confirmed.contains(id)?0x80D8FF:0xA7B1B9));}
            board.update(level(),rows,confirmed.size(),registered.size(),"开始确认","全员确认后进入商店准备");
        }else{
            var present=present();
            for(var seat:battle.seats)rows.add(Component.literal(seat.name+" · "+(present.contains(seat.owner)?"参战":"暂离")).withColor(present.contains(seat.owner)?0x80D8FF:0xA7B1B9));
            board.update(level(),rows,battle.readyCount(present),battle.presentCount(present),"已准备","第"+battle.wave+"波 · "+(battle.editing()?"准备":"战斗"));
        }
    }
    private void endBattle(){endBattle(battle==null?"":battle.result);}
    private void endBattle(String reason){
        if(battle==null)return;var ending=battle;battle=null;lastResult=reason;
        try{ending.close();}finally{
            registered.clear();confirmed.clear();plans.clear();lobby=UUID.randomUUID().toString();lobbyRules=ArkRules.current();
            for(var seat:ending.seats){var p=server.getPlayerList().getPlayer(seat.owner);if(p!=null){message.accept(p,reason);if(inside(p)){var entry=ArkWar.entrance();p.teleportTo(level(),entry.pos().getX()+.5,entry.pos().getY(),entry.pos().getZ()+.5,Set.of(),entry.yaw(),0,true);}}}
            publishAll();
        }
    }
    public void logout(ServerPlayer player){
        waiting.remove(player.getUUID());sent.remove(player.getUUID());absent.add(player.getUUID());player.removeEffect(ArkContentTypes.OBSERVER);protectedPlayers.remove(player.getUUID());
        if(battle==null){registered.remove(player.getUUID());confirmed.remove(player.getUUID());plans.remove(player.getUUID());}
        else if(battle.presentCount(present())==0)endBattle("所有参战者已离线或离场，本局结束");
    }
    public void close(){
        if(battle!=null){battle.close();battle=null;}if(scene!=null){scene.close();scene=null;}board.clear();
        for(UUID id:protectedPlayers){var player=server.getPlayerList().getPlayer(id);if(player!=null)player.removeEffect(ArkContentTypes.OBSERVER);}
        waiting.clear();registered.clear();confirmed.clear();plans.clear();sent.clear();protectedPlayers.clear();NpcInteractionComponent.unregister("ark_war");
    }
}
