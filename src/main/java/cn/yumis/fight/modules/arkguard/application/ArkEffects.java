package cn.yumis.fight.modules.arkguard.application;

import cn.yumis.fight.modules.arkguard.api.ArkWar;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Consumer;
import static cn.yumis.fight.modules.arkguard.application.ArkBattle.Actor;

/** Transient scene presentation is published only after the corresponding life facts commit. */
final class ArkEffects {
    private record Heal(double amount,long at){}
    private final ArkBattle battle;
    private final Consumer<JsonObject> send;
    private final JsonArray pending=new JsonArray();
    private final Set<Actor> recoiling=new HashSet<>();
    private final Map<Actor,Heal> healed=new LinkedHashMap<>();
    private final Set<Actor> burstCenters=new HashSet<>();

    ArkEffects(ArkBattle battle,Consumer<JsonObject> send){this.battle=battle;this.send=send;}

    static String projectile(Actor actor){
        if(actor.enemy)return actor.kind.equals("goblin_thrower")?"stone":"";
        if(actor.unit==null||actor.unit.range()<6)return "";
        return switch(actor.unit.id()){case "ark_siliu","ark_benares","ark_snake","ark_josephine","ark_karl_phantom"->"arrow";default->"orb";};
    }
    static Vec3 center(Actor actor){return actor.position().add(0,actor.entity.getBbHeight()*.6,0);}
    private static double knockbackScale(Actor victim){
        if(victim.core)return 0;
        if(victim.unit!=null){
            if(victim.entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)>=1)return 0;
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
    void projectile(Actor source,Actor target,Vec3 origin,String kind,int element,long now,long duration,int index){
        var event=event("projectile",source,now);event.addProperty("style",kind);event.addProperty("element",element);
        point(event,"",origin);
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
        event.addProperty("sourceEnemy",source!=null&&source.enemy);
        event.addProperty("health",victim.target.health(1)*victim.target.life.q);
        if(!impact||!recoiling.add(victim))return;
        battle.afterCommit.add(()->{
            var entity=victim.entity;
            if(entity.isRemoved())return;
            var damageSource=source==null?entity.damageSources().generic():entity.damageSources().mobAttack(source.entity);
            entity.hurtTime=entity.hurtDuration=10;battle.level.broadcastDamageEvent(entity,damageSource);
            if(source!=null&&!victim.core&&victim.alive())applyKnockback(source,victim,damageSource);
        });
    }
    void heal(Actor target,double amount,long now){
        healed.merge(target,new Heal(amount,now),(previous,next)->new Heal(previous.amount()+next.amount(),next.at()));
    }
    void sanctuary(Actor center,long now){
        if(!burstCenters.add(center))return;
        var event=event("sanctuary",center,now);
        event.addProperty("element",-1);
        event.addProperty("duration",450);
        event.addProperty("radius",ArkHealing.SANCTUARY_RADIUS);
    }
    private static void applyKnockback(Actor source,Actor victim,DamageSource damageSource){
        var entity=victim.entity;
        Vec3 position=victim.position(),offset=position.subtract(source.position());
        Vec3 direction=new Vec3(offset.x,0,offset.z).normalize();
        if(direction.lengthSqr()==0)return;

        var plot=victim.seat.plot();
        boolean coreField=victim.transferred||victim.enemy&&victim.phase==2;
        boolean outerField=!coreField&&(!victim.enemy||victim.phase==0);
        double strength=(victim.target.bossOrElite()?.08:.2)*knockbackScale(victim);
        if(outerField)strength*=plot.battlefieldKnockbackScale(position);
        if(strength<=0)return;
        Vec3 destination=position.add(direction.scale(strength*2));
        boolean inside=coreField?ArkWar.coreArea(destination):plot.contains(BlockPos.containing(destination));
        if(!inside)return;

        entity.indicateDamage(-direction.x,-direction.z);
        entity.knockback(strength,-direction.x,-direction.z,damageSource,0f);
        if(outerField)entity.setDeltaMovement(plot.limitBattlefieldVelocity(position,entity.getDeltaMovement(),entity.getBbWidth()*.5));
        entity.hurtMarked=true;
    }
    void publish(){
        for(var entry:healed.entrySet()){
            var target=entry.getKey();
            var heal=entry.getValue();
            var event=event("heal",target,heal.at());
            point(event,"",target.position().add(0,target.entity.getBbHeight()+.6,0));
            event.addProperty("element",-1);
            event.addProperty("duration",750);
            event.addProperty("amount",heal.amount());
            event.addProperty("health",target.target.health(1)*target.target.life.q);
        }
        if(!pending.isEmpty()){
            var packet=new JsonObject();packet.addProperty("round",battle.id);packet.addProperty("stage",battle.stage);
            packet.add("effects",pending.deepCopy());send.accept(packet);
        }
        clear();
    }
    void clear(){pending.asList().clear();recoiling.clear();healed.clear();burstCenters.clear();}
}
