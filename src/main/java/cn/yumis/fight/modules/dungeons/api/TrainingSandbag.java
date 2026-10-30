package cn.yumis.fight.modules.dungeons.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.syncher.*;

/** Original freestanding practice target. Combat health belongs to Target, not vanilla health. */
public final class TrainingSandbag extends Mob implements Enemy {
    private static final EntityDataAccessor<Boolean> TIMED=SynchedEntityData.defineId(TrainingSandbag.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FINISHED=SynchedEntityData.defineId(TrainingSandbag.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> OWNER=SynchedEntityData.defineId(TrainingSandbag.class,EntityDataSerializers.STRING);
    public TrainingSandbag(EntityType<? extends TrainingSandbag> type,Level level){
        super(type,level);setPersistenceRequired();setNoAi(true);setCanPickUpLoot(false);xpReward=0;
    }
    public static AttributeSupplier.Builder createAttributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,20).add(Attributes.MOVEMENT_SPEED,0).add(Attributes.KNOCKBACK_RESISTANCE,1).add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE,1);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(TIMED,false);builder.define(FINISHED,false);builder.define(OWNER,"");}
    public void timed(String owner){entityData.set(TIMED,true);entityData.set(OWNER,owner);getPersistentData().putBoolean("timed_training",true);getPersistentData().putString("sandbag_owner",owner);}
    public boolean timed(){return entityData.get(TIMED)||getPersistentData().getBooleanOr("timed_training",false);}
    public boolean finished(){return entityData.get(FINISHED);}
    public boolean ownedBy(Player player){return timed()&&entityData.get(OWNER).equals(player.getStringUUID());}
    public void finish(){entityData.set(FINISHED,true);setInvisible(true);}
    @Override public boolean isPickable(){return !finished()&&super.isPickable();}
    @Override public void tick(){super.tick();if(level() instanceof ServerLevel&&timed()&&!TrainingActions.exists(this))discard();}
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public void move(MoverType type,Vec3 movement){super.move(type,new Vec3(0,movement.y,0));}
    @Override protected InteractionResult mobInteract(Player player,InteractionHand hand){
        if(!player.getItemInHand(hand).isEmpty())return InteractionResult.PASS;
        if(!(level() instanceof ServerLevel level))return InteractionResult.SUCCESS;
        if(timed()){
            if(player.isShiftKeyDown()&&player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)TrainingActions.cancel(serverPlayer,this);
            else player.sendOverlayMessage(Component.literal("计时沙袋：Shift＋空手右键取消测试"));
            return InteractionResult.SUCCESS;
        }
        if(player.isShiftKeyDown()){
            String owner=getPersistentData().getStringOr("sandbag_owner","");
            if(!owner.isEmpty()&&!owner.equals(player.getStringUUID())&&!player.getAbilities().instabuild){player.sendOverlayMessage(Component.literal("只能收回自己放置的训练沙袋"));return InteractionResult.FAIL;}
            if(!player.getAbilities().instabuild){var item=new ItemEntity(level,getX(),getY()+.3,getZ(),new ItemStack(DungeonContentTypes.TRAINING_SANDBAG_ITEM.get()));item.setDefaultPickUpDelay();if(!level.addFreshEntity(item))return InteractionResult.FAIL;}
            discard();
        }else player.sendOverlayMessage(Component.literal("训练沙袋：零双防、零元素抗性、无限耐久；Shift＋空手右键收回"));
        return InteractionResult.SUCCESS;
    }
}
