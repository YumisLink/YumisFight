package cn.yumis.fight.modules.npc.api;

import cn.yumis.fight.modules.combat.api.WorldVitalEntity;
import cn.yumis.fight.modules.economy.api.ShopMenu;
import cn.yumis.fight.modules.presentation.api.ModText;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Predicate;

/** Friendly shop host; normalized vanilla health only projects the formal world life. */
public final class NpcEntity extends PathfinderMob implements WorldVitalEntity {
    public static final int LEVEL=120;
    public static final double MAXIMUM_HEALTH=10_000_000;
    private static final EntityDataAccessor<String> SKIN=SynchedEntityData.defineId(NpcEntity.class,EntityDataSerializers.STRING);
    private static Predicate<Entity> additionalPlayerOwned=entity->false;
    private int waitTicks;
    public NpcEntity(EntityType<? extends NpcEntity> type,Level level){super(type,level);setPersistenceRequired();setCanPickUpLoot(false);xpReward=0;waitTicks=nextWait();}
    public static AttributeSupplier.Builder attributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,20).add(Attributes.MOVEMENT_SPEED,.25).add(Attributes.FOLLOW_RANGE,12).add(Attributes.KNOCKBACK_RESISTANCE,1).add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE,1);}
    public static void playerOwnedSources(Predicate<Entity> predicate){additionalPlayerOwned=Objects.requireNonNull(predicate);}
    public static boolean playerOwned(Entity entity){return playerOwned(entity,new HashSet<>());}
    public static void rememberPlayerSource(Entity entity){if(playerOwned(entity))entity.getPersistentData().putBoolean("npc_player_source",true);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(SKIN,"zimin");}
    public String skin(){return entityData.get(SKIN);}
    public void initialize(String definition,String skin){
        NpcInteractionComponent.attach(this,definition);entityData.set(SKIN,skin);setHomeTo(blockPosition(),32);
        setCustomName(ModText.of(NpcDefinitions.current().require(definition).name()+" Lv."+LEVEL));setCustomNameVisible(true);
    }
    @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(2,new SlowWander());goalSelector.addGoal(5,new LookAtPlayerGoal(this,Player.class,8));goalSelector.addGoal(6,new RandomLookAroundGoal(this));}
    private int nextWait(){return 6000+getRandom().nextInt(6001);}
    public boolean trading(){return level() instanceof ServerLevel server&&server.getServer().getPlayerList().getPlayers().stream().anyMatch(p->p.level()==level()&&p.containerMenu instanceof ShopMenu menu&&menu.host.equals(getUUID()));}
    @Override protected void customServerAiStep(ServerLevel level){
        super.customServerAiStep(level);var definition=NpcInteractionComponent.query(this);
        if(definition!=null){String title=definition.name()+" Lv."+LEVEL;if(getCustomName()==null||!getCustomName().getString().equals(title))setCustomName(ModText.of(title));}
        if(trading())getNavigation().stop();else if(getNavigation().isDone()&&waitTicks>0)waitTicks--;
    }
    @Override public boolean isWithinHome(BlockPos p){return isWithinHome(Vec3.atCenterOf(p));}
    @Override public boolean isWithinHome(Vec3 p){var h=getHomePosition();return !hasHome()||Math.pow(p.x-h.getX()-.5,2)+Math.pow(p.z-h.getZ()-.5,2)<=32*32;}
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public boolean canBeLeashed(){return false;}
    @Override public boolean canUsePortal(boolean ignorePassenger){return false;}
    @Override public int combatLevel(){return LEVEL;}
    @Override public double combatMaximumHealth(){return MAXIMUM_HEALTH;}
    @Override public boolean rejectsNativeDamage(DamageSource source){return playerOwned(source.getEntity(),new HashSet<>())||playerOwned(source.getDirectEntity(),new HashSet<>());}
    private static boolean playerOwned(Entity entity,Set<UUID> seen){
        if(entity==null||!seen.add(entity.getUUID()))return false;
        if(entity instanceof Player||additionalPlayerOwned.test(entity)||entity.getPersistentData().getBooleanOr("npc_player_source",false))return true;
        if(entity instanceof OwnableEntity own){if(own.getOwnerReference()!=null&&own.getOwner()==null)return true;if(playerOwned(own.getOwner(),seen))return true;}
        if(entity instanceof Projectile projectile&&playerOwned(projectile.getOwner(),seen))return true;
        if(entity instanceof AreaEffectCloud cloud&&playerOwned(cloud.getOwner(),seen))return true;
        return entity instanceof PrimedTnt tnt&&playerOwned(tnt.getOwner(),seen);
    }
    @Override protected void addAdditionalSaveData(ValueOutput output){super.addAdditionalSaveData(output);output.putString("npc_skin",skin());output.putInt("npc_wait",waitTicks);}
    @Override protected void readAdditionalSaveData(ValueInput input){super.readAdditionalSaveData(input);entityData.set(SKIN,input.getStringOr("npc_skin","zimin"));waitTicks=Math.max(0,input.getIntOr("npc_wait",nextWait()));}
    private final class SlowWander extends Goal {
        private Path path;
        SlowWander(){setFlags(EnumSet.of(Flag.MOVE));}
        @Override public boolean canUse(){
            if(waitTicks>0||trading()||!getNavigation().isDone())return false;
            for(int attempt=0;attempt<12;attempt++){
                var destination=LandRandomPos.getPos(NpcEntity.this,8,3);if(destination==null||!isWithinHome(destination)||position().distanceToSqr(destination)<9||position().distanceToSqr(destination)>64)continue;
                path=getNavigation().createPath(destination.x,destination.y,destination.z,0);if(path==null||!path.canReach())continue;
                boolean inside=true;double length=0;var previous=position();for(int i=0;i<path.getNodeCount();i++){var node=path.getNode(i).asBlockPos();if(!isWithinHome(node)){inside=false;break;}var next=Vec3.atBottomCenterOf(node);length+=previous.distanceTo(next);previous=next;}if(length>8)inside=false;
                if(inside)return true;
            }
            waitTicks=nextWait();return false;
        }
        @Override public void start(){getNavigation().moveTo(path,.6);}
        @Override public boolean canContinueToUse(){return !getNavigation().isDone()&&!trading();}
        @Override public void stop(){getNavigation().stop();waitTicks=nextWait();}
    }
}
