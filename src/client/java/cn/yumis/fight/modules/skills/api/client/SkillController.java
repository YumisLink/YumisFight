package cn.yumis.fight.modules.skills.api.client;

import cn.yumis.fight.modules.dungeons.api.CageEntity;
import cn.yumis.fight.modules.equipment.api.NativeEquipmentRules;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.equipment.api.Slot;
import static cn.yumis.fight.modules.combat.api.LevelAdvantage.advantage;
import cn.yumis.fight.modules.skills.client.Effects;
import cn.yumis.fight.modules.skills.client.PlayerPoseChannel;
import cn.yumis.fight.modules.skills.api.SkillPreview;
import cn.yumis.fight.modules.dungeons.api.TowerCardEffects;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import cn.yumis.fight.modules.dungeons.api.TowerClimbRules;
import cn.yumis.fight.modules.skills.client.TowerSkillCombat;
import cn.yumis.fight.modules.combat.api.ActionPhase;
import cn.yumis.fight.modules.combat.api.ActionStatus;
import cn.yumis.fight.modules.combat.api.StatusLeases;
import cn.yumis.fight.modules.combat.api.CombatTiming;
import cn.yumis.fight.modules.combat.api.CombatRules;
import cn.yumis.fight.modules.combat.api.CombatEffect;
import cn.yumis.fight.modules.combat.api.LifeRules;
import cn.yumis.fight.modules.combat.api.ElementAccumulation;
import cn.yumis.fight.modules.combat.api.EquipmentDamage;
import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.combat.api.client.DamageAnalysis;
import cn.yumis.fight.modules.talents.api.client.TalentCombat;
import cn.yumis.fight.modules.skills.api.WeaponSkills;
import cn.yumis.fight.modules.equipment.api.WeaponTraits;
import cn.yumis.fight.modules.skills.api.SkillDefinition;
import cn.yumis.fight.modules.skills.api.SkillCatalog;
import cn.yumis.fight.modules.skills.api.SkillLearning;
import cn.yumis.fight.modules.skills.api.SkillGeometry;

import cn.yumis.fight.modules.skills.api.SkillCast;
import cn.yumis.fight.modules.skills.api.SkillPresentation;
import cn.yumis.fight.modules.skills.client.SkillBehaviors;
import cn.yumis.fight.modules.skills.api.SkillParameters;

import cn.yumis.fight.modules.equipment.api.Gear;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.dungeons.api.Target;

import static cn.yumis.fight.modules.combat.api.LifeRules.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;

import net.minecraft.world.entity.*;
import cn.yumis.fight.modules.combat.api.ManagedEntities;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;
/** All combat arithmetic and geometry live on the acting client. The server merges shared enemy life and stores owner facts. */
public final class SkillController {
    private SkillCast hitSource;
    private boolean confirmedTail;
    private cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot hitEquipment;
    private JsonObject projectileContext;
    private String trainingRoot="";
    private record LaunchFrame(long at,String item,Stats stats,cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot effects){}
    private final ArrayDeque<LaunchFrame> launchFrames=new ArrayDeque<>();
    private final Map<UUID,NativeLaunchSnapshot> nativeLaunches=new LinkedHashMap<>();
    private record NativeLaunchSnapshot(Stats stats,cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot effects,String item,long expires){}
    private final cn.yumis.fight.modules.skills.client.StaffBolts staffBolts=new cn.yumis.fight.modules.skills.client.StaffBolts();
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();

    public static final SkillController INSTANCE=new SkillController();
    private SkillsClient state(){return SkillsClient.current();}
    private final Random random=new Random();
    private final List<Wave> waves=new ArrayList<>();
    private final Map<String,JsonObject> bosses=new HashMap<>();
    private final Set<String> bossHit=new HashSet<>(),incomingRoots=new HashSet<>();
    private final Map<String,Long> wallActions=new LinkedHashMap<>();
    private final Map<String,Long> controls=new HashMap<>();
    private final Map<String,Long> environment=new HashMap<>();
    private final Map<String,Bleed> bleeds=new HashMap<>();
    private final Map<String,Bleed> elementalDots=new HashMap<>();
    private final Map<Long,JsonObject> equipmentReceipts=new HashMap<>();
    private final Map<Long,String> pendingElemental=new HashMap<>();
    private final Map<Long,BleedRefresh> pendingBleeds=new HashMap<>();
    private record BleedRefresh(String session,long sourceGeneration,UUID target,long generation,String effect){}
    private final ArrayDeque<Runnable> deferred=new ArrayDeque<>();
    private static final long DEFERRED_HIT_MILLIS=500L;
    private static final int DEFERRED_HIT_LIMIT=32;
    private record DeferredHit(UUID targetUuid,int entityId,String dimension,long generation,Stats stats,cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot equipment,SkillCast source,SkillDefinition skill,double coefficient,boolean basic,boolean passive,double knockback,Vec3 center,JsonObject nativeContext,String basicCycle,int basicSlash,String trainingRoot,boolean confirmedTail,long expiresAt){}
    private final ArrayDeque<DeferredHit> deferredHits=new ArrayDeque<>();
    public static final long INPUT_BUFFER_MILLIS=350L;
    private record BufferedSkill(int slot,long expiresAt,boolean released){}
    private BufferedSkill bufferedSkill;
    public SkillCast cast;
    private final LinkedHashMap<String,Set<String>> cageHits=new LinkedHashMap<>();
    private long lastAttack,lastTick;
    private double pendingOutRegen;
    private long lastOutRegenReport;
    private String currentWeapon="";
    private Stats snapshotCache,snapshotBase;private Object snapshotCareer;private long snapshotTick=-1,snapshotFingerprint;
    private record Bleed(JsonObject state,long next,int index){
    }

    public Set<UUID> referencedTargets(){
        Set<UUID> targets=new HashSet<>();
        for(JsonObject report:equipmentReceipts.values())targets.add(UUID.fromString(report.get("target").getAsString()));
        for(BleedRefresh pending:pendingBleeds.values())targets.add(pending.target());
        for(Bleed bleed:bleeds.values())targets.add(UUID.fromString(bleed.state().get("target").getAsString()));
        for(Bleed dot:elementalDots.values())targets.add(UUID.fromString(dot.state().get("target").getAsString()));
        return Set.copyOf(targets);
    }

