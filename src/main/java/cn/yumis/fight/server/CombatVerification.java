package cn.yumis.fight.server;

import cn.yumis.fight.FightMod;
import cn.yumis.fight.game.*;
import cn.yumis.fight.rules.*;
import static cn.yumis.fight.rules.Models.JSON;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.*;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.*;
import java.util.*;

/** Opt-in fixture driving real server attacks, projectiles and client reports in the isolated world. */
public final class CombatVerification {
    private static final List<Entity> fixtures=new ArrayList<>();
    private static LivingEntity target,second,attacker;
    private static Entity projectile;
    private static long forwarded,committed;
    private static String scenario="";
    private static boolean accepted;
    private CombatVerification(){}
    public static int run(ServerRuntime runtime,ServerPlayer player,String action)throws Exception{
        if(!Boolean.getBoolean("ydnf.verify"))throw new IllegalStateException("Verification is disabled");
        var level=player.level();
        var session=runtime.session(player);
        if(action.equals("reset")){
            fixtures.forEach(Entity::discard);fixtures.clear();target=second=attacker=null;projectile=null;
            for(int x=74;x<95;x++)for(int z=74;z<98;z++)level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.STONE.defaultBlockState());
            player.teleportTo(level,80.5,100,80.5,Set.of(),0,0,true);
            player.setGameMode(GameType.SURVIVAL);player.stopUsingItem();player.removeAllEffects();player.setDeltaMovement(0,0,0);player.setArrowCount(0);
            for(EquipmentSlot slot:EquipmentSlot.values())if(slot!=EquipmentSlot.BODY&&slot!=EquipmentSlot.SADDLE)player.setItemSlot(slot,ItemStack.EMPTY);
            MenuHooks.extra(player).clearContent();
            session.progress.level=80;session.resources.training=false;session.resources.controlEpoch++;session.resources.shield=0;
            session.resources.absorption.clear();session.resources.absorptionUntil.clear();session.resources.buffs.clear();
            session.resources.combatUntil=System.currentTimeMillis()+60000;session.life.q=1;session.life.dead=false;session.life.revision++;
            runtime.savePlayer(player);runtime.profile(player);
            scenario="reset";accepted=false;forwarded=runtime.nativeHits.forwarded;committed=runtime.nativeHits.committed;
        }else if(action.equals("absorb")){
            session.resources.controlEpoch++;
            session.resources.absorption.put("probe",100000d);session.resources.absorptionUntil.put("probe",System.currentTimeMillis()+60000);
            runtime.savePlayer(player);runtime.profile(player);
        }else if(action.equals("equipment_shield")){
            player.setItemSlot(EquipmentSlot.CHEST,GameItems.gear(EquipmentRules.create(Rules.Slot.CHEST,Rules.Weapon.KATANA,Rules.Rarity.EPIC,80,new Random(0))));
            session.resources.controlEpoch++;session.resources.shield=runtime.stats(player).get(Rules.Stat.SHIELD);runtime.savePlayer(player);runtime.profile(player);
        }else if(action.equals("resistance")){
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE,100));
        }else if(action.equals("block")){
            player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));player.startUsingItem(InteractionHand.OFF_HAND);
        }else if(action.equals("creative"))player.setGameMode(GameType.CREATIVE);
        else if(action.equals("skill")){
            scenario=action;forwarded=runtime.nativeHits.forwarded;committed=runtime.nativeHits.committed;
            target=mob(runtime,player,80.5,100,83);
            var context=runtime.target(target);context.speciesHealth=1000;runtime.journal.put("target/"+target.getUUID(),context);target.setData(FightMod.TARGET,JSON.toJson(context));runtime.context(player,target);
            session.progress.skills.put("DANCE",1);session.progress.skills.remove("MASTERY");session.progress.slots[0]="DANCE";
            session.resources.controlEpoch++;session.resources.cooldowns.clear();session.resources.mp=runtime.stats(player).get(Rules.Stat.MP);
            player.setItemInHand(InteractionHand.MAIN_HAND,GameItems.gear(EquipmentRules.create(Rules.Slot.WEAPON,Rules.Weapon.KATANA,Rules.Rarity.COMMON,1,new Random(0))));
            runtime.savePlayer(player);runtime.profile(player);
        }
        else if(action.equals("inspect")){}
        else{
            scenario=action;accepted=false;forwarded=runtime.nativeHits.forwarded;committed=runtime.nativeHits.committed;
            if(action.equals("melee")||action.equals("rapid")||action.equals("enemy_arrow")){
                attacker=mob(runtime,player,80.5,100,84.5);
                if(action.equals("enemy_arrow")){
                    Arrow arrow=new Arrow(level,attacker,new ItemStack(Items.ARROW),new ItemStack(Items.BOW));
                    arrow.setPos(80.5,101,83);arrow.setDeltaMovement(0,0,-1.2);projectile=arrow;spawn(level,arrow);
                }else{
                    float before=player.getHealth();
                    player.invulnerableTime=20;
                    accepted=player.hurtServer(level,player.damageSources().mobAttack(attacker),3);
                    if(action.equals("rapid"))accepted&=player.hurtServer(level,player.damageSources().mobAttack(attacker),3);
                    if(before!=player.getHealth())throw new IllegalStateException("Vanilla health was deducted before the RPG report");
                }
            }else if(action.equals("goblin")){
                var goblin=new DungeonEnemy(FightMod.ENEMIES.get("goblin_thrower").get(),level);
                goblin.setPos(80.5,100,84.5);goblin.setNoAi(true);spawn(level,goblin);attacker=goblin;
                Snowball stone=new Snowball(level,goblin,new ItemStack(Items.COBBLESTONE));
                stone.setPos(80.5,101,83);stone.setDeltaMovement(0,0,-1.2);projectile=stone;spawn(level,stone);
            }else if(action.startsWith("outgoing_")||action.equals("loyalty")||action.equals("piercing")){
                target=mob(runtime,player,80.5,100,86.5);
                if(action.equals("outgoing_lethal")){
                    var context=runtime.target(target);context.life.q=.001;
                    runtime.journal.put("target/"+target.getUUID(),context);runtime.journal.put("life/"+target.getUUID(),context.life);
                    target.setData(FightMod.TARGET,JSON.toJson(context));target.setHealth(.02f);
                }
                runtime.context(player,target);
                ItemStack weapon=new ItemStack(action.contains("trident")||action.equals("loyalty")?Items.TRIDENT:action.contains("crossbow")||action.equals("piercing")?Items.CROSSBOW:Items.BOW);
                if(action.equals("loyalty"))weapon.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOYALTY),3);
                if(action.equals("piercing")){
                    weapon.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PIERCING),2);
                    second=mob(runtime,player,80.5,100,90.5);runtime.context(player,second);
                }
                AbstractArrow arrow;
                if(weapon.is(Items.TRIDENT))arrow=new ThrownTrident(level,player,weapon);
                else{
                    Arrow normal=new Arrow(level,player,new ItemStack(Items.ARROW),weapon);
                    if(action.equals("outgoing_tipped"))normal.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING,100));
                    arrow=normal;
                }
                arrow.setPos(80.5,101,82);arrow.setDeltaMovement(0,0,1.2);projectile=arrow;spawn(level,arrow);
                // The projectile must retain its source weapon even after the player switches to a sword.
                player.setItemInHand(InteractionHand.MAIN_HAND,action.contains("trident")||action.equals("loyalty")?ItemStack.EMPTY:new ItemStack(Items.NETHERITE_SWORD));
            }else if(action.equals("pvp")){
                ServerPlayer other=runtime.server.getPlayerList().getPlayers().stream().filter(p->p!=player).findFirst().orElseThrow();
                accepted=player.hurtServer(level,player.damageSources().playerAttack(other),3);
            }else if(action.equals("fire_immune")){
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE,100));
                accepted=player.hurtServer(level,player.damageSources().onFire(),3);
            }else if(action.equals("cloud_immune")){
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(FightMod.CLOUD_STEP,100));
                accepted=player.hurtServer(level,player.damageSources().fall(),5);
            }else if(action.equals("unmanaged")){
                var cow=new net.minecraft.world.entity.animal.cow.Cow(EntityTypes.COW,level);cow.setPos(84.5,100,80.5);spawn(level,cow);target=cow;
                accepted=cow.hurtServer(level,cow.damageSources().playerAttack(player),2);
            }else throw new IllegalArgumentException("Unknown combat probe: "+action);
        }
        JsonObject report=new JsonObject();
        report.addProperty("scenario",scenario);report.addProperty("accepted",accepted);
        report.addProperty("forwarded",runtime.nativeHits.forwarded-forwarded);report.addProperty("committed",runtime.nativeHits.committed-committed);
        report.addProperty("q",session.life.q);report.addProperty("nativeHealth",player.getHealth());report.addProperty("arrows",player.getArrowCount());
        report.addProperty("player",player.getStringUUID());report.addProperty("blocking",player.isBlocking());report.addProperty("shieldDamage",player.getOffhandItem().getDamageValue());
        report.addProperty("absorption",session.resources.absorption.getOrDefault("probe",0d));report.addProperty("serverCooldown",player.invulnerableTime);
        report.addProperty("equipmentShield",session.resources.shield);
        if(target!=null){report.addProperty("target",target.getStringUUID());report.addProperty("targetHealth",target.getHealth());report.addProperty("targetArrows",target.getArrowCount());report.addProperty("targetQ",runtime.managed(target)?runtime.target(target).life.q:1);report.addProperty("targetRevision",runtime.managed(target)?runtime.target(target).life.revision:0);report.addProperty("glowing",target.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING));}
        if(second!=null)report.addProperty("secondQ",runtime.target(second).life.q);
        if(projectile!=null){report.addProperty("projectileRemoved",projectile.isRemoved());report.addProperty("projectileVelocityZ",projectile.getDeltaMovement().z);}
        Path directory=level.getServer().getServerDirectory().resolve("verification");Files.createDirectories(directory);Files.writeString(directory.resolve("combat-probe.json"),JSON.toJson(report));
        return 1;
    }
    private static Zombie mob(ServerRuntime runtime,ServerPlayer player,double x,double y,double z){
        Zombie mob=new Zombie(EntityTypes.ZOMBIE,player.level());mob.setPos(x,y,z);mob.setNoAi(true);mob.setPersistenceRequired();mob.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.CARVED_PUMPKIN));spawn(player.level(),mob);
        var target=runtime.target(mob);target.elite=false;try{runtime.journal.put("target/"+mob.getUUID(),target);}catch(Exception failure){throw new IllegalStateException(failure);}mob.setData(FightMod.TARGET,JSON.toJson(target));return mob;
    }
    private static void spawn(net.minecraft.server.level.ServerLevel level,Entity entity){fixtures.add(entity);level.addFreshEntity(entity);}
}
