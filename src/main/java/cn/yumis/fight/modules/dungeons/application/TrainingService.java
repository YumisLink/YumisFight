package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.combat.api.CombatPorts;
import cn.yumis.fight.modules.combat.api.CombatSession;
import cn.yumis.fight.modules.combat.api.CombatTarget;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.network.Network;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.*;

/** Per-player temporary training sessions; damage is journaled in the hit's own transaction. */
public final class TrainingService implements TrainingActions.Handler,CombatPorts.Training {
    private static final long RESULT_MILLIS=60_000;
    private static final String PREFIX="training/run/";
    private final MinecraftServer server;
    private final Journal journal;
    private final Function<ServerPlayer,? extends CombatSession> sessions;
    private final Predicate<ServerPlayer> inDungeon;
    private final BiConsumer<ServerPlayer,TrainingSandbag> context;
    private final BiConsumer<ServerPlayer,String> message;
    private final Map<UUID,Slot> slots=new HashMap<>();
    private final Map<UUID,VanillaUse> pendingUses=new HashMap<>();
    private final Map<UUID,UUID> active=new HashMap<>(),shown=new HashMap<>();
    private final Map<UUID,JsonObject> sent=new HashMap<>();
    private record Slot(UUID owner,TrainingSandbag bag){}
    private record VanillaUse(String dimension,BlockPos clicked,Direction face,InteractionHand hand,long expires){}
    public static final class Run {
        public UUID id,owner;
        public String session="",dimension="";
        public long generation,created,started,duration;
        public double damage;
    }
    public TrainingService(MinecraftServer server,Journal journal,Function<ServerPlayer,? extends CombatSession> sessions,Predicate<ServerPlayer> inDungeon,BiConsumer<ServerPlayer,TrainingSandbag> context,BiConsumer<ServerPlayer,String> message){
        this.server=server;this.journal=journal;this.sessions=sessions;this.inDungeon=inDungeon;this.context=context;this.message=message;
    }
    private Run load(UUID id){return journal.get(PREFIX+id,Run.class,null);}
    private boolean permitted(ServerPlayer p){return !p.isDeadOrDying()&&!sessions.apply(p).life.dead&&!inDungeon.test(p)&&!p.level().dimension().equals(DungeonService.DIMENSION);}
    public void start(ServerPlayer p,JsonObject request)throws Exception{
        int seconds=request.get("seconds").getAsInt();String session=request.get("session").getAsString(),dimension=request.get("dimension").getAsString();
        if(seconds!=20&&seconds!=60)throw new IllegalArgumentException("沙袋测试时长无效");
        Direction face=Direction.valueOf(request.get("face").getAsString());InteractionHand hand=InteractionHand.valueOf(request.get("hand").getAsString());
        BlockPos clicked=new BlockPos(request.get("x").getAsInt(),request.get("y").getAsInt(),request.get("z").getAsInt());
        if(session.equals(sessions.apply(p).id.toString())&&dimension.equals(p.level().dimension().identifier().toString()))pendingUses.put(p.getUUID(),new VanillaUse(dimension,clicked,face,hand,System.currentTimeMillis()+1000));
        if(!permitted(p)||active.containsKey(p.getUUID())||!session.equals(sessions.apply(p).id.toString())||!dimension.equals(p.level().dimension().identifier().toString()))throw new IllegalArgumentException("当前不能开始沙袋测试");
        BlockPos pos=clicked.relative(face);
        ServerLevel level=p.level();var stack=p.getItemInHand(hand);
        if(!stack.is(DungeonContentTypes.TRAINING_SANDBAG_ITEM.get())||p.distanceToSqr(Vec3.atCenterOf(pos))>64||!level.mayInteract(p,pos)||!p.mayUseItemAt(pos,face,stack)||!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP))throw new IllegalArgumentException("沙袋放置位置或手持物品已失效");
        var bag=DungeonContentTypes.TRAINING_SANDBAG.get().create(level,EntitySpawnReason.SPAWN_ITEM_USE);if(bag==null)throw new IllegalArgumentException("无法创建计时沙袋");
        bag.setPos(Vec3.atBottomCenterOf(pos));bag.setYRot(p.getYRot()+180);bag.setYBodyRot(bag.getYRot());bag.timed(p.getStringUUID());
        if(!level.getWorldBorder().isWithinBounds(bag.getBoundingBox())||!level.noCollision(bag)||!level.getEntities(bag,bag.getBoundingBox()).isEmpty())throw new IllegalArgumentException("沙袋放置位置被占用");
        var run=new Run();run.id=bag.getUUID();run.owner=p.getUUID();run.session=session;run.dimension=dimension;run.generation=sessions.apply(p).life.generation;run.duration=seconds*1000L;run.created=System.currentTimeMillis();
        journal.put(PREFIX+run.id,run);slots.put(run.id,new Slot(run.owner,bag));active.put(run.owner,run.id);
        if(!level.addFreshEntity(bag)){cleanup(run.id);throw new IllegalArgumentException("无法放置计时沙袋");}
        shown.put(run.owner,run.id);sent.remove(run.owner);
        context.accept(p,bag);send(p);
    }
    @Override public boolean exists(TrainingSandbag bag){return slots.containsKey(bag.getUUID());}
    @Override public boolean consumeVanillaUse(ServerPlayer p,UseOnContext context){
        VanillaUse use=pendingUses.get(p.getUUID());if(use==null)return false;
        if(use.expires<System.currentTimeMillis()){pendingUses.remove(p.getUUID());return false;}
        if(use.dimension.equals(p.level().dimension().identifier().toString())&&use.clicked.equals(context.getClickedPos())&&use.face==context.getClickedFace()&&use.hand==context.getHand()){pendingUses.remove(p.getUUID());return true;}
        return false;
    }
    public boolean allowsNative(LivingEntity entity,ServerPlayer player){
        if(!(entity instanceof TrainingSandbag bag)||!bag.timed())return true;
        if(player==null||!bag.ownedBy(player)||bag.finished()||!Objects.equals(active.get(player.getUUID()),bag.getUUID()))return false;
        Run run=load(bag.getUUID());long now=System.currentTimeMillis();
        return run!=null&&run.session.equals(sessions.apply(player).id.toString())&&(run.started==0||now<run.started+run.duration);
    }
    @Override public void cancel(ServerPlayer p,TrainingSandbag bag){
        Slot slot=slots.get(bag.getUUID());if(slot==null||!slot.owner.equals(p.getUUID())||!Objects.equals(active.get(p.getUUID()),bag.getUUID())){message.accept(p,"只能取消自己的进行中测试");return;}
        try{cleanup(bag.getUUID());send(p);}catch(Exception error){message.accept(p,"取消测试失败："+error.getMessage());}
    }
    @Override public void validate(ServerPlayer p,CombatTarget target,long occurredAt){
        Slot slot=slots.get(target.id());Run run=load(target.id());long now=System.currentTimeMillis();
        if(slot==null||run==null||!run.owner.equals(p.getUUID())||!run.session.equals(sessions.apply(p).id.toString())||run.generation!=sessions.apply(p).life.generation||!run.dimension.equals(p.level().dimension().identifier().toString())||inDungeon.test(p)||occurredAt>now)throw new IllegalArgumentException("沙袋测试会话已失效");
        long event=Math.max(run.created,occurredAt);
        if(run.started>0&&(event<run.started||event>=run.started+run.duration||now>=run.started+run.duration+RESULT_MILLIS))throw new IllegalArgumentException("命中不在沙袋测试时段内");
    }
    @Override public void record(ServerPlayer p,CombatTarget target,double fraction,long occurredAt)throws Exception{
        Run run=load(target.id());if(run==null||fraction<=0)return;
        if(run.started==0)run.started=Math.max(run.created,occurredAt);
        double next=run.damage+fraction*target.maximumHealth(1,false);if(!Double.isFinite(next))throw new IllegalArgumentException("训练伤害合计超出范围");
        run.damage=next;journal.put(PREFIX+run.id,run);
    }
    public void tick(){
        long now=System.currentTimeMillis();
        for(var pair:new ArrayList<>(slots.entrySet())){
            UUID id=pair.getKey();Slot slot=pair.getValue();Run run=load(id);ServerPlayer p=server.getPlayerList().getPlayer(slot.owner);
            try{
                if(run==null){cleanup(id);continue;}
                boolean complete=run.started>0&&now>=run.started+run.duration;
                if(Objects.equals(active.get(slot.owner),id)){
                    if(p==null||!permitted(p)||!run.session.equals(sessions.apply(p).id.toString())||run.generation!=sessions.apply(p).life.generation||!run.dimension.equals(p.level().dimension().identifier().toString())||slot.bag.isRemoved()){cleanup(id);continue;}
                    if(complete){active.remove(slot.owner,id);slot.bag.finish();}
                }
                if(p!=null&&inDungeon.test(p)&&Objects.equals(shown.get(slot.owner),id)){cleanup(id);continue;}
                if(complete&&now>=run.started+run.duration+RESULT_MILLIS)cleanup(id);
            }catch(Exception error){if(p!=null)message.accept(p,"沙袋测试结束失败："+error.getMessage());}
        }
        for(ServerPlayer p:server.getPlayerList().getPlayers())send(p);
    }
    public void send(ServerPlayer p){
        JsonObject view=new JsonObject();UUID id=shown.get(p.getUUID());Run run=id==null?null:load(id);long now=System.currentTimeMillis();
        if(run!=null&&!inDungeon.test(p)&&!(run.started>0&&now>=run.started+run.duration+RESULT_MILLIS)){
            view.addProperty("id",run.id.toString());view.addProperty("name",p.getScoreboardName());view.addProperty("started",run.started);view.addProperty("duration",run.duration);view.addProperty("damage",run.damage);view.addProperty("completed",run.started>0&&now>=run.started+run.duration);
        }
        if(!view.equals(sent.get(p.getUUID()))){sent.put(p.getUUID(),view.deepCopy());Network.send(p,"trainingMeter",view);}
    }
    public void logout(ServerPlayer p){
        pendingUses.remove(p.getUUID());sent.remove(p.getUUID());UUID id=active.get(p.getUUID());if(id!=null)try{cleanup(id);}catch(Exception error){message.accept(p,"清理沙袋测试失败："+error.getMessage());}
    }
    public void recover()throws Exception{
        for(String key:journal.keys(PREFIX)){
            Run run=journal.get(key,Run.class,null);if(run==null)journal.put(key,null);else clearRun(run.id);
        }
    }
    private void cleanup(UUID id)throws Exception{
        clearRun(id);Slot slot=slots.remove(id);if(slot!=null){active.remove(slot.owner,id);shown.remove(slot.owner,id);slot.bag.discard();}
    }
    private void clearRun(UUID id)throws Exception{
        Map<String,com.google.gson.JsonElement> changes=new LinkedHashMap<>();
        changes.put(PREFIX+id,com.google.gson.JsonNull.INSTANCE);changes.put("target/"+id,com.google.gson.JsonNull.INSTANCE);changes.put("life/"+id,com.google.gson.JsonNull.INSTANCE);
        for(String key:journal.keys("ailments/"+id+"/"))changes.put(key,com.google.gson.JsonNull.INSTANCE);
        journal.transaction(changes);
    }
}