    private static final class Wave {
        SkillCast source;
        Vec3 point,direction,origin;
        long started,next;
        int sequence=-1;
        double remaining,width,height,coefficient;
        Stats stats;
        cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot equipment;
        long generation;
        String dimension;
        public SkillDefinition skill;
        String visualSkill="";
        String id;
        String trainingRoot;
        boolean passive,ground,danceStyle;
        Set<UUID> hit=new HashSet<>();
    }
    private SkillController(){
    }
    public void verificationSeed(long seed){if(!Boolean.getBoolean("ydnf.verify"))throw new IllegalStateException("Verification is disabled");random.setSeed(seed);}
    public boolean validWeapon(){
        var p=Minecraft.getInstance().player;
        var g=p==null?null:EquipmentItems.view(p.getMainHandItem());
        return state().ready()&&g!=null&&g.slot()==Slot.WEAPON&&g.valid(state().progress(),cn.yumis.fight.modules.equipment.api.EquipmentEligibility.allowance(Minecraft.getInstance().player));
    }
    private final StatusLeases leases=new StatusLeases();
    private boolean gravityManaged,gravityBaseline;
    public StatusLeases statusLeases(){return leases;}
    public StatusLeases.Lease acquireStatus(String owner,ActionStatus status){return acquireStatus(owner,status,Long.MAX_VALUE);}
    public StatusLeases.Lease acquireStatus(String owner,ActionStatus status,long expiresAt){
        var lease=leases.acquire(owner,status,expiresAt);
        if(status==ActionStatus.NO_GRAVITY)syncGravity();
        return lease;
    }
    public void releaseStatusOwner(String owner){
        if(leases.releaseOwner(owner))syncGravity();
    }
    public void releaseStatus(String owner,ActionStatus status){
        if(leases.release(owner,status)&&status==ActionStatus.NO_GRAVITY)syncGravity();
    }
    public void syncGravity(){
        var p=Minecraft.getInstance().player;
        if(p==null)return;
        boolean need=leases.has(ActionStatus.NO_GRAVITY,cn.yumis.fight.modules.presentation.api.client.ClientUi.now());
        if(need){
            if(!gravityManaged){gravityBaseline=p.isNoGravity();gravityManaged=true;}
            p.setNoGravity(true);
            p.fallDistance=0;
        }else if(gravityManaged){
            p.setNoGravity(gravityBaseline);
            p.fallDistance=0;
            gravityManaged=false;
        }
    }
    public boolean locked(){
        return cast!=null||cn.yumis.fight.modules.skills.client.ChantClient.active()||cn.yumis.fight.modules.skills.client.ElementalistChargeClient.active()||cn.yumis.fight.modules.skills.client.BattleMageChargeClient.active();
    }
    public boolean invulnerable(){
        long now=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();
        return leases.has(ActionStatus.INVULNERABLE,now)||TowerSkillCombat.invulnerable()||cn.yumis.fight.modules.skills.client.BattleMageClient.invulnerable()||cn.yumis.fight.modules.skills.client.WitchClient.invulnerable();
    }
    public Stats snapshot(){
        var mc=Minecraft.getInstance();long tick=mc.player==null?-1:mc.player.tickCount;var resources=state().resources();var career=cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.view;
        long now=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();
        long fingerprint=career.sequence*31L+resources.buffs.hashCode()*31L+resources.absorption.hashCode()+Double.doubleToLongBits(state().predicted(state().own()))+System.identityHashCode(resources.equipment)+(mc.player==null?0:mc.player.position().hashCode());
        for(var entry:resources.buffs.entrySet())fingerprint=fingerprint*31+entry.getKey().hashCode()+(entry.getValue()>now?1:0);
        for(var entry:resources.equipmentBuffs.entrySet()){var b=entry.getValue();fingerprint=fingerprint*31+entry.getKey().hashCode()+Objects.hashCode(b.source)+b.stacks*17L+b.expires+(b.expires>now?1:0);}
        if(snapshotCache==null||snapshotTick!=tick||snapshotBase!=state().stats()||snapshotCareer!=career||snapshotFingerprint!=fingerprint){snapshotCache=cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.snapshot(SkillPreview.currentStats(state().stats(),resources,cn.yumis.fight.modules.presentation.api.client.ClientUi.now()));snapshotTick=tick;snapshotBase=state().stats();snapshotCareer=career;snapshotFingerprint=fingerprint;}
        return snapshotCache.copy();
    }
    public void invalidateSnapshot(){snapshotCache=null;}
    public void clearTransient(){cn.yumis.fight.modules.skills.client.ChantClient.reset();cn.yumis.fight.modules.skills.client.ElementalistChargeClient.reset();cn.yumis.fight.modules.skills.client.BattleMageChargeClient.reset();SkillBehaviors.finish(cast);if(cast!=null)cast.phase=ActionPhase.ABANDONED;cast=null;bufferedSkill=null;for(var e:ongoingEffects)e.phase=ActionPhase.ABANDONED;ongoingEffects.clear();leases.clear();syncGravity();PlayerPoseChannel.clear();cn.yumis.fight.modules.skills.client.OracleClient.INSTANCE.clear();cn.yumis.fight.modules.skills.client.SwordmasterClient.clear();cn.yumis.fight.modules.skills.client.BerserkerClient.clear();cn.yumis.fight.modules.skills.client.SoulBenderClient.clear();greatCharge=null;basicActions.clear();basicVisualCycles.clear();waves.clear();staffBolts.clear();nativeLaunches.clear();launchFrames.clear();bosses.clear();bossHit.clear();incomingRoots.clear();wallActions.clear();state().resources().equipmentBuffs.remove("wall");controls.clear();environment.clear();bleeds.clear();pendingBleeds.clear();equipmentReceipts.clear();elementalDots.clear();pendingElemental.clear();deferred.clear();deferredHits.clear();lastAttack=lastTick=0;pendingOutRegen=0;lastOutRegenReport=0;currentWeapon="";invalidateSnapshot();cn.yumis.fight.modules.skills.client.MageClient.INSTANCE.clear();TrainingCombat.clear();}
    private final List<SkillCast> ongoingEffects=new ArrayList<>();
    private SkillCast advancing;
    public boolean readyToCast(long now){
        return readyToCast(now,null);
    }
    public boolean readyToCast(long now,SkillDefinition incoming){
        if(cn.yumis.fight.modules.skills.client.BattleMageClient.canCancelActive(incoming,now))return true;
        boolean recoveryOk=!PlayerPoseChannel.coolingDown(now)||SkillPresentation.canCancelRecovery(incoming);
        boolean castOk=SkillPresentation.canCancel(incoming,cast,cast!=null&&PlayerPoseChannel.canYield(cast,now));
        return recoveryOk&&castOk;
    }
    public void releaseCompletedCast(){
        if(cast!=null)yieldCast();
    }
    private boolean canBufferSlot(int slot){
        if(slot<0||slot>=state().progress().slots.length)return false;
        String binding=state().progress().slots[slot];
        if(binding==null||binding.isEmpty()||!SkillCatalog.contains(binding))return false;
        SkillDefinition skill=SkillCatalog.require(binding);
        return !skill.passive()&&(validWeapon()||skill.equipmentGranted);
    }
    public boolean press(int slot){
        if(!state().ready())return false;
        if(cn.yumis.fight.modules.dungeons.api.client.SealBossState.isFrozen())return true;
        if(cn.yumis.fight.modules.skills.client.BattleMageClient.tryOverlayChaserCast(slot))return true;
        if(cn.yumis.fight.modules.skills.client.BattleMageClient.tryOverlayInstantFusion(slot))return true;
        if(cn.yumis.fight.modules.skills.client.SunMoonClient.tryOverlaySparkSunfire(slot))return true;
        if(cn.yumis.fight.modules.skills.client.ChantClient.press(slot))return true;
        if(cn.yumis.fight.modules.skills.client.ElementalistChargeClient.press(slot))return true;
        if(cn.yumis.fight.modules.skills.client.BattleMageChargeClient.press(slot))return true;
        String initial=state().progress().slots[slot];
        if(cn.yumis.fight.modules.dungeons.api.client.SealBossState.isSkillSealed(initial))return true;
        var p=Minecraft.getInstance().player;
        long now=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();
        if(p.isDeadOrDying()||cn.yumis.fight.modules.combat.api.CombatContentTypes.controlled(p))return true;
        if(cn.yumis.fight.modules.skills.client.ElementalistClient.tryManualDetonate(initial))return true;
        if(cn.yumis.fight.modules.skills.client.WitchClient.tryBitterDetonate(initial))return true;
        if(cn.yumis.fight.modules.skills.client.BattleMageClient.tryTriggerPuntoFinisher(initial))return true;
        if(p.hasEffect(cn.yumis.fight.modules.combat.api.CombatContentTypes.RITUAL_ROOT)&&SkillPresentation.movement(initial)){
            state().message("定身期间无法使用位移技能");
            return true;
        }
        SkillDefinition initialDef=SkillCatalog.contains(initial)?SkillCatalog.require(initial):null;
        boolean equipmentSkill=initialDef!=null&&initialDef.equipmentGranted;
        if(cast!=null){
            if(!validWeapon()&&!equipmentSkill)return false;
            if(cast.slot%8==slot%8&&SkillPresentation.segmented(cast.skill)&&now<=cast.window&&hasMoreSegments(cast)){
                if(now<cast.stageStart+SkillPresentation.segmentDelay(cast.skill)/cast.speed){
                    bufferedSkill=new BufferedSkill(slot,now+INPUT_BUFFER_MILLIS,false);
                    return true;
                }
                // Complete the previous segment before an input arriving between movement ticks.
                advance(now,0);
                if(cast==null)return true;
                cast.segment++;
                cast.stageStart=now;
                cast.moved=0;
                if(cast.skill.id.equals(cn.yumis.fight.modules.skills.api.CareerSkills.PHANTOM))cast.origin=p.position();
                cast.direction=horizontal(p.getLookAngle());
                cast.hit.clear();
                cast.window=now+(long)(SkillPresentation.segmentWindow(cast.skill)/cast.speed);
                if(cast.skill==SkillCatalog.DRAGON&&TowerBattleCard.SM_DRAGON_WHIRLWIND.held(cast.stats)){
                    cn.yumis.fight.modules.skills.client.SwordmasterClient.spawnDragonWhirlwind(cast,p.position(),cast.direction);
                }
                visual(cast,"SEGMENT");
                poseSegment(cast);
                return true;
            }
            if(!SkillPresentation.canCancel(initialDef,cast,PlayerPoseChannel.canYield(cast,now))){
                if(canBufferSlot(slot))bufferedSkill=new BufferedSkill(slot,now+INPUT_BUFFER_MILLIS,false);
                return true;
            }
        }
        if(PlayerPoseChannel.coolingDown(now)&&!SkillPresentation.canCancelRecovery(initialDef)&&!cn.yumis.fight.modules.skills.client.BattleMageClient.canCancelActive(initialDef,now)){
            if(canBufferSlot(slot))bufferedSkill=new BufferedSkill(slot,now+INPUT_BUFFER_MILLIS,false);
            return true;
        }
        if(initialDef!=null&&readyToCast(now,initialDef))cn.yumis.fight.modules.skills.client.OracleClient.INSTANCE.prepareInput(initialDef);
        if(!validWeapon()&&!equipmentSkill)return false;
        String binding=state().progress().slots[slot];
        if(binding.isEmpty())return true;
        if(!SkillCatalog.contains(binding)){state().message("该技能内容尚未加载，快捷槽已保留");return false;}
        SkillDefinition skill=cn.yumis.fight.modules.skills.client.OracleClient.INSTANCE.resolve(SkillCatalog.require(binding));
        if(skill==null)return true;
        if(p.hasEffect(cn.yumis.fight.modules.combat.api.CombatContentTypes.RITUAL_ROOT)&&SkillPresentation.movement(skill)){
            state().message("定身期间无法使用位移技能");
            return true;
        }
        int rank=SkillLearning.effective(state().stats(),state().progress(),skill);
        if(rank<=0||skill.passive())return true;
        cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.request(skill,slot);return true;
    }
    public void release(int baseSlot){
        if(bufferedSkill!=null&&bufferedSkill.slot()%8==baseSlot)bufferedSkill=new BufferedSkill(bufferedSkill.slot(),bufferedSkill.expiresAt(),true);
        cn.yumis.fight.modules.skills.client.ChantClient.released(baseSlot);
        cn.yumis.fight.modules.skills.client.ElementalistChargeClient.released(baseSlot);
        cn.yumis.fight.modules.skills.client.BattleMageChargeClient.released(baseSlot);
        cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.released(baseSlot);
        if(cast!=null&&cast.charging&&cast.slot%8==baseSlot){cast.release=true;if(Minecraft.getInstance().player!=null)cast.direction=horizontal(Minecraft.getInstance().player.getLookAngle());}
    }
    public void begin(SkillCast c){
        long now=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();
        if(cast!=null){
            if(cast.canBreak||PlayerPoseChannel.canYield(cast,now))yieldCast();
            else cancel(false);
        }
        cancelBasicAttacks();
        greatCharge=null;
        cast=c;
        c.phase=ActionPhase.ACTIVE;
        c.timelineCursor=0;
        c.canBreak=false;
        if(SkillPresentation.invulnerable(c.skill))acquireStatus(c.id,ActionStatus.INVULNERABLE);
        if(SkillPresentation.segmented(c.skill))c.window=now+(long)(SkillPresentation.segmentWindow(c.skill)/c.speed);
        TrainingCombat.begin(c.id,c.skill.title);
        cn.yumis.fight.modules.skills.client.SkillCastHooks.dispatch(cn.yumis.fight.modules.skills.client.SkillCastHooks.Stage.ON_CAST_START,new cn.yumis.fight.modules.skills.client.SkillCastHooks.CastEvent(c,now,true));
        PlayerPoseChannel.begin(c);
        SkillBehaviors.start(skillContext,c,now);visual(c,"START");
        if(SkillPresentation.segmented(c.skill)){visual(c,"SEGMENT");poseSegment(c);}
        cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.publish();
    }
    private void poseSegment(SkillCast source){
        long duration=PlayerPoseChannel.scaled(source.parameters.get("dashSeconds",.2)*1000,source.speed);
        PlayerPoseChannel.play(source,source.skill.id,duration,source.direction,false);
    }
    public List<LivingEntity> targets(AABB box){return enemies(box);}
    public static boolean canTarget(LivingEntity entity){
        return entity.isAlive()&&(ManagedEntities.hostile(entity)
            ||cn.yumis.fight.modules.presentation.api.client.ClientSettings.attackNeutralMobs
                &&ManagedEntities.neutralCreature(entity));
    }
    public boolean sight(Vec3 from,Vec3 to){return visible(from,to);}
    /** Resolve a local content-owned hit with its own immutable cast snapshot. */
    public boolean hitCast(SkillCast source,LivingEntity target,double coefficient,double knockback){var previous=hitSource;boolean previousTail=confirmedTail;hitSource=source;confirmedTail=true;try{return hit(target,source.stats,source.skill,coefficient,false,source.passiveDamage,knockback,null);}finally{hitSource=previous;confirmedTail=previousTail;}}
    public void stopVisual(SkillCast c){visual(c,"CANCEL");}
    public void discardEffect(SkillCast source){ongoingEffects.remove(source);}
    private String basicCycle="";
    private int basicSlash;
    private record BasicCycle(String id,String session,String weapon,long generation,String dimension,String instance,Stats stats,cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot effects,JsonObject content,double interval,int slashes,long started){}
    private record BasicAction(double at,long order,BasicCycle cycle,int slash,boolean strike){}
    private long basicOrder;
    private final PriorityQueue<BasicAction> basicActions=new PriorityQueue<>(Comparator.comparingDouble(BasicAction::at).thenComparingLong(BasicAction::order));
    private final Map<String,BasicCycle> basicVisualCycles=new HashMap<>();
    private static boolean hasMoreSegments(SkillCast c){
        return (c.skill==SkillCatalog.TRIPLE&&TowerBattleCard.SM_TRIPLE_INFINITE.held(c.stats))||c.segment<c.parameters.get("slashes",3);
    }
    private record GreatCharge(Stats stats,String weapon,long generation,String dimension,long start,double durationMillis){}
    private GreatCharge greatCharge;
    public boolean greatCharging(){return greatCharge!=null;}
    public double greatChargeProgress(long now){return greatCharge==null?0:Math.clamp((now-greatCharge.start)/Math.max(1d,greatCharge.durationMillis),0,1);}
    public boolean greatChargeFull(long now){return greatChargeProgress(now)>=1d;}
    public boolean shouldInterceptBlockAttack(Minecraft mc){
        if(mc==null||mc.hitResult==null||mc.hitResult.getType()!=HitResult.Type.BLOCK)return true;
        if(mc.player==null||mc.level==null)return true;
        if(state().ready()&&cn.yumis.fight.modules.presentation.api.client.ClientUi.now()<state().resources().combatUntil)return true;
        if(state().ready()&&state().own().instance!=null&&!state().own().instance.isEmpty())return true;
        if(mc.hitResult instanceof BlockHitResult hit){
            var blockState=mc.level.getBlockState(hit.getBlockPos());
            if(blockState.getCollisionShape(mc.level,hit.getBlockPos()).isEmpty()||blockState.getDestroySpeed(mc.level,hit.getBlockPos())<0)return true;
        }
        Stats stats=snapshot();
        boolean mastery=WeaponSkills.mastery(state().progress(),stats),great=mastery&&stats.weaponType.equals("GREATSWORD");
        double reach=great?6.8:WeaponTraits.basic(stats.weaponType).range();
        return !meleeSweep(mc.player.getEyePosition(),Vec3.directionFromRotation(0,mc.player.getYRot()),mc.player.getBoundingBox(),reach).isEmpty();
    }
    public void attack(){
        var mc=Minecraft.getInstance();
        if(!state().combatAvailable()||!validWeapon()||cast!=null||cn.yumis.fight.modules.dungeons.api.client.SealBossState.isFrozen()||cn.yumis.fight.modules.skills.client.DemonHunterClient.active()||cn.yumis.fight.modules.skills.client.ChantClient.active()||cn.yumis.fight.modules.skills.client.ElementalistChargeClient.active()||cn.yumis.fight.modules.skills.client.ElementalistClient.blocksBasic()||cn.yumis.fight.modules.skills.client.BattleMageChargeClient.active()||cn.yumis.fight.modules.skills.client.BattleMageClient.blocksBasic()||cn.yumis.fight.modules.skills.client.WitchClient.blocksBasic()||mc.player.isDeadOrDying()||cn.yumis.fight.modules.combat.api.CombatContentTypes.controlled(mc.player)||cn.yumis.fight.modules.presentation.api.client.ClientUi.now()<lastAttack)return;
        Stats stats=snapshot();if(!cn.yumis.fight.modules.skills.client.DemonHunterClient.basicAllowed(stats))return;
        if(WeaponSkills.mastery(state().progress(),stats)&&stats.weaponType.equals("GREATSWORD")){
            if(greatCharge==null){
                double chargeMs=WeaponSkills.greatswordChargeMillis(stats);
                greatCharge=new GreatCharge(stats,stats.weaponInstance,state().own().generation,mc.level.dimension().identifier().toString(),cn.yumis.fight.modules.presentation.api.client.ClientUi.now(),chargeMs);
                PlayerPoseChannel.greatCharge(horizontal(mc.player.getLookAngle()),Math.round(chargeMs));
            }
            return;
        }
        sendBasic(stats);
    }
    private void sendBasic(Stats stats){
        var mc=Minecraft.getInstance();var profile=WeaponSkills.basicAttack(state().progress(),stats);double interval=cn.yumis.fight.modules.skills.client.BattleMageClient.basicInterval(stats,profile.interval());
        lastAttack=cn.yumis.fight.modules.presentation.api.client.ClientUi.now()+(long)Math.ceil(interval);
        String cycle=UUID.randomUUID().toString();stats=WeaponSkills.basicSnapshot(state().progress(),stats);var equipment=state().resources().equipment;
        JsonObject content=cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.basicContent(cycle);
        var weapon=WeaponTraits.basic(stats.weaponType);
        if(weapon.projectileSpeed()>0){var direction=mc.player.getLookAngle().normalize();var launch=cn.yumis.fight.modules.combat.api.ProjectileSpawn.inWorld(mc.player,mc.player.getEyePosition(),direction,weapon.range(),.15,SkillController::canTarget);content.add("staffProjectile",cn.yumis.fight.modules.combat.api.ProjectileSpawn.snapshot(launch,direction,state().serverNow()));}
        TrainingCombat.begin(cycle,"普通攻击");
        if(mc.hitResult instanceof EntityHitResult hit&&hit.getEntity() instanceof CageEntity cage){
            JsonObject j=new JsonObject();j.addProperty("cage",cage.root());j.addProperty("operation",UUID.randomUUID().toString());state().send("cageHit",j);
            swingBasic();
            return;
        }
        TowerSkillCombat.basic(stats);
        if(cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.basic(stats,cycle,content)){
            swingBasic();
            TrainingCombat.actionFinished(cycle);
            return;
        }
        long start=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();int slashes=profile.slashes();double offset=profile.contactOffset();
        var accepted=new BasicCycle(cycle,state().session(),stats.weaponInstance,state().own().generation,mc.level.dimension().identifier().toString(),state().own().instance,stats,equipment,content,interval,slashes,start);
        boolean whole=BasicAttackVisuals.wholeCycle(stats.weaponType,slashes);if(whole){basicVisualCycles.put(cycle,accepted);basicVisual(accepted,0,start);}
        for(int i=0;i<slashes;i++){
            double frac=i/(double)slashes;
            if(!whole||i>0)basicActions.add(new BasicAction(start+frac*interval,++basicOrder,accepted,i,false));
            basicActions.add(new BasicAction(start+(frac+offset)*interval,++basicOrder,accepted,i,true));
        }
        advanceBasicActions(start);
    }
    public void cancelBasicAttacks(){
        PlayerPoseChannel.cancelBasic();
        var cycles=new HashSet<String>();for(var action:basicActions)cycles.add(action.cycle.id);
        basicActions.clear();for(String cycle:cycles)TrainingCombat.actionFinished(cycle);
        for(var cycle:basicVisualCycles.values())cancelBasicVisual(cycle);basicVisualCycles.clear();
    }
    private boolean validBasic(BasicCycle cycle){
        var mc=Minecraft.getInstance();return state().ready()&&mc.player!=null&&mc.level!=null&&!state().own().dead&&!mc.player.isDeadOrDying()&&!cn.yumis.fight.modules.combat.api.CombatContentTypes.controlled(mc.player)&&cast==null&&!cn.yumis.fight.modules.skills.client.DemonHunterClient.active()&&!cn.yumis.fight.modules.skills.client.ChantClient.active()&&!cn.yumis.fight.modules.skills.client.ElementalistChargeClient.active()&&!cn.yumis.fight.modules.skills.client.ElementalistClient.blocksBasic()&&!cn.yumis.fight.modules.skills.client.BattleMageChargeClient.active()&&!cn.yumis.fight.modules.skills.client.BattleMageClient.blocksBasic()&&!cn.yumis.fight.modules.skills.client.WitchClient.blocksBasic()&&!cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.waiting()&&validWeapon()&&cycle.session.equals(state().session())&&cycle.generation==state().own().generation&&cycle.instance.equals(state().own().instance)&&cycle.dimension.equals(mc.level.dimension().identifier().toString())&&cycle.weapon.equals(EquipmentItems.view(mc.player.getMainHandItem()).instance().toString());
    }
    private void advanceBasicActions(long now){
        basicVisualCycles.values().removeIf(cycle->{if(!validBasic(cycle)){cancelBasicVisual(cycle);return true;}return now>=cycle.started+cycle.interval;});
        var cancelled=new HashSet<String>();basicActions.removeIf(action->{if(validBasic(action.cycle))return false;cancelled.add(action.cycle.id);return true;});
        for(String cycle:cancelled)TrainingCombat.actionFinished(cycle);
        while(!basicActions.isEmpty()&&basicActions.peek().at<=now){
            var action=basicActions.remove();var cycle=action.cycle;
            if(action.strike){basicStrike(cycle.stats,cycle.id,action.slash,cycle.interval,cycle.effects,cycle.content);if(action.slash==cycle.slashes-1)TrainingCombat.actionFinished(cycle.id);}
            else basicVisual(cycle,action.slash,Math.round(action.at));
        }
    }
    private void cancelBasicVisual(BasicCycle cycle){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||!state().ready()||!cycle.session.equals(state().session())||cycle.generation!=state().own().generation||!cycle.dimension.equals(mc.level.dimension().identifier().toString()))return;
        var visual=visualEnvelope(UUID.nameUUIDFromBytes((cycle.id+"/0").getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),"ATTACK","CANCEL",cycle.started,mc.player.position(),mc.player.position(),mc.player.getLookAngle(),4096);
        visual.addProperty("duration",Math.max(1,(long)Math.ceil(cycle.interval)));visual.addProperty("cosmeticOnly",true);BasicAttackVisuals.apply(cycle.stats.weaponType,visual,0,cycle.interval);visual.addProperty("sequence",4096);Effects.receive(visual);state().send("visual",visual);
    }
    private void basicStrike(Stats stats,String cycle,int index,double interval,cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot equipment,JsonObject content){
        var previousEquipment=hitEquipment;hitEquipment=equipment;try{
        var mc=Minecraft.getInstance();var p=mc.player;
        basicCycle=cycle;basicSlash=index;
        if(WeaponTraits.basic(stats.weaponType).projectileSpeed()>0){staffBolts.fire(state(),stats,equipment,cycle,index,content);return;}
        boolean mastery=WeaponSkills.mastery(state().progress(),stats),great=mastery&&stats.weaponType.equals("GREATSWORD");
        Vec3 from=p.getEyePosition(),direction=Vec3.directionFromRotation(0,p.getYRot());double reach=cn.yumis.fight.modules.skills.client.BattleMageClient.basicReach(stats,great?6.8:WeaponTraits.basic(stats.weaponType).range());
        double coefficient=stats.modifiers.getOrDefault("careerFrenzy",0d)>0?.65:great?stats.modifiers.getOrDefault("greatCharge",1d):mastery&&stats.weaponType.equals("LIGHTSABER")?.75:1;
        coefficient*=stats.modifiers.getOrDefault("basicStrikeCoefficient",1d);
        var targets=meleeSweep(from,direction,p.getBoundingBox(),reach,"BROOM".equals(stats.weaponType));
        for(var target:targets)hit(target,stats,null,coefficient,true,false,.4,null);
        if(!targets.isEmpty())cn.yumis.fight.modules.skills.client.BattleMageClient.onBasicStrikeHit(stats,targets.iterator().next());
        if(mastery&&stats.weaponType.equals("BLUNT")&&!targets.isEmpty()){
            Vec3 end=from.add(p.getLookAngle().scale(reach));
            var primary=targets.stream().min(Comparator.comparingDouble(target->{
                var contact=target.getBoundingBox().inflate(.2).clip(from,end);
                if(contact.isPresent()&&visible(from,contact.get()))return from.distanceToSqr(contact.get());
                return reach*reach+target.distanceToSqr(from);
            })).orElseThrow();
            Vec3 center=primary.position().add(0,1,0);
            for(var target:enemies(primary.getBoundingBox().inflate(2)))
                if(!targets.contains(target)&&target.position().distanceToSqr(primary.position())<=4&&visible(center,target.getBoundingBox().getCenter()))
                    hit(target,stats,null,1,true,false,.4,null);
        }
        if(stats.weaponType.equals("SHORTSWORD")&&mastery){
            boolean giant=WeaponSkills.giantShortSwordWave(stats);
            double waveWidth=giant?4.4:3.6,waveHeight=giant?2.8:2.4;
            double waveCoeff=.2*mastery(stats)*stats.modifiers.getOrDefault("basicDamageScale",1d)*WeaponSkills.shortSwordWaveDamageFactor(stats);
            wave(p.position().add(0,.8,0),direction,10,waveWidth,waveHeight,stats,null,waveCoeff,true,false,giant,"");
        }
        }finally{hitEquipment=previousEquipment;basicCycle="";}
    }
    private Set<LivingEntity> meleeSweep(Vec3 from,Vec3 direction,AABB bounds,double reach){
        return meleeSweep(from,direction,bounds,reach,false);
    }
    private Set<LivingEntity> meleeSweep(Vec3 from,Vec3 direction,AABB bounds,double reach,boolean wideArc){
        var targets=new LinkedHashSet<LivingEntity>();
        for(var target:enemies(bounds.inflate(reach,1,reach))){
            Vec3 center=target.getBoundingBox().getCenter(),delta=center.subtract(from);
            double forward=delta.dot(direction),side=Math.abs(delta.x*direction.z-delta.z*direction.x);
            boolean inArc=wideArc?forward>=-0.25&&forward>=0.26*Math.hypot(delta.x,delta.z):forward>=side;
            if(delta.horizontalDistanceSqr()<=reach*reach&&Math.abs(delta.y)<=2
                &&inArc&&visible(from,center)){
                targets.add(target);
                if(wideArc&&targets.size()>=6)break;
            }
        }
        return targets;
    }
    private void basicVisual(BasicCycle cycle,int index,long at){
        var stats=cycle.stats;var p=Minecraft.getInstance().player;
        boolean mastery=WeaponSkills.mastery(state().progress(),stats),great=mastery&&stats.weaponType.equals("GREATSWORD");
        boolean dual=stats.modifiers.getOrDefault("careerFrenzy",0d)>0||(mastery&&stats.weaponType.equals("LIGHTSABER"));
        boolean alternatingDual=(dual||stats.weaponType.equals("WARGLAIVE"))&&cycle.slashes>1&&!great;
        InteractionHand swingHand=alternatingDual&&(index&1)==1?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND;
        if(!cn.yumis.fight.modules.skills.client.BattleMageClient.onBasicSwing(stats,cycle.interval))swingBasic(swingHand);
        if(WeaponTraits.basic(stats.weaponType).projectileSpeed()>0||(index>0&&BasicAttackVisuals.wholeCycle(stats.weaponType,cycle.slashes)))return;
        double reach=cn.yumis.fight.modules.skills.client.BattleMageClient.basicReach(stats,great?6.8:WeaponTraits.basic(stats.weaponType).range());
        long attackDuration=BasicAttackVisuals.slashDuration(cycle.interval,cycle.slashes,great);
        JsonObject visual=visualEnvelope(UUID.nameUUIDFromBytes((cycle.id+"/"+index).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),"ATTACK","START",at,p.position(),p.position(),p.getLookAngle(),index);visual.addProperty("color",cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.basicColor(stats));visual.addProperty("duration",attackDuration);visual.addProperty("cosmeticOnly",true);visual.addProperty("reach",reach);visual.addProperty("sweep",great);visual.addProperty("dual",dual);visual.addProperty("weaponType",stats.weaponType);visual.addProperty("horizontalScale",great?1:WeaponTraits.sword(stats.weaponType)?1.5:1);
        BasicAttackVisuals.apply(stats.weaponType,visual,index,cycle.interval);
        Effects.receive(visual);state().send("visual",visual);
    }
    private void swingBasic(){
        swingBasic(InteractionHand.MAIN_HAND);
    }
    private void swingBasic(InteractionHand hand){
        PlayerPoseChannel.stop();
        var p=Minecraft.getInstance().player;
        if(p.swinging&&p.swingingArm!=hand){p.swinging=false;p.swingTime=0;p.oAttackAnim=0f;p.attackAnim=0f;}
        p.swing(hand);
    }
    public void tick(){
        var mc=Minecraft.getInstance();
        if(!state().ready()||mc.player==null||mc.level==null)return;
        cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.ensureContext();
        long now=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();
        launchFrames.add(new LaunchFrame(state().serverNow(),ItemData.id(mc.player.getMainHandItem()),SkillPreview.currentStats(state().stats(),state().resources(),now),state().resources().equipment.copy()));while(!launchFrames.isEmpty()&&launchFrames.peek().at<state().serverNow()-10000)launchFrames.removeFirst();
        cn.yumis.fight.modules.skills.client.ChantClient.tick();
        cn.yumis.fight.modules.skills.client.ElementalistChargeClient.tick();
        cn.yumis.fight.modules.skills.client.BattleMageChargeClient.tick();
        if(mc.isPaused()){
            lastTick=now;
            return;
        }
        cn.yumis.fight.modules.skills.client.DemonHunterClient.tick();
        cn.yumis.fight.modules.skills.client.ElementalistClient.tick();
        cn.yumis.fight.modules.skills.client.BattleMageClient.tick();
        cn.yumis.fight.modules.skills.client.WitchClient.tick();
        cn.yumis.fight.modules.skills.client.SwordmasterClient.tick();
        double dt=lastTick==0?.05:Math.min(.25,(now-lastTick)/1000d);
        lastTick=now;
        while(!deferred.isEmpty())deferred.removeFirst().run();
        deferredHits.removeIf(h->now>h.expiresAt());
        String weapon=validWeapon()?EquipmentItems.view(mc.player.getMainHandItem()).instance().toString():"";
        if(!weapon.equals(currentWeapon)){
            currentWeapon=weapon;
            if(!weapon.isEmpty())Effects.spark(mc.player.position().add(0,1,0),0xffedab);
        }
        if(cast!=null&&(mc.player.isDeadOrDying()||cn.yumis.fight.modules.combat.api.CombatContentTypes.controlled(mc.player)||!cast.dimension.equals(mc.level.dimension().identifier().toString())))cancel(false);
        if(mc.gui.screen()!=null||mc.player.isDeadOrDying()||cn.yumis.fight.modules.combat.api.CombatContentTypes.controlled(mc.player))bufferedSkill=null;
        advanceBasicActions(now);
        if(greatCharge!=null){var charge=greatCharge;
            if(cast!=null||mc.gui.screen()!=null||!validWeapon()||state().own().dead||mc.player.isDeadOrDying()||cn.yumis.fight.modules.combat.api.CombatContentTypes.controlled(mc.player)||!charge.weapon.equals(weapon)||charge.generation!=state().own().generation||!charge.dimension.equals(mc.level.dimension().identifier().toString())){greatCharge=null;PlayerPoseChannel.cancelBasic();}
            else if(!mc.options.keyAttack.isDown()){greatCharge=null;Stats charged=charge.stats.copy();charged.modifiers.put("greatCharge",1+1.5*Math.clamp((now-charge.start)/Math.max(1d,charge.durationMillis),0,1));sendBasic(charged);}
        }
        if(mc.gui.screen()==null&&mc.options.keyAttack.isDown()&&shouldInterceptBlockAttack(mc))attack();
        // A held charge keeps aiming along the caster's live crosshair instead of the frozen launch heading.
        if(cast!=null&&cast.charging&&!cast.release)cast.direction=horizontal(mc.player.getLookAngle());
        if(cast!=null)advance(now,dt);
        if(cast!=null&&cast.effectContinues&&PlayerPoseChannel.canYield(cast,now))yieldCast();
        leases.prune(now);
        syncGravity();
        if(bufferedSkill!=null){
            if(now>bufferedSkill.expiresAt()){bufferedSkill=null;}
            else if(mc.gui.screen()==null&&!mc.player.isDeadOrDying()&&!cn.yumis.fight.modules.combat.api.CombatContentTypes.controlled(mc.player)){
                String pendingBinding=bufferedSkill.slot()>=0&&bufferedSkill.slot()<state().progress().slots.length?state().progress().slots[bufferedSkill.slot()]:"";
                SkillDefinition pendingDef=SkillCatalog.contains(pendingBinding)?SkillCatalog.require(pendingBinding):null;
                boolean segmentReady=cast!=null&&cast.slot%8==bufferedSkill.slot()%8&&SkillPresentation.segmented(cast.skill)&&now>=cast.stageStart+SkillPresentation.segmentDelay(cast.skill)/cast.speed&&now<=cast.window&&hasMoreSegments(cast);
                if(segmentReady||readyToCast(now,pendingDef)){
                    int pendingSlot=bufferedSkill.slot();
                    boolean alreadyReleased=bufferedSkill.released();
                    bufferedSkill=null;
                    press(pendingSlot);
                    if(alreadyReleased)release(pendingSlot%8);
                }
            }
        }
        for(SkillCast effect:new ArrayList<>(ongoingEffects)){
            if(!cn.yumis.fight.modules.skills.client.MageClient.INSTANCE.valid(effect))finishCast(effect,false);
            else advance(effect,now);
        }
        cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.tick(skillContext);
        cn.yumis.fight.modules.skills.client.MageClient.INSTANCE.tick(skillContext);
        for(Wave w:new ArrayList<>(waves))advance(w);
        staffBolts.tick(state(),this::projectileBasicHit);
        nativeLaunches.values().removeIf(launch->launch.expires<now);TrainingCombat.tick();
        boolean out=now>=state().resources().combatUntil;
        TowerSkillCombat.tick(snapshot(),now);
        if(!state().own().dead){
            Stats recovery=snapshot();double recoveryTick=1d/20;
            if(recovery.modifiers.getOrDefault("manaRecoveryBlocked",0d)<=0)state().resources().mp=Math.min(recovery.get(Stat.MP),state().resources().mp+CombatTiming.manaRegeneration(recovery.get(Stat.MP),recovery.get(Stat.SPIRIT))*recovery.get(Stat.MANA_REGENERATION)*(out?CombatTiming.manaOutOfCombatMultiplier():1)*recoveryTick);
            if(CombatTiming.shieldReady(state().resources(),now))state().resources().shield=Math.min(recovery.get(Stat.SHIELD),state().resources().shield+CombatTiming.shieldRegenerationRate()*recovery.get(Stat.SHIELD)*recovery.get(Stat.SHIELD_REGENERATION)*recoveryTick);
            double predicted=state().predicted(state().own());
            if(out&&predicted<1){
                double step=CombatTiming.regeneration(recoveryTick,recovery.get(Stat.HEALTH),recovery.get(Stat.HEALTH_REGENERATION));
                if(step>0){
                    pendingOutRegen+=step;
                    if(now-lastOutRegenReport>=250||predicted+pendingOutRegen>=1d){
                        state().report(state().own(),pendingOutRegen,true,recovery.get(Stat.HEALTH),0xffffff,false,null);
                        pendingOutRegen=0;
                        lastOutRegenReport=now;
                    }
                }
            }else pendingOutRegen=0;
        }else pendingOutRegen=0;
        state().resources().buffs.entrySet().removeIf(e->e.getValue()<now);
        state().resources().absorptionUntil.entrySet().removeIf(e->{
            if(e.getValue()<now){
                state().resources().absorption.remove(e.getKey());return true;
            }
            return false;
        }
        );
        tickBoss(now);
        tickBleed(now);
        tickElemental(now);
        Effects.tick();
    }
    private final SkillBehaviors.Context skillContext=new SkillBehaviors.Context(){
        public net.minecraft.client.player.LocalPlayer player(){return Minecraft.getInstance().player;}
        public void buff(String name,long expires){state().resources().buffs.put(name,expires);}
        public void absorption(String name,double value){state().resources().absorption.put(name,value);}
        public void visual(SkillCast c,String phase){SkillController.this.visual(c,phase);}
        public void slash(SkillCast c,double length,double width,double height,double coefficient,double knockback,boolean fan){var previous=hitSource;hitSource=c;try{SkillController.this.slash(c,length,width,height,coefficient,knockback,fan);}finally{hitSource=previous;}}
        public void path(SkillCast c,Vec3 from,Vec3 to,double width,double height,double coefficient,double knockback){var previous=hitSource;hitSource=c;try{SkillController.this.path(c,from,to,width,height,coefficient,knockback);}finally{hitSource=previous;}}
        public void wave(Vec3 origin,Vec3 direction,double range,double width,double height,Stats stats,SkillDefinition skill,double coefficient,boolean passive,boolean ground){SkillController.this.wave(origin,direction,range,width,height,stats,skill,coefficient,passive,ground);}
        public void area(SkillCast c,double radius,double coefficient,boolean inward){var previous=hitSource;hitSource=c;try{SkillController.this.area(c,radius,coefficient,inward);}finally{hitSource=previous;}}
        public boolean hit(SkillCast c,LivingEntity target,double coefficient){var previous=hitSource;hitSource=c;try{return SkillController.this.hit(target,c.stats,c.skill,coefficient,false,c.dot,0,null);}finally{hitSource=previous;}}
        public void passive(SkillCast c){SkillController.this.passive(c);}
        public void cancel(boolean normal){
            if(advancing==null)SkillController.this.cancel(normal);
            else finishCast(advancing,normal);
        }
    };
    private void advance(long now,double dt){advance(cast,now);}
    private void advance(SkillCast source,long now){
        advancing=source;
        try{SkillBehaviors.advance(skillContext,source,now);}finally{advancing=null;}
    }
    private void yieldCast(){
        if(cast.effectContinues){cast.phase=ActionPhase.ONGOING;ongoingEffects.add(cast);cast=null;}
        else completeCast(cast);
    }

    private void passive(SkillCast c){
        if(SkillLearning.effective(c.stats,state().progress(),SkillCatalog.MASTERY)>0){
            boolean giant=WeaponSkills.giantShortSwordWave(c.stats);
            double w=c.skill==SkillCatalog.DRAW?7.2:3.6,h=c.skill==SkillCatalog.DRAW?4.4:2.4;
            if(giant){w=Math.max(w,4.4);h=Math.max(h,2.8);}
            double coeff=c.parameters.get("damage",1)*.2*mastery(c.stats)*WeaponSkills.shortSwordWaveDamageFactor(c.stats);
            wave(Minecraft.getInstance().player.position().add(0,.8,0),c.direction,10,w,h,c.stats,c.skill,coeff,true,false,giant,"");
        }
    }
    private void slash(SkillCast c,double length,double width,double height,double coefficient,double knockback,boolean fan){
        double scale=c.parameters.get("meleeScale",1);length*=scale;width*=scale;
        var p=Minecraft.getInstance().player;
        Vec3 start=p.position().add(0,.8,0);
        strikeCages(new AABB(start,start.add(c.direction.scale(length))).inflate(width/2,height/2,width/2),start,c.id);
        for(LivingEntity e:enemies(p.getBoundingBox().inflate(length+1,height, length+1))){
            Vec3 offset=e.getBoundingBox().getCenter().subtract(start);
            double forward=offset.dot(c.direction),side=Math.abs(offset.x*c.direction.z-offset.z*c.direction.x);
            boolean inside=fan?offset.horizontalDistanceSqr()<=length*length&&(c.sweepDegrees>0?c.sweepDegrees>=360||horizontal(offset).dot(c.direction)>=Math.cos(Math.toRadians(c.sweepDegrees/2)):forward>=-.3):forward>=-.5&&forward<=length+e.getBbWidth()*.5&&side<=width*.5+e.getBbWidth()*.5;
            if(inside&&Math.abs(offset.y)<=height*.5+e.getBbHeight()*.5&&visible(start,e.getBoundingBox().getCenter())&&!c.hit.contains(e.getUUID())){
                if(hit(e,c.stats,c.skill,coefficient,false,c.passiveDamage,knockback,null))c.hit.add(e.getUUID());
            }
        }
    }
    private void strikeCages(AABB bounds,Vec3 from,String attack){
        var mc=Minecraft.getInstance();
        Set<String> hit=cageHits.computeIfAbsent(attack,key->new HashSet<>());
        for(CageEntity cage:mc.level.getEntitiesOfClass(CageEntity.class,bounds))if(visible(from,cage.position().add(0,1,0))&&hit.add(cage.root())){
            JsonObject j=new JsonObject();
            j.addProperty("cage",cage.root());
            j.addProperty("operation",attack);
            state().send("cageHit",j);
        }
        if(cageHits.size()>256)cageHits.remove(cageHits.keySet().iterator().next());
    }
    private void path(SkillCast c,Vec3 from,Vec3 to,double width,double height,double coefficient,double knockback){
        width*=c.parameters.get("meleeScale",1);Vec3 start=from.add(0,.8,0),end=to.add(0,.8,0);AABB bounds=new AABB(start,end).inflate(width/2,height/2,width/2);strikeCages(bounds,start,c.id);
        for(var target:enemies(bounds))if(!c.hit.contains(target.getUUID())&&SkillGeometry.intersects(target.getBoundingBox().inflate(width/2,height/2,width/2),start,end)&&visible(start,target.getBoundingBox().getCenter())&&hit(target,c.stats,c.skill,coefficient,false,c.passiveDamage,knockback,null))c.hit.add(target.getUUID());
    }
    private void area(SkillCast c,double radius,double coefficient,boolean inward){
        strikeCages(new AABB(c.center.add(-radius,0,-radius),c.center.add(radius,4,radius)),c.center.add(0,1,0),c.id);
        for(LivingEntity e:enemies(new AABB(c.center.add(-radius,0,-radius),c.center.add(radius,4,radius))))if(e.position().subtract(c.center).horizontalDistanceSqr()<=radius*radius&&visible(c.center.add(0,1,0),e.getBoundingBox().getCenter()))hit(e,c.stats,c.skill,coefficient,false,false,inward?.4:0,inward?c.center:null);
    }
    public void fireDanceWave(Vec3 start,Vec3 direction,double range,double width,double height,SkillCast source,double coefficient){
        Wave w=new Wave();
        w.source=source;
        w.point=start;
        w.origin=start;
        w.started=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();
        w.next=w.started+50;
        w.direction=direction;
        w.remaining=range;
        w.width=width;
        w.height=height;
        w.stats=source.stats;
        w.equipment=source.equipment!=null?source.equipment:state().resources().equipment;
        w.generation=state().own().generation;w.dimension=Minecraft.getInstance().level.dimension().identifier().toString();
        w.skill=source.skill;
        w.visualSkill=SkillCatalog.DANCE.id;
        w.coefficient=coefficient;
        w.id=UUID.randomUUID().toString();
        w.trainingRoot=source.id;
        TrainingCombat.begin(w.trainingRoot,w.skill==null?"普通攻击":w.skill.title);TrainingCombat.hold(w.trainingRoot,w.id,cn.yumis.fight.modules.presentation.api.client.ClientUi.now()+5000);
        w.passive=false;
        w.ground=false;
        w.danceStyle=true;
        waves.add(w);
        waveVisual(w,"START");
        waveVisual(w,"RELEASE");
    }
    private void wave(Vec3 start,Vec3 direction,double range,double width,double height,Stats stats,SkillDefinition skill,double coefficient,boolean passive,boolean ground){
        wave(start,direction,range,width,height,stats,skill,coefficient,passive,ground,false,"");
    }
    private void wave(Vec3 start,Vec3 direction,double range,double width,double height,Stats stats,SkillDefinition skill,double coefficient,boolean passive,boolean ground,boolean danceStyle,String visualSkill){
        Wave w=new Wave();
        SkillCast owner=advancing!=null?advancing:cast;
        if(owner!=null&&owner.skill==skill){w.source=new SkillCast();w.source.id=owner.id;w.source.skill=owner.skill;w.source.parameters=owner.parameters;w.source.hitIndex=owner.hitIndex;w.source.controlSeconds=owner.controlSeconds;w.source.dot=owner.dot;w.source.elementOverride=owner.elementOverride;w.source.equipment=owner.equipment;}
        w.point=start;
        w.origin=start;
        w.started=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();
        w.next=w.started+50;
        w.direction=direction;
        w.remaining=range;
        w.width=width;
        w.height=height;
        w.stats=stats;
        w.equipment=w.source!=null&&w.source.equipment!=null?w.source.equipment:hitEquipment!=null?hitEquipment:state().resources().equipment;
        w.generation=state().own().generation;w.dimension=Minecraft.getInstance().level.dimension().identifier().toString();
        w.skill=skill;
        w.visualSkill=visualSkill;
        w.coefficient=coefficient;
        w.id=UUID.randomUUID().toString();
        w.trainingRoot=w.source!=null?w.source.id:!basicCycle.isEmpty()?basicCycle:w.id;
        TrainingCombat.begin(w.trainingRoot,skill==null?"普通攻击":skill.title);TrainingCombat.hold(w.trainingRoot,w.id,cn.yumis.fight.modules.presentation.api.client.ClientUi.now()+5000);
        w.passive=passive;
        w.ground=ground;
        w.danceStyle=danceStyle;
        waves.add(w);
        waveVisual(w,"START");
        waveVisual(w,"RELEASE");
    }
    private void advance(Wave w){
        TrainingCombat.touch(w.trainingRoot);
        long now=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();int budget=20;
        while(waves.contains(w)&&now>=w.next&&budget-->0){w.next+=50;advanceWaveStep(w);}
    }
    private void advanceWaveStep(Wave w){
        var mc=Minecraft.getInstance();
        if(state().own().dead||w.generation!=state().own().generation||!w.dimension.equals(mc.level.dimension().identifier().toString())){waveVisual(w,"END");waves.remove(w);return;}
        Vec3 next=w.point.add(w.direction.scale(Math.min(.75,w.remaining)));
        if(w.ground){
            BlockHitResult floor=mc.level.clip(new ClipContext(next.add(0,1.2,0),next.add(0,-1.2,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
            if(floor.getType()==HitResult.Type.MISS||Math.abs(floor.getLocation().y-w.point.y)>1.05){
                waveVisual(w,"END");
                waves.remove(w);
                return;
            }
            next=new Vec3(next.x,floor.getLocation().y+.1,next.z);
        }
        if(!visible(w.point.add(0,.2,0),next.add(0,.2,0))){
            waveVisual(w,"END");
            waves.remove(w);
            return;
        }
        Vec3 from=w.point;
        w.point=next;
        w.remaining-=.75;
        waveVisual(w,"TRAIL");
        var previousHit=hitSource;var previousEquipment=hitEquipment;String previousRoot=trainingRoot;hitSource=w.source;hitEquipment=w.equipment;trainingRoot=w.trainingRoot;
        boolean radialWave=w.skill==SkillCatalog.WAVE&&TowerBattleCard.ASURA_WAVE_RING.held(w.stats);
        double fromD=radialWave?from.subtract(w.origin).dot(w.direction):0,nextD=radialWave?next.subtract(w.origin).dot(w.direction):0;
        try{for(LivingEntity e:enemies(new AABB(from,next).inflate(w.width/2,w.height/2,w.width/2))){
            if(w.hit.contains(e.getUUID()))continue;
            if(radialWave){
                Vec3 rel=e.getBoundingBox().getCenter().subtract(w.origin);
                double fwd=rel.x*w.direction.x+rel.z*w.direction.z;
                double side=Math.abs(rel.x*w.direction.z-rel.z*w.direction.x);
                double half=e.getBbWidth()*.5;
                if(fwd<Math.max(0d,fromD-half)||fwd>nextD+half||side>w.width*.5+half)continue;
            }
            if(visible(from.add(0,.3,0),e.getBoundingBox().getCenter())&&hit(e,w.stats,w.skill,w.coefficient,false,w.passive,.04,null))w.hit.add(e.getUUID());
        }}
        finally{hitSource=previousHit;hitEquipment=previousEquipment;trainingRoot=previousRoot;}
        strikeCages(new AABB(from,next).inflate(w.width/2,w.height/2,w.width/2),from,w.id);
        if(w.remaining<=0){
            waveVisual(w,"END");
            waves.remove(w);
        }
    }
    public void contextArrived(UUID targetId){
        if(deferredHits.isEmpty()||targetId==null)return;
        var mc=Minecraft.getInstance();
        if(!state().ready()||state().own().dead||mc.level==null||mc.player==null)return;
        Target target=state().targets().get(targetId);
        if(target==null||target.life.dead)return;
        long now=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();
        String dim=mc.level.dimension().identifier().toString();
        var matched=new ArrayList<DeferredHit>();
        for(var iterator=deferredHits.iterator();iterator.hasNext();){
            DeferredHit item=iterator.next();
            if(now>item.expiresAt()){iterator.remove();continue;}
            if(item.targetUuid().equals(targetId)){iterator.remove();matched.add(item);}
        }
        for(DeferredHit item:matched){
            if(state().own().dead||item.generation()!=state().own().generation||!item.dimension().equals(dim))continue;
            Entity raw=mc.level.getEntity(item.entityId());
            if(!(raw instanceof LivingEntity entity)||!entity.isAlive()||!entity.getUUID().equals(targetId))continue;
            var prevSource=hitSource;var prevEquip=hitEquipment;var prevCycle=basicCycle;int prevSlash=basicSlash;var prevRoot=trainingRoot;boolean prevTail=confirmedTail;
            hitSource=item.source();hitEquipment=item.equipment();basicCycle=item.basicCycle();basicSlash=item.basicSlash();trainingRoot=item.trainingRoot();confirmedTail=item.confirmedTail();
            try{
                if(!target.training()||target.timedTraining)resolveHit(entity,target,item.stats(),item.skill(),item.coefficient(),item.basic(),item.passive(),item.knockback(),item.center(),item.nativeContext(),"");
                else{
                    String root=hitSource!=null?hitSource.id:!trainingRoot.isEmpty()?trainingRoot:!basicCycle.isEmpty()?basicCycle:"native/"+entity.getUUID()+"/"+now;
                    try{resolveHit(entity,target,item.stats(),item.skill(),item.coefficient(),item.basic(),item.passive(),item.knockback(),item.center(),item.nativeContext(),root);}
                    catch(RuntimeException failure){TrainingCombat.failed(root,item.skill()==null?"普通攻击":item.skill().title,failure);}
                }
            }finally{
                hitSource=prevSource;hitEquipment=prevEquip;basicCycle=prevCycle;basicSlash=prevSlash;trainingRoot=prevRoot;confirmedTail=prevTail;
            }
        }
    }
    private boolean hit(LivingEntity entity,Stats stats,SkillDefinition skill,double coefficient,boolean basic,boolean passive,double knockback,Vec3 center){
        return hit(entity,stats,skill,coefficient,basic,passive,knockback,center,null);
    }
    private boolean hit(LivingEntity entity,Stats stats,SkillDefinition skill,double coefficient,boolean basic,boolean passive,double knockback,Vec3 center,JsonObject nativeContext){
        if(!canTarget(entity))return false;
        if(entity instanceof cn.yumis.fight.modules.dungeons.api.TrainingSandbag bag&&bag.timed()){
            var player=Minecraft.getInstance().player;
            if(player==null||!bag.ownedBy(player)||bag.finished()&&!confirmedTail&&nativeContext==null&&(hitSource==null||hitSource.summonSource.isBlank()))return false;
        }
        Target target=state().context(entity);
        if(target==null){
            var mc=Minecraft.getInstance();
            if(mc.level!=null&&entity.isAlive()&&!state().own().dead){
                long expiresAt=cn.yumis.fight.modules.presentation.api.client.ClientUi.now()+DEFERRED_HIT_MILLIS;
                var equip=(hitSource!=null&&hitSource.equipment!=null?hitSource.equipment:hitEquipment!=null?hitEquipment:state().resources().equipment).copy();
                var savedNative=nativeContext==null?null:nativeContext.deepCopy();
                if(deferredHits.size()>=DEFERRED_HIT_LIMIT)deferredHits.removeFirst();
                deferredHits.addLast(new DeferredHit(entity.getUUID(),entity.getId(),mc.level.dimension().identifier().toString(),state().own().generation,stats.copy(),equip,hitSource,skill,coefficient,basic,passive,knockback,center,savedNative,basicCycle,basicSlash,trainingRoot,confirmedTail,expiresAt));
                return true;
            }
            return false;
        }
        if(!target.training()||target.timedTraining)return resolveHit(entity,target,stats,skill,coefficient,basic,passive,knockback,center,nativeContext,"");
        String root=hitSource!=null?hitSource.id:!trainingRoot.isEmpty()?trainingRoot:!basicCycle.isEmpty()?basicCycle:"native/"+entity.getUUID()+"/"+cn.yumis.fight.modules.presentation.api.client.ClientUi.now();
        try{return resolveHit(entity,target,stats,skill,coefficient,basic,passive,knockback,center,nativeContext,root);}
        catch(RuntimeException failure){TrainingCombat.failed(root,skill==null?"普通攻击":skill.title,failure);return true;}
    }
    private boolean resolveHit(LivingEntity entity,Target target,Stats stats,SkillDefinition skill,double coefficient,boolean basic,boolean passive,double knockback,Vec3 center,JsonObject nativeContext,String practiceRoot){
        if(target==null||target.life.dead||!state().combatAvailable())return false;
        double before=state().predicted(target.life);
        if(before<=0)return false;
        if(entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss wither&&wither.getInvulnerableTicks()>0)return false;
        int level=target.level(state().progress().level,state().encounterSync());
        boolean magic=nativeContext==null&&(basic?WeaponTraits.basic(stats.weaponType).magic():cn.yumis.fight.modules.skills.api.CareerRules.magic(skill,state().progress()));
        var equipment=hitSource!=null&&hitSource.equipment!=null?hitSource.equipment:hitEquipment!=null?hitEquipment:state().resources().equipment;
        boolean external=cn.yumis.fight.modules.combat.api.CombatEffect.permitsExternalHitEffects(Minecraft.getInstance().player,entity);
        if(!external){
            var direct=new cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot();direct.weaponElement=equipment.weaponElement;
            direct.fireOnly=equipment.fireOnly;direct.blockElemental=equipment.blockElemental;equipment=direct;
            stats=stats.copy();stats.extras.clear();stats.modifiers.keySet().removeIf(key->key.startsWith("elementAccumulation/"));
        }
        int[] elements=cn.yumis.fight.modules.skills.api.SummonerSkills.elements(stats,cn.yumis.fight.modules.skills.api.SkillPreviewRules.elements(skill));
        if(hitSource!=null&&!hitSource.damagePhase.isEmpty())magic=cn.yumis.fight.modules.skills.api.SkillDamagePhases.require(skill,hitSource.damagePhase).magic();
        boolean dot=hitSource!=null&&(hitSource.dot||!hitSource.damagePhase.isEmpty()&&cn.yumis.fight.modules.skills.api.SkillDamagePhases.require(skill,hitSource.damagePhase).dot());
        double dotCritical=dot?stats.modifiers.getOrDefault("crit",0d):0;
        if(dot){stats=stats.copy();stats.modifiers.put("crit",0d);}
        if(hitSource!=null&&hitSource.elementsOverride!=null&&hitSource.elementsOverride.length>0)elements=hitSource.elementsOverride;
        else if(hitSource!=null&&hitSource.elementOverride>=-1)elements=hitSource.elementOverride<0?new int[0]:new int[]{hitSource.elementOverride};
        else elements=cn.yumis.fight.modules.skills.api.WitchRules.previewElements(skill,stats,cn.yumis.fight.modules.skills.api.ElementalistRules.previewElements(skill,stats,elements));
        boolean prismConverted=nativeContext==null&&!basic&&!passive&&skill!=null&&cn.yumis.fight.modules.skills.api.ElementalistRules.isPrismActive(stats);
        if(prismConverted)elements=cn.yumis.fight.modules.skills.api.ElementalistRules.ALL_FOUR_ELEMENTS;
        if(nativeContext==null&&basic&&stats.modifiers.getOrDefault("basicDark",0d)>0)elements=new int[]{3};
        if(nativeContext==null&&basic&&SkillLearning.effective(stats,state().progress(),SkillCatalog.require(cn.yumis.fight.modules.skills.api.CareerSkills.KEIGA))>0)elements=new int[]{3};
        double n=level-1;
        double defenseScale=external&&cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.view.green(entity.getX(),entity.getY(),entity.getZ(),state().serverNow())?.85:1;
        if(external)defenseScale*=cn.yumis.fight.modules.skills.client.SummonerClient.defenseScale(entity,cn.yumis.fight.modules.presentation.api.client.ClientUi.now());
        if(external)defenseScale*=cn.yumis.fight.modules.skills.client.BattleMageClient.defenseScale(entity,cn.yumis.fight.modules.presentation.api.client.ClientUi.now());
        double defense=target.defense(level,magic)*defenseScale;
        double[] resist=cn.yumis.fight.modules.combat.api.EquipmentDamage.resistances(equipment,target.resistances());
        if(external){
            long shredNow=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();
            resist[1]-=cn.yumis.fight.modules.skills.client.WitchClient.fireResistShred(entity,shredNow);
            for(int e=0;e<4;e++)resist[e]-=cn.yumis.fight.modules.skills.client.ElementalistClient.resistShred(entity,e,shredNow)+cn.yumis.fight.modules.skills.client.SunMoonClient.resistShred(entity,e,shredNow);
        }
        boolean mainAllowed=cn.yumis.fight.modules.combat.api.EquipmentDamage.allowed(equipment,elements);
        elements=cn.yumis.fight.modules.combat.api.EquipmentDamage.elements(equipment,elements);
        var talents=state().progress();
        TalentCombat.Context talentContext=null;
        if(TalentCombat.conditionalLit(talents)){stats=stats.copy();talentContext=hitContext(stats,target);TalentCombat.apply(stats,talentContext,talents);}
        double witchRes=cn.yumis.fight.modules.skills.api.WitchRules.ailmentResonanceMut(stats,target.life,state().serverNow());
        if(witchRes>0){stats=stats.copy();stats.modifier("final").mut+=witchRes;}
        stats=TowerSkillCombat.damageSnapshot(stats);
        if(external&&hitSource!=null&&!hitSource.summonSource.isEmpty()){
            stats=cn.yumis.fight.modules.skills.client.SummonerClient.applyMarkBonus(stats,entity,snapshot(),state().serverNow());
        }
        var cardDamage=cn.yumis.fight.modules.skills.api.SkillDamageRules.rotation(stats,skill,towerHit(stats,entity,before));
        double defenseRemaining=1-Math.clamp(stats.modifiers.getOrDefault("piercing",0d)+TowerCardEffects.penetration(stats),0,1);
        double basicScale=stats.modifiers.getOrDefault("basicDamageScale",1d);
        double hitCoefficient=basic&&!passive?basicScale:coefficient*(nativeContext!=null?basicScale:1);
        boolean includeExtras=!passive&&!dot;
        var rolls=CombatRules.draw(stats,includeExtras,random);
        boolean forced=TowerCardEffects.forceCritical(stats,before);
        double extraCritical=Math.min(1,dotCritical+stats.modifiers.getOrDefault("crit",0d));
        CombatRules.Damage parts=forced
            ? CombatRules.recalculateCritical(cardDamage.apply(towerCritical(stats,extraCritical)),defense*defenseRemaining,resist,magic,elements,hitCoefficient,includeExtras,rolls)
            : CombatRules.calculate(cardDamage.apply(stats),defense*defenseRemaining,resist,magic,elements,hitCoefficient,includeExtras,rolls);
        if(parts.critical()&&TalentCombat.critLit(talents)){
            var withCrit=stats.copy();TalentCombat.applyCrit(withCrit,talentContext.withCrit(),talents);
            if(forced)withCrit.modifiers.merge("critDamage",extraCritical,Double::sum);
            parts=CombatRules.recalculateCritical(cardDamage.apply(withCrit),defense*defenseRemaining,resist,magic,elements,hitCoefficient,includeExtras,rolls);
        }
        if(!mainAllowed)parts=parts.withMain(0);
        if(basic&&!passive)parts=parts.scaled(coefficient);
        var resolved=cn.yumis.fight.modules.skills.api.SkillDamageRules.skillDamage(parts,stats,skill,mastery(stats),basic,passive,magic,advantage(stats.modifiers.getOrDefault("sourceLevel",(double)state().progress().level).intValue(),level));
        double damage=resolved.total();
        double main=resolved.main();
        double[] components=resolved.components();
        double maximum=target.health(level);
        JsonObject extra=new JsonObject();
        var analysisSource=skill==null?cn.yumis.fight.modules.combat.api.client.DamageAnalysis.BASIC:new cn.yumis.fight.modules.combat.api.client.DamageAnalysis.Source(skill.id,skill.title);
        cn.yumis.fight.modules.combat.api.client.DamageAnalysis.attach(extra,analysisSource);
        if(external)cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.decorate(hitSource,entity,extra);
        if(basic&&!passive&&!basicCycle.isEmpty()){extra.addProperty("basicCycle",basicCycle);extra.addProperty("basicSlash",basicSlash);}
        JsonObject kb=new JsonObject();
        kb.addProperty("strength",target.kind.equals("BOSS")?0:knockback);
        if(center!=null){
            kb.addProperty("inward",true);
            kb.addProperty("centerX",center.x);
            kb.addProperty("centerZ",center.z);
        }
        if(nativeContext==null&&external)extra.add("knockback",kb);
        else if(nativeContext!=null)extra.add("nativeRoot",nativeContext.get("nativeRoot"));
        int rank=SkillLearning.effective(stats,state().progress(),SkillCatalog.MASTERY);
        long now=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();
        if(external&&!passive&&rank>0){
            double g=Math.pow(1.12,rank-1);
            JsonObject ailment=state().ailment(entity.getStringUUID(),"BLEED");
            if(stats.weaponType.equals("KATANA")){
                if(ailment!=null&&ailment.get("expires").getAsLong()>state().serverNow()&&random.nextDouble()<.05){
                    damage*=2*g;
                    main*=2*g;
                    for(int i=0;i<components.length;i++)components[i]*=2*g;
                }
                if(random.nextDouble()<.1){
                    JsonObject a=new JsonObject();
                    a.addProperty("kind","BLEED");
                    a.addProperty("durationTicks",200);
                    a.addProperty("effect",UUID.randomUUID().toString());
                    a.addProperty("fraction",main/maximum*.05/10);
                    extra.add("ailment",a);
                }
            }
            if(stats.weaponType.equals("LIGHTSABER")){
                JsonObject shock=state().ailment(entity.getStringUUID(),"SHOCK");
                if(shock!=null&&shock.get("expires").getAsLong()>state().serverNow()&&cn.yumis.fight.modules.combat.api.EquipmentDamage.allowed(equipment,new int[]{0})){double sourceScale=hitSource!=null&&hitSource.parameters!=null?hitSource.parameters.get("balanceScale",1):stats.modifiers.getOrDefault("basicDamageScale",1d);double added=(10+2*state().progress().level)*g*sourceScale*1000/(1000+Math.max(0,defense))*Math.max(0,1+stats.get(Stat.LIGHT)/120);added*=stats.modifiers.getOrDefault("allSkillAttackProduct",1d);damage+=added;components[1]+=added;}
                if(random.nextDouble()<.2){
                    JsonObject a=new JsonObject();
                    a.addProperty("kind","SHOCK");
                    a.addProperty("durationTicks",100);
                    extra.add("ailment",a);
                }
            }
            if(stats.weaponType.equals("BLUNT")&&random.nextDouble()<WeaponSkills.bluntStunChance(stats)){
                JsonObject a=new JsonObject();
                a.addProperty("kind","STUN");
                a.addProperty("durationTicks",WeaponSkills.bluntStunTicks(g,target.boss(),target.elite||!target.kind.equals("NORMAL")));
                extra.add("ailment",a);
            }
        }
        int color=parts.element()<0?0xffffff:new int[]{
            0xffed8d,0xff8a58,0x83dfff,0xc783ff
        }
        [parts.element()];
        if(nativeContext!=null){
            double scale=nativeContext.get("nativeScale").getAsDouble();
            damage*=scale;
            for(int i=0;i<components.length;i++)components[i]*=scale;
            if(extra.has("ailment")&&extra.getAsJsonObject("ailment").has("fraction")){
                var ailment=extra.getAsJsonObject("ailment");
                ailment.addProperty("fraction",ailment.get("fraction").getAsDouble()*scale);
            }
        }
        boolean appendEquipment=external&&!dot&&(hitSource==null||!hitSource.suppressEquipment);
        double primaryDamage=damage;
        if(appendEquipment){double appended=EquipmentDamage.append(damage,stats,equipment,defense*defenseRemaining,resist);damage+=appended;components[2]+=appended;}
        double overdriveLight=stats.modifiers.getOrDefault("overdriveLightAppend",0d);
        if(appendEquipment&&overdriveLight>0){double appendedLight=EquipmentDamage.appendLight(primaryDamage,stats,equipment,defense*defenseRemaining,resist,overdriveLight);damage+=appendedLight;components[1]+=appendedLight;}
        if(!dot&&target.life.elemental.active(ElementAccumulation.Kind.SHOCK,target.life,state().serverNow())&&cn.yumis.fight.modules.combat.api.EquipmentDamage.allowed(equipment,new int[]{0})){
            double added=ElementAccumulation.shockDamage(damage,defense*defenseRemaining,stats.get(Stat.LIGHT),resist[0]);damage+=added;components[1]+=added;
        }
        if(magic){
            double received=ElementAccumulation.magicTaken(target.life.elemental,target.life,target.bossOrElite(),state().serverNow());
            damage*=received;for(int i=0;i<components.length;i++)components[i]*=received;
            primaryDamage*=received;
            if(extra.has("ailment")&&extra.getAsJsonObject("ailment").has("fraction")){var ailment=extra.getAsJsonObject("ailment");ailment.addProperty("fraction",ailment.get("fraction").getAsDouble()*received);}
        }
        double erosionScale=cn.yumis.fight.modules.equipment.api.EquipmentEffects.erosionDamageScale(equipment,target.life.elemental.active(ElementAccumulation.Kind.EROSION,target.life,state().serverNow()));
        if(erosionScale!=1){
            damage*=erosionScale;primaryDamage*=erosionScale;for(int i=0;i<components.length;i++)components[i]*=erosionScale;
            if(extra.has("ailment")&&extra.getAsJsonObject("ailment").has("fraction")){var ailment=extra.getAsJsonObject("ailment");ailment.addProperty("fraction",ailment.get("fraction").getAsDouble()*erosionScale);}
        }
        if(appendEquipment&&equipment.neutralPhysicalAfterDamage>0){
            double physicalDefense=magic?target.defense(level,false)*defenseScale:defense;
            double appended=EquipmentDamage.appendNeutralPhysical(primaryDamage,equipment,physicalDefense*defenseRemaining,resist);
            damage+=appended;components[0]+=appended;
        }
        double outgoing=cn.yumis.fight.modules.combat.api.CombatEffect.outgoingScale(Minecraft.getInstance().player);
        damage*=outgoing;for(int i=0;i<components.length;i++)components[i]*=outgoing;
        Stats buildupStats=external?TowerSkillCombat.elements(stats):stats;
        if(prismConverted){
            buildupStats=buildupStats.copy();
            ElementAccumulation.scaleBuildup(buildupStats,TowerBattleCard.ELEM_BURN_PRISM.ratio("buildup"));
        }
        extra.add("sourceStats",JSON.toJsonTree(buildupStats));extra.add("sourceEffects",JSON.toJsonTree(equipment));
        double takenScale=Double.isFinite(target.damageTakenMultiplier)?Math.max(0d,target.damageTakenMultiplier):1d;
        double actual=target.damageImmune?0:target.training()?damage/maximum:Math.min(target.damageCapacity(before),(damage/maximum)*takenScale);double remaining=Math.max(0,before-actual);
        if(remaining<=0&&actual>0&&!target.training()&&hitSource!=null&&!hitSource.summonSource.isEmpty())TalentCombat.summonKillExpected(target.id);
        var actor=JSON.fromJson(JSON.toJson(state().own()),Life.class);double actorBefore=actor.q=state().predicted(state().own());
        var receipt=new cn.yumis.fight.modules.combat.api.Receipt(true,"",actual,remaining,target.life.revision+1,"");
        if(external)cn.yumis.fight.modules.combat.api.EquipmentHitEffects.apply(target,receipt,!dot&&!extra.has("effect")&&(hitSource==null||hitSource.summonSource.isEmpty()),stats,snapshot().get(Stat.HEALTH),equipment,actor,state().resources(),maximum,now);
        if(external){
            double ownMaximum=snapshot().get(Stat.HEALTH);
            double healing=TowerSkillCombat.hitHealing(stats,hitSource,skill,actual*maximum,parts.critical());
            if(healing>0&&ownMaximum>0)LifeRules.apply(actor,healing/ownMaximum,true);
        }
        if(actor.q>actorBefore)extra.addProperty("ownerHealing",(actor.q-actorBefore)*cn.yumis.fight.modules.combat.api.CombatEffect.healingScale(Minecraft.getInstance().player));
        if(actual>0&&!equipment.wallSource.isEmpty()&&equipment.wallSource.equals(state().resources().equipment.wallSource)&&(basic&&!passive||hitSource!=null&&hitSource.summonSource.isEmpty())){
            String action=hitSource!=null?hitSource.id:!basicCycle.isEmpty()?basicCycle:nativeContext!=null&&nativeContext.has("nativeRoot")?nativeContext.get("nativeRoot").getAsString():"attack/"+Minecraft.getInstance().player.tickCount;
            wallActions.values().removeIf(until->until<now);
            if(wallActions.putIfAbsent(action,now+600000)==null){cn.yumis.fight.modules.combat.api.EquipmentBuffs.wall(state().resources(),now);invalidateSnapshot();}
        }
        if(external){
            cn.yumis.fight.modules.skills.client.SkillCastHooks.dispatch(cn.yumis.fight.modules.skills.client.SkillCastHooks.Stage.ON_HIT,
                new cn.yumis.fight.modules.skills.client.SkillCastHooks.HitEvent(hitSource,entity,stats,basicCycle,basic&&!passive&&!dot,extra,actual));
            cn.yumis.fight.modules.skills.client.SummonerClient.onOwnerHit(hitSource,entity,stats,basic&&!passive&&!dot);
            if(hitSource!=null&&!hitSource.summonSource.isEmpty()&&actual>0){
                cn.yumis.fight.modules.skills.client.SummonerClient.onSummonHit(hitSource.summonSource,snapshot());
            }
        }
        extra.add("damageComponents",JSON.toJsonTree(ElementAccumulation.fractions(components,maximum)));
        if(projectileContext!=null)projectileContext.entrySet().forEach(entry->extra.add(entry.getKey(),entry.getValue()));
        if(extra.has("ailment")){var ailment=extra.getAsJsonObject("ailment");if(ailment.has("effect"))cn.yumis.fight.modules.combat.api.client.DamageAnalysis.rememberEffect(ailment.get("effect").getAsString(),analysisSource,ailment.get("durationTicks").getAsLong()*50);}
        boolean heavyBleed=external&&basic&&!passive&&!dot&&rank>0&&stats.weaponType.equals("KATANA")&&TowerBattleCard.SM_MASTERY_OVERLOAD.held(stats)&&actual>0;
        if(target.training()&&!target.timedTraining){
            TrainingCombat.report(practiceRoot,skill==null?"普通攻击":skill.title,target,components,damage,color,parts.critical(),extra);
            if(heavyBleed)cn.yumis.fight.modules.skills.client.SwordmasterClient.applyHeavyBleed(entity,target,1.0);
            if(!basic&&!passive&&actual>0)state().skillHit(target,skill,actual);
            if(hitSource==null&&trainingRoot.isEmpty()&&basicCycle.isEmpty())TrainingCombat.actionFinished(practiceRoot);
            return true;
        }
        state().report(target.life,damage/maximum,false,maximum,color,parts.critical(),extra);
        if(heavyBleed&&remaining>0)cn.yumis.fight.modules.skills.client.SwordmasterClient.applyHeavyBleed(entity,target,remaining);
        if(external)TowerSkillCombat.hit(entity,target,stats,hitSource,skill,basic&&!passive&&!dot,actual*maximum,before);
        return true;
    }
    private static Stats towerCritical(Stats source,double extra){
        Stats stats=source.copy();
        stats.modifiers.merge("critDamage",extra,Double::sum);
        return stats;
    }
    private TowerCardEffects.DamageBonus towerHit(Stats stats,LivingEntity target,double targetLife){
        var player=Minecraft.getInstance().player;
        return TowerCardEffects.hit(stats,state().predicted(state().own()),targetLife,player.distanceToSqr(target));
    }
    public void towerStrike(TowerBattleCard card,LivingEntity entity,Stats source,double coefficient,boolean magic,int element){
        if(!canTarget(entity))return;
        Target target=state().context(entity);
        if(!mayDamageWithCard(entity,target))return;
        double before=state().predicted(target.life);
        int level=target.level(state().progress().level,state().encounterSync());
        double penetration=source.modifiers.getOrDefault("piercing",0d)+TowerCardEffects.penetration(source);
        double defense=target.defense(level,magic)*(1-Math.clamp(penetration,0,1))*cn.yumis.fight.modules.skills.client.SummonerClient.defenseScale(entity,cn.yumis.fight.modules.presentation.api.client.ClientUi.now());
        double[] resist=EquipmentDamage.resistances(state().resources().equipment,target.resistances());
        if(CombatEffect.permitsExternalHitEffects(Minecraft.getInstance().player,entity)){
            long shredNow=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();
            resist[1]-=cn.yumis.fight.modules.skills.client.WitchClient.fireResistShred(entity,shredNow);
            for(int e=0;e<4;e++)resist[e]-=cn.yumis.fight.modules.skills.client.ElementalistClient.resistShred(entity,e,shredNow)+cn.yumis.fight.modules.skills.client.SunMoonClient.resistShred(entity,e,shredNow);
        }
        int[] elements=element<0?new int[0]:new int[]{element};
        if(!EquipmentDamage.allowed(state().resources().equipment,elements))return;
        source=TowerSkillCombat.damageSnapshot(source);
        var bonus=towerHit(source,entity,before);
        if(TowerBattleCard.VULNERABLE.held(source))
            bonus=new TowerCardEffects.DamageBonus(bonus.increase()+TowerBattleCard.VULNERABLE.ratio("damage"),bonus.total());
        Stats attack=bonus.apply(source);
        boolean forced=TowerCardEffects.forceCritical(attack,before);
        var rolls=CombatRules.draw(attack,false,random);
        var damage=forced
            ? CombatRules.recalculateCritical(towerCritical(attack,attack.modifiers.getOrDefault("crit",0d)),defense,resist,magic,elements,coefficient,false,rolls)
            : CombatRules.calculate(attack,defense,resist,magic,elements,coefficient,false,rolls);
        double amount=damage.total()*advantage(state().progress().level,level)*CombatEffect.outgoingScale(Minecraft.getInstance().player);
        emitTowerDamage(card,target,amount,damage.element(),damage.critical());
    }
    public void towerFixed(TowerBattleCard card,LivingEntity entity,Stats stats,double amount){
        if(!canTarget(entity))return;
        Target target=state().context(entity);
        if(!mayDamageWithCard(entity,target))return;
        boolean critical=TowerCardEffects.forceCritical(stats,state().predicted(target.life));
        if(critical)amount*=TowerCardEffects.criticalMultiplier(stats);
        if(TowerBattleCard.VULNERABLE.held(stats))amount*=1+TowerBattleCard.VULNERABLE.ratio("damage");
        emitTowerDamage(card,target,amount,-1,critical);
    }
    private boolean mayDamageWithCard(LivingEntity entity,Target target){
        return target!=null&&entity.isAlive()&&!target.life.dead&&!target.damageImmune&&state().predicted(target.life)>0
            &&!(entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss wither&&wither.getInvulnerableTicks()>0);
    }
    private void emitTowerDamage(TowerBattleCard card,Target target,double amount,int element,boolean critical){
        if(!(amount>0)||!Double.isFinite(amount))return;
        double maximum=target.health(target.level(state().progress().level,state().encounterSync()));
        double fraction=amount/maximum;
        double[] components=new double[5];
        components[element+1]=fraction;
        var report=new JsonObject();
        report.addProperty(CombatEffect.NO_TRIGGERS,true);
        report.add("damageComponents",JSON.toJsonTree(components));
        DamageAnalysis.attach(report,new DamageAnalysis.Source("towerCard/"+card.id(),card.title()));
        state().report(target.life,fraction,false,maximum,element<0?0xffffff:ElementAccumulation.Kind.values()[element].color,critical,report);
    }
    private boolean projectileBasicHit(LivingEntity entity,Stats stats,cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot effects,String cycle,int index,Vec3 point){
        var previous=hitEquipment;String oldCycle=basicCycle;int oldSlash=basicSlash;hitEquipment=effects;basicCycle=cycle;basicSlash=index;projectileContext=new JsonObject();JsonArray position=new JsonArray();position.add(point.x);position.add(point.y);position.add(point.z);projectileContext.add("projectilePoint",position);
        try{return hit(entity,stats,null,1,true,false,0,null);}finally{hitEquipment=previous;basicCycle=oldCycle;basicSlash=oldSlash;projectileContext=null;}
    }
    /** Native projectiles retain their launch snapshot for normal and practice hit receipts alike. */
    public void nativeLaunch(JsonObject message){
        var mc=Minecraft.getInstance();if(!state().ready()||mc.level==null||!state().session().equals(message.get("session").getAsString())||state().own().generation!=message.get("generation").getAsLong()||!mc.level.dimension().identifier().toString().equals(message.get("dimension").getAsString()))return;
        UUID id=UUID.fromString(message.get("id").getAsString());var weapon=net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.parse(mc.player.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),message.get("weapon")).getOrThrow();
        LaunchFrame frozen=null;long at=message.get("at").getAsLong();for(var frame:launchFrames)if(frame.at<=at&&frame.item.equals(message.get("item").getAsString()))frozen=frame;
        Stats stats=frozen!=null?frozen.stats.copy():SkillPreview.currentStats(state().weaponStats(weapon),state().resources(),cn.yumis.fight.modules.presentation.api.client.ClientUi.now());var effects=frozen!=null?frozen.effects.copy():state().resources().equipment.copy();stats.modifiers.put("basicDamageScale",cn.yumis.fight.modules.skills.api.SkillBalanceRules.current().basicScale(state().progress().jobId(),stats.weaponType,false,state().progress().level));
        nativeLaunches.putIfAbsent(id,new NativeLaunchSnapshot(stats,effects,message.get("item").getAsString(),cn.yumis.fight.modules.presentation.api.client.ClientUi.now()+300000));while(nativeLaunches.size()>4096)nativeLaunches.remove(nativeLaunches.keySet().iterator().next());
    }
    private double mastery(Stats stats){
        return SkillPreview.mastery(state().progress(),stats);
    }
    private List<LivingEntity> enemies(AABB bounds){
        var mc=Minecraft.getInstance();
        return mc.level.getEntitiesOfClass(LivingEntity.class,bounds,SkillController::canTarget);
    }
    private boolean visible(Vec3 from,Vec3 to){
        var mc=Minecraft.getInstance();
        return mc.level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player)).getType()==HitResult.Type.MISS;
    }
    private Vec3 horizontal(Vec3 v){
        Vec3 h=new Vec3(v.x,0,v.z);
        return h.lengthSqr()<.001?new Vec3(0,0,1):h.normalize();
    }
    public static boolean isSteadfast(){
        return INSTANCE.leases.has(ActionStatus.STEADFAST,cn.yumis.fight.modules.presentation.api.client.ClientUi.now());
    }
    public void cancel(boolean normal){
        if(!normal){pendingOutRegen=0;bufferedSkill=null;cn.yumis.fight.modules.skills.client.ChantClient.cancel();cn.yumis.fight.modules.skills.client.ElementalistChargeClient.cancel();cn.yumis.fight.modules.skills.client.BattleMageChargeClient.cancel();}
        if(!normal)cancelBasicAttacks();
        if(cast!=null){
            if(normal)completeCast(cast);
            else abandonCast(cast);
        }
    }
    /** 强控（如封印塔伊蕾娜 80% 极寒冰封）触发时立即打断一切读条、引导与普攻。 */
    public void cancelActiveForControl(){
        cn.yumis.fight.modules.skills.client.DemonHunterClient.cancelChannel();
        cn.yumis.fight.modules.skills.client.ElementalistClient.cancelChannel();
        cn.yumis.fight.modules.skills.client.ElementalistChargeClient.cancel();
        cn.yumis.fight.modules.skills.client.BattleMageClient.cancelChannel();
        cn.yumis.fight.modules.skills.client.BattleMageChargeClient.cancel();
        cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.stopRenewing();
        greatCharge=null;
        cancel(false);
    }
    public void prepareFloorRecovery() {
        cancelActiveForControl();
        bosses.clear();
        String owner = state().own().target.toString();
        for (var dot : List.copyOf(elementalDots.entrySet())) {
            if (owner.equals(dot.getValue().state().get("target").getAsString())) finishElemental(dot.getKey());
        }
        cn.yumis.fight.modules.skills.client.SoulBenderClient.resetCooldowns();
        var oracle = cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.view.oracle;
        oracle.hermesSurgeReadyAt = 0;
        oracle.cycleResetReadyAt = 0;
        lastAttack = 0;
        invalidateSnapshot();
    }
    /** 封印塔伊蕾娜终局第 1 枚巨型陨石：按玩家最大生命固定比例扣血（可被护盾/吸收抵扣，不受护甲抗性减免）。 */
    public void sealBossRatioDamage(double ratio,JsonObject bossJson){
        var player=Minecraft.getInstance().player;
        if(!state().ready()||state().own().dead||player==null||player.isDeadOrDying()||invulnerable()
            ||cn.yumis.fight.modules.combat.api.CombatEffect.protectedFromDamage(player)
            ||player.isSpectator()||player.getAbilities().invulnerable)return;
        double clamped=Math.clamp(ratio,0,1);
        if(clamped<=0)return;
        Stats current=snapshot();
        double maximum=current.get(Stat.HEALTH);
        if(!(maximum>0))return;
        double rawDamage=clamped*maximum;
        if(cn.yumis.fight.modules.skills.client.ElementalistClient.tryAbsorbIceWall(rawDamage))return;
        double damage=rawDamage;
        JsonObject feedback=new JsonObject();
        feedback.addProperty("outcome","HIT");
        feedback.addProperty("damageTaken",rawDamage);
        if(bossJson!=null&&bossJson.has("id"))feedback.addProperty("feedbackEntity",bossJson.get("id").getAsString());
        for(String key:new ArrayList<>(state().resources().absorption.keySet()))if(!key.equals("overdrivePower")){
            if(key.startsWith("towerCard/")&&state().resources().absorptionUntil.getOrDefault(key,Long.MAX_VALUE)<=cn.yumis.fight.modules.presentation.api.client.ClientUi.now())continue;
            double value=state().resources().absorption.get(key),take=Math.min(value,damage);
            state().resources().absorption.put(key,value-take);
            damage-=take;
        }
        double absorb=Math.min(state().resources().shield,damage);
        state().resources().shield-=absorb;
        damage-=absorb;
        double fraction=Math.max(0,damage/maximum);
        feedback.add("damageComponents",JSON.toJsonTree(new double[]{fraction,0,0,0,0}));
        state().report(state().own(),fraction,false,maximum,0xff5a2c,false,feedback);
    }
    /** 封印塔伊蕾娜终局第 2 轮 30 秒超时：无视位置、无敌与护盾，直接秒杀在场存活玩家。 */
    public void sealBossInstantDefeat(JsonObject bossJson){
        var player=Minecraft.getInstance().player;
        if(!state().ready()||state().own().dead||player==null||player.isDeadOrDying())return;
        JsonObject feedback=new JsonObject();
        feedback.addProperty("outcome","HIT");
        if(bossJson!=null&&bossJson.has("id"))feedback.addProperty("feedbackEntity",bossJson.get("id").getAsString());
        state().report(state().own(),1.0,false,snapshot().get(Stat.HEALTH),0xff2233,false,feedback);
    }
    private void completeCast(SkillCast source){finishCast(source,true);}
    private void abandonCast(SkillCast source){finishCast(source,false);}
    private void finishCast(SkillCast source,boolean normal){
        if(source==null||source.phase.terminal())return;
        long now=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();
        source.phase=normal?ActionPhase.COMPLETED:ActionPhase.ABANDONED;
        source.canBreak=true;
        try{
            TrainingCombat.actionFinished(source.id);
            if(normal)PlayerPoseChannel.release(source);
            else{
                PlayerPoseChannel.cancel(source);
                cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.cancel(source);
            }
            visual(source,normal?"END":"CANCEL");
            SkillBehaviors.finish(source);
            cn.yumis.fight.modules.skills.client.SkillCastHooks.dispatch(
                normal?cn.yumis.fight.modules.skills.client.SkillCastHooks.Stage.ON_CAST_COMPLETE
                    :cn.yumis.fight.modules.skills.client.SkillCastHooks.Stage.ON_CAST_ABANDON,
                new cn.yumis.fight.modules.skills.client.SkillCastHooks.CastEvent(source,now,normal));
        }finally{
            releaseStatusOwner(source.id);
            if(cast==source)cast=null;
            ongoingEffects.remove(source);
            cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.publish();state().upload();
        }
    }
    private void visual(SkillCast c,String phase){
        var p=Minecraft.getInstance().player;
        if(p==null)return;
        if(phase.equals("RELEASE")||phase.equals("FINISH"))PlayerPoseChannel.lock(c);
        if(phase.equals("TRAIL"))return;
        if(phase.equals("END")&&(c.skill.id.equals(cn.yumis.fight.modules.skills.api.CareerSkills.ORB)||c.skill.id.equals(cn.yumis.fight.modules.skills.api.CareerSkills.CRESCENT)||c.skill.id.equals(cn.yumis.fight.modules.skills.api.CareerSkills.FIRE)))return;
        long uiNow=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();
        if(c.visualId.isEmpty()){c.visualId=c.id;c.visualStart=c.start;}
        if(c.skill==SkillCatalog.TRIPLE&&phase.equals("SEGMENT")&&(c.visualSequence>=3500||uiNow-c.visualStart>=25000L)){
            JsonObject close=visualEnvelope(c.visualId,c.skill.id,"END",c.visualStart,c.origin,p.position(),c.direction,4096);
            close.addProperty("duration",SkillPresentation.duration(c));
            Effects.receive(close);state().send("visual",close);
            c.visualId=UUID.randomUUID().toString();c.visualStart=uiNow;c.visualSequence=-1;
            JsonObject reopen=visualEnvelope(c.visualId,c.skill.id,"START",c.visualStart,p.position(),p.position(),c.direction,++c.visualSequence);
            reopen.addProperty("weaponType",c.visualWeapon.isEmpty()?c.stats.weaponType:c.visualWeapon);
            reopen.addProperty("duration",SkillPresentation.duration(c));
            reopen.addProperty("castSpeed",SkillPresentation.visualSpeed(c));
            reopen.addProperty("rank",c.parameters.rank());
            reopen.addProperty("horizontalScale",c.parameters.get("meleeScale",1));
            reopen.addProperty("color",cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.visualColor(c.stats,SkillPresentation.color(c.skill,state().resources().buffs.getOrDefault("yumis_dungeon_and_fight:overdrive",0L)>uiNow)));
            Effects.receive(reopen);state().send("visual",reopen);
        }
        JsonObject j=visualEnvelope(c.visualId,c.skill.id,phase,c.visualStart,c.origin,p.position(),c.direction,++c.visualSequence);
        j.addProperty("weaponType",c.visualWeapon.isEmpty()?c.stats.weaponType:c.visualWeapon);
        j.addProperty("duration",SkillPresentation.duration(c));
        j.addProperty("castSpeed",SkillPresentation.visualSpeed(c));
        j.addProperty("rank",c.parameters.rank());
        double hScale=c.parameters.get("meleeScale",1);
        if(c.skill==SkillCatalog.DRAW)hScale*=Math.max(.5,c.parameters.get("range",6)/6.0);
        j.addProperty("horizontalScale",hScale);
        if(c.skill==SkillCatalog.RISING&&TowerBattleCard.SM_RISING_DRAGON_SLAY.held(c.stats))j.addProperty("sweep",true);
        if(c.skill==SkillCatalog.DRAW&&TowerBattleCard.SM_DRAW_BARRAGE.held(c.stats))j.addProperty("dual",true);
        j.addProperty("charge",Math.clamp((uiNow-c.visualStart)*c.speed/1000d,0,1));
        j.addProperty("color",cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.visualColor(c.stats,SkillPresentation.color(c.skill,state().resources().buffs.getOrDefault("yumis_dungeon_and_fight:overdrive",0L)>uiNow)));
        if(SkillPresentation.segmented(c.skill)&&phase.equals("SEGMENT"))j.add("motion",motion(c.parameters.get("dashDistance",c.skill==SkillCatalog.TRIPLE?3.0:4.8),SkillPresentation.segmentDelay(c.skill)/c.speed,false));
        if(c.skill==SkillCatalog.RISING&&phase.equals("START"))j.add("motion",motion(TowerBattleCard.SM_RISING_DRAGON_SLAY.held(c.stats)?Math.max(7.0,c.parameters.get("dashDistance",4.5)*1.55):c.parameters.get("dashDistance",4.5),300/c.speed,false));
        cn.yumis.fight.modules.skills.client.SkillCastHooks.dispatch(cn.yumis.fight.modules.skills.client.SkillCastHooks.Stage.DECORATE_VISUAL,
            new cn.yumis.fight.modules.skills.client.SkillCastHooks.VisualEvent(c,phase,j));
        Effects.receive(j);
        state().send("visual",j);
    }
    public static JsonObject motion(double range,double millis,boolean ground){JsonObject m=new JsonObject();m.addProperty("velocity",millis<=0?0:range*1000/millis);m.addProperty("range",range);m.addProperty("millis",(long)Math.ceil(millis));m.addProperty("ground",ground);m.addProperty("released",true);return m;}
    private JsonObject visualEnvelope(String id,String kind,String phase,long start,Vec3 origin,Vec3 point,Vec3 direction,int sequence){
        JsonObject j=new JsonObject();
        var p=Minecraft.getInstance().player;
        j.addProperty("caster",p.getStringUUID());
        j.addProperty("session",state().session());
        j.addProperty("generation",state().own().generation);
        j.addProperty("id",id);
        j.addProperty("kind",kind);
        j.addProperty("phase",phase);
        j.addProperty("sequence",sequence);
        j.addProperty("seed",id.hashCode());
        j.addProperty("startedAt",state().serverNow()-(cn.yumis.fight.modules.presentation.api.client.ClientUi.now()-start));
        j.addProperty("at",state().serverNow());
        j.addProperty("x",origin.x);
        j.addProperty("y",origin.y);
        j.addProperty("z",origin.z);
        j.addProperty("px",point.x);
        j.addProperty("py",point.y);
        j.addProperty("pz",point.z);
        j.addProperty("dx",direction.x);
        j.addProperty("dy",direction.y);
        j.addProperty("dz",direction.z);
        return j;
    }
    private void waveVisual(Wave wave,String phase){
        if(Minecraft.getInstance().player==null)return;
        if(phase.equals("END")||phase.equals("CANCEL"))TrainingCombat.release(wave.trainingRoot,wave.id);
        if(phase.equals("TRAIL"))return;
        String kind=!wave.visualSkill.isEmpty()?wave.visualSkill:wave.passive?"MASTERY_WAVE":wave.skill.id;
        JsonObject j=visualEnvelope(wave.id,kind,phase,wave.started,wave.origin,wave.point,wave.direction,++wave.sequence);
        j.addProperty("horizontalScale",wave.passive?wave.width/3:Math.max(1d,wave.width/4.4));j.addProperty("slashAngle",wave.passive?new Random(wave.id.hashCode()).nextDouble()*180:0);j.addProperty("weaponType",wave.stats.weaponType);
        if(wave.danceStyle)j.addProperty("sweep",true);
        j.addProperty("duration",Math.min(40000,(long)((wave.origin.distanceTo(wave.point)+wave.remaining)/15*1000)+500));
        j.addProperty("cosmeticOnly",true);
        int waveFallback=wave.skill!=null&&cn.yumis.fight.modules.skills.api.OracleSkills.ACTIONS.contains(wave.skill.id)
            ?(wave.stats.modifiers.getOrDefault("oracleInstruction",0d)>0?0xf2cb72:0xdce4ee)
            :(wave.skill==SkillCatalog.WAVE?0xB4F2FF:0xFFD368);
        j.addProperty("color",cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.visualColor(wave.stats,waveFallback));
        if(phase.equals("RELEASE"))j.add("motion",motion(wave.remaining,wave.remaining/15*1000,wave.ground));
        Effects.receive(j);
        state().send("visual",j);
    }
    public void incoming(JsonObject j){
        if(j.has("session")&&!j.get("session").getAsString().equals(state().session())||j.has("generation")&&j.get("generation").getAsLong()!=state().own().generation)return;
        if(!state().ready()||state().own().dead||!incomingRoots.add(j.get("root").getAsString()))return;
        if(incomingRoots.size()>2048)incomingRoots.clear();
        // 原版 /kill 走 genericKill：按原版语义无条件致死，不经过无敌帧、装备防护、护盾与吸收。
        if(j.get("source").getAsString().equals("genericKill")){nativeKill(j);return;}
        if(invulnerable()||cn.yumis.fight.modules.combat.api.CombatEffect.protectedFromDamage(Minecraft.getInstance().player)||Minecraft.getInstance().player.isSpectator()||Minecraft.getInstance().player.getAbilities().invulnerable&&!(j.has("bypassInvulnerability")&&j.get("bypassInvulnerability").getAsBoolean())){nativeOutcome(j,"IMMUNE");return;}
        if(j.get("source").getAsString().equals("fall")&&Minecraft.getInstance().player.hasEffect(cn.yumis.fight.modules.combat.api.CombatContentTypes.CLOUD_STEP)){nativeOutcome(j,"IMMUNE");return;}
        double fraction;boolean enemyAttack=false;int enemyElement=-1;
        double[] componentFractions=new double[5];
        Target attacker=j.has("attacker")?JSON.fromJson(j.get("attacker"),Target.class):null;
        if(attacker!=null){
            int level=attacker.level(state().progress().level,state().encounterSync());
            double points=attacker.attack(level);
            int element=nativeElement(j.get("source").getAsString());
            enemyAttack=points>0;enemyElement=element;
            componentFractions=ElementAccumulation.fractions(monsterComponents(points,element>=0||Set.of("magic","indirectMagic","sonicBoom").contains(j.get("source").getAsString()),element,level),snapshot().get(Stat.HEALTH));
            fraction=Arrays.stream(componentFractions).sum();
        }
        else{
            String source=j.get("source").getAsString();
            long delay=switch(source){
                case "lava","inFire","onFire","drown","inWall","outOfWorld","outsideBorder"->1000;
                case "cactus","sweetBerryBush","hotFloor","cramming","sting"->500;
                case "freeze"->2000;
                default->0;
            }
            ;
            long now=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();
            if(delay>0&&environment.getOrDefault(source,0L)>now){nativeOutcome(j,"IMMUNE");return;}
            environment.put(source,now+delay);
            fraction=switch(source){
                case "fall"->cn.yumis.fight.modules.skills.client.WitchClient.protectsFromFall()?0:Math.max(0,(j.get("fall").getAsDouble()-3)*.05);
                case "lava","outOfWorld","outsideBorder"->.2;
                case "inFire","onFire","inWall","starve","cactus","sweetBerryBush","hotFloor","freeze","sting"->.05;
                case "drown","fallingBlock","anvil","fallingStalactite"->.1;
                case "lightningBolt"->.25;
                case "cramming","magic"->.3;
                case "wither","poison"->.05;
                case "stalagmite"->Math.max(0,(j.get("fall").getAsDouble()-1)*.1);
                case "flyIntoWall"->cn.yumis.fight.modules.skills.client.WitchClient.protectsFromFall()?0:Math.max(0,Minecraft.getInstance().player.getDeltaMovement().horizontalDistance()*10-3)*.05;
                case "explosion","badRespawnPoint","fireworks"->explosion(j);
                case "arrow","mobProjectile"->Math.ceil(Math.max(0,j.has("speed")?j.get("speed").getAsDouble()*2:2))*.05;
                case "trident"->.4;
                case "thorns"->Math.max(0,j.get("points").getAsDouble())*.05;
                default->{
                    state().message("未适配的伤害来源："+source);
                    yield 0;
                }
            }
            ;
            if(source.equals("poison"))fraction=Math.min(fraction,Math.max(0,state().predicted(state().own())-.001));
            if(source.equals("inFire")||source.equals("onFire")||source.equals("lava")){
                if(Minecraft.getInstance().player.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE))fraction=0;
            }
            if(source.equals("drown")&&Minecraft.getInstance().player.hasEffect(net.minecraft.world.effect.MobEffects.WATER_BREATHING))fraction=0;
            int element=nativeElement(source);
            if(element>=0||source.equals("magic")||source.equals("poison"))fraction*=ElementAccumulation.magicTaken(state().own().elemental,state().own(),false,state().serverNow());
            componentFractions[element+1]=fraction;
        }
        double received=NativeEquipmentRules.receivedMultiplier(Minecraft.getInstance().player,j.get("source").getAsString(),j.has("projectile")&&j.get("projectile").getAsBoolean(),j.has("bypassEnchantments")&&j.get("bypassEnchantments").getAsBoolean(),j.has("bypassResistance")&&j.get("bypassResistance").getAsBoolean())*(j.has("nativeScale")?j.get("nativeScale").getAsDouble():1);
        fraction*=received;for(int i=0;i<componentFractions.length;i++)componentFractions[i]*=received;
        if(enemyAttack)fraction=minimumEnemyFraction(componentFractions,snapshot().get(Stat.HEALTH),enemyElement);
        if(fraction<=0){nativeOutcome(j,"IMMUNE");return;}
        JsonObject feedback=new JsonObject();
        if(j.has("nativeRoot"))feedback.add("nativeRoot",j.get("nativeRoot"));
        if(attacker!=null)feedback.addProperty("retaliationTarget",attacker.id.toString());
        if(j.has("directAttack")&&j.get("directAttack").getAsBoolean()){
            if(attacker!=null&&guardCounter(attacker,feedback))return;
            if(CombatRules.evades(snapshot().get(Stat.EVASION),random::nextDouble)){TalentCombat.dodged();cn.yumis.fight.modules.skills.client.SoulBenderClient.onEvade();nativeOutcome(j,"DODGE");return;}
        }
        feedback.add("damageComponents",JSON.toJsonTree(componentFractions));
        ownDamage(fraction,feedback);
    }
    private void nativeOutcome(JsonObject source,String outcome){
        if(!source.has("nativeRoot"))return;
        JsonObject feedback=new JsonObject();feedback.add("nativeRoot",source.get("nativeRoot"));feedback.addProperty("outcome",outcome);
        state().report(state().own(),0,false,snapshot().get(Stat.HEALTH),0xff5555,false,feedback);
    }
    /** 原版 /kill：上报整条生命为致死份额，服务器确认后照常走死亡投影与重生。 */
    private void nativeKill(JsonObject j){
        JsonObject feedback=new JsonObject();
        if(j.has("nativeRoot"))feedback.add("nativeRoot",j.get("nativeRoot"));
        feedback.addProperty("outcome","HIT");
        // 整条生命份额而非当前预测剩余值：存在在途伤害上报时也能确保服务器把权威生命结算到 0。
        state().report(state().own(),1,false,snapshot().get(Stat.HEALTH),0xff5555,false,feedback);
    }
    private double explosion(JsonObject j){
        if(!j.has("x")){
            state().message("未适配的爆炸：缺少原点");
            return 0;
        }
        Vec3 pos=new Vec3(j.get("x").getAsDouble(),j.get("y").getAsDouble(),j.get("z").getAsDouble());
        double f=1-Minecraft.getInstance().player.position().distanceTo(pos)/8;
        return f<=0?0:((f*f+f)*.5*7*8+1)*.05;
    }
    private double monsterDamage(double points,boolean magic,int element,int level){
        Stats current=snapshot();
        var talents=state().progress();
        if(TalentCombat.conditionalLit(talents)){current=current.copy();TalentCombat.apply(current,takenContext(current),talents);}
        double defense=current.get(magic?Stat.MAGICAL_DEFENSE:Stat.PHYSICAL_DEFENSE);
        double resist=element>=0?current.get(new Stat[]{Stat.LIGHT_RESIST,Stat.FIRE_RESIST,Stat.ICE_RESIST,Stat.DARK_RESIST}[element]):Math.max(Math.max(current.get(Stat.LIGHT_RESIST),current.get(Stat.FIRE_RESIST)),Math.max(current.get(Stat.ICE_RESIST),current.get(Stat.DARK_RESIST)));
        if(element<0)resist=Math.max(0,resist);
        var combatRules=cn.yumis.fight.modules.combat.api.CombatBalanceRules.current().combat;
        double careerTaken=cn.yumis.fight.modules.skills.client.WitchClient.careerDamageTaken(cn.yumis.fight.modules.skills.client.BattleMageClient.careerDamageTaken(cn.yumis.fight.modules.skills.client.ElementalistClient.careerDamageTaken(cn.yumis.fight.modules.skills.client.DemonHunterClient.careerDamageTaken(cn.yumis.fight.modules.character.api.JobCatalog.style(state().progress().jobId()).damageTaken()))))*cn.yumis.fight.modules.skills.client.SummonerClient.ownerDamageTakenMultiplier(current);
        double damage=points*combatRules.defenseDivisor()/(combatRules.defenseDivisor()+Math.max(0,defense))*Math.max(combatRules.playerResistFloor(),1-resist/combatRules.elementalDivisor())*advantage(level,state().progress().level)*careerTaken*combatRules.playerDamageTakenMultiplier();
        if(state().resources().buffs.getOrDefault("movementProtection",0L)>cn.yumis.fight.modules.presentation.api.client.ClientUi.now())damage*=combatRules.movementProtectionMultiplier();
        return damage*Math.max(0,1-current.modifiers.getOrDefault("taken",0d));
    }
    /** 条件位上下文：活状态读本机会话与召唤在场；目标事实由当次命中填入。 */
    private TalentCombat.Context hitContext(Stats stats,Target target){
        Set<String> ailments=new HashSet<>(4);
        long serverNow=state().serverNow();
        for(var kind:ElementAccumulation.Kind.values())if(target.life.elemental.active(kind,target.life,serverNow))ailments.add(kind.id);
        return new TalentCombat.Context(state().predicted(state().own()),resourceFraction(stats.get(Stat.MP)),state().resources().shield>0,summoned(),stats.modifiers.getOrDefault("killWindow",0d),false,ailments,state().predicted(target.life));
    }
    private TalentCombat.Context takenContext(Stats stats){
        return new TalentCombat.Context(state().predicted(state().own()),resourceFraction(stats.get(Stat.MP)),state().resources().shield>0,summoned(),stats.modifiers.getOrDefault("killWindow",0d),false,Set.of(),1);
    }
    private double resourceFraction(double maximum){return maximum>0?Math.clamp(state().resources().mp,0,maximum)/maximum:1;}
    private boolean summoned(){var mage=cn.yumis.fight.modules.skills.client.MageClient.INSTANCE;return !mage.army.isEmpty()||mage.summonUntil>state().serverNow();}
    private static int nativeElement(String source){return switch(source){case "lava","inFire","onFire","hotFloor","campfire","fireball","unattributedFireball"->1;case "lightningBolt"->0;case "freeze"->2;case "wither","witherSkull","dragonBreath"->3;default->-1;};}
    private static double minimumEnemyFraction(double[] components,double maximum,int element){
        double total=Arrays.stream(components).sum();
        if(maximum>0&&Double.isFinite(total)&&total>=0&&total<1/maximum){components[element+1]+=1/maximum-total;return 1/maximum;}
        return total;
    }
    private double[] monsterComponents(double points,boolean magic,int element,int level){
        double[] components=new double[5];double damage=monsterDamage(points,magic,element,level);components[element+1]=damage;
        if(state().own().elemental.active(ElementAccumulation.Kind.SHOCK,state().own(),state().serverNow())){
            var current=snapshot();components[1]+=ElementAccumulation.shockDamage(damage,current.get(magic?Stat.MAGICAL_DEFENSE:Stat.PHYSICAL_DEFENSE),0,Math.min(84,current.get(Stat.LIGHT_RESIST)));
        }
        if(magic){double received=ElementAccumulation.magicTaken(state().own().elemental,state().own(),false,state().serverNow());for(int i=0;i<components.length;i++)components[i]*=received;}
        return components;
    }
    /** Owner-side maximum-health environmental damage using normal absorption and hit reports. */
    public void environmentDamage(double fraction){
        if(mayTakeEnvironmentDamage())ownDamage(fraction);
    }
    private boolean mayTakeEnvironmentDamage(){
        var player=Minecraft.getInstance().player;
        return player!=null&&state().ready()&&!state().own().dead&&!player.isDeadOrDying()
            &&!player.isSpectator()&&!player.getAbilities().invulnerable&&!invulnerable();
    }
    public void towerFire(){
        if(!mayTakeEnvironmentDamage())return;
        double maximum=snapshot().get(Stat.HEALTH);
        if(!(maximum>0))return;
        double damage=monsterDamage(maximum*TowerClimbRules.FIRE_HEALTH_FRACTION,true,1,state().progress().level);
        var feedback=new JsonObject();
        feedback.addProperty(CombatEffect.NO_TRIGGERS,true);
        feedback.add("damageComponents",JSON.toJsonTree(new double[]{0,0,damage/maximum,0,0}));
        ownDamage(damage/maximum,feedback);
    }
    public void towerFrost(double damage){
        var life=state().own();
        if(life.dead||life.elemental.active(ElementAccumulation.Kind.FREEZE,life,state().serverNow()))return;
        double maximum=snapshot().get(Stat.HEALTH);
        if(!(maximum>0)||!(damage>0)||!Double.isFinite(damage))return;
        var feedback=new JsonObject();
        double[] components={0,0,0,damage*TowerClimbRules.frostDamageFraction()/maximum,0};
        advanceOwnElements(components,1,feedback);
        state().report(life,0,false,maximum,0x83dfff,false,feedback);
    }
    private void ownDamage(double fraction){
        ownDamage(fraction,null);
    }
    private void ownDamage(double fraction,JsonObject feedback){
        if(cn.yumis.fight.modules.combat.api.CombatEffect.protectedFromDamage(Minecraft.getInstance().player)){if(feedback!=null)nativeOutcome(feedback,"IMMUNE");return;}
        if(fraction<=0||!Double.isFinite(fraction))return;
        Stats current=snapshot();
        double maximum=current.get(Stat.HEALTH);
        double damage=fraction*maximum*TowerSkillCombat.incoming(current);
        double sharedToSummons=cn.yumis.fight.modules.skills.client.SummonerClient.shareIncomingDamage(current,damage);
        damage=Math.max(0,damage-sharedToSummons);
        if(cn.yumis.fight.modules.skills.client.ElementalistClient.tryAbsorbIceWall(damage)){
            if(feedback!=null&&feedback.has("nativeRoot"))nativeOutcome(feedback,"IMMUNE");
            else if(feedback!=null&&feedback.has("effect")&&feedback.has("effectKind")){
                feedback.addProperty("outcome","IMMUNE");
                feedback.add("damageComponents",JSON.toJsonTree(new double[5]));
                state().report(state().own(),0,false,maximum,0xff5555,false,feedback);
            }
            return;
        }
        if(feedback==null)feedback=new JsonObject();
        feedback.addProperty("outcome","HIT");feedback.addProperty("damageTaken",damage);
        for(String key:new ArrayList<>(state().resources().absorption.keySet()))if(!key.equals("overdrivePower")){
            if(key.startsWith("towerCard/")&&state().resources().absorptionUntil.getOrDefault(key,Long.MAX_VALUE)<=cn.yumis.fight.modules.presentation.api.client.ClientUi.now())continue;
            double value=state().resources().absorption.get(key),take=Math.min(value,damage);
            state().resources().absorption.put(key,value-take);
            damage-=take;
        }
        double absorb=Math.min(state().resources().shield,damage);
        state().resources().shield-=absorb;
        damage-=absorb;
        double mana=Math.min(state().resources().mp,damage*Math.clamp(state().resources().equipment.manaShare,0,1));state().resources().mp-=mana;damage-=mana;
        double[] components=feedback.has("damageComponents")?JSON.fromJson(feedback.get("damageComponents"),double[].class):new double[]{fraction,0,0,0,0};
        double scale=damage/maximum/fraction;for(int i=0;i<components.length;i++)components[i]*=scale;feedback.add("damageComponents",JSON.toJsonTree(components));
        if(damage>0&&feedback.has("selfStunTicks")&&!isSteadfast()){
            long now=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();controls.values().removeIf(until->until<now);String id=feedback.get("controlId").getAsString();if(controls.putIfAbsent(id,now+60000)==null)Minecraft.getInstance().player.addEffect(new net.minecraft.world.effect.MobEffectInstance(cn.yumis.fight.modules.combat.api.CombatContentTypes.STUN,feedback.get("selfStunTicks").getAsInt()));
        }
        boolean triggers=CombatEffect.triggers(feedback);
        double actual=Math.min(damage,state().predicted(state().own())*maximum);
        if(triggers)ownElements(components,damage/maximum,feedback);
        if(damage>0)state().report(state().own(),damage/snapshot().get(Stat.HEALTH),false,snapshot().get(Stat.HEALTH),0xff5555,false,feedback);
        else if(feedback.has("nativeRoot")||feedback.has("feedbackEntity")||feedback.has("effectKind"))state().report(state().own(),0,false,snapshot().get(Stat.HEALTH),0xff5555,false,feedback);
        else state().upload();
        if(triggers&&!feedback.has("armorRetaliation")){
            String attacker=feedback.has("retaliationTarget")?feedback.get("retaliationTarget").getAsString():feedback.has("feedbackEntity")?feedback.get("feedbackEntity").getAsString():"";
            if(!attacker.isEmpty()){
                UUID id=UUID.fromString(attacker);
                TowerSkillCombat.hurt(current,id,actual);
                retaliate(id);
            }
        }
    }
    private void retaliate(UUID attacker){
        var effects=state().resources().equipment;
        if(effects.armorRetaliation<=0)return;
        Stats stats=snapshot();double raw=stats.get(Stat.PHYSICAL_DEFENSE)*effects.armorRetaliation;
        if(raw<=0)return;
        Stats attack=stats.copy();attack.values.put(Stat.PHYSICAL_ATTACK,raw/(1+stats.get(Stat.STRENGTH)/100));
        JsonObject report=new JsonObject();report.addProperty("armorRetaliation",true);
        counterAttack(state().targets().get(attacker),attack,1,new DamageAnalysis.Source("equipment/armor_retaliation","倾塌之墙"),report);
    }
    private boolean guardCounter(Target attacker,JsonObject feedback){
        var effects=state().resources().equipment;
        if(effects.guardCounterChance<=0||random.nextDouble()>=effects.guardCounterChance)return false;

        Stats attack=snapshot();
        feedback.addProperty("outcome","IMMUNE");
        state().report(state().own(),0,false,attack.get(Stat.HEALTH),0xff5555,false,feedback);
        var player=Minecraft.getInstance().player;
        player.playSound(SoundEvents.SHIELD_BLOCK.value(),1,1);
        double coefficient=effects.guardCounterCoefficient*CombatEffect.outgoingScale(player);
        counterAttack(state().targets().getOrDefault(attacker.id,attacker),attack,coefficient,
            new DamageAnalysis.Source("equipment/phantom_guard","卡尔的幻影剑"),new JsonObject());
        return true;
    }
    private void counterAttack(Target target,Stats attack,double coefficient,DamageAnalysis.Source source,JsonObject report){
        var mc=Minecraft.getInstance();
        if(mc.level==null||target==null||target.life.dead||target.damageImmune)return;
        double before=state().predicted(target.life);
        if(before<=0)return;
        if(!(mc.level.getEntity(target.entityId) instanceof LivingEntity entity)||!target.id.equals(entity.getUUID()))return;
        if(!CombatEffect.permitsExternalHitEffects(mc.player,entity))return;
        int level=target.level(state().progress().level,state().encounterSync());
        double defense=target.defense(level,false)*(1-Math.clamp(attack.modifiers.getOrDefault("piercing",0d)+TowerCardEffects.penetration(attack),0,1))*cn.yumis.fight.modules.skills.client.SummonerClient.defenseScale(entity,cn.yumis.fight.modules.presentation.api.client.ClientUi.now());
        double[] resist=EquipmentDamage.resistances(state().resources().equipment,target.resistances());
        if(CombatEffect.permitsExternalHitEffects(Minecraft.getInstance().player,entity)){
            long shredNow=cn.yumis.fight.modules.presentation.api.client.ClientUi.now();
            resist[1]-=cn.yumis.fight.modules.skills.client.WitchClient.fireResistShred(entity,shredNow);
            for(int e=0;e<4;e++)resist[e]-=cn.yumis.fight.modules.skills.client.ElementalistClient.resistShred(entity,e,shredNow)+cn.yumis.fight.modules.skills.client.SunMoonClient.resistShred(entity,e,shredNow);
        }
        var prepared=towerHit(attack,entity,before).apply(attack);
        var hit=TowerCardEffects.forceCritical(prepared,before)
            ? CombatRules.recalculateCritical(towerCritical(prepared,prepared.modifiers.getOrDefault("crit",0d)),defense,resist,false,new int[0],coefficient,false,CombatRules.draw(prepared,false,random))
            : CombatRules.calculate(prepared,defense,resist,false,new int[0],coefficient,false,random);
        if(hit.total()<=0)return;
        double maximum=target.health(level);
        report.add("damageComponents",JSON.toJsonTree(ElementAccumulation.fractions(hit.components(),maximum)));
        DamageAnalysis.attach(report,source);
        state().report(target.life,hit.total()/maximum,false,maximum,0xffffff,hit.critical(),report);
    }
    private void ownElements(double[] components,double fraction,JsonObject report){
        if(fraction<=0)return;
        advanceOwnElements(components,Math.min(fraction,state().predicted(state().own()))/fraction,report);
    }
    private void advanceOwnElements(double[] components,double acceptedScale,JsonObject report){
        var life=state().own();var draft=JSON.fromJson(JSON.toJson(life),Life.class);long now=state().serverNow();
        var accumulated=ElementAccumulation.advance(life.elemental,draft,components,acceptedScale,null,false,false,now);life.elemental=accumulated.state();report.add("elemental",JSON.toJsonTree(life.elemental));JsonArray effects=new JsonArray();
        for(var kind:accumulated.triggered()){
            var change=new JsonObject();change.addProperty("kind",kind.id);change.addProperty("effect",UUID.randomUUID().toString());change.addProperty("strong",false);if(kind.damaging()){change.add("stats",JSON.toJsonTree(new Stats()));change.add("equipment",JSON.toJsonTree(new cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot()));}
            var effect=new JsonObject();effect.addProperty("target",life.target.toString());effect.addProperty("generation",life.generation);effect.addProperty("owner",life.target.toString());effect.addProperty("ownerGeneration",life.generation);effect.addProperty("session",state().session());effect.addProperty("kind",kind.id);effect.addProperty("dimension",life.dimension);effect.addProperty("instance",life.instance);effect.addProperty("expires",life.elemental.until[kind.ordinal()]);effect.add("change",change);effect.addProperty("settleUntil",life.elemental.until[kind.ordinal()]+(kind.damaging()?ElementAccumulation.SETTLEMENT_GRACE:0));effect.addProperty("nextIndex",0);effect.addProperty("nextAt",now+(kind==ElementAccumulation.Kind.IGNITE?0:1000));effects.add(effect);ailment(effect);
            Minecraft.getInstance().player.addEffect(new net.minecraft.world.effect.MobEffectInstance(cn.yumis.fight.modules.combat.api.CombatContentTypes.elementalEffect(kind),(int)Math.max(1,(kind.duration(false)+49)/50)));
        }
        if(!effects.isEmpty())report.add("elementalAilments",effects);
    }
    public boolean nativeAttack(Entity raw){
        var mc=Minecraft.getInstance();
        if(!state().combatAvailable()||cn.yumis.fight.modules.dungeons.api.client.SealBossState.isFrozen())return true;
        LivingEntity target=raw instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragonPart part?part.parentMob:raw instanceof LivingEntity living?living:null;
        if(target==null)return false;
        if(!canTarget(target))return ManagedEntities.neutralCreature(target);
        if(cn.yumis.fight.modules.combat.api.CombatContentTypes.controlled(mc.player))return true;
        var hand=mc.player.getMainHandItem();
        Gear custom=EquipmentItems.gear(hand);
        if(custom!=null)return true;
        if(!hand.isEmpty()&&NativeEquipmentRules.weapon(hand)&&NativeEquipmentRules.level(hand)>state().progress().level)return true;
        double charge=mc.player.getAttackStrengthScale(.5f),coefficient=.2+.8*charge*charge;
        if(mc.player.fallDistance>0&&!mc.player.onGround()&&!mc.player.isInWater()&&!mc.player.isSprinting())coefficient*=1.5;
        if(ItemData.id(hand).equals("mace")&&mc.player.fallDistance>1.5)coefficient+=mc.player.fallDistance*4/Math.max(1,mc.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
        Stats stats=cn.yumis.fight.modules.skills.api.CareerRules.eyeDamage(snapshot(),null).copy();stats.modifiers.put("basicDamageScale",cn.yumis.fight.modules.skills.api.SkillBalanceRules.current().basicScale(state().progress().jobId(),stats.weaponType,false,state().progress().level));
        coefficient*=stats.modifiers.getOrDefault("basicDamageScale",1d);
        double direct=raw instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragonPart part&&!part.name.equals("head")?.25:1;
        hit(target,stats,null,coefficient*direct,true,true,.4,null);
        var player=mc.player;
        for(var nearby:meleeSweep(player.getEyePosition(),Vec3.directionFromRotation(0,player.getYRot()),player.getBoundingBox(),player.entityInteractionRange()))
            if(nearby!=target)hit(nearby,stats,null,coefficient,true,true,.4,null);
        mc.player.resetAttackStrengthTicker();
        swingBasic();
        return true;
    }
    public void projectile(JsonObject j){
        if(!state().ready()||state().own().dead||!j.get("session").getAsString().equals(state().session())||j.get("generation").getAsLong()!=state().own().generation||!incomingRoots.add(j.get("root").getAsString()))return;
        if(incomingRoots.size()>2048)incomingRoots.clear();
        if(j.has("source")&&j.get("source").getAsString().equals("thrown"))return;
        Target t=JSON.fromJson(j.get("target"),Target.class);
        state().targets().put(t.id,t);
        var mc=Minecraft.getInstance();
        var hand=j.has("weapon")?net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.parse(mc.player.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),j.get("weapon")).getOrThrow():net.minecraft.world.item.ItemStack.EMPTY;
        Gear custom=EquipmentItems.gear(hand);
        if(custom!=null&&!custom.valid(state().progress(),cn.yumis.fight.modules.equipment.api.EquipmentEligibility.allowance(Minecraft.getInstance().player))||custom==null&&NativeEquipmentRules.weapon(hand)&&NativeEquipmentRules.level(hand)>state().progress().level)return;
        var raw=mc.level.getEntity(t.entityId);
        if(raw instanceof LivingEntity living){
            var launch=j.has("projectileId")?nativeLaunches.get(UUID.fromString(j.get("projectileId").getAsString())):null;
            Stats stats=launch!=null?launch.stats.copy():SkillPreview.currentStats(state().weaponStats(hand),state().resources(),cn.yumis.fight.modules.presentation.api.client.ClientUi.now());
            if(launch==null)stats.modifiers.put("basicDamageScale",cn.yumis.fight.modules.skills.api.SkillBalanceRules.current().basicScale(state().progress().jobId(),stats.weaponType,false,state().progress().level));
            SkillDiagnostics.attack.accept(ItemData.id(hand),stats.get(Stat.PHYSICAL_ATTACK));
            String id=ItemData.id(hand);
            double coefficient=1;
            if(id.endsWith("_spear"))coefficient=Math.max(.5,mc.player.getDeltaMovement().subtract(living.getDeltaMovement()).length()*3);
            else if(Set.of("bow","crossbow").contains(id))coefficient=Math.clamp((j.has("speed")?j.get("speed").getAsDouble():3)/3,0,1);
            if(living instanceof net.minecraft.world.entity.boss.wither.WitherBoss wither&&(wither.getInvulnerableTicks()>0||state().predicted(t.life)<=.5&&Set.of("bow","crossbow").contains(id)))return;
            var previousEquipment=hitEquipment;if(launch!=null)hitEquipment=launch.effects;try{hit(living,stats,null,coefficient,false,true,.4,null,j);}finally{hitEquipment=previousEquipment;}
        }
    }
    public boolean boss(JsonObject j){
        String id=j.get("id").getAsString();
        if(bossHit.contains(id))return false;
        if(j.has("clientContact")&&j.get("clientContact").getAsBoolean()){
            var mc=Minecraft.getInstance();
            if(!state().ready()||state().own().dead||mc.level==null||mc.player==null||mc.player.isDeadOrDying())return false;
            Target source=JSON.fromJson(j.get("boss"),Target.class);
            if(!source.dimension.equals(mc.level.dimension().identifier().toString())||!source.instance.equals(state().own().instance))return false;
            bossHit.add(id);
            return receiveBossHit(j,source);
        }
        bosses.put(id,j);
        return false;
    }
    /** The local contact owner has already resolved geometry; all incoming damage uses this same calculation. */
    private boolean receiveBossHit(JsonObject j,Target boss){
        var p=Minecraft.getInstance().player;
        if(boss.tower!=null&&!state().combatAvailable())return false;
        if(invulnerable()||cn.yumis.fight.modules.combat.api.CombatEffect.protectedFromDamage(p)||p.getAbilities().invulnerable||p.isSpectator())return false;
        int level=boss.level(state().progress().level,state().encounterSync());
        double points=boss.attack(level)*j.get("coefficient").getAsDouble();
        JsonObject feedback=new JsonObject();feedback.addProperty("feedbackEntity",boss.id.toString());
        if(j.has("treasureHit"))feedback.addProperty("treasureAction",j.get("id").getAsString());
        if(j.has("encounterHit"))feedback.addProperty("encounterAction",j.get("id").getAsString());
        if(j.has("occurredAt"))feedback.add("occurredAt",j.get("occurredAt"));
        if(points>0&&guardCounter(boss,feedback))return false;
        if(CombatRules.evades(snapshot().get(Stat.EVASION),random::nextDouble)){TalentCombat.dodged();cn.yumis.fight.modules.skills.client.SoulBenderClient.onEvade();return false;}
        if(j.has("ritualStunTicks")&&!isSteadfast())
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(cn.yumis.fight.modules.combat.api.CombatContentTypes.STUN,j.get("ritualStunTicks").getAsInt()));
        if(j.has("ritualRootTicks"))
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(cn.yumis.fight.modules.combat.api.CombatContentTypes.RITUAL_ROOT,j.get("ritualRootTicks").getAsInt()));
        if(j.has("ritualSlowTicks"))
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS,j.get("ritualSlowTicks").getAsInt(),j.get("ritualSlowAmplifier").getAsInt()));
        if(j.has("ritualDebtTicks")){
            var effect=cn.yumis.fight.modules.combat.api.CombatContentTypes.RITUAL_DEBT;
            var previous=p.getEffect(effect);
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect,j.get("ritualDebtTicks").getAsInt(),previous==null?0:Math.min(2,1+previous.getAmplifier())));
        }
        if(j.has("ritualHealDownTicks"))
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(cn.yumis.fight.modules.combat.api.CombatContentTypes.HEAL_DOWN,j.get("ritualHealDownTicks").getAsInt()));
        if(j.has("selfStunTicks")){feedback.add("selfStunTicks",j.get("selfStunTicks"));feedback.add("controlId",j.get("controlId"));}
        if(points<=0)return true;
        int element=j.get("element").getAsInt();
        double[] components=monsterComponents(points,j.get("magic").getAsBoolean(),element,level);
        double multiplier=NativeEquipmentRules.receivedMultiplier(p,"bossAction",false,false,false);for(int i=0;i<components.length;i++)components[i]*=multiplier;
        double maximum=snapshot().get(Stat.HEALTH);double[] fractions=ElementAccumulation.fractions(components,maximum);double fraction=points>0?minimumEnemyFraction(fractions,maximum,element):Arrays.stream(fractions).sum();feedback.add("damageComponents",JSON.toJsonTree(fractions));
        ownDamage(fraction,feedback);
        return true;
    }
    public void cancelBoss(String id){
        bosses.remove(id);
        bossHit.add(id);
    }
    private void tickBoss(long now){
        for(var entry:new ArrayList<>(bosses.entrySet())){
            JsonObject j=entry.getValue();
            Target source=JSON.fromJson(j.get("boss"),Target.class);
            if(!source.dimension.equals(Minecraft.getInstance().level.dimension().identifier().toString())||!source.instance.equals(state().own().instance)){
                bosses.remove(entry.getKey());
                continue;
            }
            long elapsed=state().serverNow()-j.get("startedAt").getAsLong();
            long windup=j.get("windupTicks").getAsLong()*50,active=j.get("activeTicks").getAsLong()*50;
            Vec3 center=new Vec3(j.get("x").getAsDouble(),j.get("y").getAsDouble(),j.get("z").getAsDouble());
            double radius=j.get("radius").getAsDouble();
            if(elapsed<windup){
                continue;
            }
            if(elapsed>windup+active+1000){
                bosses.remove(entry.getKey());
                continue;
            }
            if(elapsed>=windup&&elapsed<=windup+active&&!bossHit.contains(entry.getKey())&&!j.get("kind").getAsString().equals("CAGE")){
                var p=Minecraft.getInstance().player;
                Vec3 offset=p.position().subtract(center),direction=new Vec3(j.get("dx").getAsDouble(),0,j.get("dz").getAsDouble());
                double half=j.get("halfAngle").getAsDouble();
                String shape=j.has("shape")?j.get("shape").getAsString():"FAN";
                double forward=offset.dot(direction),side=Math.abs(offset.x*direction.z-offset.z*direction.x);
                boolean geometry=shape.equals("SQUARE")?Math.abs(offset.x)<=radius+.3&&Math.abs(offset.z)<=radius+.3:shape.equals("RECT")?forward>=-.3&&forward<=radius+.3&&side<=(j.has("width")?j.get("width").getAsDouble():2)*.5+.3:offset.horizontalDistanceSqr()<=radius*radius&&(shape.equals("CIRCLE")||direction.lengthSqr()<.001||offset.lengthSqr()<.001||horizontal(offset).dot(direction.normalize())>=Math.cos(half));
                if(geometry&&Math.abs(offset.y)<3&&visible(center.add(0,1,0),p.getEyePosition())){
                    bossHit.add(entry.getKey());
                    receiveBossHit(j,source);
                }
            }
        }
    }
    /** Keep report identities for lifecycle retirement; local clocks schedule every contact. */
    public void reported(JsonObject packet){
        TrainingCombat.reported(packet);
        if(packet.has("sourceEffects"))equipmentReceipts.put(packet.get("operation").getAsLong(),packet.deepCopy());
        if(state().ready()&&packet.has("effectKind")&&state().session().equals(packet.get("session").getAsString())&&state().own().generation==packet.get("sourceGeneration").getAsLong())pendingElemental.put(packet.get("operation").getAsLong(),packet.get("effect").getAsString());
        if(!state().ready()||!packet.has("ailment")||packet.get("heal").getAsBoolean()||packet.get("fraction").getAsDouble()<=0)return;
        JsonObject change=packet.getAsJsonObject("ailment");if(!change.get("kind").getAsString().equals("BLEED"))return;
        if(!state().session().equals(packet.get("session").getAsString())||state().own().generation!=packet.get("sourceGeneration").getAsLong())return;
        UUID target=UUID.fromString(packet.get("target").getAsString());Target known=state().targets().get(target);
        if(target.equals(state().own().target)||known==null)return;
        pendingBleeds.put(packet.get("operation").getAsLong(),new BleedRefresh(state().session(),state().own().generation,target,packet.get("generation").getAsLong(),change.get("effect").getAsString()));
    }
    public void ack(JsonObject packet){
        TrainingCombat.ack(packet);
        var source=equipmentReceipts.remove(packet.get("operation").getAsLong());
        if(source!=null&&state().ready()&&state().session().equals(packet.get("session").getAsString())&&source.get("sourceGeneration").getAsLong()==state().own().generation){var receipt=JSON.fromJson(packet.get("receipt"),cn.yumis.fight.modules.combat.api.Receipt.class);if(receipt.accepted()&&!receipt.deathId().isEmpty()){var target=state().targets().get(UUID.fromString(source.get("target").getAsString()));if(target!=null){var actor=JSON.fromJson(JSON.toJson(state().own()),Life.class);double before=actor.q=state().predicted(state().own());var effects=JSON.fromJson(source.get("sourceEffects"),cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot.class);TalentCombat.killed();double talentHeal=snapshot().modifiers.getOrDefault(TalentCombat.consumeSummonKill(target.id)?"talentKillHealSummon":"talentKillHeal",0d);cn.yumis.fight.modules.combat.api.EquipmentHitEffects.apply(target,receipt,false,snapshot(),snapshot().get(Stat.HEALTH),effects,actor,state().resources(),target.health(target.level(state().progress().level,state().encounterSync())),cn.yumis.fight.modules.presentation.api.client.ClientUi.now());if(target.rewardsEligible()&&talentHeal>0)cn.yumis.fight.modules.combat.api.LifeRules.apply(actor,talentHeal,true);if(actor.q>before)state().report(state().own(),actor.q-before,true,snapshot().get(Stat.HEALTH),0x8ef5ad,false,null);state().upload();}}}

        if(state().ready()&&state().session().equals(packet.get("session").getAsString())){
            String elemental=pendingElemental.remove(packet.get("operation").getAsLong());
            if(elemental!=null&&!packet.getAsJsonObject("receipt").get("accepted").getAsBoolean())finishElemental(elemental);
        }
        BleedRefresh pending=pendingBleeds.get(packet.get("operation").getAsLong());if(pending==null)return;
        if(!pending.session.equals(packet.get("session").getAsString())||!pending.session.equals(state().session())||pending.sourceGeneration!=state().own().generation)return;
        if(packet.has("target")&&!pending.target.toString().equals(packet.get("target").getAsString())||packet.has("generation")&&pending.generation!=packet.get("generation").getAsLong()||packet.has("ownerLife")&&pending.sourceGeneration!=packet.getAsJsonObject("ownerLife").get("generation").getAsLong())return;
        JsonObject receipt=packet.getAsJsonObject("receipt");
        // Accepted live damage waits for the existing ailment push. Rejection/zero damage/death cannot apply it.
        if(!receipt.get("accepted").getAsBoolean()||receipt.get("actual").getAsDouble()<=0||receipt.get("q").getAsDouble()<=0)pendingBleeds.remove(packet.get("operation").getAsLong());
    }
    private void confirmedBleed(JsonObject incoming){
        pendingBleeds.values().removeIf(p->p.session.equals(incoming.get("session").getAsString())&&p.sourceGeneration==incoming.get("ownerGeneration").getAsLong()&&p.target.toString().equals(incoming.get("target").getAsString())&&p.generation==incoming.get("generation").getAsLong()&&p.effect.equals(incoming.getAsJsonObject("change").get("effect").getAsString()));
    }
    public void ailment(JsonObject j){
        if(!j.get("owner").getAsString().equals(Minecraft.getInstance().player.getStringUUID())||!j.get("session").getAsString().equals(state().session()))return;
        if(j.get("ownerGeneration").getAsLong()!=state().own().generation)return;
        var elemental=ElementAccumulation.kind(j.get("kind").getAsString());
        if(elemental!=null){
            if(elemental.damaging()){
                String effect=j.getAsJsonObject("change").get("effect").getAsString();
                elementalDots.put(effect,refreshBleed(elementalDots.get(effect),j,cn.yumis.fight.modules.presentation.api.client.ClientUi.now(),state().serverNow()));
            }
            return;
        }
        if(!j.get("kind").getAsString().equals("BLEED"))return;
        String key=j.get("target").getAsString();
        Bleed old=bleeds.get(key);
        bleeds.put(key,refreshBleed(old,j,cn.yumis.fight.modules.presentation.api.client.ClientUi.now(),state().serverNow()));
        confirmedBleed(j);
    }
    private static Bleed refreshBleed(Bleed old,JsonObject incoming,long now,long serverNow){
        int nextIndex=incoming.get("nextIndex").getAsInt();long next=now+Math.max(0,incoming.get("nextAt").getAsLong()-serverNow);
        boolean same=old!=null&&old.state.getAsJsonObject("change").get("effect").equals(incoming.getAsJsonObject("change").get("effect"))
            &&java.util.stream.Stream.of("session","ownerGeneration","generation").allMatch(key->old.state.get(key).equals(incoming.get(key)));
        // A context replay may lag a report already sent by this client. It cannot rewind that sequence.
        if(same&&old.index>=nextIndex){nextIndex=old.index;next=old.index>incoming.get("nextIndex").getAsInt()?old.next:Math.max(old.next,next);}
        return new Bleed(incoming.deepCopy(),next,nextIndex);
    }
    private void tickBleed(long now){
        for(var entry:new ArrayList<>(bleeds.entrySet())){
            Bleed b=entry.getValue();
            JsonObject j=b.state;
            Target target=state().targets().get(UUID.fromString(j.get("target").getAsString()));
            var entity=target==null||Minecraft.getInstance().level==null?null:Minecraft.getInstance().level.getEntity(target.entityId);
            if(entity instanceof cn.yumis.fight.modules.dungeons.api.TrainingSandbag bag&&bag.timed()&&bag.finished()){
                bleeds.remove(entry.getKey());pendingBleeds.values().removeIf(p->p.target.toString().equals(entry.getKey()));continue;
            }
            if(target==null||entity instanceof LivingEntity living&&!canTarget(living)||target.life.dead||target.life.generation!=j.get("generation").getAsLong()||j.get("expires").getAsLong()<state().serverNow()||state().own().dead){
                bleeds.remove(entry.getKey());
                pendingBleeds.values().removeIf(p->p.target.toString().equals(entry.getKey()));
                continue;
            }
            if(now>=b.next&&b.index<10){
                JsonObject extra=new JsonObject();
                JsonObject change=j.getAsJsonObject("change");
                extra.add("effect",change.get("effect"));
                cn.yumis.fight.modules.combat.api.client.DamageAnalysis.attach(extra,cn.yumis.fight.modules.combat.api.client.DamageAnalysis.effect(change.get("effect").getAsString()));
                extra.addProperty("index",b.index);
                if(target.training()&&!target.timedTraining)TrainingCombat.bleed(target,j,extra);
                else{
                    Stats source=snapshot();
                    boolean critical=TowerCardEffects.forceCritical(source,state().predicted(target.life));
                    double fraction=change.get("fraction").getAsDouble()*(critical?TowerCardEffects.criticalMultiplier(source):1);
                    state().report(target.life,fraction,false,target.health(target.level(state().progress().level,state().encounterSync())),0xe05360,critical,extra);
                }
                bleeds.put(entry.getKey(),new Bleed(j,now+1000,b.index+1));
            }
        }
    }
    private void finishElemental(String effect){
        elementalDots.remove(effect);pendingElemental.values().removeIf(effect::equals);
        TrainingCombat.release("element/"+effect,"element");TrainingCombat.actionFinished("element/"+effect);
    }
    private void tickElemental(long now){
        var mc=Minecraft.getInstance();
        for(var entry:new ArrayList<>(elementalDots.entrySet())){
            String effect=entry.getKey();Bleed pending=entry.getValue();JsonObject j=pending.state;
            var kind=ElementAccumulation.kind(j.get("kind").getAsString());
            UUID id=UUID.fromString(j.get("target").getAsString());boolean own=id.equals(state().own().target);
            Target target=own?null:state().targets().get(id);Life life=own?state().own():target==null?null:target.life;
            var entity=own?mc.player:target==null||mc.level==null?null:mc.level.getEntity(target.entityId);
            if(life==null||entity==null||!entity.isAlive()||!own&&entity instanceof LivingEntity living&&!canTarget(living)||life.dead||state().own().dead||!state().session().equals(j.get("session").getAsString())||state().own().generation!=j.get("ownerGeneration").getAsLong()||life.generation!=j.get("generation").getAsLong()||!life.dimension.equals(j.get("dimension").getAsString())||!life.instance.equals(j.get("instance").getAsString())||j.get("settleUntil").getAsLong()<state().serverNow()||entity instanceof cn.yumis.fight.modules.dungeons.api.TrainingSandbag bag&&bag.timed()&&bag.finished()){
                finishElemental(effect);continue;
            }
            if(pending.index>=kind.ticks()){finishElemental(effect);continue;}
            if(now<pending.next)continue;
            JsonObject change=j.getAsJsonObject("change");boolean strong=change.get("strong").getAsBoolean();
            double maximum=own?snapshot().get(Stat.HEALTH):target.health(target.level(state().progress().level,state().encounterSync()));
            Stats source=own?null:JSON.fromJson(change.get("stats"),Stats.class);
            double fraction;
            if(kind==ElementAccumulation.Kind.EROSION)fraction=ElementAccumulation.erosionFraction(strong);
            else if(own)fraction=monsterDamage(maximum*.30,true,1,state().progress().level)/maximum*ElementAccumulation.magicTaken(life.elemental,life,false,state().serverNow());
            else{
                var equipment=JSON.fromJson(change.get("equipment"),cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot.class);
                double defense=target.defense(target.level(state().progress().level,state().encounterSync()),true);
                if(cn.yumis.fight.modules.combat.api.CombatEffect.permitsExternalHitEffects(mc.player,(LivingEntity)entity)&&cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.view.green(entity.getX(),entity.getY(),entity.getZ(),state().serverNow()))defense*=.85;
                defense*=1-Math.clamp(TowerCardEffects.penetration(source),0,1);
                double[] resist=cn.yumis.fight.modules.combat.api.EquipmentDamage.resistances(equipment,target.resistances());
                if(cn.yumis.fight.modules.combat.api.CombatEffect.permitsExternalHitEffects(mc.player,(LivingEntity)entity)){
                    resist[1]-=cn.yumis.fight.modules.skills.client.WitchClient.fireResistShred((LivingEntity)entity,now);
                    for(int e=0;e<4;e++)resist[e]-=cn.yumis.fight.modules.skills.client.ElementalistClient.resistShred((LivingEntity)entity,e,now)+cn.yumis.fight.modules.skills.client.SunMoonClient.resistShred((LivingEntity)entity,e,now);
                }
                fraction=cn.yumis.fight.modules.combat.api.EquipmentDamage.allowed(equipment,new int[]{1})?ElementAccumulation.igniteFraction(strong,defense,source.get(Stat.FIRE),resist[1],ElementAccumulation.magicTaken(life.elemental,life,strong,state().serverNow())):0;
            }
            if(!own){
                if(source!=null)fraction*=towerHit(source,(LivingEntity)entity,state().predicted(life)).fixedMultiplier();
                fraction*=cn.yumis.fight.modules.combat.api.CombatEffect.outgoingScale(mc.player);
            }
            boolean critical=!own&&source!=null&&TowerCardEffects.forceCritical(source,state().predicted(life));
            if(critical)fraction*=TowerCardEffects.criticalMultiplier(source);
            if(own&&(invulnerable()||cn.yumis.fight.modules.combat.api.CombatEffect.protectedFromDamage(mc.player)||mc.player.getAbilities().invulnerable||mc.player.isSpectator()||kind==ElementAccumulation.Kind.IGNITE&&mc.player.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE)))fraction=0;
            double[] components=new double[5];components[kind==ElementAccumulation.Kind.IGNITE?2:0]=fraction;
            JsonObject extra=new JsonObject();extra.addProperty("effect",effect);extra.addProperty("effectKind",kind.id);extra.addProperty("index",pending.index);extra.add("damageComponents",JSON.toJsonTree(components));
            cn.yumis.fight.modules.combat.api.client.DamageAnalysis.attach(extra,new cn.yumis.fight.modules.combat.api.client.DamageAnalysis.Source("yumis_dungeon_and_fight:"+kind.id.toLowerCase(Locale.ROOT),kind.title));
            if(own&&fraction>0)ownDamage(fraction,extra);
            else if(target!=null&&target.training()&&!target.timedTraining){
                double[] values=components.clone();for(int i=0;i<values.length;i++)values[i]*=maximum;
                TrainingCombat.begin("element/"+effect,kind.title);TrainingCombat.hold("element/"+effect,"element",now+Math.max(0,j.get("settleUntil").getAsLong()-state().serverNow()));
                TrainingCombat.report("element/"+effect,kind.title,target,values,fraction*maximum,kind.color,false,extra);
            }
            else state().report(life,fraction,false,maximum,kind.color,critical,extra);
            elementalDots.put(effect,new Bleed(j,pending.next+(kind==ElementAccumulation.Kind.IGNITE?0:1000),pending.index+1));
        }
    }
}
