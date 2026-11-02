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
    public void prepare(MachineBlockEntity be,List<FunctionJob> jobs){
        if(!be.running)return;
        if(be.device().has(DeviceType.Capability.LOGISTICS)||be.device().has(DeviceType.Capability.FLUID_LOGISTICS)){jobs.add(CrystalTransfers.prepare(be));return;}
        FunctionJob pending=pendingFunctions.get(be.identity.toString());if(pending!=null&&!pending.done){jobs.add(pending);return;}
        FunctionJob job=null;var rules=IndustrialRules.current();
        if(be.device().has(DeviceType.Capability.PUMP)){job=IndustrialFluidWork.pump(be);}
        else {
            int width=rules.harvestSizes[be.tier()-1],area=width*width;
            be.functionTime=be.functionTime.add(be.pluginSpeed().multiply(area).divide(rules.harvestPeriod));
            long whole=be.functionTime.numerator().divide(be.functionTime.denominator()).longValueExact();be.functionTime=be.functionTime.subtract(cn.yumis.fight.modules.industry.api.Fraction.of(whole));
            int checks=(int)Math.min(area,whole);List<FunctionJob> ready=new ArrayList<>();
            if(be.functionCredit==0)be.functionCost=Math.multiplyExact(rules.harvestEnergy,be.energyMultiplier());
            for(int i=0;i<checks;i++){int at=Math.floorMod(be.harvestCursor++,area);BlockPos crop=be.getBlockPos().below().offset(at%width-width/2,0,at/width-width/2);var work=harvest(be,crop);if(work!=null)ready.add(work);}
            if(!ready.isEmpty())job=payment(new FunctionJob(be){
                long demand(){long result=0;for(var child:ready)if(!child.done){long n=child.demand();result=result>Long.MAX_VALUE-n?Long.MAX_VALUE:result+n;}return result;}
                long run(long budget){long used=0;for(var child:ready)if(!child.done){long n=child.run(budget-used);if(n<0)child.done=true;else used+=n;}done=ready.stream().allMatch(child->child.done);return used==0&&done?-1:used;}
            });
        }
        if(job!=null){pendingFunctions.put(be.identity.toString(),job);jobs.add(job);}be.setChanged();
    }
    private FunctionJob payment(FunctionJob delegate){
        var be=delegate.be;return new FunctionJob(be){
            long demand(){return Math.max(0,delegate.demand()-be.functionCredit);}
            long run(long budget){long credit=be.functionCredit;long spent=delegate.run(Math.addExact(credit,budget));done=delegate.done;
                if(spent<0)return -1;if(spent==0&&!done){be.functionCredit=Math.addExact(credit,budget);be.setChanged();return budget;}
                be.functionCredit=Math.max(0,credit-spent);be.setChanged();return Math.max(0,spent-credit);
            }
        };
    }
    private FunctionJob harvest(MachineBlockEntity be,BlockPos pos){
        ServerLevel level=(ServerLevel)be.getLevel();BlockPos output=be.getBlockPos().below();if(!level.hasChunkAt(pos)||!level.hasChunkAt(output))return null;ResourceHandler<ItemResource> target=level.getCapability(Capabilities.Item.BLOCK,output,Direction.UP);if(target==null){be.status="下方没有可接收容器";return null;}
        BlockState state=level.getBlockState(pos);String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();if(!IndustrialRules.current().harvestCrops.contains(id))return null;IntegerProperty age=state.getProperties().stream().filter(p->p instanceof IntegerProperty&&p.getName().equals("age")).map(p->(IntegerProperty)p).findFirst().orElse(null);if(age==null||state.getValue(age)!=Collections.max(age.getPossibleValues()))return null;
        List<ItemStack> drops=Block.getDrops(state,level,pos,level.getBlockEntity(pos),null,ItemStack.EMPTY);BlockState reset=state.setValue(age,Collections.min(age.getPossibleValues()));long cost=be.functionCost;
        var outputEntity=level.getBlockEntity(output);
        return new FunctionJob(be){long demand(){return cost;}long run(long budget){if(!level.hasChunkAt(pos)||!level.getBlockState(pos).equals(state)||!level.hasChunkAt(output)||level.getBlockEntity(output)!=outputEntity){done=true;return -1;}var receiver=level.getCapability(Capabilities.Item.BLOCK,output,Direction.UP);if(receiver==null){done=true;return -1;}try(var tx=Transaction.openRoot()){for(ItemStack s:drops)if(receiver.insert(ItemResource.of(s),s.getCount(),tx)!=s.getCount()){be.status="下方容器装不下完整掉落";return -1;}if(budget<cost){be.status="当前供能不足";return 0;}new SnapshotJournal<BlockState>(){protected BlockState createSnapshot(){return level.getBlockState(pos);}protected void revertToSnapshot(BlockState old){level.setBlock(pos,old,2);}protected void onRootCommit(BlockState old){level.sendBlockUpdated(pos,old,reset,3);}}.updateSnapshots(tx);level.setBlock(pos,reset,2);tx.commit();done=true;be.status="作物已收获并重置";return cost;}}};
    }
}
