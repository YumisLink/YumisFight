package cn.yumis.fight.modules.industry.platform;
import cn.yumis.fight.modules.industry.api.IndustrialRules;

import cn.yumis.fight.modules.industry.api.DeviceType;

import com.google.gson.*;

import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;

import net.minecraft.server.level.*;

import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.saveddata.*;

import net.neoforged.neoforge.capabilities.Capabilities;

import net.neoforged.neoforge.transfer.*;
import net.neoforged.neoforge.transfer.item.*;
import net.neoforged.neoforge.transfer.fluid.*;
import net.neoforged.neoforge.transfer.transaction.*;
import java.util.*;

/** Industrial module implementation; owns one scheduling responsibility. */
public final class IndustrialFunctions {
    public abstract static class FunctionJob {final MachineBlockEntity be;boolean done;FunctionJob(MachineBlockEntity b){be=b;}abstract long demand();abstract long run(long budget);}
    private final Map<String,FunctionJob> pendingFunctions=new HashMap<>();
    public void unload(String identity){pendingFunctions.remove(identity);}
    private static String dimension(MachineBlockEntity be){return be.getLevel().dimension().identifier().toString();}
    public void prepare(MachineBlockEntity be,List<FunctionJob> jobs){
        if(!be.running)return;FunctionJob pending=pendingFunctions.get(be.identity.toString());if(pending!=null&&!pending.done){jobs.add(pending);return;}
        ServerLevel level=(ServerLevel)be.getLevel();IndustrialRules rules=IndustrialRules.current();
        if(be.device().has(DeviceType.Capability.LOGISTICS)){if(Math.floorMod(level.getGameTime()+be.getBlockPos().asLong(),rules.transferIntervals[be.tier()-1])!=0)return;FunctionJob j=transfer(be);if(j!=null){pendingFunctions.put(be.identity.toString(),j);jobs.add(j);}}
        else {int width=rules.harvestSizes[be.tier()-1],area=width*width;int phase=be.harvestPhase%rules.harvestPeriod;int checks=area*(phase+1)/rules.harvestPeriod-area*phase/rules.harvestPeriod;be.harvestPhase=(phase+1)%rules.harvestPeriod;List<FunctionJob> ready=new ArrayList<>();for(int i=0;i<checks;i++){int at=Math.floorMod(be.harvestCursor++,area);BlockPos crop=be.getBlockPos().below().offset(at%width-width/2,0,at/width-width/2);FunctionJob j=harvest(be,crop);if(j!=null)ready.add(j);}if(!ready.isEmpty()){FunctionJob batch=new FunctionJob(be){long demand(){long n=0;for(var j:ready)if(!j.done)n=Math.addExact(n,j.demand());return n;}long run(long budget){long used=0;for(var j:ready)if(!j.done){long n=j.run(budget-used);if(n<0)j.done=true;else used+=n;}done=ready.stream().allMatch(j->j.done);return used==0&&done?-1:used;}};pendingFunctions.put(be.identity.toString(),batch);jobs.add(batch);}be.setChanged();}
    }
    private FunctionJob transfer(MachineBlockEntity be){
        ServerLevel level=(ServerLevel)be.getLevel();if(be.targetDimension.isEmpty()){be.status="尚未绑定目标容器";return null;}if(!be.targetDimension.equals(dimension(be))){be.status="目标不在同一维度";return null;}BlockPos sourcePos=be.getBlockPos().below(),target=BlockPos.of(be.targetPosition);if(!level.hasChunkAt(sourcePos)||!level.hasChunkAt(target)){be.status="目标区块未加载";return null;}int tier=be.tier()-1;double range=IndustrialRules.current().transferRanges[tier];if(be.tier()<3&&be.getBlockPos().distSqr(target)>range*range){be.status="目标超出传送距离";return null;}if(sourcePos.equals(target)||sameChest(level,sourcePos,target)){be.status="源与目标为同一容器";return null;}
        ResourceHandler<ItemResource> source=transferSource(level,sourcePos),destination=level.getCapability(Capabilities.Item.BLOCK,target,be.targetFace);if(source==null||destination==null||source==destination){be.status="源或目标容器不可用";return null;}
        var sourceEntity=level.getBlockEntity(sourcePos);var targetEntity=level.getBlockEntity(target);var sourceBlock=level.getBlockState(sourcePos).getBlock();var targetBlock=level.getBlockState(target).getBlock();
        return new FunctionJob(be){int left=IndustrialRules.current().transferAmounts[tier];final long cost=IndustrialRules.current().transferEnergy[tier];long demand(){return Math.multiplyExact(cost,left);}long run(long budget){if(!level.hasChunkAt(target)||!level.hasChunkAt(sourcePos)||level.getBlockEntity(target)!=targetEntity||level.getBlockEntity(sourcePos)!=sourceEntity||!level.getBlockState(target).is(targetBlock)||!level.getBlockState(sourcePos).is(sourceBlock)){done=true;return -1;}var currentSource=transferSource(level,sourcePos);var currentTarget=level.getCapability(Capabilities.Item.BLOCK,target,be.targetFace);if(currentSource==null||currentTarget==null){done=true;return -1;}boolean possible=false;try(var simulation=Transaction.openRoot()){for(int index=0;index<currentSource.size();index++){ItemResource item=currentSource.getResource(index);if(!item.isEmpty()&&currentSource.extract(index,item,1,simulation)==1&&currentTarget.insert(item,1,simulation)==1){possible=true;break;}}}if(!possible){done=true;be.status="源为空或目标拒绝接收";return -1;}int quota=cost==0?left:(int)Math.min(left,budget/cost);if(quota<=0){be.status="当前供能不足";return 0;}int moved=0;for(int slot=0;slot<currentSource.size()&&moved<quota;slot++){int index=(slot+be.roundRobin)%currentSource.size();ItemResource r=currentSource.getResource(index);if(r.isEmpty())continue;try(var tx=Transaction.openRoot()){int inserted=currentTarget.insert(r,Math.min(quota-moved,currentSource.getAmountAsInt(index)),tx);if(inserted>0&&currentSource.extract(index,r,inserted,tx)==inserted){tx.commit();moved+=inserted;}}}left-=moved;be.roundRobin=(be.roundRobin+1)%Math.max(1,currentSource.size());be.status=moved>0?"已传送 "+moved+" 件":"源为空或目标拒绝接收";if(left==0||moved==0)done=true;return moved*cost;}};
    }
    private ResourceHandler<ItemResource> transferSource(ServerLevel level,BlockPos pos){
        // Our machines expose only product slots on the bottom; never use their full inventory.
        Direction face=level.getBlockEntity(pos) instanceof MachineBlockEntity?Direction.DOWN:Direction.UP;
        return level.getCapability(Capabilities.Item.BLOCK,pos,face);
    }
    private boolean sameChest(ServerLevel level,BlockPos a,BlockPos b){BlockState state=level.getBlockState(a);return state.getBlock() instanceof ChestBlock&&state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE&&a.relative(ChestBlock.getConnectedDirection(state)).equals(b);}
    private FunctionJob harvest(MachineBlockEntity be,BlockPos pos){
        ServerLevel level=(ServerLevel)be.getLevel();BlockPos output=be.getBlockPos().below();if(!level.hasChunkAt(pos)||!level.hasChunkAt(output))return null;ResourceHandler<ItemResource> target=level.getCapability(Capabilities.Item.BLOCK,output,Direction.UP);if(target==null){be.status="下方没有可接收容器";return null;}
        BlockState state=level.getBlockState(pos);String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();if(!IndustrialRules.current().harvestCrops.contains(id))return null;IntegerProperty age=state.getProperties().stream().filter(p->p instanceof IntegerProperty&&p.getName().equals("age")).map(p->(IntegerProperty)p).findFirst().orElse(null);if(age==null||state.getValue(age)!=Collections.max(age.getPossibleValues()))return null;
        List<ItemStack> drops=Block.getDrops(state,level,pos,level.getBlockEntity(pos),null,ItemStack.EMPTY);BlockState reset=state.setValue(age,Collections.min(age.getPossibleValues()));long cost=IndustrialRules.current().harvestEnergy;
        var outputEntity=level.getBlockEntity(output);
        return new FunctionJob(be){long demand(){return cost;}long run(long budget){if(!level.hasChunkAt(pos)||!level.getBlockState(pos).equals(state)||!level.hasChunkAt(output)||level.getBlockEntity(output)!=outputEntity){done=true;return -1;}var receiver=level.getCapability(Capabilities.Item.BLOCK,output,Direction.UP);if(receiver==null){done=true;return -1;}try(var tx=Transaction.openRoot()){for(ItemStack s:drops)if(receiver.insert(ItemResource.of(s),s.getCount(),tx)!=s.getCount()){be.status="下方容器装不下完整掉落";return -1;}if(budget<cost){be.status="当前供能不足";return 0;}new SnapshotJournal<BlockState>(){protected BlockState createSnapshot(){return level.getBlockState(pos);}protected void revertToSnapshot(BlockState old){level.setBlock(pos,old,2);}protected void onRootCommit(BlockState old){level.sendBlockUpdated(pos,old,reset,3);}}.updateSnapshots(tx);level.setBlock(pos,reset,2);tx.commit();done=true;be.status="作物已收获并重置";return cost;}}};
    }
}
