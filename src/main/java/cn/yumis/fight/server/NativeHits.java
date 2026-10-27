package cn.yumis.fight.server;

import cn.yumis.fight.FightMod;
import cn.yumis.fight.network.Network;
import static cn.yumis.fight.rules.Models.JSON;
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
public final class NativeHits {
    private record Hit(UUID player,String session,long sourceGeneration,UUID target,long generation,long basis,String dimension,String instance,long created,long expires,DamageSource source,NativeEffects.Scope effects) {}
    private final Map<String,Hit> hits=new HashMap<>();
    private final ServerRuntime runtime;
    public long forwarded,committed;
    public boolean suppressProjectionFeedback;
    public NativeHits(ServerRuntime runtime){this.runtime=runtime;}
    public boolean handles(LivingEntity entity){return !runtime.bridge&&runtime.managed(entity)&&!entity.level().isClientSide();}
    public void incoming(LivingIncomingDamageEvent event){
        if(runtime.bridge&&suppressProjectionFeedback){event.getContainer().setShouldCauseSideEffects(false);return;}
        var victim=event.getEntity();
        if(!handles(victim))return;
        NativeEffects.attempted(victim);
        var source=event.getSource();
        var attacker=source.getEntity();
        boolean reject;
        if(victim instanceof ServerPlayer player){
            reject=attacker instanceof Player&&attacker!=player
                ||source.is(DamageTypes.FALL)&&player.hasEffect(FightMod.CLOUD_STEP)
                ||runtime.visuals.invulnerable(player);
        }else reject=!(attacker instanceof ServerPlayer);
        if(reject){event.setCanceled(true);return;}
        // This project deliberately accepts every collision, including during an existing hurt animation.
        event.setInvulnerabilityTicks(0);
    }
    public void pre(LivingDamageEvent.Pre event){
        LivingEntity victim=event.getEntity();
        if(!handles(victim))return;
        var container=event.getContainer();
        double original=event.getOriginalDamage();
        double scale=original>0?Math.clamp((original-container.getBlockedDamage())/original,0,1):0;
        // Never let vanilla consume health or absorption; hurtServer's local hit result is retained.
        event.setNewDamage(0);
        if(scale<=0)return;
        DamageSource source=event.getSource();
        ServerPlayer owner=victim instanceof ServerPlayer player?player:source.getEntity() instanceof ServerPlayer player?player:null;
        if(owner==null)return;
        var session=runtime.session(owner);
        var life=victim==owner?session.life:runtime.target(victim).life;
        if(life.dead||session.life.dead)return;
        long now=System.currentTimeMillis();
        hits.entrySet().removeIf(entry->entry.getValue().expires<now);
        String root=UUID.randomUUID().toString();
        boolean direct=victim==owner&&source.getEntity() instanceof LivingEntity attacker&&attacker!=owner&&runtime.managed(attacker)&&!source.is(DamageTypes.THORNS)&&!Set.of("poison","wither","onFire","inFire","lava").contains(source.getMsgId());
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
        hits.put(root,new Hit(owner.getUUID(),session.id.toString(),session.life.generation,victim.getUUID(),life.generation,life.basis,life.dimension,life.instance,now,now+60000,source,effects));
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
        data.addProperty("bypassInvulnerability",source.is(DamageTypeTags.BYPASSES_INVULNERABILITY));
        data.addProperty("bypassEnchantments",source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS));
        data.addProperty("bypassResistance",source.is(DamageTypeTags.BYPASSES_RESISTANCE));
        if(source.getDirectEntity()!=null)data.addProperty("speed",source.getDirectEntity().getDeltaMovement().length());
        var pos=source.getSourcePosition();
        if(pos!=null){data.addProperty("x",pos.x);data.addProperty("y",pos.y);data.addProperty("z",pos.z);}
        if(victim==owner){
            data.addProperty("fall",victim.fallDistance);
            if(!source.is(DamageTypes.THORNS)&&source.getEntity() instanceof LivingEntity attacker&&attacker!=owner&&runtime.managed(attacker))data.add("attacker",JSON.toJsonTree(runtime.target(attacker)));
            Network.send(owner,"incoming",data);
        }else{
            data.add("target",JSON.toJsonTree(runtime.target(victim)));
            ItemStack weapon=source.getWeaponItem();
            data.add("weapon",runtime.inventory.encode(owner,weapon==null?ItemStack.EMPTY:weapon));
            Network.send(owner,"projectile",data);
        }
        forwarded++;
    }
    /** Only binds an issued collision to its report; does not audit client damage formulas. */
    public boolean validate(ServerPlayer player,LivingEntity entity,JsonObject report){
        if(!report.has("nativeRoot"))return false;
        String root=report.get("nativeRoot").getAsString();
        Hit hit=hits.get(root);
        var session=runtime.session(player);
        if(hit==null||hit.expires<System.currentTimeMillis()||!hit.player.equals(player.getUUID())||!hit.session.equals(session.id.toString())||hit.sourceGeneration!=session.life.generation||!hit.target.equals(entity.getUUID())||hit.generation!=report.get("generation").getAsLong()||hit.basis!=report.get("basis").getAsLong()||!hit.dimension.equals(report.get("dimension").getAsString())||!hit.instance.equals(report.get("instance").getAsString()))throw new IllegalArgumentException("原生命中来源已失效");
        if(!runtime.journal.get(key(player,root)).isJsonNull())throw new IllegalArgumentException("原生命中已结算");
        return true;
    }
    public void commit(ServerPlayer player,JsonObject report)throws Exception{
        runtime.journal.put(key(player,report.get("nativeRoot").getAsString()),report.get("operation"));
        committed++;
    }
    public DamageSource source(JsonObject report){return hits.get(report.get("nativeRoot").getAsString()).source;}
    public long occurredAt(JsonObject report){return hits.get(report.get("nativeRoot").getAsString()).created;}
    public void complete(JsonObject report){
        if(!report.has("nativeRoot"))return;
        Hit hit=hits.get(report.get("nativeRoot").getAsString());
        if(hit!=null&&hit.effects!=null)hit.effects.resolve(!report.has("outcome")||report.get("outcome").getAsString().equals("HIT"));
    }
    private String key(ServerPlayer player,String root){return "nativeHit/"+runtime.session(player).id+"/"+root;}
}
