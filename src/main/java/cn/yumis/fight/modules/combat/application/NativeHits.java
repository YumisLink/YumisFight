package cn.yumis.fight.modules.combat.application;
import cn.yumis.fight.modules.combat.api.CombatTarget;
import cn.yumis.fight.modules.combat.api.CombatSession;

import cn.yumis.fight.modules.combat.api.NativeEffects;
import cn.yumis.fight.modules.combat.api.*;
import cn.yumis.fight.foundation.Journal;
import java.util.function.*;

import cn.yumis.fight.network.Network;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import java.util.*;

/** Vanilla owns collision/feedback; the existing RPG report is the only health writer. */
public class NativeHits implements cn.yumis.fight.modules.combat.api.CombatPorts.NativeHits {
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();

    @Override public void suppressProjectionFeedback(boolean enabled){suppressProjectionFeedback=enabled;}
    private record Hit(UUID player,String session,long sourceGeneration,long expires,DamageSource source,NativeEffects.Scope effects) {}
    private final Map<String,Hit> hits=new HashMap<>();
    private final VanillaAttackClock vanillaAttacks=new VanillaAttackClock();
    private final Journal journal;
    private final Function<ServerPlayer,CombatSession> sessions;
    private final Function<LivingEntity,CombatTarget> targets;
    private final Predicate<LivingEntity> managed;
    private final BooleanSupplier bridge;
    private final BiFunction<ServerPlayer,ItemStack,com.google.gson.JsonElement> encode;
    private BiPredicate<LivingEntity,ServerPlayer> targetAccess=(target,player)->true;
    private BiConsumer<LivingEntity,Double> worldDamage=(entity,points)->{};
    private final Map<UUID,Long> launchSent=new LinkedHashMap<>();
    public long forwarded,committed;
    public boolean suppressProjectionFeedback;
    public NativeHits(Journal journal,Function<ServerPlayer,CombatSession> sessions,Function<LivingEntity,CombatTarget> targets,Predicate<LivingEntity> managed,BooleanSupplier bridge,BiFunction<ServerPlayer,ItemStack,com.google.gson.JsonElement> encode){this.journal=journal;this.sessions=sessions;this.targets=targets;this.managed=managed;this.bridge=bridge;this.encode=encode;}
    public void targetAccess(BiPredicate<LivingEntity,ServerPlayer> access){targetAccess=Objects.requireNonNull(access);}
    public void worldDamage(BiConsumer<LivingEntity,Double> receiver){worldDamage=Objects.requireNonNull(receiver);}
    public void launched(Projectile projectile){
        if(!(projectile instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow)||!(arrow.getOwner() instanceof ServerPlayer owner)||launchSent.containsKey(arrow.getUUID()))return;
        long now=System.currentTimeMillis();launchSent.values().removeIf(expires->expires<now);launchSent.put(arrow.getUUID(),now+300000);while(launchSent.size()>4096)launchSent.remove(launchSent.keySet().iterator().next());
        ItemStack weapon=arrow.getWeaponItem();if(weapon==null||weapon.isEmpty())weapon=owner.getMainHandItem();var session=sessions.apply(owner);
        JsonObject message=new JsonObject();message.addProperty("id",arrow.getStringUUID());message.addProperty("session",session.id.toString());message.addProperty("generation",session.life.generation);message.addProperty("dimension",owner.level().dimension().identifier().toString());message.addProperty("at",now);message.addProperty("item",cn.yumis.fight.platform.ItemData.id(weapon));message.add("weapon",encode.apply(owner,weapon));Network.send(owner,"nativeLaunch",message);
    }
    public void projectileLeft(UUID projectile){launchSent.remove(projectile);}
    public boolean handles(LivingEntity entity){return !bridge.getAsBoolean()&&managed.test(entity)&&!entity.level().isClientSide();}
    public void incoming(LivingIncomingDamageEvent event){
        if(bridge.getAsBoolean()&&suppressProjectionFeedback){event.getContainer().setShouldCauseSideEffects(false);return;}
        var victim=event.getEntity();
        if(!handles(victim))return;
        NativeEffects.attempted(victim);
        var source=event.getSource();
        var attacker=source.getEntity();
        if(victim instanceof WorldVitalEntity world){
            if(world.rejectsNativeDamage(source)){event.getContainer().setShouldCauseSideEffects(false);event.setCanceled(true);}
            return;
        }
        if(!targetAccess.test(victim,attacker instanceof ServerPlayer player?player:null)){event.getContainer().setShouldCauseSideEffects(false);event.setCanceled(true);return;}
        boolean reject;
        if(victim instanceof ServerPlayer player)reject=attacker instanceof Player&&attacker!=player;
        else reject=!(attacker instanceof ServerPlayer);
        if(reject){event.setCanceled(true);return;}
        if(victim instanceof ServerPlayer player&&attacker instanceof LivingEntity&&!(attacker instanceof Player)
            &&net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType()).getNamespace().equals("minecraft")
            &&!source.is(DamageTypes.THORNS)&&!Set.of("poison","wither","onFire","inFire","lava").contains(source.getMsgId())){
            long now=System.currentTimeMillis();var session=sessions.apply(player);
            if(!vanillaAttacks.accept(attacker.getUUID(),player.getUUID(),session.id.toString(),session.life.generation,now)){event.getContainer().setShouldCauseSideEffects(false);event.setCanceled(true);return;}
        }
        event.setInvulnerabilityTicks(0);
    }
    public void pre(LivingDamageEvent.Pre event){
        LivingEntity victim=event.getEntity();
        if(!handles(victim))return;
        if(victim instanceof WorldVitalEntity world){
            double points=event.getNewDamage();event.setNewDamage(0);
            if(points>0&&!world.rejectsNativeDamage(event.getSource()))worldDamage.accept(victim,points);
            return;
        }
        var container=event.getContainer();
        double original=event.getOriginalDamage();
        double scale=original>0?Math.clamp((original-container.getBlockedDamage())/original,0,1):0;
        // Never let vanilla consume health or absorption; hurtServer's local hit result is retained.
        event.setNewDamage(0);
        if(scale<=0)return;
        DamageSource source=event.getSource();
        ServerPlayer owner=victim instanceof ServerPlayer player?player:source.getEntity() instanceof ServerPlayer player?player:null;
        if(owner==null)return;
        var session=sessions.apply(owner);
        var life=victim==owner?session.life:targets.apply(victim).life();
        if(life.dead||session.life.dead)return;
        long now=System.currentTimeMillis();
        hits.entrySet().removeIf(entry->entry.getValue().expires<now);
        String root=UUID.randomUUID().toString();
        boolean direct=victim==owner&&source.getEntity() instanceof LivingEntity attacker&&attacker!=owner&&!(attacker instanceof Player)&&!source.is(DamageTypes.THORNS)&&!Set.of("poison","wither","onFire","inFire","lava").contains(source.getMsgId());
        NativeEffects.Scope effects=direct?NativeEffects.current(victim):null;
        if(effects!=null){
            container.setShouldCauseSideEffects(false);
            effects.bind(root);
            double dx,dz;
            if(source.getDirectEntity() instanceof Projectile projectile){var direction=projectile.calculateHorizontalHurtKnockbackDirection(victim,source);dx=-direction.leftDouble();dz=-direction.rightDouble();}
            else{var position=source.getSourcePosition();dx=position==null?0:position.x-victim.getX();dz=position==null?0:position.z-victim.getZ();}
            final double x=dx,z=dz;
            effects.defer(()->{
                victim.hurtTime=10;victim.hurtDuration=10;
                owner.level().broadcastDamageEvent(victim,source);
                if(!source.is(DamageTypeTags.NO_IMPACT))victim.hurtMarked=true;
                if(!source.is(DamageTypeTags.NO_KNOCKBACK)){victim.knockback(.4,x,z,source,(float)original);victim.indicateDamage(x,z);}
            });
        }
        hits.put(root,new Hit(owner.getUUID(),session.id.toString(),session.life.generation,now+60000,source,effects));
        JsonObject data=new JsonObject();
        data.addProperty("session",session.id.toString());
        data.addProperty("generation",session.life.generation);
        data.addProperty("root",root);
        data.addProperty("nativeRoot",root);
        data.addProperty("nativeScale",scale);
        data.addProperty("directAttack",direct);
        data.addProperty("source",victim.hasEffect(net.minecraft.world.effect.MobEffects.POISON)&&source.getMsgId().equals("magic")?"poison":source.getMsgId());
        data.addProperty("points",original);
        data.addProperty("projectile",source.getDirectEntity() instanceof Projectile);
        if(source.getDirectEntity() instanceof Projectile projectile)data.addProperty("projectileId",projectile.getStringUUID());
        data.addProperty("bypassInvulnerability",source.is(DamageTypeTags.BYPASSES_INVULNERABILITY));
        data.addProperty("bypassEnchantments",source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS));
        data.addProperty("bypassResistance",source.is(DamageTypeTags.BYPASSES_RESISTANCE));
        if(source.getDirectEntity()!=null)data.addProperty("speed",source.getDirectEntity().getDeltaMovement().length());
        var pos=source.getSourcePosition();
        if(pos!=null){data.addProperty("x",pos.x);data.addProperty("y",pos.y);data.addProperty("z",pos.z);}
        if(victim==owner){
            data.addProperty("fall",victim.fallDistance);
            if(!source.is(DamageTypes.THORNS)&&source.getEntity() instanceof LivingEntity attacker&&attacker!=owner&&!(attacker instanceof Player))data.add("attacker",JSON.toJsonTree(targets.apply(attacker)));
            Network.send(owner,"incoming",data);
        }else{
            data.add("target",JSON.toJsonTree(targets.apply(victim)));
            ItemStack weapon=source.getWeaponItem();
            data.add("weapon",encode.apply(owner,weapon==null?ItemStack.EMPTY:weapon));
            Network.send(owner,"projectile",data);
        }
        forwarded++;
    }
    public void commit(ServerPlayer player,JsonObject report)throws Exception{
        var hit=hits.get(report.get("nativeRoot").getAsString());var session=sessions.apply(player);if(hit!=null&&(!hit.player.equals(player.getUUID())||!hit.session.equals(session.id.toString())||hit.sourceGeneration!=session.life.generation))throw new IllegalArgumentException("原生事实归属已失效");
        String key=key(player,report.get("nativeRoot").getAsString());if(!journal.get(key).isJsonNull())throw new IllegalArgumentException("原生命中事实已接收");
        journal.put(key,report.get("operation"));
        committed++;
    }
    public DamageSource source(JsonObject report){Hit hit=hits.get(report.get("nativeRoot").getAsString());return hit==null?null:hit.source;}
    public void complete(JsonObject report){
        if(!report.has("nativeRoot"))return;
        Hit hit=hits.get(report.get("nativeRoot").getAsString());
        if(hit!=null&&hit.effects!=null)hit.effects.resolve(!report.has("outcome")||report.get("outcome").getAsString().equals("HIT"));
    }
    private String key(ServerPlayer player,String root){return "nativeHit/"+sessions.apply(player).id+"/"+root;}
}
