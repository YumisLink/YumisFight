package cn.yumis.fight.modules.dungeons.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/** Normal server item placement; no custom packet or second inventory model. */
public final class TrainingSandbagItem extends Item {
    public TrainingSandbagItem(Properties properties){super(properties);}
    @Override public InteractionResult useOn(UseOnContext context){
        var player=context.getPlayer();if(player==null)return InteractionResult.PASS;
        if(context.getLevel().isClientSide()){
            if(player.isShiftKeyDown())TrainingActions.clientUse(context);
            return InteractionResult.SUCCESS;
        }
        if(!(context.getLevel() instanceof ServerLevel level))return InteractionResult.SUCCESS;
        if(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer&&TrainingActions.consumeVanillaUse(serverPlayer,context))return InteractionResult.SUCCESS;
        if(player.isShiftKeyDown())return InteractionResult.SUCCESS;
        BlockPos pos=context.getClickedPos().relative(context.getClickedFace());
        if(!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,context.getClickedFace(),context.getItemInHand()))return InteractionResult.FAIL;
        if(!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),net.minecraft.core.Direction.UP))return InteractionResult.FAIL;
        var bag=DungeonContentTypes.TRAINING_SANDBAG.get().create(level,EntitySpawnReason.SPAWN_ITEM_USE);if(bag==null)return InteractionResult.FAIL;
        bag.setPos(Vec3.atBottomCenterOf(pos));bag.setYRot(player.getYRot()+180);bag.setYBodyRot(bag.getYRot());
        if(!level.getWorldBorder().isWithinBounds(bag.getBoundingBox())||!level.noCollision(bag)||!level.getEntities(bag,bag.getBoundingBox()).isEmpty())return InteractionResult.FAIL;
        bag.getPersistentData().putString("sandbag_owner",player.getStringUUID());
        if(!level.addFreshEntity(bag))return InteractionResult.FAIL;
        if(!player.getAbilities().instabuild)context.getItemInHand().shrink(1);
        return InteractionResult.SUCCESS;
    }
}
