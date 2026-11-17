package cn.yumis.fight.modules.arkguard.application;

import com.google.gson.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Consumer;
import static cn.yumis.fight.modules.arkguard.application.ArkBattle.Actor;

/** Transient scene presentation is published only after the corresponding life facts commit. */
final class ArkEffects {
    private final ArkBattle battle;
    private final Consumer<JsonObject> send;
    private final JsonArray pending=new JsonArray();
    private final Set<Actor> recoiling=new HashSet<>();

    ArkEffects(ArkBattle battle,Consumer<JsonObject> send){this.battle=battle;this.send=send;}

    static String projectile(Actor actor){
        if(actor.enemy)return actor.kind.equals("goblin_thrower")?"stone":"";
        if(actor.unit==null||actor.unit.range()<6)return "";
        return switch(actor.unit.id()){case "ark_siliu","ark_benares","ark_snake"->"arrow";default->"orb";};
    }
    static Vec3 center(Actor actor){return actor.position().add(0,actor.entity.getBbHeight()*.6,0);}
    private static double knockbackScale(Actor victim){
        if(victim.core)return 0;
        if(victim.unit!=null){
            if(victim.unit.id().equals("ark_baiyan"))return 0;
            return victim.unit.range()<6?.8:1;
        }
        return victim.kind.equals("goblin_thrower")||victim.kind.equals("goblin_shaman")?1:.8;
    }
    private JsonObject event(String kind,Actor actor,long now){
        var event=new JsonObject();event.addProperty("kind",kind);event.addProperty("at",now);
        event.addProperty("entity",actor.entity.getId());event.addProperty("target",actor.entity.getStringUUID());
        point(event,"",center(actor));pending.add(event);return event;
    }
    private static void point(JsonObject event,String prefix,Vec3 point){
        event.addProperty(prefix+"x",point.x);event.addProperty(prefix+"y",point.y);event.addProperty(prefix+"z",point.z);
    }
    void projectile(Actor source,Actor target,String kind,int element,long now,long duration,int index){
        var event=event("projectile",source,now);event.addProperty("style",kind);event.addProperty("element",element);
        event.addProperty("victim",target.entity.getId());event.addProperty("victimId",target.entity.getStringUUID());
        event.addProperty("duration",duration);event.addProperty("index",index);point(event,"end",center(target));
    }
    void cast(Actor source,int element,long now,long duration){
        var event=event("cast",source,now);event.addProperty("element",element);event.addProperty("duration",Math.max(700,duration));
        event.addProperty("chant",source.castUntil>now);
        if(source.unit!=null)battle.combat.skillState(source,event,now,false);
    }
    void hit(Actor source,Actor victim,double amount,int element,boolean critical,boolean impact,long now){
        var event=event("hit",victim,now);event.addProperty("amount",amount);event.addProperty("element",element);
        event.addProperty("critical",critical);event.addProperty("impact",impact);event.addProperty("enemy",victim.enemy);
        event.addProperty("health",victim.target.health(1)*victim.target.life.q);
        if(!impact||!recoiling.add(victim))return;
        battle.afterCommit.add(()->{
            var entity=victim.entity;
            if(entity.isRemoved())return;
            var damageSource=source==null?entity.damageSources().generic():entity.damageSources().mobAttack(source.entity);
            entity.hurtTime=entity.hurtDuration=10;battle.level.broadcastDamageEvent(entity,damageSource);
            if(source==null||victim.core||!victim.alive())return;
            Vec3 direction=victim.position().subtract(source.position());
            direction=new Vec3(direction.x,0,direction.z).normalize();
            double strength=(victim.target.bossOrElite()?.08:.2)*knockbackScale(victim);
            if(strength<=0)return;
            Vec3 destination=victim.position().add(direction.scale(strength*2));
            boolean inside=victim.transferred||victim.enemy&&victim.phase==2?
                cn.yumis.fight.modules.arkguard.api.ArkWar.coreArea(destination):victim.seat.plot().contains(net.minecraft.core.BlockPos.containing(destination));
            if(inside&&direction.lengthSqr()>0){entity.indicateDamage(-direction.x,-direction.z);entity.knockback(strength,-direction.x,-direction.z,damageSource,0f);entity.hurtMarked=true;}
        });
    }
    void publish(){
        if(!pending.isEmpty()){
            var packet=new JsonObject();packet.addProperty("round",battle.id);packet.addProperty("stage",battle.stage);
            packet.add("effects",pending.deepCopy());send.accept(packet);
        }
        clear();
    }
    void clear(){pending.asList().clear();recoiling.clear();}
}
