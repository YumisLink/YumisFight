package cn.yumis.fight.modules.skills.application;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.modules.combat.api.*;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.network.Network;
import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.function.*;

/** Receives owner-authored career snapshots and projects them to nearby viewers. */
public final class CareerSkillService implements JobTransitions,CombatPorts.SkillEffects {
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    private static <T>T copy(T value,Class<T> type){return JSON.fromJson(JSON.toJson(value),type);}
    private final Journal journal;private final MinecraftServer server;
    private final Function<ServerPlayer,CombatSession> sessions;
    private final Function<ServerPlayer,Progress> progress;
    private final BiPredicate<ServerPlayer,UUID> friendly;
    private final Map<UUID,CareerState> states=new HashMap<>();
    private final Map<UUID,JsonElement> sent=new HashMap<>();
    public CareerSkillService(Journal journal,MinecraftServer server,Function<ServerPlayer,CombatSession> sessions,Function<ServerPlayer,Progress> progress,BiPredicate<ServerPlayer,UUID> friendly){this.journal=journal;this.server=server;this.sessions=sessions;this.progress=progress;this.friendly=friendly;}
    public void register(CommandRouter<ServerPlayer,JsonObject> router){router.register("skills","careerSnapshot",this::receive);
        router.register("skills","careerMotion",(p,j)->{if(!identity(p,j))return;for(var raw:j.getAsJsonArray("moves")){var move=raw.getAsJsonObject();var e=p.level().getEntity(UUID.fromString(move.get("target").getAsString()));if(!(e instanceof net.minecraft.world.entity.LivingEntity target)||target instanceof net.minecraft.world.entity.player.Player||boss(target)||target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE)>=1)continue;var to=new net.minecraft.world.phys.Vec3(move.get("x").getAsDouble(),move.get("y").getAsDouble(),move.get("z").getAsDouble());target.move(net.minecraft.world.entity.MoverType.SELF,to.subtract(target.position()));target.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);target.hurtMarked=true;}});
        router.register("skills","careerBlind",(p,j)->{if(!identity(p,j))return;for(var id:j.getAsJsonArray("targets")){var e=p.level().getEntity(UUID.fromString(id.getAsString()));if(e instanceof net.minecraft.world.entity.LivingEntity target&&!(target instanceof net.minecraft.world.entity.player.Player))target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS,20,0,false,true));}});router.register("skills","careerState",(p,j)->{sent.remove(p.getUUID());sync(p);});}
    private boolean identity(ServerPlayer p,JsonObject j){var session=sessions.apply(p);return session.id.toString().equals(j.get("session").getAsString())&&session.life.generation==j.get("generation").getAsLong();}
    public boolean steadfast(ServerPlayer p){return !sessions.apply(p).life.dead&&state(p).steadfast;}
    public boolean invulnerable(ServerPlayer p){return !sessions.apply(p).life.dead&&state(p).invulnerable;}
    private boolean boss(net.minecraft.world.entity.LivingEntity target){if(target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon||target instanceof net.minecraft.world.entity.boss.wither.WitherBoss)return true;String data=target.getData(CombatContentTypes.TARGET);return !data.isBlank()&&JsonParser.parseString(data).getAsJsonObject().get("kind").getAsString().equals("BOSS");}
    @Override public CombatPorts.Commit prepare(ServerPlayer p,net.minecraft.world.entity.LivingEntity target,JsonObject report,Receipt receipt,Life actor){
        if(target==p||!receipt.accepted()||receipt.actual()<=0)return CombatPorts.Commit.NONE;
        return new CombatPorts.Commit(){public void publish(){if(report.has("careerControl")&&!boss(target)){double seconds=report.get("careerControl").getAsDouble();if(!Double.isFinite(seconds)||seconds<=0)return;String data=target.getData(CombatContentTypes.TARGET);if(!data.isBlank()&&JsonParser.parseString(data).getAsJsonObject().get("kind").getAsString().equals("ELITE"))seconds*=.5;target.addEffect(new net.minecraft.world.effect.MobEffectInstance(CombatContentTypes.STUN,(int)Math.max(1,Math.ceil(seconds*20))));}if(report.has("careerSkill")&&report.get("careerSkill").getAsString().equals(CareerSkills.HAND)&&!boss(target)){var delta=p.position().add(p.getLookAngle().multiply(1,0,1)).subtract(target.position());target.move(net.minecraft.world.entity.MoverType.SELF,delta.normalize().scale(Math.min(2,delta.length())));}}};
    }
    private CareerState state(ServerPlayer p){
        var session=sessions.apply(p);String dimension=p.level().dimension().identifier().toString();var value=states.get(p.getUUID());
        if(value==null||!value.session.equals(session.id.toString())||value.generation!=session.life.generation||!value.job.equals(progress.apply(p).jobId())||!value.dimension.equals(dimension)||!value.instance.equals(session.life.instance)){
            value=new CareerState();value.session=session.id.toString();value.generation=session.life.generation;value.job=progress.apply(p).jobId();value.dimension=dimension;value.instance=session.life.instance;states.put(p.getUUID(),value);
        }return value;
    }
    private void receive(ServerPlayer p,JsonObject packet){
        var session=sessions.apply(p);if(!packet.has("session")||!packet.get("session").getAsString().equals(session.id.toString())||packet.get("generation").getAsLong()!=session.life.generation)return;
        var next=JSON.fromJson(packet.get("state"),CareerState.class);var previous=state(p);
        if(!next.session.equals(previous.session)||next.generation!=previous.generation||!next.job.equals(previous.job)||!next.dimension.equals(previous.dimension)||!next.instance.equals(previous.instance)||next.sequence<=previous.sequence)return;
        next.fields.clear();next.actors.removeIf(actor->!actor.owner.equals(p.getStringUUID()));
        try{journal.put("clientCareer/"+p.getUUID()+"/"+session.id+"/"+session.life.generation,next);}catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        states.put(p.getUUID(),next);for(var viewer:server.getPlayerList().getPlayers())sync(viewer);
    }
    public CareerState snapshot(ServerPlayer p){
        var view=copy(state(p),CareerState.class);view.fields.clear();view.actors.clear();long now=System.currentTimeMillis();
        for(var entry:states.entrySet()){
            var other=entry.getValue();var owner=server.getPlayerList().getPlayer(entry.getKey());if(owner==null||sessions.apply(owner).life.dead||other.generation!=sessions.apply(owner).life.generation)continue;
            if(owner.level()==p.level()&&owner.distanceToSqr(p)<=4096)other.actors.stream().filter(actor->actor.owner.equals(owner.getStringUUID())).forEach(actor->view.actors.add(copy(actor,CareerState.Actor.class)));
            var field=other.field;if(field!=null&&field.expires>now&&field.dimension.equals(view.dimension)&&field.instance.equals(view.instance)){var projected=copy(field,CareerState.Field.class);projected.friendly=entry.getKey().equals(p.getUUID())||friendly.test(p,entry.getKey());view.fields.add(projected);}
        }return view;
    }
    public void sync(ServerPlayer p){var data=JSON.toJsonTree(snapshot(p));if(!data.equals(sent.get(p.getUUID()))){sent.put(p.getUUID(),data);Network.send(p,"careerState",data);}}
    public void clear(ServerPlayer p){states.remove(p.getUUID());sent.remove(p.getUUID());}
    public void tick(){states.keySet().removeIf(id->server.getPlayerList().getPlayer(id)==null);for(var p:server.getPlayerList().getPlayers())sync(p);}
    @Override public Change prepare(ServerPlayer p,Progress next,String destination){return clear(p,next);}
    @Override public Change clear(ServerPlayer p,Progress next){
        var session=sessions.apply(p);var resources=copy(session.resources,cn.yumis.fight.modules.combat.api.Runtime.class);resources.controlEpoch++;
        return new Change(Map.of("resources/"+p.getUUID(),JSON.toJsonTree(resources)),()->{session.resources=resources;clear(p);sync(p);});
    }
    @Override public Change switchProfile(ServerPlayer p,Progress next){return clear(p,next);}
    @Override public Change reduceSkill(ServerPlayer p,Progress next,String id){
        return new Change(Map.of(),()->{var session=sessions.apply(p);var event=new JsonObject();event.addProperty("session",session.id.toString());event.addProperty("generation",session.life.generation);event.addProperty("skill",id);Network.send(p,"skillReduced",event);});
    }
}
