package cn.yumis.fight.modules.industry.client;

import cn.yumis.fight.modules.industry.platform.ArtificialStarPartBlock;
import cn.yumis.fight.modules.industry.platform.MachineBlock;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.util.function.DoubleConsumer;

/** The local player judges contact; bootstrap supplies the existing incoming-damage path. */
public final class ArtificialStarHazard {
    private static Object world,player;
    private static String session="";
    private static long nextDamage;
    private ArtificialStarHazard(){}
    public static void register(DoubleConsumer receiveDamage){NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event)->tick(receiveDamage));}
    private static void tick(DoubleConsumer receiveDamage){
        var mc=Minecraft.getInstance();String current=ClientUi.current().session();
        if(world!=mc.level||player!=mc.player||!session.equals(current)){world=mc.level;player=mc.player;session=current;nextDamage=0;}
        var own=mc.player;
        if(mc.level==null||own==null||!ClientUi.current().ready()||mc.isPaused()||own.isDeadOrDying()||own.isSpectator()||own.getAbilities().invulnerable||!own.onGround())return;
        long now=ClientUi.now();if(now<nextDamage)return;
        var bounds=own.getBoundingBox();var feet=new AABB(bounds.minX,bounds.minY-.025,bounds.minZ,bounds.maxX,bounds.minY+.001,bounds.maxZ);
        VoxelShape contact=Shapes.create(feet);var context=CollisionContext.of(own);
        for(BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(feet.minX,feet.minY,feet.minZ),BlockPos.containing(feet.maxX,feet.maxY,feet.maxZ))){
            var state=mc.level.getBlockState(pos);
            if(!(state.getBlock() instanceof ArtificialStarPartBlock)&&!ArtificialStarPartBlock.isStar(state))continue;
            BlockPos center=ArtificialStarPartBlock.base(mc.level,pos);if(!mc.level.hasChunkAt(center))continue;
            var controller=mc.level.getBlockState(center);if(!ArtificialStarPartBlock.isStar(controller)||!controller.getValue(MachineBlock.ACTIVE))continue;
            var pedestal=state.getCollisionShape(mc.level,pos,context).move(pos.getX(),pos.getY(),pos.getZ());
            if(!Shapes.joinIsNotEmpty(pedestal,contact,BooleanOp.AND))continue;
            // One cooldown spans all pedestal cells and remains in force across brief exits.
            nextDamage=now+ArtificialStarPartBlock.HAZARD_INTERVAL_MILLIS;
            receiveDamage.accept(ArtificialStarPartBlock.HAZARD_FRACTION);return;
        }
    }
}
